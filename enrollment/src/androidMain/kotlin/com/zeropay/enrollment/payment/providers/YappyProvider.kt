package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.security.CryptoUtils

/**
 * Yappy Provider - Hashed Reference Implementation
 * 
 * Yappy is Panama's popular mobile payment app
 * Uses phone number + UUID hashing
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class YappyProvider : PaymentProviderInterface {
    
    override val providerId = "yappy"
    override val providerName = "Yappy"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("Yappy uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for Yappy
     * 
     * @param uuid User UUID
     * @param identifier Phone number (format: +507xxxxxxxx)
     * @return Hashed reference
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.startsWith("+")) { "Phone must start with country code (+)" }
        
        val salt = "zeropay.yappy.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        return digest.joinToString("") { "%02x".format(it) }
    }
}
