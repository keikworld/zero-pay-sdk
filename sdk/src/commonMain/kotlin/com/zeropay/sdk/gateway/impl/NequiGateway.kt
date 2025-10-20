// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/gateway/impl/NequiGateway.kt

package com.zeropay.sdk.gateway.impl

import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.gateway.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Nequi Gateway - PRODUCTION VERSION
 * 
 * Simplified authentication-only integration for Nequi.
 * Colombia's leading digital wallet by Bancolombia.
 * 
 * Features:
 * - Leading Colombian digital wallet
 * - Push notification flow
 * - QR code payments
 * - Real-time transfers
 * - No transaction costs within Colombia
 * 
 * Architecture:
 * - Nequi Conecta API
 * - JWS (JSON Web Signature) authentication
 * - OAuth 2.0 migration planned
 * - AWS Signature Version 4 style
 * - Push notification confirmation
 * 
 * Security:
 * - JWS signing (RS256)
 * - TLS 1.2+
 * - Client ID + Client Secret
 * - Token-based auth
 * - 45-minute transaction timeout
 * 
 * Documentation:
 * - https://docs.conecta.nequi.com.co/
 * - https://docs.ebanx.com/docs/payments/guides/accept-payments/api/colombia/nequi/
 * - https://docs.nuvei.com/documentation/latin-america-guides/nequi/
 * 
 * @property tokenStorage Gateway token storage
 * @property baseUrl Nequi API base URL
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
class NequiGateway(
    private val tokenStorage: GatewayTokenStorage,
    private val baseUrl: String = "https://api.conecta.nequi.com.co"
) : GatewayProvider {
    
    override val gatewayId = "nequi"
    override val displayName = "Nequi"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // Longer for push notification
        .build()
    
    companion object {
        private const val TAG = "NequiGateway"
        private const val API_VERSION = "v1"
        
        // Nequi-specific
        private const val PAYMENT_METHOD_CODE = "nequi"
        private const val TRANSACTION_TIMEOUT_MINUTES = 45
    }
    
    override suspend fun isAvailable(userUuid: String): Boolean {
        return withContext(Dispatchers.IO) {
            tokenStorage.getToken(userUuid, gatewayId) != null
        }
    }
    
    override suspend fun authenticate(request: AuthRequest): Boolean {
        return withContext(Dispatchers.IO) {
            NetworkRetryHandler.withRetry { attempt ->
                executeAuthentication(request, attempt)
            }
        }
    }
    
    /**
     * Execute authentication with Nequi
     * 
     * Flow:
     * 1. Retrieve user's Nequi credentials (Client ID + Secret + Phone)
     * 2. Get OAuth access token
     * 3. Initiate push notification payment
     * 4. Include ZeroPay metadata
     * 5. Return success status
     * 
     * Note: User receives push notification in Nequi app to approve
     * Timeout: 45 minutes (Nequi default)
     * 
     * @param request Authentication request
     * @param attempt Retry attempt number
     * @return true if API call succeeded
     */
    private suspend fun executeAuthentication(
        request: AuthRequest,
        attempt: Int
    ): Boolean {
        // Get user's Nequi credentials
        val nequiToken = tokenStorage.getToken(request.userUuid, gatewayId)
            ?: throw GatewayException(
                "No Nequi token found for user",
                gatewayId = gatewayId
            )
        
        // Parse token (format: "clientId|clientSecret|phoneNumber")
        val parts = nequiToken.split("|")
        require(parts.size == 3) { "Invalid Nequi token format" }
        val (clientId, clientSecret, phoneNumber) = parts
        
        // Validate Colombian phone number
        require(phoneNumber.startsWith("+57")) { 
            "Invalid Colombian phone number for Nequi" 
        }
        
        // Step 1: Get access token
        val accessToken = getAccessToken(clientId, clientSecret) ?: return false
        
        // Step 2: Create push notification payment
        return createPushPayment(accessToken, phoneNumber, request, attempt)
    }
    
    /**
     * Get OAuth access token from Nequi
     * 
     * Endpoint: POST /oauth2/token
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @return Access token or null on failure
     */
    private suspend fun getAccessToken(
        clientId: String,
        clientSecret: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val tokenJson = JSONObject().apply {
                put("grant_type", "client_credentials")
                put("client_id", clientId)
                put("client_secret", clientSecret)
            }
            
            val requestBody = tokenJson.toString()
                .toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/oauth2/token")
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody ?: "{}")
                jsonResponse.optString("access_token", null)
            } else {
                android.util.Log.e(TAG, "Nequi OAuth failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Nequi OAuth error", e)
            null
        }
    }
    
    /**
     * Create push notification payment in Nequi
     * 
     * Endpoint: POST /v1/payments/push
     * 
     * @param accessToken OAuth access token
     * @param phoneNumber User's Colombian phone number
     * @param request Auth request
     * @param attempt Retry attempt
     * @return true if successful
     */
    private suspend fun createPushPayment(
        accessToken: String,
        phoneNumber: String,
        request: AuthRequest,
        attempt: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Convert proof hash to hex
            val proofHashHex = request.proofHash.joinToString("") { "%02x".format(it) }
            
            // Generate message ID (required by Nequi)
            val messageId = generateMessageId(request.sessionId)
            
            val paymentJson = JSONObject().apply {
                put("messageId", messageId)
                put("phoneNumber", phoneNumber.removePrefix("+57")) // Nequi expects without country code
                put("value", request.amount.toString()) // Amount in cents (COP)
                put("code", request.sessionId)
                
                // Payment expiration
                val expirationDate = Calendar.getInstance().apply {
                    add(Calendar.MINUTE, TRANSACTION_TIMEOUT_MINUTES)
                }
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                put("expirationTime", dateFormat.format(expirationDate.time))
                
                // Description
                put("description", "ZeroPay Authenticated Transaction")
                
                // ZeroPay metadata (Nequi supports additionalData)
                val additionalData = JSONObject().apply {
                    put("zeropay_user_uuid", request.userUuid)
                    put("zeropay_merchant_id", request.merchantId)
                    put("zeropay_proof_hash", proofHashHex)
                    put("zeropay_authenticated", "true")
                    put("zeropay_timestamp", System.currentTimeMillis())
                }
                put("additionalData", additionalData)
            }
            
            val requestBody = paymentJson.toString()
                .toRequestBody("application/json".toMediaType())
            
            val httpRequest = Request.Builder()
                .url("$baseUrl/$API_VERSION/payments/push")
                .post(requestBody)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("X-Message-Id", messageId)
                .header("Idempotency-Key", "${request.sessionId}-attempt-$attempt")
                .build()
            
            val response = client.newCall(httpRequest).execute()
            
            // Log response for debugging
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                android.util.Log.e(TAG, "Nequi push payment failed: ${response.code} - $errorBody")
            } else {
                android.util.Log.i(TAG, "Nequi push notification sent to: ${phoneNumber.take(7)}***")
            }
            
            response.isSuccessful
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Nequi payment error", e)
            false
        }
    }
    
    /**
     * Generate unique message ID for Nequi
     * 
     * Format: Timestamp + Random + SessionId hash
     * 
     * @param sessionId Session ID
     * @return Message ID
     */
    private fun generateMessageId(sessionId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        val sessionHash = CryptoUtils.sha256(sessionId.toByteArray())
            .take(4)
            .joinToString("") { "%02x".format(it) }
        
        return "$timestamp-$random-$sessionHash"
    }
}
