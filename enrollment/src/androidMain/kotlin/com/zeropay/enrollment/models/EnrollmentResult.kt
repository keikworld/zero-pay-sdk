package com.zeropay.enrollment.models

/**
 * Result of enrollment operation
 */
sealed class EnrollmentResult {
    data class Success(
        val user: User,
        val cacheKey: String,
        val factorCount: Int
    ) : EnrollmentResult()
    
    data class Failure(
        val error: EnrollmentError,
        val message: String
    ) : EnrollmentResult()
}

enum class EnrollmentError {
    NO_CONSENT,
    INVALID_FACTOR,
    WEAK_FACTOR,
    CACHE_FAILURE,
    CRYPTO_FAILURE,
    NETWORK_FAILURE,
    UNKNOWN
}
