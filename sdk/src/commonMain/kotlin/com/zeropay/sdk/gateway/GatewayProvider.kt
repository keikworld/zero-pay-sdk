package com.zeropay.sdk.gateway

/**
 * Gateway Provider Interface
 * 
 * Simplified for ZeroPay's authentication-only model.
 * After zkSNARK verification, we just hand off to the gateway.
 * Gateway handles all payment processing.
 * 
 * Zero-knowledge principle:
 * - Only send: userUuid, proof hash, amount, merchant
 * - Don't track payment success/failure
 * - Don't validate amounts/currencies
 * - Gateway does everything else
 */
interface GatewayProvider {
    
    /**
     * Unique gateway identifier
     * e.g., "stripe", "alipay", "payu"
     */
    val gatewayId: String
    
    /**
     * Human-readable name for UI
     * e.g., "Stripe", "Alipay", "PayU"
     */
    val displayName: String
    
    /**
     * Check if user has linked this gateway
     * 
     * @param userUuid User identifier (SHA-256 hashed)
     * @return True if token exists in Redis
     */
    suspend fun isAvailable(userUuid: String): Boolean
    
    /**
     * Authenticate user to gateway
     * 
     * Called after zkSNARK verification succeeds.
     * Sends authentication proof to gateway API.
     * Gateway handles the actual payment processing.
     * 
     * We don't care about payment success/failure,
     * only that our API call succeeded (network level).
     * 
     * @param request Authentication request
     * @return True if API call succeeded, false if network error
     */
    suspend fun authenticate(request: AuthRequest): Boolean
}

/**
 * Authentication Request
 * 
 * Minimal data for post-authentication handoff.
 * 
 * @property userUuid User identifier (SHA-256 hashed)
 * @property proofHash zkSNARK proof hash (SHA-256, 32 bytes)
 * @property amount Transaction amount (cents/minor units)
 * @property currency ISO 4217 currency code
 * @property merchantId Merchant identifier
 * @property sessionId Unique session ID for idempotency
 */
data class AuthRequest(
    val userUuid: String,
    val proofHash: ByteArray,
    val amount: Long,
    val currency: String,
    val merchantId: String,
    val sessionId: String
) {
    init {
        require(userUuid.isNotBlank()) { "User UUID required" }
        require(proofHash.size == 32) { "Proof hash must be 32 bytes (SHA-256)" }
        require(amount > 0) { "Amount must be positive" }
        require(currency.length == 3) { "Currency must be 3-letter code" }
        require(merchantId.isNotBlank()) { "Merchant ID required" }
        require(sessionId.isNotBlank()) { "Session ID required" }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuthRequest) return false
        return userUuid == other.userUuid &&
                proofHash.contentEquals(other.proofHash) &&
                amount == other.amount &&
                currency == other.currency &&
                merchantId == other.merchantId &&
                sessionId == other.sessionId
    }
    
    override fun hashCode(): Int {
        var result = userUuid.hashCode()
        result = 31 * result + proofHash.contentHashCode()
        result = 31 * result + amount.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + merchantId.hashCode()
        result = 31 * result + sessionId.hashCode()
        return result
    }
}

/**
 * Gateway exception for errors
 */
class GatewayException(
    message: String,
    val gatewayId: String,
    cause: Throwable? = null
) : Exception(message, cause)
