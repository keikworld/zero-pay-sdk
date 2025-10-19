// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/VerificationManager.kt

package com.zeropay.merchant.verification

import android.util.Log
import com.zeropay.merchant.config.MerchantConfig
import com.zeropay.merchant.fraud.FraudDetector
import com.zeropay.merchant.fraud.RateLimiter
import com.zeropay.merchant.models.*
import com.zeropay.sdk.Factor
import com.zeropay.sdk.api.VerificationClient
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.crypto.CryptoUtils
import com.zeropay.sdk.integration.BackendIntegration
import com.zeropay.sdk.models.api.FactorDigest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Verification Manager - PRODUCTION VERSION v2.0
 *
 * Core orchestrator for merchant-side user verification.
 *
 * NEW IN V2.0:
 * - ✅ Backend API integration with automatic fallback
 * - ✅ Circuit breaker pattern for resilience
 * - ✅ Session management via API
 * - ✅ Zero-knowledge verification via backend
 *
 * Process Flow:
 * 1. Create session with UUID (via API or local)
 * 2. Retrieve enrolled factors from backend/cache
 * 3. Challenge user to complete factors
 * 4. Verify via API (with constant-time comparison on backend)
 * 5. Generate ZK-SNARK proof (optional)
 * 6. Return verification result
 *
 * Security:
 * - Constant-time digest comparison (backend)
 * - Rate limiting per user/device
 * - Fraud detection
 * - Session timeout enforcement
 * - Attempt tracking
 *
 * @param redisCacheClient Cache client for factor retrieval (fallback)
 * @param verificationClient Backend API client (optional, primary)
 * @param backendIntegration Backend integration utility (optional)
 * @param digestComparator Constant-time comparator (local fallback)
 * @param proofGenerator ZK-SNARK proof generator
 * @param fraudDetector Fraud detection system
 * @param rateLimiter Rate limiter
 *
 * @version 2.0.0
 * @date 2025-10-18
 */
