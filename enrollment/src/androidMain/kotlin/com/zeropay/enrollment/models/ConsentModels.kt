package com.zeropay.enrollment.models

import com.zeropay.enrollment.config.EnrollmentConfig

/**
 * Consent Models - GDPR Compliance
 * 
 * GDPR Article 7: Conditions for consent
 * - Clear and affirmative action
 * - Freely given, specific, informed, unambiguous
 * - Withdrawable at any time
 * - Separate consent for different purposes
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */

/**
 * Consent record for GDPR compliance
 * 
 * Stores user consent with timestamp and version tracking
 */
data class ConsentRecord(
    val userId: String,
    val consentType: EnrollmentConfig.ConsentType,
    val granted: Boolean,
    val grantedAt: Long = System.currentTimeMillis(),
    val ipAddress: String? = null,  // Optional: For audit trail
    val userAgent: String? = null,   // Optional: For audit trail
    val version: String = "1.0.0"    // Terms version
) {
    /**
     * Check if consent is still valid (not expired)
     */
    fun isValid(): Boolean {
        // Consent valid for 1 year (GDPR recommendation)
        val oneYearMs = 365L * 24 * 60 * 60 * 1000
        return granted && (System.currentTimeMillis() - grantedAt) < oneYearMs
    }
}

/**
 * Consent summary for UI display
 */
data class ConsentSummary(
    val totalRequired: Int,
    val totalGranted: Int,
    val missingConsents: List<EnrollmentConfig.ConsentType>
) {
    val isComplete: Boolean
        get() = totalGranted == totalRequired && missingConsents.isEmpty()
    
    val percentage: Int
        get() = if (totalRequired == 0) 100 else (totalGranted * 100) / totalRequired
}

/**
 * Consent validation result
 */
sealed class ConsentValidation {
    object Valid : ConsentValidation()
    data class Invalid(
        val missingConsents: List<EnrollmentConfig.ConsentType>,
        val message: String
    ) : ConsentValidation()
}
