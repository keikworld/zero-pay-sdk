package com.zeropay.enrollment.models

import com.zeropay.sdk.Factor
import com.zeropay.enrollment.config.EnrollmentConfig

/**
 * Enrollment Data Models - PRODUCTION VERSION
 * 
 * GDPR-Compliant Data Models:
 * - No raw factor data stored
 * - Only SHA-256 digests
 * - Encrypted payment tokens
 * - Consent tracking
 * 
 * Security:
 * - All data encrypted at rest
 * - Memory wiping after use
 * - No PII stored
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */

/**
 * Enrollment session state
 * 
 * Tracks user progress through enrollment wizard
 */
data class EnrollmentSession(
    val sessionId: String,
    val userId: String? = null,
    val currentStep: EnrollmentStep = EnrollmentStep.FACTOR_SELECTION,
    val selectedFactors: List<Factor> = emptyList(),
    val capturedFactors: Map<Factor, ByteArray> = emptyMap(), // SHA-256 digests
    val linkedPaymentProviders: List<PaymentProviderLink> = emptyList(),
    val consents: Map<EnrollmentConfig.ConsentType, Boolean> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + EnrollmentConfig.SESSION_TIMEOUT_MS
) {
    /**
     * Check if session is expired
     */
    fun isExpired(): Boolean = System.currentTimeMillis() > expiresAt
    
    /**
     * Check if ready for next step
     */
    fun canProceedToNext(): Boolean = when (currentStep) {
        EnrollmentStep.FACTOR_SELECTION -> 
            selectedFactors.size >= EnrollmentConfig.MIN_FACTORS
        
        EnrollmentStep.FACTOR_CAPTURE -> 
            capturedFactors.size == selectedFactors.size
        
        EnrollmentStep.PAYMENT_LINKING -> 
            linkedPaymentProviders.size >= EnrollmentConfig.MIN_PAYMENT_PROVIDERS
        
        EnrollmentStep.CONSENT -> 
            consents.size == EnrollmentConfig.ConsentType.values().size &&
            consents.values.all { it }
        
        EnrollmentStep.CONFIRMATION -> true
    }
    
    /**
     * Get completion percentage
     */
    fun getCompletionPercentage(): Int {
        val totalSteps = EnrollmentStep.values().size
        val currentStepIndex = currentStep.ordinal
        return (currentStepIndex * 100) / totalSteps
    }
}

/**
 * Enrollment wizard steps
 */
enum class EnrollmentStep(val title: String, val description: String) {
    FACTOR_SELECTION(
        "Select Factors",
        "Choose at least ${EnrollmentConfig.MIN_FACTORS} authentication factors"
    ),
    FACTOR_CAPTURE(
        "Capture Factors",
        "Set up your authentication factors"
    ),
    PAYMENT_LINKING(
        "Link Payment",
        "Connect your payment provider"
    ),
    CONSENT(
        "Consent & Privacy",
        "Review and accept terms"
    ),
    CONFIRMATION(
        "Confirm & Complete",
        "Review and finalize enrollment"
    )
}

/**
 * Payment provider link data
 * 
 * ZERO-KNOWLEDGE: Only encrypted tokens stored, never raw credentials
 */
data class PaymentProviderLink(
    val providerId: String,
    val providerName: String,
    val linkType: EnrollmentConfig.PaymentLinkType,
    val encryptedToken: ByteArray? = null,  // For OAuth (AES-256-GCM encrypted)
    val hashedReference: String? = null,    // For hashed reference
    val linkedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as PaymentProviderLink
        
        if (providerId != other.providerId) return false
        if (providerName != other.providerName) return false
        if (linkType != other.linkType) return false
        if (encryptedToken != null) {
            if (other.encryptedToken == null) return false
            if (!encryptedToken.contentEquals(other.encryptedToken)) return false
        } else if (other.encryptedToken != null) return false
        if (hashedReference != other.hashedReference) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = providerId.hashCode()
        result = 31 * result + providerName.hashCode()
        result = 31 * result + linkType.hashCode()
        result = 31 * result + (encryptedToken?.contentHashCode() ?: 0)
        result = 31 * result + (hashedReference?.hashCode() ?: 0)
        return result
    }
}

/**
 * Enrollment result
 */
sealed class EnrollmentResult {
    data class Success(
        val user: User,
        val factorCount: Int,
        val cacheKey: String,
        val linkedProviders: List<String>
    ) : EnrollmentResult()
    
    data class Failure(
        val error: EnrollmentError,
        val message: String,
        val retryable: Boolean = true
    ) : EnrollmentResult()
}

/**
 * User model - GDPR compliant
 */
data class User(
    val uuid: String,
    val alias: String,
    val enrolledAt: Long = System.currentTimeMillis()
)

/**
 * Enrollment errors
 */
enum class EnrollmentError {
    INVALID_FACTOR,
    INSUFFICIENT_FACTORS,
    FACTOR_CAPTURE_FAILED,
    PAYMENT_LINKING_FAILED,
    CONSENT_REQUIRED,
    SESSION_EXPIRED,
    RATE_LIMIT_EXCEEDED,
    NETWORK_ERROR,
    ENCRYPTION_ERROR,
    STORAGE_ERROR,
    UNKNOWN_ERROR
}

/**
 * Factor capture state
 */
sealed class FactorCaptureState {
    object Idle : FactorCaptureState()
    object Capturing : FactorCaptureState()
    data class Success(val digest: ByteArray) : FactorCaptureState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return digest.contentEquals(other.digest)
        }
        
        override fun hashCode(): Int = digest.contentHashCode()
    }
    data class Error(val message: String) : FactorCaptureState()
}

/**
 * Payment linking state
 */
sealed class PaymentLinkingState {
    object Idle : PaymentLinkingState()
    object Linking : PaymentLinkingState()
    data class Success(val link: PaymentProviderLink) : PaymentLinkingState()
    data class Error(val message: String) : PaymentLinkingState()
}
