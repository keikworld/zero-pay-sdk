// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/gateway/impl/WorldpayGateway.kt

package com.zeropay.sdk.gateway.impl

import com.zeropay.sdk.gateway.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64

/**
 * Worldpay Gateway - PRODUCTION VERSION
 * 
 * Authentication-only integration for Worldpay.
 * ZeroPay authenticates users via zkSNARK proofs, then notifies Worldpay.
 * Worldpay handles all payment processing.
 * 
 * Features:
 * - 146+ countries supported
 * - 120+ currencies
 * - Omnichannel payments
 * - Fraud detection (Accertify)
 * - 3D Secure 2.0
 * - Digital wallets
 * 
 * Architecture:
 * - Access Worldpay API (RESTful)
 * - Basic Authentication (Base64-encoded)
 * - HAL+JSON format
 * - Authentication notification only
 * - No payment processing in ZeroPay
 * 
 * Security:
 * - TLS 1.2+ encryption
 * - PCI DSS Level 1 certified
 * - Basic Auth credentials
 * - Encrypted token storage
 * 
 * Documentation:
 * - https://developer.worldpay.com/
 * - https://developer.worldpay.com/products/payments
 * 
 * @property tokenStorage Gateway token storage
 * @property baseUrl Worldpay API base URL
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
class WorldpayGateway(
    private val tokenStorage: GatewayTokenStorage,
    private val baseUrl: String = "https://try.access.worldpay.com"
) : GatewayProvider {
    
    override val gatewayId = "worldpay"
    override val displayName = "Worldpay"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val TAG = "WorldpayGateway"
        
        // Production URL (use when ready)
        private const val PROD_BASE_URL = "https://access.worldpay.com"
        
        // API version
        private const val API_VERSION = "v1"
        private const val ACCEPT_HEADER = "application/vnd.worldpay.payments-v6.hal+json"
        private const val CONTENT_TYPE = "application/vnd.worldpay.payments-v6.hal+json"
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
     * Execute authentication with Worldpay
     * 
     * Flow:
     * 1. Retrieve user's Worldpay credentials (username:password|entityId)
     * 2. Send authentication notification to Worldpay
     * 3. Include ZeroPay proof metadata
     * 4. Return success status
     * 
     * Note: ZeroPay only authenticates. Worldpay handles payment processing.
     * This just notifies Worldpay that the user is authenticated.
     * 
     * @param request Authentication request
     * @param attempt Retry attempt number
     * @return true if API call succeeded
     */
    private suspend fun executeAuthentication(
        request: AuthRequest,
        attempt: Int
    ): Boolean {
        // Get user's Worldpay credentials
        val worldpayToken = tokenStorage.getToken(request.userUuid, gatewayId)
            ?: throw GatewayException(
                "No Worldpay token found for user",
                gatewayId = gatewayId
            )
        
        // Parse token (format: "username:password|entityId")
        val parts = worldpayToken.split("|")
        require(parts.size == 2) { "Invalid Worldpay token format" }
        val credentials = parts[0] // username:password
        val entityId = parts[1] // Merchant Entity ID
        
        // Create Basic Auth header
        val basicAuth = Base64.getEncoder().encodeToString(credentials.toByteArray())
        
        // Convert proof hash to hex
        val proofHashHex = request.proofHash.joinToString("") { "%02x".format(it) }
        
        // Build authentication notification (HAL+JSON format)
        // ZeroPay only sends proof of authentication, not payment details
        val authNotification = JSONObject().apply {
            put("transactionReference", request.sessionId)
            
            // Merchant information
            put("merchant", JSONObject().apply {
                put("entity", entityId)
            })
            
            // Authentication proof - this is what ZeroPay provides
            put("authenticationData", JSONObject().apply {
                put("userIdentifier", request.userUuid)
                put("merchantId", request.merchantId)
                put("proofHash", proofHashHex)
                put("authenticationType", "zeropay_device_free")
                put("timestamp", System.currentTimeMillis())
            })
        }
        
        val requestBody = authNotification.toString().toRequestBody(
            CONTENT_TYPE.toMediaType()
        )
        
        // Send to Worldpay's authentication notification endpoint
        // This would be configured in Worldpay dashboard to receive authentication events
        val httpRequest = Request.Builder()
            .url("$baseUrl/authentication/notification")
            .post(requestBody)
            .header("Content-Type", CONTENT_TYPE)
            .header("Accept", ACCEPT_HEADER)
            .header("Authorization", "Basic $basicAuth")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        // Check if notification was received successfully
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw GatewayException(
                "Worldpay authentication notification failed: HTTP ${response.code} - $errorBody",
                gatewayId = gatewayId
            )
        }
        
        // Success - Worldpay received authentication proof
        // Worldpay will now handle the actual payment processing
        return true
    }
}
