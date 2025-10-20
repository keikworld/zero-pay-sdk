// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/payment/providers/TilopayProvider.kt

package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.security.CryptoUtils

/**
 * Tilopay Provider - Hashed Reference Implementation
 * 
 * Tilopay is a Panamanian payment gateway supporting:
 * - Cards (Visa, Mastercard, Amex)
 * - Yappy (Panama)
 * - SINPE Móvil (Costa Rica)
 * - Tokenization
 * 
 * Architecture:
 * - Hashed reference: email + UUID
 * - Region: Panama, Central America, Caribbean
 * - Authentication: API Key + Username + Password
 * - Test credentials available
 * 
 * Security:
 * - SHA-256 hashing
 * - Salt: zeropay.tilopay.v1
 * - Email validation
 * 
 * Documentation:
 * - https://tilopay.com/documentacion
 * - https://woocommerce.com/document/tilopay-gateway/
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
class TilopayProvider : PaymentProviderInterface {
    
    override val providerId = "tilopay"
    override val providerName = "Tilopay"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    companion object {
        private const val TAG = "TilopayProvider"
        
        // Test credentials (from documentation)
        const val TEST_API_KEY = "6609-5850-8330-8034-3464"
        const val TEST_USER = "lSrT45"
        const val TEST_PASSWORD = "Zlb8H9"
    }
    
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("Tilopay uses hashed reference, not OAuth")
    }
    
    /**
     * Generate hashed reference for Tilopay
     * 
     * Format: SHA-256(uuid:email:salt)
     * 
     * @param uuid User UUID
     * @param identifier Email address
     * @return Hashed reference (64-char hex)
     * @throws IllegalArgumentException if validation fails
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        // Input validation
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.isNotBlank()) { "Email cannot be blank" }
        require(identifier.contains("@")) { "Invalid email format for Tilopay" }
        require(identifier.length >= 5) { "Email too short" }
        require(identifier.length <= 254) { "Email too long (RFC 5321 limit)" }
        
        // Email format validation (basic)
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        require(identifier.matches(emailRegex)) { 
            "Invalid email format: must match pattern user@domain.tld" 
        }
        
        // Generate hashed reference
        val salt = "zeropay.tilopay.v1"
        val combined = "$uuid:$identifier:$salt"
        val digest = CryptoUtils.sha256(combined.toByteArray())
        
        // Convert to hex string
        val hashedRef = digest.joinToString("") { "%02x".format(it) }
        
        android.util.Log.d(TAG, "Generated Tilopay reference for email: ${identifier.take(3)}***")
        
        return hashedRef
    }
}
