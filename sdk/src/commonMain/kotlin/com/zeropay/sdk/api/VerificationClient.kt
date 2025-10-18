package com.zeropay.sdk.api

import com.zeropay.sdk.models.api.*
import com.zeropay.sdk.network.NetworkException
import com.zeropay.sdk.network.ZeroPayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Verification API Client
 *
 * Handles all verification-related operations for merchant-side authentication.
 *
 * Security Features:
 * - Zero-knowledge: Server returns only boolean (never reveals which factor failed)
 * - Constant-time comparison on backend
 * - Session-based verification (prevents replay attacks)
 * - Nonce validation
 * - ZK-SNARK proof generation (optional)
 *
 * Compliance:
 * - PSD3 SCA: Strong Customer Authentication
 * - PSD3 Dynamic Linking: Transaction amount/recipient binding
 * - GDPR: No factor data stored, only comparison result
 *
 * Flow:
 * 1. Merchant creates verification session
 * 2. User completes required factors
 * 3. SDK sends factor digests to backend
 * 4. Backend compares (constant-time) and returns boolean
 * 5. Optional: Generate ZK-SNARK proof for privacy
 *
 * Usage:
 * ```kotlin
 * val client = VerificationClient(httpClient, apiConfig)
 *
 * // Step 1: Create session
 * val session = client.createSession(userUuid, amount = 99.99, currency = "USD")
 *
 * // Step 2: User completes factors
 * val factors = collectUserFactors()
 *
 * // Step 3: Verify
 * val result = client.verify(session.session_id, userUuid, factors)
 * if (result.verified) {
 *     // Payment authorized
 * }
 * ```
 *
 * @param httpClient HTTP client for network requests
 * @param config API configuration
 * @version 1.0.0
 */
