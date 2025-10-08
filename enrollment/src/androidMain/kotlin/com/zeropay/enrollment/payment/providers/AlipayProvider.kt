package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Alipay Provider - Hashed Reference Implementation
 * 
 * Uses Alipay ID + UUID hashing
 * Popular in China and Asia
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class AlipayProvider : PaymentProviderInterface {
    
    override val providerId = "alipay"
    override val providerName = "Alipay"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("Alipay uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for Alipay
     * 
     * @param uuid User UUID
     * @param identifier Alipay ID or email
     * @return Hashed reference
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.isNotBlank()) { "Alipay ID cannot be blank" }
        
        val salt = "zeropay.alipay.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        return digest.joinToString("") { "%02x".format(it) }
    }
}
