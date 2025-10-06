package com.zeropay.enrollment

import com.zeropay.enrollment.factors.EmojiFactor
import com.zeropay.enrollment.factors.PatternFactor
import com.zeropay.enrollment.factors.PinFactor
import com.zeropay.enrollment.models.EnrollmentError
import com.zeropay.enrollment.models.EnrollmentResult
import com.zeropay.enrollment.models.User
import com.zeropay.enrollment.security.AliasGenerator
import com.zeropay.enrollment.security.UUIDManager
import com.zeropay.sdk.Factor
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.storage.KeyStoreManager

/**
 * Enrollment Manager - Main orchestrator for user enrollment
 * 
 * Features:
 * - UUID generation and alias creation
 * - Multi-factor enrollment
 * - Validation and security checks
 * - Redis caching (24h TTL)
 * - Integration with SDK storage
 */
class EnrollmentManager(
    private val keyStoreManager: KeyStoreManager,
    private val redisCacheClient: RedisCacheClient
) {
    
    /**
     * Enrolls user with multiple factors
     * 
     * @param pinValue Optional PIN (4-8 digits)
     * @param patternCoordinates Optional pattern (3-9 strokes)
     * @param emojiSequence Optional emoji sequence (3-6 emojis)
     * @return EnrollmentResult with success/failure details
     */
    suspend fun enroll(
        pinValue: String? = null,
        patternCoordinates: List<Int>? = null,
        emojiSequence: List<String>? = null
    ): EnrollmentResult {
        try {
            // Step 1: Generate UUID
            val uuid = UUIDManager.generateUUID()
            if (!UUIDManager.validateUUID(uuid)) {
                return EnrollmentResult.Failure(
                    EnrollmentError.CRYPTO_FAILURE,
                    "Failed to generate valid UUID"
                )
            }
            
            // Step 2: Generate alias
            val alias = AliasGenerator.generateAlias(uuid)
            if (!AliasGenerator.isValidAlias(alias)) {
                return EnrollmentResult.Failure(
                    EnrollmentError.CRYPTO_FAILURE,
                    "Failed to generate valid alias"
                )
            }
            
            // Step 3: Process factors
            val factorDigests = mutableMapOf<Factor, ByteArray>()
            var factorCount = 0
            
            // Process PIN
            pinValue?.let { pin ->
                val result = PinFactor.processPin(pin)
                if (result.isFailure) {
                    return EnrollmentResult.Failure(
                        EnrollmentError.WEAK_FACTOR,
                        result.exceptionOrNull()?.message ?: "Invalid PIN"
                    )
                }
                factorDigests[Factor.PIN] = result.getOrThrow()
                factorCount++
            }
            
            // Process Pattern
            patternCoordinates?.let { coords ->
                val result = PatternFactor.processPattern(coords)
                if (result.isFailure) {
                    return EnrollmentResult.Failure(
                        EnrollmentError.INVALID_FACTOR,
                        result.exceptionOrNull()?.message ?: "Invalid pattern"
                    )
                }
                factorDigests[Factor.PATTERN] = result.getOrThrow()
                factorCount++
            }
            
            // Process Emoji
            emojiSequence?.let { emojis ->
                val result = EmojiFactor.processEmojiSequence(emojis)
                if (result.isFailure) {
                    return EnrollmentResult.Failure(
                        EnrollmentError.INVALID_FACTOR,
                        result.exceptionOrNull()?.message ?: "Invalid emoji sequence"
                    )
                }
                factorDigests[Factor.EMOJI] = result.getOrThrow()
                factorCount++
            }
            
            // Validate: At least 2 factors required (PSD3 SCA)
            if (factorCount < 2) {
                return EnrollmentResult.Failure(
                    EnrollmentError.INVALID_FACTOR,
                    "At least 2 factors required for PSD3 SCA compliance"
                )
            }
            
            // Step 4: Store in KeyStore
            factorDigests.forEach { (factor, digest) ->
                keyStoreManager.storeEnrollment(uuid, factor, digest)
            }
            
            // Step 5: Cache in Redis
            val deviceId = "device_${System.currentTimeMillis()}" // Replace with actual device ID
            val cacheResult = redisCacheClient.storeEnrollment(uuid, factorDigests, deviceId)
            
            if (cacheResult.isFailure) {
                return EnrollmentResult.Failure(
                    EnrollmentError.CACHE_FAILURE,
                    "Failed to cache enrollment data"
                )
            }
            
            // Step 6: Create user object
            val user = User(
                uuid = uuid,
                alias = alias
            )
            
            return EnrollmentResult.Success(
                user = user,
                cacheKey = alias,
                factorCount = factorCount
            )
            
        } catch (e: Exception) {
            return EnrollmentResult.Failure(
                EnrollmentError.UNKNOWN,
                e.message ?: "Unknown error during enrollment"
            )
        }
    }
    
    /**
     * Checks if user is already enrolled
     */
    fun isEnrolled(uuid: String): Boolean {
        return keyStoreManager.getEnrolledFactors(uuid).isNotEmpty()
    }
    
    /**
     * Deletes enrollment (GDPR right to erasure)
     */
    suspend fun deleteEnrollment(uuid: String): Boolean {
        return try {
            // Delete from KeyStore
            keyStoreManager.deleteAllEnrollments(uuid)
            
            // Delete from Redis
            redisCacheClient.deleteEnrollment(uuid)
            
            true
        } catch (e: Exception) {
            false
        }
    }
}
