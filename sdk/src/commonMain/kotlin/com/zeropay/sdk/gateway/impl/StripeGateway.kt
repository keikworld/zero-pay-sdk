package com.zeropay.sdk.gateway.impl

import com.zeropay.sdk.gateway.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Stripe Gateway
 * 
 * Simplified authentication-only integration.
 * After zkSNARK verification, we just tell Stripe:
 * "This user is authenticated. Here's the proof hash."
 * 
 * Stripe handles the actual payment processing.
 */
class StripeGateway(
    private val tokenStorage: GatewayTokenStorage,
    private val baseUrl: String = "https://api.stripe.com"
) : GatewayProvider {
    
    override val gatewayId = "stripe"
    override val displayName = "Stripe"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val API_VERSION = "2024-12-18"
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
    
    private suspend fun executeAuthentication(
        request: AuthRequest,
        attempt: Int
    ): Boolean {
        // Get user's Stripe token
        val stripeToken = tokenStorage.getToken(request.userUuid, gatewayId)
            ?: throw GatewayException(
                "No Stripe token found for user",
                gatewayId = gatewayId
            )
        
        // Convert proof hash to hex string
        val proofHashHex = request.proofHash.joinToString("") { "%02x".format(it) }
        
        // Create Payment Intent with ZeroPay authentication
        val formBody = FormBody.Builder()
            .add("amount", request.amount.toString())
            .add("currency", request.currency.lowercase())
            .add("customer", stripeToken)
            .add("confirm", "true")
            .add("automatic_payment_methods[enabled]", "true")
            .add("metadata[zeropay_user_uuid]", request.userUuid)
            .add("metadata[zeropay_merchant_id]", request.merchantId)
            .add("metadata[zeropay_session_id]", request.sessionId)
            .add("metadata[zeropay_proof_hash]", proofHashHex)
            .add("metadata[zeropay_authenticated]", "true")
            .build()
        
        val httpRequest = Request.Builder()
            .url("$baseUrl/v1/payment_intents")
            .post(formBody)
            .header("Authorization", "Bearer $stripeToken")
            .header("Stripe-Version", API_VERSION)
            .header("Idempotency-Key", "${request.sessionId}-attempt-$attempt")
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        // We only care if the API call succeeded (network level)
        // Don't care about payment success/failure (Stripe handles that)
        return response.isSuccessful
    }
}
