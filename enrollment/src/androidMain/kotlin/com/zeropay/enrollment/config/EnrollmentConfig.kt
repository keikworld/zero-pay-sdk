package com.zeropay.enrollment.config

import com.zeropay.sdk.Factor

/**
 * Enrollment Configuration - PRODUCTION VERSION
 * 
 * Centralized configuration for enrollment rules and constraints.
 * 
 * Security:
 * - DoS Protection: Max factor limits
 * - PSD3 SCA: Min factor requirements
 * - GDPR: Consent requirements
 * 
 * Compliance:
 * - GDPR: Explicit consent, right to erasure
 * - PSD3 SCA: Multi-factor authentication
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
object EnrollmentConfig {
    
    // ==================== FACTOR REQUIREMENTS ====================
    
    /**
     * Minimum factors required for enrollment
     * Increased to 6 for enhanced security
     */
    const val MIN_FACTORS = 6
    
    /**
     * Maximum factors allowed (DoS protection)
     * Higher limit to accommodate 6-factor minimum
     */
    const val MAX_FACTORS = 15
    
    /**
     * Total available factors for selection
     * Excluding FINGERPRINT and FACE (biometric, handled separately)
     */
    val AVAILABLE_FACTORS = listOf(
        Factor.PIN,
        Factor.COLOUR,
        Factor.EMOJI,
        Factor.WORDS,
        Factor.PATTERN_MICRO,
        Factor.PATTERN_NORMAL,
        Factor.MOUSE_DRAW,
        Factor.STYLUS_DRAW,
        Factor.VOICE,
        Factor.IMAGE_TAP,
        Factor.BALANCE,
        Factor.RHYTHM_TAP,
        Factor.NFC
    )
    
    // ==================== CATEGORY REQUIREMENTS (PSD3 SCA) ====================
    
    /**
     * Minimum number of factor categories required
     * PSD3 SCA: Factors must be from different categories
     */
    const val MIN_CATEGORIES = 2
    
    /**
     * Factor categories for PSD3 SCA compliance
     */
    enum class FactorCategory {
        KNOWLEDGE,    // Something you know
        INHERENCE,    // Something you are
        POSSESSION    // Something you have
    }
    
    /**
     * Map factors to categories
     */
    val FACTOR_CATEGORIES = mapOf(
        // KNOWLEDGE (4 factors)
        Factor.PIN to FactorCategory.KNOWLEDGE,
        Factor.COLOUR to FactorCategory.KNOWLEDGE,
        Factor.EMOJI to FactorCategory.KNOWLEDGE,
        Factor.WORDS to FactorCategory.KNOWLEDGE,
        
        // INHERENCE (8 factors - behavioral biometrics)
        Factor.PATTERN_MICRO to FactorCategory.INHERENCE,
        Factor.PATTERN_NORMAL to FactorCategory.INHERENCE,
        Factor.MOUSE_DRAW to FactorCategory.INHERENCE,
        Factor.STYLUS_DRAW to FactorCategory.INHERENCE,
        Factor.VOICE to FactorCategory.INHERENCE,
        Factor.IMAGE_TAP to FactorCategory.INHERENCE,
        Factor.BALANCE to FactorCategory.INHERENCE,
        Factor.RHYTHM_TAP to FactorCategory.INHERENCE,
        
        // POSSESSION (1 factor)
        Factor.NFC to FactorCategory.POSSESSION
    )
    
    // ==================== PAYMENT PROVIDER CONFIGURATION ====================
    
    /**
     * Minimum payment providers to link
     */
    const val MIN_PAYMENT_PROVIDERS = 1
    
    /**
     * Maximum payment providers to link (DoS protection)
     */
    const val MAX_PAYMENT_PROVIDERS = 5
    
    /**
     * Payment link types
     */
    enum class PaymentLinkType {
        OAUTH,       // OAuth 2.0 flow (e.g., Stripe, Google Pay, Adyen, MercadoPago)
        HASHED_REF   // Hashed reference (e.g., PayU, Alipay, Worldpay, AuthorizeNet)
    }
    
    /**
     * Available payment providers - ALL 13 GATEWAYS
     * 
     * @version 1.0.1
     * @updated 2025-10-10 - Added Adyen, MercadoPago, Worldpay, AuthorizeNet
     */
    enum class PaymentProvider(
        val id: String,
        val displayName: String,
        val linkType: PaymentLinkType
    ) {
        // ==================== OAuth Gateways (5) ====================
        GOOGLE_PAY("googlepay", "Google Pay", PaymentLinkType.OAUTH),
        APPLE_PAY("applepay", "Apple Pay", PaymentLinkType.OAUTH),
        STRIPE("stripe", "Stripe", PaymentLinkType.OAUTH),
        ADYEN("adyen", "Adyen", PaymentLinkType.OAUTH),
        MERCADOPAGO("mercadopago", "Mercado Pago", PaymentLinkType.OAUTH),
        
        // ==================== Hashed Reference Gateways (8) ====================
        PAYU("payu", "PayU", PaymentLinkType.HASHED_REF),
        YAPPY("yappy", "Yappy", PaymentLinkType.HASHED_REF),
        NEQUI("nequi", "Nequi", PaymentLinkType.HASHED_REF),
        TILOPAY("tilopay", "Tilopay", PaymentLinkType.HASHED_REF),
        ALIPAY("alipay", "Alipay", PaymentLinkType.HASHED_REF),
        WECHAT("wechat", "WeChat Pay", PaymentLinkType.HASHED_REF),
        WORLDPAY("worldpay", "Worldpay", PaymentLinkType.HASHED_REF),
        AUTHORIZENET("authorizenet", "Authorize.Net", PaymentLinkType.HASHED_REF);
        
        companion object {
            /**
             * Get provider by ID
             */
            fun fromId(id: String): PaymentProvider? {
                return values().find { it.id == id }
            }
            
            /**
             * Get all OAuth providers
             */
            fun getOAuthProviders(): List<PaymentProvider> {
                return values().filter { it.linkType == PaymentLinkType.OAUTH }
            }
            
            /**
             * Get all hashed reference providers
             */
            fun getHashedRefProviders(): List<PaymentProvider> {
                return values().filter { it.linkType == PaymentLinkType.HASHED_REF }
            }
        }
    }
    
    // ==================== CACHE CONFIGURATION ====================
    
    /**
     * Redis cache TTL for enrollment data
     */
    const val CACHE_TTL_SECONDS = 86400 // 24 hours
    
    /**
     * Enrollment session timeout (milliseconds)
     */
    const val SESSION_TIMEOUT_MS = 1800000L // 30 minutes

    /**
     * Factor capture timeout per factor (2 minutes)
     */
    const val FACTOR_CAPTURE_TIMEOUT_MS = 2 * 60 * 1000L
    
    /**
     * Enable practice mode for new factors
     */
    const val ENABLE_PRACTICE_MODE = true
    
    /**
     * Number of practice attempts before actual capture
     */
    const val PRACTICE_ATTEMPTS = 2
    
    // ==================== SECURITY CONFIGURATION ====================
    
    /**
     * PBKDF2 iterations for key derivation
     */
    const val PBKDF2_ITERATIONS = 100_000
    
    /**
     * AES key length for payment token encryption
     */
    const val AES_KEY_LENGTH = 32 // 256 bits
    
    /**
     * Rate limiting: Max enrollment attempts per hour
     */
    const val MAX_ENROLLMENTS_PER_HOUR = 3
    
    /**
     * Rate limiting: Cooldown period after max attempts (milliseconds)
     */
    const val RATE_LIMIT_COOLDOWN_MS = 3600000L // 1 hour
    
    // ==================== GDPR CONFIGURATION ====================
    
    /**
     * Data retention period for enrollment (milliseconds)
     */
    const val DATA_RETENTION_MS = CACHE_TTL_SECONDS * 1000L
    
    /**
     * Required consent types for GDPR compliance
     */
    enum class ConsentType(val description: String) {
        DATA_PROCESSING(
            "I consent to the processing of my authentication factors " +
            "(stored as irreversible SHA-256 hashes)"
        ),
        DATA_STORAGE(
            "I consent to temporary storage of my enrollment data " +
            "(24-hour cache, automatically deleted)"
        ),
        PAYMENT_LINKING(
            "I consent to linking my payment provider accounts " +
            "(encrypted tokens, no raw payment data stored)"
        ),
        BIOMETRIC_PROCESSING(
            "I consent to processing of behavioral biometric patterns " +
            "(patterns only, no images or recordings stored)"
        ),
        TERMS_OF_SERVICE(
            "I agree to ZeroPay Terms of Service and Privacy Policy"
        ),
        GDPR_COMPLIANCE(
            "I understand my GDPR rights including right to erasure and data portability."
        )
    }
    
    // ==================== VALIDATION RULES ====================
    
    /**
     * Get display name for factor
     */
    fun getFactorDisplayName(factor: Factor): String = when (factor) {
        Factor.PIN -> "PIN Code"
        Factor.COLOUR -> "Color Sequence"
        Factor.EMOJI -> "Emoji Pattern"
        Factor.WORDS -> "Word Selection"
        Factor.PATTERN_MICRO -> "Micro Pattern"
        Factor.PATTERN_NORMAL -> "Pattern Lock"
        Factor.MOUSE_DRAW -> "Mouse Drawing"
        Factor.STYLUS_DRAW -> "Stylus Drawing"
        Factor.VOICE -> "Voice Pattern"
        Factor.IMAGE_TAP -> "Image Tap"
        Factor.BALANCE -> "Balance Tilt"
        Factor.RHYTHM_TAP -> "Rhythm Tap"
        Factor.NFC -> "NFC Tag"
        Factor.FINGERPRINT -> "Fingerprint"
        Factor.FACE -> "Face ID"
    }
    
    /**
     * Get emoji icon for factor
     */
    fun getFactorIcon(factor: Factor): String = when (factor) {
        Factor.PIN -> "🔢"
        Factor.COLOUR -> "🎨"
        Factor.EMOJI -> "😀"
        Factor.WORDS -> "🔡"
        Factor.PATTERN_MICRO -> "🔲"
        Factor.PATTERN_NORMAL -> "⚪"
        Factor.MOUSE_DRAW -> "🖱️"
        Factor.STYLUS_DRAW -> "✏️"
        Factor.VOICE -> "🎤"
        Factor.IMAGE_TAP -> "🖼️"
        Factor.BALANCE -> "⚖️"
        Factor.RHYTHM_TAP -> "🥁"
        Factor.NFC -> "📱"
        Factor.FINGERPRINT -> "👆"
        Factor.FACE -> "😊"
    }
    
    /**
     * Get category display name
     */
    fun getCategoryDisplayName(category: FactorCategory): String = when (category) {
        FactorCategory.KNOWLEDGE -> "Knowledge (What You Know)"
        FactorCategory.INHERENCE -> "Inherence (What You Are)"
        FactorCategory.POSSESSION -> "Possession (What You Have)"
    }
    
    /**
     * Validate factor selection
     * 
     * @param selectedFactors List of selected factors
     * @return ValidationResult
     */
    fun validateFactorSelection(selectedFactors: List<Factor>): ValidationResult {
        // Check minimum count
        if (selectedFactors.size < MIN_FACTORS) {
            return ValidationResult.Invalid(
                "Please select at least $MIN_FACTORS factors " +
                "(${selectedFactors.size}/$MIN_FACTORS selected)"
            )
        }
        
        // Check maximum count (DoS protection)
        if (selectedFactors.size > MAX_FACTORS) {
            return ValidationResult.Invalid(
                "Maximum $MAX_FACTORS factors allowed " +
                "(${selectedFactors.size}/$MAX_FACTORS selected)"
            )
        }
        
        // Check category diversity (PSD3 SCA)
        val categories = selectedFactors.mapNotNull { FACTOR_CATEGORIES[it] }.toSet()
        if (categories.size < MIN_CATEGORIES) {
            return ValidationResult.Invalid(
                "Factors must be from at least $MIN_CATEGORIES different categories " +
                "(Knowledge, Inherence, Possession)"
            )
        }
        
        return ValidationResult.Valid
    }

    /**
     * Validation result
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
    }

    // ==================== ERROR MESSAGES ====================

    const val ERROR_MIN_FACTORS = "Please select at least $MIN_FACTORS factors"
    const val ERROR_MIN_CATEGORIES = "Please select factors from at least $MIN_CATEGORIES categories"
    const val ERROR_SESSION_EXPIRED = "Session expired. Please start over."
    const val ERROR_FACTOR_CAPTURE_FAILED = "Factor capture failed. Please try again."
    const val ERROR_PAYMENT_LINK_FAILED = "Payment linking failed. Please try again."
    const val ERROR_WEAK_PATTERN = "Pattern is too weak. Please choose a stronger pattern."

    // ==================== SUCCESS MESSAGES ====================

    const val SUCCESS_ENROLLMENT_COMPLETE = "Enrollment complete! You can now use ZeroPay."
    const val SUCCESS_FACTOR_CAPTURED = "Factor captured successfully"
    const val SUCCESS_PAYMENT_LINKED = "Payment provider linked successfully"
}
