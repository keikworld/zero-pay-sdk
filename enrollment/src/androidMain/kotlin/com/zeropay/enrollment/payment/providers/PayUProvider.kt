package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * PayU Provider - Hashed Reference Implementation
 * 
 * Uses email + UUID hashing for authentication
 * Popular in Latin America, Central Europe
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class PayUProvider : PaymentProviderInterface {
    
    override val providerId = "payu"
    override val providerName = "PayU"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("PayU uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for PayU
     * 
     * Format: SHA-256(uuid + email + salt)
     * 
     * @param uuid User UUID
     * @param identifier User email
     * @return Hashed reference (64-char hex)
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.contains("@")) { "Invalid email format" }
        
        val salt = "zeropay.payu.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        return digest.joinToString("") { "%02x".format(it) }
    }
}
