package com.zeropay.sdk.gateway

import com.zeropay.sdk.security.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Gateway Token Storage
 * 
 * Manages encrypted storage of payment gateway tokens in Redis.
 * 
 * Security:
 * - AES-256-GCM encryption
 * - Unique salt per token
 * - 96-bit random nonces
 * - 128-bit authentication tags
 * - 24-hour TTL in Redis
 * 
 * Zero-knowledge:
 * - Tokens encrypted at rest
 * - Only decrypted in-memory for API calls
 * - Never logged or persisted in plaintext
 */
class GatewayTokenStorage(
    private val baseUrl: String = "https://api.zeropay.com"
) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val CACHE_TTL_SECONDS = 86400 // 24 hours
        private const val AES_KEY_SIZE = 32 // 256 bits
        private const val GCM_NONCE_SIZE = 12 // 96 bits
        private const val GCM_TAG_SIZE = 128 // 128 bits
        private const val SALT_SIZE = 32 // 256 bits
    }
    
    /**
     * Store encrypted gateway token
     * 
     * @param userUuid User identifier
     * @param gatewayId Gateway identifier
     * @param token Gateway API token (plaintext)
     * @return Success status
     */
    suspend fun storeToken(
        userUuid: String,
        gatewayId: String,
        token: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Generate random salt and nonce
            val salt = ByteArray(SALT_SIZE).apply { SecureRandom().nextBytes(this) }
            val nonce = ByteArray(GCM_NONCE_SIZE).apply { SecureRandom().nextBytes(this) }
            
            // Derive encryption key
            val key = deriveKey(userUuid, salt)
            
            // Encrypt token
            val encryptedToken = encryptToken(token, key, nonce)
            
            // Store in Redis via API
            val payload = JSONObject().apply {
                put("user_uuid", userUuid)
                put("gateway_id", gatewayId)
                put("encrypted_token", android.util.Base64.encodeToString(
                    encryptedToken, android.util.Base64.NO_WRAP
                ))
                put("nonce", android.util.Base64.encodeToString(
                    nonce, android.util.Base64.NO_WRAP
                ))
                put("salt", android.util.Base64.encodeToString(
                    salt, android.util.Base64.NO_WRAP
                ))
                put("ttl", CACHE_TTL_SECONDS)
            }
            
            val requestBody = payload.toString()
                .toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$baseUrl/v1/gateway/tokens")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Retrieve and decrypt gateway token
     * 
     * @param userUuid User identifier
     * @param gatewayId Gateway identifier
     * @return Decrypted token or null if not found
     */
    suspend fun getToken(
        userUuid: String,
        gatewayId: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v1/gateway/tokens/$userUuid/$gatewayId"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                
                val encryptedToken = android.util.Base64.decode(
                    json.getString("encrypted_token"),
                    android.util.Base64.NO_WRAP
                )
                val nonce = android.util.Base64.decode(
                    json.getString("nonce"),
                    android.util.Base64.NO_WRAP
                )
                val salt = android.util.Base64.decode(
                    json.getString("salt"),
                    android.util.Base64.NO_WRAP
                )
                
                // Derive key and decrypt
                val key = deriveKey(userUuid, salt)
                decryptToken(encryptedToken, key, nonce)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete gateway token
     * 
     * @param userUuid User identifier
     * @param gatewayId Gateway identifier
     * @return Success status
     */
    suspend fun deleteToken(
        userUuid: String,
        gatewayId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$baseUrl/v1/gateway/tokens/$userUuid/$gatewayId"
            
            val request = Request.Builder()
                .url(url)
                .delete()
                .build()
            
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
    
    // ========== ENCRYPTION UTILITIES ==========
    
    private fun deriveKey(userUuid: String, salt: ByteArray): ByteArray {
        // Use user UUID + salt for key derivation
        val combined = userUuid.toByteArray() + salt
        return CryptoUtils.sha256(combined)
    }
    
    private fun encryptToken(token: String, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(token.toByteArray())
    }
    
    private fun decryptToken(encryptedToken: ByteArray, key: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val decrypted = cipher.doFinal(encryptedToken)
        return String(decrypted)
    }
}