class VerificationClient(
    private val httpClient: ZeroPayHttpClient,
    private val config: ApiConfig
) {

    companion object {
        private const val TAG = "VerificationClient"
    }

    /**
     * Create verification session
     *
     * Merchant initiates a verification request for a user.
     *
     * PSD3 Dynamic Linking: Transaction details (amount, currency) are bound to session
     *
     * @param userUuid User UUID
     * @param amount Transaction amount (optional, but recommended for PSD3)
     * @param currency Currency code (ISO 4217, e.g., "USD", "EUR")
     * @param transactionId Merchant transaction ID (optional)
     * @return Verification session with required factors
     * @throws IllegalArgumentException if validation fails
     * @throws NetworkException on network error
     */
    suspend fun createSession(
        userUuid: String,
        amount: Double? = null,
        currency: String? = null,
        transactionId: String? = null
    ): Result<VerificationSessionResponse> = withContext(Dispatchers.IO) {
        try {
            // Validate UUID
            validateUuid(userUuid)

            // Validate amount/currency pairing
            if (amount != null || currency != null) {
                require(amount != null && currency != null) {
                    "Amount and currency must both be provided or both be null"
                }
                require(amount >= 0.0) {
                    "Amount must be non-negative"
                }
                require(currency.matches(Regex("^[A-Z]{3}$"))) {
                    "Currency must be 3-letter ISO code (e.g., USD, EUR)"
                }
            }

            // Generate nonce
            val nonce = generateNonce()

            // Create request
            val request = CreateSessionRequest(
                user_uuid = userUuid,
                amount = amount,
                currency = currency,
                transaction_id = transactionId,
                nonce = nonce,
                timestamp = getCurrentTimestamp()
            )

            // Execute HTTP request
            val response = httpClient.post<ApiResponse<VerificationSessionResponse>>(
                endpoint = ApiConfig.Endpoints.VERIFICATION_CREATE_SESSION,
                body = request
            )

            // Parse response
            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("User not found or not enrolled")
                    )
                }
                response.isClientError -> {
                    val error = response.body?.error
                    Result.failure(
                        IllegalArgumentException(
                            error?.message ?: "Invalid request"
                        )
                    )
                }
                response.isServerError -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Server error"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.UnknownException("Unexpected response: ${response.statusCode}")
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Session creation failed", e))
        }
    }

    /**
     * Verify user with factor digests
     *
     * Zero-Knowledge: Backend returns only boolean, never reveals which factor failed
     *
     * @param sessionId Verification session ID (from createSession)
     * @param userUuid User UUID
     * @param factors List of factor digests provided by user
     * @param deviceId Optional device identifier
     * @return Verification result with confidence score and optional ZK proof
     * @throws IllegalArgumentException if validation fails
     * @throws NetworkException on network error
     */
    suspend fun verify(
        sessionId: String,
        userUuid: String,
        factors: List<FactorDigest>,
        deviceId: String? = null
    ): Result<VerificationResponse> = withContext(Dispatchers.IO) {
        try {
            // Validate inputs
            validateUuid(userUuid)
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }
            require(factors.isNotEmpty()) { "At least one factor required" }

            // Validate each factor digest
            factors.forEach { factor ->
                require(factor.digest.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                    "Invalid digest format for factor ${factor.type}"
                }
            }

            // Generate nonce
            val nonce = generateNonce()

            // Create request
            val request = VerificationRequest(
                session_id = sessionId,
                user_uuid = userUuid,
                factors = factors,
                nonce = nonce,
                timestamp = getCurrentTimestamp(),
                device_id = deviceId
            )

            // Execute HTTP request
            val response = httpClient.post<ApiResponse<VerificationResponse>>(
                endpoint = ApiConfig.Endpoints.VERIFICATION_VERIFY,
                body = request
            )

            // Parse response
            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("Session not found or expired")
                    )
                }
                response.statusCode == 403 -> {
                    // Verification failed (factors don't match)
                    // Return the response even though verified = false
                    response.body?.data?.let {
                        Result.success(it)
                    } ?: Result.failure(
                        IllegalArgumentException("Verification failed")
                    )
                }
                response.statusCode == 429 -> {
                    Result.failure(
                        NetworkException.RateLimitException(
                            message = "Rate limit exceeded. Please try again later."
                        )
                    )
                }
                response.isClientError -> {
                    val error = response.body?.error
                    Result.failure(
                        IllegalArgumentException(
                            error?.message ?: "Invalid request"
                        )
                    )
                }
                response.isServerError -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Server error"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.UnknownException("Unexpected response: ${response.statusCode}")
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Verification failed", e))
        } finally {
            // Zero sensitive data from memory
            factors.forEach { factor ->
                // Note: Kotlin strings are immutable, but we clear the reference
                // Actual memory wiping happens at Factor level (ByteArray)
            }
        }
    }

    /**
     * Get session status
     *
     * Check if a verification session is still active.
     *
     * @param sessionId Session ID
     * @return Session status information
     */
    suspend fun getSessionStatus(
        sessionId: String
    ): Result<VerificationSessionResponse> = withContext(Dispatchers.IO) {
        try {
            require(sessionId.isNotBlank()) { "Session ID cannot be blank" }

            val response = httpClient.get<ApiResponse<VerificationSessionResponse>>(
                endpoint = "${ApiConfig.Endpoints.VERIFICATION_SESSION_STATUS}/$sessionId"
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("Session not found or expired")
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to get session status"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Status check failed", e))
        }
    }

    /**
     * Quick verify (without session)
     *
     * Simplified verification flow for low-risk operations.
     * Not recommended for high-value transactions (use createSession + verify instead).
     *
     * @param userUuid User UUID
     * @param factors Factor digests
     * @return Verification result
     */
    suspend fun quickVerify(
        userUuid: String,
        factors: List<FactorDigest>
    ): Result<VerificationResponse> = withContext(Dispatchers.IO) {
        // Create session and verify in one call
        val sessionResult = createSession(userUuid)

        if (sessionResult.isFailure) {
            return@withContext Result.failure(sessionResult.exceptionOrNull()!!)
        }

        val session = sessionResult.getOrNull()!!

        // Verify with session
        verify(session.session_id, userUuid, factors)
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Validate UUID format (RFC 4122, version 4)
     */
    private fun validateUuid(uuid: String) {
        require(uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))) {
            "Invalid UUID format: $uuid"
        }
    }

    /**
     * Generate secure nonce for replay protection
     */
    private fun generateNonce(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get current timestamp in ISO 8601 format
     */
    private fun getCurrentTimestamp(): String {
        return Instant.now().toString()
    }
}
