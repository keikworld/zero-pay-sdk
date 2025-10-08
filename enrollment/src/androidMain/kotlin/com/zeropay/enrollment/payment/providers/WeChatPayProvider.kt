package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * WeChat Pay Provider - Hashed Reference Implementation
 * 
 * Uses WeChat ID + UUID hashing
 * Popular in China
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class WeChatPayProvider : PaymentProviderInterface {
    
    override val providerId = "wechat"
    override val providerName = "WeChat Pay"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("WeChat Pay uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for WeChat Pay
     * 
     * @param uuid User UUID
     * @param identifier WeChat ID
     * @return Hashed reference
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.isNotBlank()) { "WeChat ID cannot be blank" }
        
        val salt = "zeropay.wechat.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        return digest.joinToString("") { "%02x".format(it) }
    }
}
