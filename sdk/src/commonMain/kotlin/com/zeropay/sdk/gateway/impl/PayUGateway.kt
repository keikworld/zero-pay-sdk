package com.zeropay.sdk.gateway.impl

import com.zeropay.sdk.gateway.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * PayU Gateway
 * 
 * Simplified authentication-only integration for PayU.
 * Supports Latin America, Central Europe, Middle East.
 */
class PayUGateway(
    private val tokenStorage: GatewayTokenStorage,
    private val baseUrl: String = "https://secure.payu.com"
) : GatewayProvider {
    
    override val gatewayId = "payu"
    override val displayName = "PayU"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
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
    
    private suspend fun executeAuthentication(
        request: AuthRequest,
        attempt: Int
    ): Boolean {
        // Get user's PayU credentials
        val payuToken = tokenStorage.getToken(request.userUuid, gatewayId)
            ?: throw GatewayException(
                "No PayU token found for user",
                gatewayId = gatewayId
            )
        
        // Parse token (format: "apiKey:merchantId")
        val (apiKey, merchantId) = payuToken.split(":")
        
        // Convert proof hash to hex
        val proofHashHex = request.proofHash.joinToString("") { "%02x".format(it) }
        
        // Create order with ZeroPay authentication
        val orderJson = JSONObject().apply {
            put("merchantPosId", merchantId)
            put("description", "ZeroPay Authenticated Transaction")
            put("currencyCode", request.currency)
            put("totalAmount", request.amount.toString())
            put("extOrderId", request.sessionId)
            
            // ZeroPay metadata
            val metadata = JSONObject().apply {
                put("zeropay_user_uuid", request.userUuid)
                put("zeropay_merchant_id", request.merchantId)
                put("zeropay_proof_hash", proofHashHex)
                put("zeropay_authenticated", "true")
            }
            put("metadata", metadata)
        }
        
        val requestBody = orderJson.toString()
            .toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url("$baseUrl/api/v2_1/orders")
            .post(requestBody)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        // Only care if API call succeeded
        return response.isSuccessful
    }
}
