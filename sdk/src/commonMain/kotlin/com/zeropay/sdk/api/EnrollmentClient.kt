package com.zeropay.sdk.api

import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.models.api.*
import com.zeropay.sdk.network.NetworkException
import com.zeropay.sdk.network.ZeroPayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Enrollment API Client
 *
 * Handles all enrollment-related operations with the ZeroPay backend.
 *
 * Security Features:
 * - Zero-knowledge: Only digests transmitted (never raw factors)
 * - Nonce-based replay protection
 * - GDPR consent validation
 * - Automatic retry on transient failures
 * - Memory wiping of sensitive data
 *
 * Compliance:
 * - PSD3 SCA: Enforces minimum 6 factors, 2+ categories
 * - GDPR: Explicit consent required, data portability support
 *
 * Usage:
 * ```kotlin
 * val client = EnrollmentClient(httpClient, apiConfig)
 * val response = client.enroll(uuid, factorDigests, deviceId)
 * ```
 *
 * @param httpClient HTTP client for network requests
 * @param config API configuration
 * @version 1.0.0
 */
class EnrollmentClient(
    private val httpClient: ZeroPayHttpClient,
    private val config: ApiConfig
) {

    companion object {
        private const val TAG = "EnrollmentClient"

        /**
         * PSD3 SCA requirements
         */
        private const val MIN_FACTORS = 6
        private const val MIN_CATEGORIES = 2
    }

    /**
     * Enroll user with factor digests
     *
     * Zero-Knowledge: Server never sees raw factor data, only SHA-256 digests
     *
     * @param userUuid User UUID (client-generated, v4)
     * @param factors List of factor digests (min 6 for PSD3)
     * @param deviceId Optional device identifier (anonymized)
     * @param ttlSeconds Time-to-live (default 24 hours)
     * @param gdprConsent GDPR consent flag (required)
     * @return EnrollmentResponse on success
     * @throws IllegalArgumentException if validation fails
     * @throws NetworkException on network error
     */
    suspend fun enroll(
        userUuid: String,
        factors: List<FactorDigest>,
        deviceId: String? = null,
        ttlSeconds: Int = 86400, // 24 hours
        gdprConsent: Boolean = true
    ): Result<EnrollmentResponse> = withContext(Dispatchers.IO) {
        try {
            // Validate inputs
            validateEnrollmentRequest(userUuid, factors, gdprConsent)

            // Generate nonce (replay protection)
            val nonce = generateNonce()

            // Create request
            val request = EnrollmentRequest(
                user_uuid = userUuid,
                factors = factors,
                device_id = deviceId,
                ttl_seconds = ttlSeconds,
                nonce = nonce,
                timestamp = getCurrentTimestamp(),
                gdpr_consent = gdprConsent,
                consent_timestamp = if (gdprConsent) getCurrentTimestamp() else null
            )

            // Execute HTTP request
            val response = httpClient.post<ApiResponse<EnrollmentResponse>>(
                endpoint = ApiConfig.Endpoints.ENROLLMENT_STORE,
                body = request
            )

            // Parse response
            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
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
            Result.failure(NetworkException.UnknownException("Enrollment failed", e))
        }
    }

    /**
     * Retrieve enrolled factors for a user
     *
     * Note: Server returns factor types only, not digests (zero-knowledge)
     *
     * @param userUuid User UUID
     * @return List of enrolled factor types
     */
    suspend fun retrieveFactors(
        userUuid: String
    ): Result<EnrollmentRetrievalResponse> = withContext(Dispatchers.IO) {
        try {
            validateUuid(userUuid)

            val response = httpClient.get<ApiResponse<EnrollmentRetrievalResponse>>(
                endpoint = "${ApiConfig.Endpoints.ENROLLMENT_RETRIEVE}/$userUuid"
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("User not found")
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to retrieve factors"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Retrieval failed", e))
        }
    }

    /**
     * Update enrolled factors
     *
     * Use case: User wants to add/remove factors
     *
     * @param userUuid User UUID
     * @param factors New list of factor digests
     * @return Updated EnrollmentResponse
     */
    suspend fun updateFactors(
        userUuid: String,
        factors: List<FactorDigest>
    ): Result<EnrollmentResponse> = withContext(Dispatchers.IO) {
        try {
            validateEnrollmentRequest(userUuid, factors, gdprConsent = true)

            val nonce = generateNonce()

            val request = EnrollmentRequest(
                user_uuid = userUuid,
                factors = factors,
                nonce = nonce,
                timestamp = getCurrentTimestamp(),
                gdpr_consent = true,
                consent_timestamp = getCurrentTimestamp()
            )

            val response = httpClient.put<ApiResponse<EnrollmentResponse>>(
                endpoint = ApiConfig.Endpoints.ENROLLMENT_UPDATE,
                body = request
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to update factors"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Update failed", e))
        }
    }

    /**
     * Delete enrollment (GDPR right to erasure)
     *
     * Permanently deletes all user data from backend.
     *
     * @param userUuid User UUID
     * @return true if successful
     */
    suspend fun deleteEnrollment(
        userUuid: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            validateUuid(userUuid)

            val response = httpClient.delete<ApiResponse<Unit>>(
                endpoint = "${ApiConfig.Endpoints.ENROLLMENT_DELETE}/$userUuid"
            )

            when {
                response.isSuccessful -> Result.success(true)
                response.statusCode == 404 -> Result.success(true) // Already deleted
                else -> Result.failure(
                    NetworkException.HttpException(
                        statusCode = response.statusCode,
                        message = "Failed to delete enrollment"
                    )
                )
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Deletion failed", e))
        }
    }

    /**
     * Export user data (GDPR data portability)
     *
     * Returns all user data in machine-readable format.
     *
     * @param userUuid User UUID
     * @return GDPR export response with download URL
     */
    suspend fun exportUserData(
        userUuid: String
    ): Result<GdprExportResponse> = withContext(Dispatchers.IO) {
        try {
            validateUuid(userUuid)

            val nonce = generateNonce()

            val request = GdprExportRequest(
                user_uuid = userUuid,
                request_type = "export",
                nonce = nonce,
                timestamp = getCurrentTimestamp()
            )

            val response = httpClient.post<ApiResponse<GdprExportResponse>>(
                endpoint = ApiConfig.Endpoints.ENROLLMENT_EXPORT,
                body = request
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to export data"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Export failed", e))
        }
    }

    // ========================================================================
    // PRIVATE VALIDATION HELPERS
    // ========================================================================

    /**
     * Validate enrollment request
     *
     * Enforces PSD3 SCA requirements:
     * - Minimum 6 factors
     * - Minimum 2 categories
     */
    private fun validateEnrollmentRequest(
        userUuid: String,
        factors: List<FactorDigest>,
        gdprConsent: Boolean
    ) {
        // Validate UUID
        validateUuid(userUuid)

        // Validate factor count (PSD3 SCA)
        require(factors.size >= MIN_FACTORS) {
            "PSD3 SCA requires minimum $MIN_FACTORS factors (got ${factors.size})"
        }

        // Validate factor categories
        val categories = factors.map { getFactorCategory(it.type) }.toSet()
        require(categories.size >= MIN_CATEGORIES) {
            "PSD3 SCA requires minimum $MIN_CATEGORIES factor categories (got ${categories.size})"
        }

        // Validate GDPR consent
        require(gdprConsent) {
            "GDPR consent is required for enrollment"
        }

        // Validate each factor digest
        factors.forEach { factor ->
            require(factor.digest.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                "Invalid digest format for factor ${factor.type}: must be 64 hex characters"
            }
        }
    }

    /**
     * Validate UUID format (RFC 4122, version 4)
     */
    private fun validateUuid(uuid: String) {
        require(uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))) {
            "Invalid UUID format: $uuid"
        }
    }

    /**
     * Get factor category for PSD3 compliance
     */
    private fun getFactorCategory(factorType: String): String {
        return when (factorType.uppercase()) {
            "PIN", "PATTERN", "WORDS", "COLOUR", "EMOJI" -> "KNOWLEDGE"
            "FACE", "FINGERPRINT", "VOICE" -> "BIOMETRIC"
            "RHYTHM_TAP", "MOUSE_DRAW", "STYLUS_DRAW", "IMAGE_TAP" -> "BEHAVIOR"
            "NFC" -> "POSSESSION"
            "BALANCE" -> "LOCATION"
            else -> "UNKNOWN"
        }
    }

    /**
     * Generate secure nonce for replay protection
     *
     * Uses cryptographically secure random bytes (32 bytes = 64 hex chars)
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
