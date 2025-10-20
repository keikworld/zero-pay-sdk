// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/payment/providers/WorldpayProvider.kt

package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import com.zeropay.sdk.security.CryptoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worldpay Provider - Hashed Reference Implementation
 * 
 * Worldpay (FIS) is a global payment processing company.
 * 
 * Features:
 * - 146+ countries supported
 * - 120+ currencies
 * - Omnichannel payments
 * - Fraud detection (Accertify)
 * - 3D Secure 2.0
 * - Network tokenization
 * - Recurring payments
 * 
 * Architecture:
 * - Access Worldpay (modern API)
 * - Basic Authentication (Base64)
 * - Merchant Entity ID
 * - RESTful API
 * - HAL+JSON format
 * 
 * Products:
 * - Payments API (orchestrated)
 * - Hosted Payment Pages
 * - Card Payments
 * - Digital Wallets (Apple Pay, Google Pay)
 * - Alternative Payment Methods
 * 
 * Regions:
 * - North America, Europe
 * - Asia-Pacific, Latin America
 * - Middle East, Africa
 * 
 * Security:
 * - TLS 1.2+ encryption
 * - PCI DSS Level 1
 * - Basic Auth or API Key
 * - Certificate pinning supported
 * - Fraud screening
 * 
 * Documentation:
 * - https://developer.worldpay.com/
 * - https://developer.worldpay.com/products/payments
 * - https://docs.worldpay.com/apis/
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
class WorldpayProvider : PaymentProviderInterface {
    
    override val providerId = "worldpay"
    override val providerName = "Worldpay"
    override val linkType = EnrollmentConfig.PaymentLinkType.HASHED_REF
    
    companion object {
        private const val TAG = "WorldpayProvider"
        
        // API endpoints
        private const val TEST_BASE_URL = "https://try.access.worldpay.com"
        private const val PROD_BASE_URL = "https://access.worldpay.com"
        
        // Test credentials (for sandbox testing)
        const val TEST_USERNAME = "testMerchant"
        const val TEST_PASSWORD = "testPassword123"
        const val TEST_ENTITY_ID = "POxxxxxx"
    }
    
    /**
     * Initiate OAuth flow (not used for Worldpay)
     */
    override suspend fun initiateOAuthFlow(context: Context): String {
        throw UnsupportedOperationException("Worldpay uses hashed reference with Basic Auth, not OAuth")
    }
    
    /**
     * Generate hashed reference for Worldpay
     * 
     * Format: SHA-256(uuid:merchantEntityId:salt)
     * 
     * Worldpay uses Basic Authentication with username/password or API key.
     * The hashed reference serves as a unique identifier to link
     * the user to their Worldpay merchant account credentials.
     * 
     * @param uuid User UUID
     * @param identifier Merchant Entity ID (e.g., "POxxxxxx")
     * @return Hashed reference (64-char hex)
     * @throws IllegalArgumentException if validation fails
     */
    override fun generateHashedReference(uuid: String, identifier: String): String {
        // Input validation
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(identifier.isNotBlank()) { "Merchant Entity ID cannot be blank" }
        require(identifier.length >= 8) { "Merchant Entity ID too short" }
        require(identifier.length <= 50) { "Merchant Entity ID too long" }
        
        // Entity ID format validation (typically starts with "PO" or "default")
        val validPrefixes = listOf("PO", "default", "test")
        val hasValidPrefix = validPrefixes.any { identifier.startsWith(it, ignoreCase = true) }
        require(hasValidPrefix || identifier.matches(Regex("^[A-Za-z0-9]+$"))) {
            "Invalid Merchant Entity ID format. Should start with PO or be alphanumeric"
        }
        
        // Generate hashed reference
        val salt = "zeropay.worldpay.v1"
        val data = "$uuid:$identifier:$salt"
        val hash = CryptoUtils.sha256(data.toByteArray())
        
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Validate Merchant Entity ID format
     * 
     * @param entityId Merchant Entity ID
     * @return true if valid format
     */
    fun validateEntityId(entityId: String): Boolean {
        return when {
            entityId.isBlank() -> false
            entityId.length < 8 -> false
            entityId.length > 50 -> false
            !entityId.matches(Regex("^[A-Za-z0-9]+$")) -> false
            else -> true
        }
    }
}