class VerificationManager(
    private val redisCacheClient: RedisCacheClient,
    private val verificationClient: VerificationClient? = null,
    private val backendIntegration: BackendIntegration? = null,
    private val digestComparator: DigestComparator,
    private val proofGenerator: ProofGenerator,
    private val fraudDetector: FraudDetector,
    private val rateLimiter: RateLimiter
) {
    
    companion object {
        private const val TAG = "VerificationManager"
    }
    
    // Active sessions
    private val activeSessions = ConcurrentHashMap<String, VerificationSession>()
    
    /**
     * Create verification session
     * 
     * @param userId User UUID
     * @param merchantId Merchant identifier
     * @param transactionAmount Transaction amount
     * @param deviceFingerprint Optional device fingerprint
     * @param ipAddress Optional IP address
     * @return Verification session or error
     */
    suspend fun createSession(
        userId: String,
        merchantId: String,
        transactionAmount: Double,
        deviceFingerprint: String? = null,
        ipAddress: String? = null
    ): Result<VerificationSession> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating verification session for user: $userId")
            
            // Check rate limiting
            if (!rateLimiter.allowVerificationAttempt(userId, deviceFingerprint, ipAddress)) {
                return@withContext Result.failure(
                    Exception("Rate limit exceeded. Please try again later.")
                )
            }
            
            // Check fraud detection
            val fraudCheck = fraudDetector.checkFraud(userId, deviceFingerprint, ipAddress)
            if (!fraudCheck.isLegitimate) {
                Log.w(TAG, "Fraud detected for user: $userId - ${fraudCheck.reason}")
                return@withContext Result.failure(
                    Exception("Verification blocked: ${fraudCheck.reason}")
                )
            }
            
            // Try API session creation first
            val apiSession = tryApiSessionCreation(userId, merchantId, transactionAmount)
            if (apiSession != null) {
                Log.d(TAG, "Session created via API: ${apiSession.sessionId}")
                activeSessions[apiSession.sessionId] = apiSession
                return@withContext Result.success(apiSession)
            }

            Log.w(TAG, "API session creation unavailable/failed, using local session")

            // FALLBACK: Retrieve enrolled factors from cache
            val factorsResult = redisCacheClient.retrieveEnrollment(userId)
            if (factorsResult.isFailure) {
                return@withContext Result.failure(
                    Exception("User not found or enrollment expired")
                )
            }

            val enrolledFactors = factorsResult.getOrNull()!!

            // Validate minimum factors (PSD3 SCA)
            if (enrolledFactors.size < MerchantConfig.MIN_FACTORS_REQUIRED) {
                return@withContext Result.failure(
                    Exception("Insufficient factors enrolled")
                )
            }

            // Create local session
            val session = VerificationSession(
                sessionId = UUID.randomUUID().toString(),
                userId = userId,
                merchantId = merchantId,
                transactionAmount = transactionAmount,
                requiredFactors = enrolledFactors.keys.toList(),
                deviceFingerprint = deviceFingerprint,
                ipAddress = ipAddress
            )
            
            activeSessions[session.sessionId] = session
            
            Log.i(TAG, "Verification session created: ${session.sessionId}")
            Result.success(session)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Submit factor for verification
     * 
     * @param sessionId Session identifier
     * @param factor Factor being submitted
     * @param digest Submitted digest
     * @return Verification result
     */
    suspend fun submitFactor(
        sessionId: String,
        factor: Factor,
        digest: ByteArray
    ): VerificationResult = withContext(Dispatchers.IO) {
        try {
            val session = activeSessions[sessionId]
                ?: return@withContext VerificationResult.Failure(
                    sessionId = sessionId,
                    error = MerchantConfig.VerificationError.INVALID_UUID,
                    message = "Session not found",
                    attemptNumber = 0,
                    canRetry = false
                )
            
            // Check session expiration
            if (session.isExpired()) {
                activeSessions.remove(sessionId)
                return@withContext VerificationResult.Failure(
                    sessionId = sessionId,
                    error = MerchantConfig.VerificationError.TIMEOUT,
                    message = "Session expired",
                    attemptNumber = session.attemptCount,
                    canRetry = true
                )
            }
            
            // Check if factor is required
            if (!session.requiredFactors.contains(factor)) {
                return@withContext VerificationResult.Failure(
                    sessionId = sessionId,
                    error = MerchantConfig.VerificationError.FACTOR_MISMATCH,
                    message = "Factor not required for this user",
                    attemptNumber = session.attemptCount,
                    canRetry = true
                )
            }
            
            // Add to session
            session.addCompletedFactor(factor, digest)
            
            // Check if all factors completed
            if (session.isComplete()) {
                // Verify all digests
                verifySession(session)
            } else {
                // More factors needed
                VerificationResult.PendingFactors(
                    sessionId = sessionId,
                    remainingFactors = session.requiredFactors.filter { !session.completedFactors.contains(it) },
                    completedFactors = session.completedFactors.toList()
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit factor", e)
            VerificationResult.Failure(
                sessionId = sessionId,
                error = MerchantConfig.VerificationError.UNKNOWN,
                message = e.message ?: "Unknown error",
                attemptNumber = 0,
                canRetry = true
            )
        }
    }
    
    /**
     * Verify complete session
     * 
     * @param session Verification session
     * @return Verification result
     */
    private suspend fun verifySession(session: VerificationSession): VerificationResult {
        session.attemptCount++

        try {
            Log.d(TAG, "Verifying session: ${session.sessionId}")

            // Try API verification first
            val apiResult = tryApiVerification(session)
            if (apiResult != null) {
                Log.d(TAG, "Verification completed via API")
                return apiResult
            }

            Log.w(TAG, "API verification unavailable/failed, using local verification")

            // FALLBACK: Retrieve enrolled digests from cache
            val enrolledDigestsResult = redisCacheClient.retrieveEnrollment(session.userId)
            if (enrolledDigestsResult.isFailure) {
                return VerificationResult.Failure(
                    sessionId = session.sessionId,
                    error = MerchantConfig.VerificationError.USER_NOT_FOUND,
                    message = "Enrollment data not found",
                    attemptNumber = session.attemptCount,
                    canRetry = false
                )
            }

            val enrolledDigests = enrolledDigestsResult.getOrNull()!!
            
            // Compare all digests (constant-time)
            val allMatch = session.submittedDigests.all { (factor, submittedDigest) ->
                val enrolledDigest = enrolledDigests[factor]
                if (enrolledDigest == null) {
                    Log.w(TAG, "No enrolled digest for factor: ${factor.name}")
                    false
                } else {
                    digestComparator.compare(submittedDigest, enrolledDigest)
                }
            }
            
            if (!allMatch) {
                // Check max attempts
                if (session.isMaxAttemptsReached()) {
                    activeSessions.remove(session.sessionId)
                    return VerificationResult.Failure(
                        sessionId = session.sessionId,
                        error = MerchantConfig.VerificationError.DIGEST_VERIFICATION_FAILED,
                        message = "Maximum attempts reached",
                        attemptNumber = session.attemptCount,
                        canRetry = false
                    )
                }
                
                return VerificationResult.Failure(
                    sessionId = session.sessionId,
                    error = MerchantConfig.VerificationError.DIGEST_VERIFICATION_FAILED,
                    message = "Authentication failed. Please try again.",
                    attemptNumber = session.attemptCount,
                    canRetry = true
                )
            }
            
            // Generate ZK-SNARK proof (optional)
            var zkProof: ByteArray? = null
            if (MerchantConfig.ENABLE_ZK_PROOF) {
                val proofResult = proofGenerator.generateProof(
                    userId = session.userId,
                    factors = session.submittedDigests
                )
                if (proofResult.isSuccess) {
                    zkProof = proofResult.getOrNull()
                } else {
                    Log.w(TAG, "ZK proof generation failed: ${proofResult.exceptionOrNull()?.message}")
                }
            }
            
            // Success!
            activeSessions.remove(session.sessionId)
            
            Log.i(TAG, "Verification successful: ${session.sessionId}")
            
            VerificationResult.Success(
                sessionId = session.sessionId,
                userId = session.userId,
                merchantId = session.merchantId,
                verifiedFactors = session.completedFactors.toList(),
                zkProof = zkProof
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Verification error", e)
            VerificationResult.Failure(
                sessionId = session.sessionId,
                error = MerchantConfig.VerificationError.UNKNOWN,
                message = e.message ?: "Unknown error",
                attemptNumber = session.attemptCount,
                canRetry = true
            )
        }
    }
    
    /**
     * Get active session
     * 
     * @param sessionId Session identifier
     * @return Session or null
     */
    fun getSession(sessionId: String): VerificationSession? {
        return activeSessions[sessionId]
    }
    
    /**
     * Cancel session
     * 
     * @param sessionId Session identifier
     */
    fun cancelSession(sessionId: String) {
        activeSessions.remove(sessionId)
        Log.d(TAG, "Session cancelled: $sessionId")
    }
    
    /**
     * Clean up expired sessions
     */
    fun cleanupExpiredSessions() {
        val now = System.currentTimeMillis()
        val expiredSessions = activeSessions.filter { (_, session) ->
            session.expiresAt < now
        }

        expiredSessions.forEach { (sessionId, _) ->
            activeSessions.remove(sessionId)
            Log.d(TAG, "Expired session removed: $sessionId")
        }
    }

    // ==================== API INTEGRATION METHODS ====================

    /**
     * Try API session creation with automatic fallback
     *
     * @return VerificationSession if API succeeded, null if unavailable/failed
     */
    private suspend fun tryApiSessionCreation(
        userId: String,
        merchantId: String,
        transactionAmount: Double
    ): VerificationSession? {
        if (verificationClient == null) {
            Log.d(TAG, "VerificationClient not configured, skipping API session creation")
            return null
        }

        return try {
            val transactionId = "TXN-${System.currentTimeMillis()}"

            // Create session via API
            val apiResponse = if (backendIntegration != null) {
                backendIntegration.execute(
                    primary = {
                        verificationClient.createSession(
                            userUuid = userId,
                            transactionId = transactionId,
                            amount = transactionAmount,
                            currency = "USD" // TODO: Make configurable
                        ).getOrThrow()
                    },
                    fallback = null,
                    operationName = "create-session"
                )
            } else {
                verificationClient.createSession(
                    userUuid = userId,
                    transactionId = transactionId,
                    amount = transactionAmount,
                    currency = "USD"
                ).getOrThrow()
            }

            // Convert API response to local VerificationSession
            val requiredFactors = apiResponse.required_factors.mapNotNull { factorName ->
                try {
                    Factor.valueOf(factorName)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Unknown factor type from API: $factorName")
                    null
                }
            }

            VerificationSession(
                sessionId = apiResponse.session_id,
                userId = userId,
                merchantId = merchantId,
                transactionAmount = transactionAmount,
                requiredFactors = requiredFactors,
                deviceFingerprint = null,
                ipAddress = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "API session creation failed: ${e.message}")
            null
        }
    }

    /**
     * Try API verification with automatic fallback
     *
     * @return VerificationResult if API succeeded, null if unavailable/failed
     */
    private suspend fun tryApiVerification(
        session: VerificationSession
    ): VerificationResult? {
        if (verificationClient == null) {
            Log.d(TAG, "VerificationClient not configured, skipping API verification")
            return null
        }

        return try {
            // Convert completed factors to API format
            val factorDigests = session.submittedDigests.map { (factor, digest) ->
                FactorDigest(
                    type = factor.name,
                    digest = CryptoUtils.bytesToHex(digest)
                )
            }

            // Verify via API
            val apiResponse = if (backendIntegration != null) {
                backendIntegration.execute(
                    primary = {
                        verificationClient.verify(
                            sessionId = session.sessionId,
                            userUuid = session.userId,
                            factors = factorDigests
                        ).getOrThrow()
                    },
                    fallback = null,
                    operationName = "verification"
                )
            } else {
                verificationClient.verify(
                    sessionId = session.sessionId,
                    userUuid = session.userId,
                    factors = factorDigests
                ).getOrThrow()
            }

            // Convert API response to VerificationResult
            if (apiResponse.verified) {
                VerificationResult.Success(
                    sessionId = session.sessionId,
                    userId = session.userId,
                    verifiedAt = System.currentTimeMillis(),
                    confidenceScore = apiResponse.confidence_score,
                    factorsUsed = session.completedFactors.toList(),
                    zkProof = apiResponse.zk_proof
                )
            } else {
                VerificationResult.Failure(
                    sessionId = session.sessionId,
                    error = MerchantConfig.VerificationError.FACTOR_MISMATCH,
                    message = "Verification failed",
                    attemptNumber = session.attemptCount,
                    canRetry = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "API verification failed: ${e.message}")
            null
        }
    }
}
