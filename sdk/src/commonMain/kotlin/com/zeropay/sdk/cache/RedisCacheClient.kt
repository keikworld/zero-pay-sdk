// File: sdk/src/commonMain/kotlin/com/zeropay/sdk/cache/RedisCacheClient.kt
package com.zeropay.sdk.cache

import com.zeropay.sdk.network.SecureApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Redis Cache Client
 * 
 * Communicates with backend Redis cache for enrollment data storage
 * 
 * Features:
 * - 24-hour TTL for enrollment data
 * - Automatic expiration
 * - Encrypted transmission
 * - Nonce-based replay protection
 * 
 * Cache Structure:
 * Key: enrollment:{userUuid}
 * Value: {
 *   "factors": {
 *     "PIN": "64-char-hex-digest",
 *     "PATTERN": "64-char-hex-digest"
 *   },
 *   "created_at": 1234567890,
 *   "expires_at": 1234654290,
 *   "device_id": "hashed-device-id"
 * }
 * TTL: 86400 seconds (24 hours)
 */
class RedisCacheClient(
    private val apiClient: SecureApiClient,
    private val baseUrl: String = "https://api.zeropay.com"
) {
    
    companion object {
        private const val CACHE_TTL_SECONDS = 86400 // 24 hours
    }
    
    /**
     * Store enrollment data in Redis with 24h TTL
     * 
     * @param userUuid User identifier
     * @param factorDigests Map of factor to SHA-256 digest (32 bytes each)
     * @param deviceId Hashed device identifier
     * @return Success status
     */
    suspend fun storeEnrollment(
        userUuid: String,
        factorDigests: Map<com.zeropay.sdk.Factor, ByteArray>,
        deviceId: String
    ): Result<EnrollmentResponse> = withContext(Dispatchers.IO) {
        try {
            // Validate inputs
            require(userUuid.isNotBlank()) { "User UUID cannot be empty" }
            require(factorDigests.size >= 2) { "At least 2 factors required for PSD3 SCA" }
            factorDigests.values.forEach { digest ->
                require(digest.size == 32) { "Each digest must be exactly 32 bytes (SHA-256)" }
            }
            
            // Convert digests to hex strings
            val factorsJson = JSONObject()
            factorDigests.forEach { (factor, digest) ->
                val hexDigest = digest.joinToString("") { "%02x".format(it) }
                factorsJson.put(factor.name, hexDigest)
            }
            
            // Build payload
            val now = System.currentTimeMillis()
            val expiresAt = now + (CACHE_TTL_SECONDS * 1000)
            
            val payload = JSONObject().apply {
                put("user_uuid", userUuid)
                put("factors", factorsJson)
                put("created_at", now)
                put("expires_at", expiresAt)
                put("device_id", deviceId)
                put("ttl_seconds", CACHE_TTL_SECONDS)
            }
            
            // Send to API
            val response = apiClient.post(
                url = "$baseUrl/v1/enrollment/store",
                body = payload.toString().toByteArray(),
                contentType = "application/json"
            )
            
            if (response.success) {
                Result.success(EnrollmentResponse(
                    success = true,
                    enrollmentId = userUuid,
                    expiresAt = expiresAt,
                    message = "Enrollment stored successfully"
                ))
            } else {
                Result.failure(Exception(response.errorMessage ?: "Failed to store enrollment"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Retrieve enrollment data from Redis
     * 
     * @param userUuid User identifier
     * @return Enrollment data if exists and not expired
     */
    suspend fun retrieveEnrollment(
        userUuid: String
    ): Result<EnrollmentData> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.get(
                url = "$baseUrl/v1/enrollment/retrieve/$userUuid"
            )
            
            if (response.success && response.data != null) {
                val json = JSONObject(String(response.data))
                
                // Parse factors
                val factorsJson = json.getJSONObject("factors")
                val factors = mutableMapOf<com.zeropay.sdk.Factor, ByteArray>()
                
                factorsJson.keys().forEach { factorName ->
                    val hexDigest = factorsJson.getString(factorName)
                    val digest = hexDigest.chunked(2)
                        .map { it.toInt(16).toByte() }
                        .toByteArray()
                    
                    try {
                        val factor = com.zeropay.sdk.Factor.valueOf(factorName)
                        factors[factor] = digest
                    } catch (e: IllegalArgumentException) {
                        // Skip unknown factors
                    }
                }
                
                Result.success(EnrollmentData(
                    userUuid = json.getString("user_uuid"),
                    factors = factors,
                    createdAt = json.getLong("created_at"),
                    expiresAt = json.getLong("expires_at"),
                    deviceId = json.getString("device_id")
                ))
            } else {
                Result.failure(Exception(response.errorMessage ?: "Enrollment not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete enrollment data from Redis (GDPR right to erasure)
     * 
     * @param userUuid User identifier
     * @return Success status
     */
    suspend fun deleteEnrollment(
        userUuid: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.delete(
                url = "$baseUrl/v1/enrollment/delete/$userUuid"
            )
            
            Result.success(response.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if enrollment exists and is not expired
     */
    suspend fun exists(userUuid: String): Boolean {
        return retrieveEnrollment(userUuid).isSuccess
    }
    
    data class EnrollmentResponse(
        val success: Boolean,
        val enrollmentId: String,
        val expiresAt: Long,
        val message: String
    )
    
    data class EnrollmentData(
        val userUuid: String,
        val factors: Map<com.zeropay.sdk.Factor, ByteArray>,
        val createdAt: Long,
        val expiresAt: Long,
        val deviceId: String
    )
}
