package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Nequi Provider - Hashed Reference Implementation
 * 
 * Nequi is Colombia's popular digital wallet
 * Uses phone number + UUID hashing
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class NequiProvider : PaymentProviderInterface {
    
    override val providerId = "nequi"
    override val providerName = "Nequi"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("Nequi uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for Nequi
     * 
     * @param uuid User UUID
     * @param identifier Phone number (format: +57xxxxxxxxxx)
     * @return Hashed reference
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.startsWith("+57")) { "Nequi requires Colombian phone number (+57)" }
        
        val salt = "zeropay.nequi.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        return digest.joinToString("") { "%02x".format(it) }
    }
}
