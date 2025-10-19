// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/models/EnrollmentResult.kt

package com.zeropay.enrollment.models

import com.zeropay.sdk.security.SecurityPolicy

/**
 * Result of enrollment operation - ENHANCED VERSION
 */
sealed class EnrollmentResult {
    data class Success(
        val user: User,
        val cacheKey: String,
        val factorCount: Int,
        val enrollmentId: String,
        val linkedProviders: List<String> = emptyList(),
        val timestamp: Long
    ) : EnrollmentResult()

    data class Failure(
        val error: EnrollmentError,
        val message: String,
        val enrollmentId: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val retryable: Boolean = false,
        val securityDecision: SecurityPolicy.SecurityDecision? = null  // For security-related failures
    ) : EnrollmentResult()
}

enum class EnrollmentError {
    // Session errors
    INVALID_SESSION,
    SESSION_EXPIRED,
    
    // Consent errors
    NO_CONSENT,
    INVALID_CONSENT,
    
    // Factor errors
    INVALID_FACTOR,
    WEAK_FACTOR,
    INSUFFICIENT_FACTORS,
    TOO_MANY_FACTORS,
    INVALID_FACTOR_CATEGORY,
    
    // Storage errors
    STORAGE_FAILURE,
    CACHE_FAILURE,
    KEYSTORE_FAILURE,
    
    // Network errors
    NETWORK_FAILURE,
    TIMEOUT,
    
    // Security errors
    CRYPTO_FAILURE,
    VALIDATION_FAILURE,
    RATE_LIMIT_EXCEEDED,
    SECURITY_VIOLATION,  // Device security check failed

    // General
    UNKNOWN
}
