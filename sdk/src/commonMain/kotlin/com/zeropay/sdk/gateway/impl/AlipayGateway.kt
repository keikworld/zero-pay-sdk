package com.zeropay.sdk.gateway.impl

import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.gateway.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Alipay Gateway
 * 
 * Simplified authentication-only integration for Alipay.
 * Supports China and international markets.
 */
class AlipayGateway(
    private val tokenStorage: GatewayTokenStorage,
    private val baseUrl: String = "https://openapi.alipay.com/gateway.do"
) : GatewayProvider {
    
    override val gatewayId = "alipay"
    override val displayName = "Alipay"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    companion object {
        private const val CHARSET = "UTF-8"
        private const val SIGN_TYPE = "RSA2"
        private const val VERSION = "1.0"
        private const val FORMAT = "json"
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
        // Get user's Alipay credentials
        val alipayToken = tokenStorage.getToken(request.userUuid, gatewayId)
            ?: throw GatewayException(
                "No Alipay token found for user",
                gatewayId = gatewayId
            )
        
        // Parse token (format: "appId|privateKey")
        val (appId, privateKey) = alipayToken.split("|")
        
        // Convert proof hash to hex
        val proofHashHex = request.proofHash.joinToString("") { "%02x".format(it) }
        
        // Build business content
        val bizContent = JSONObject().apply {
            put("out_trade_no", request.sessionId)
            put("total_amount", String.format("%.2f", request.amount / 100.0))
            put("subject", "ZeroPay Authenticated Transaction")
            put("trans_currency", request.currency)
            
            // ZeroPay metadata
            val extendParams = JSONObject().apply {
                put("zeropay_user_uuid", request.userUuid)
                put("zeropay_merchant_id", request.merchantId)
                put("zeropay_proof_hash", proofHashHex)
                put("zeropay_authenticated", "true")
            }
            put("extend_params", extendParams)
        }
        
        // Build request parameters
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val params = mutableMapOf(
            "app_id" to appId,
            "method" to "alipay.trade.create",
            "charset" to CHARSET,
            "sign_type" to SIGN_TYPE,
            "timestamp" to timestamp,
            "version" to VERSION,
            "format" to FORMAT,
            "biz_content" to bizContent.toString()
        )
        
        // Generate signature
        val sign = generateSignature(params, privateKey)
        params["sign"] = sign
        
        // Build query string
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, CHARSET)}"
        }
        
        val url = "$baseUrl?$queryString"
        
        val httpRequest = Request.Builder()
            .url(url)
            .get()
            .build()
        
        val response = client.newCall(httpRequest).execute()
        
        // Only care if API call succeeded
        return response.isSuccessful
    }
    
    private fun generateSignature(params: Map<String, String>, privateKey: String): String {
        // Sort parameters
        val sortedParams = params.entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }
        
        // Generate SHA-256 hash (simplified - use RSA signing in production)
        val hash = CryptoUtils.sha256(sortedParams.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
