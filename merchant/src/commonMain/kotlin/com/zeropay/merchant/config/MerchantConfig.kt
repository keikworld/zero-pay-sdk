// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/config/MerchantConfig.kt

package com.zeropay.merchant.config

/**
 * Merchant Configuration - PRODUCTION VERSION
 * 
 * Central configuration for merchant verification system.
 * 
 * Features:
 * - Verification limits and timeouts
 * - Security parameters
 * - Payment gateway settings
 * - Fraud detection thresholds
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
object MerchantConfig {
    
    // ==================== VERIFICATION LIMITS ====================
    
    const val MAX_VERIFICATION_ATTEMPTS = 3
    const val VERIFICATION_TIMEOUT_SECONDS = 300 // 5 minutes
    const val FACTOR_INPUT_TIMEOUT_SECONDS = 60 // 1 minute per factor
    const val MIN_FACTORS_REQUIRED = 2 // PSD3 SCA minimum
    
    // ==================== SECURITY ====================
    
    const val ENABLE_RATE_LIMITING = true
    const val MAX_ATTEMPTS_PER_HOUR = 5
    const val LOCKOUT_DURATION_MINUTES = 30
    const val ENABLE_DEVICE_FINGERPRINTING = true
    const val ENABLE_FRAUD_DETECTION = true
    
    // ==================== DIGEST COMPARISON ====================
    
    const val DIGEST_COMPARISON_TIMEOUT_MS = 100 // Constant-time operation
    const val DIGEST_SIZE_BYTES = 32 // SHA-256
    
    // ==================== ZK-SNARK ====================
    
    const val ENABLE_ZK_PROOF = true
    const val ZK_CIRCUIT_SIZE_KB = 80 // Groth16 circuit
    const val ZK_PROOF_GENERATION_TIMEOUT_MS = 2000
    
    // ==================== PAYMENT GATEWAYS ====================
    
    const val GATEWAY_TIMEOUT_SECONDS = 30
    const val ENABLE_MULTI_GATEWAY_FAILOVER = true

    /**
     * Payment Gateway Enum - ALL 13 GATEWAYS
     * 
     * @version 1.0.1
     * @updated 2025-10-10
     * @changes
     * - Added: Adyen, Mercado Pago, Worldpay, Authorize.Net, Tilopay
     * - Removed: PayPal (not implemented)
     * 
     * OAuth Gateways (5):
     * - Google Pay, Apple Pay, Stripe, Adyen, Mercado Pago
     * 
     * Hashed Reference Gateways (8):
     * - PayU, Yappy, Nequi, Tilopay, Alipay, WeChat Pay, Worldpay, Authorize.Net
     */
    
    enum class PaymentGateway(val id: String, val displayName: String) {
        // ==================== OAuth Gateways (5) ====================
        GOOGLE_PAY("googlepay", "Google Pay"),
        APPLE_PAY("applepay", "Apple Pay"),
        STRIPE("stripe", "Stripe"),
        ADYEN("adyen", "Adyen"),
        MERCADOPAGO("mercadopago", "Mercado Pago"),
        
        // ==================== Hashed Reference Gateways (8) ====================
        PAYU("payu", "PayU"),
        YAPPY("yappy", "Yappy"),
        NEQUI("nequi", "Nequi"),
        TILOPAY("tilopay", "Tilopay"),
        ALIPAY("alipay", "Alipay"),
        WECHAT("wechat", "WeChat Pay"),
        WORLDPAY("worldpay", "Worldpay"),
        AUTHORIZENET("authorizenet", "Authorize.Net");
        
        companion object {
            /**
             * Get gateway by ID
             */
            fun fromId(id: String): PaymentGateway? {
                return values().find { it.id == id }
            }
            
            /**
             * Get all gateway IDs
             */
            fun getAllIds(): List<String> {
                return values().map { it.id }
            }
            
            /**
             * Validate gateway ID
             */
            fun isValidId(id: String): Boolean {
                return values().any { it.id == id }
            }
        }
    }
    
    // ==================== UUID PRESENTATION ====================
    
    enum class UUIDInputMethod {
        QR_CODE,      // Scan QR code
        NFC,          // Tap NFC device
        MANUAL_ENTRY, // Type UUID manually
        BLUETOOTH     // Bluetooth Low Energy
    }
    
    // ==================== TRANSACTION ====================
    
    const val TRANSACTION_MIN_AMOUNT = 0.01 // USD
    const val TRANSACTION_MAX_AMOUNT = 10000.00 // USD (SCA threshold)
    const val REQUIRE_SCA_ABOVE_AMOUNT = 30.00 // PSD3 threshold
    
    // ==================== LOGGING ====================
    
    const val ENABLE_AUDIT_LOGGING = true
    const val LOG_FAILED_ATTEMPTS = true
    const val LOG_SUCCESSFUL_VERIFICATIONS = true
    const val RETENTION_DAYS = 90 // GDPR compliance
    
    // ==================== VERIFICATION FLOW ====================
    
    enum class VerificationStage {
        UUID_INPUT,           // User presents UUID
        FACTOR_RETRIEVAL,     // Fetch enrolled factors
        FACTOR_CHALLENGE,     // User inputs factors
        DIGEST_COMPARISON,    // Verify digests
        PROOF_GENERATION,     // Generate ZK-SNARK proof
        PAYMENT_AUTHORIZATION,// Authorize payment
        TRANSACTION_COMPLETE  // Complete transaction
    }
    
    // ==================== ERROR HANDLING ====================
    
    enum class VerificationError {
        INVALID_UUID,
        USER_NOT_FOUND,
        CACHE_EXPIRED,
        FACTOR_MISMATCH,
        DIGEST_VERIFICATION_FAILED,
        PROOF_GENERATION_FAILED,
        PAYMENT_FAILED,
        TIMEOUT,
        RATE_LIMIT_EXCEEDED,
        FRAUD_DETECTED,
        NETWORK_ERROR,
        UNKNOWN
    }
}
