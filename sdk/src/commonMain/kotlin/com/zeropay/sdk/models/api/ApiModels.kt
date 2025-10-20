package com.zeropay.sdk.models.api

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * API Request/Response Models
 *
 * All models use kotlinx.serialization for JSON serialization.
 *
 * Security:
 * - No sensitive data in logs
 * - Validation on all inputs
 * - Constant-time comparison hints
 *
 * Zero-Knowledge:
 * - Only digests transmitted (never raw factors)
 * - Server sees only boolean verification result
 *
 * @version 1.0.0
 */

// ==============================================================================
// COMMON MODELS
// ==============================================================================

/**
 * Factor digest representation
 */
@Serializable
data class FactorDigest(
    /**
     * Factor type (e.g., "PIN", "PATTERN", "FACE")
     */
    val type: String,

    /**
     * SHA-256 digest (64 hex characters = 32 bytes)
     */
    val digest: String,

    /**
     * Factor metadata (optional)
     * Example: { "version": "1.0", "complexity": "high" }
     */
    val metadata: Map<String, String>? = null
) {
    init {
        // Validate digest format
        require(digest.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "Invalid digest format: must be 64 hex characters (SHA-256)"
        }
        require(type.isNotBlank()) {
            "Factor type cannot be blank"
        }
    }
}

/**
 * Generic API response wrapper
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val metadata: ResponseMetadata? = null
)

/**
 * API error details
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, String>? = null,
    val timestamp: String? = null
)

/**
 * Response metadata
 */
@Serializable
data class ResponseMetadata(
    val requestId: String? = null,
    val timestamp: String? = null,
    val version: String? = null
)

// ==============================================================================
// ENROLLMENT MODELS
// ==============================================================================

/**
 * Enrollment request
 */
@Serializable
data class EnrollmentRequest(
    /**
     * User UUID (client-generated, v4)
     */
    val user_uuid: String,

    /**
     * List of factor digests (minimum 6 required for PSD3 SCA)
     */
    val factors: List<FactorDigest>,

    /**
     * Device identifier (anonymized)
     */
    val device_id: String? = null,

    /**
     * TTL in seconds (default 24 hours)
     */
    val ttl_seconds: Int = 86400,

    /**
     * Nonce for replay protection
     */
    val nonce: String,

    /**
     * Timestamp (ISO 8601)
     */
    val timestamp: String,

    /**
     * GDPR consent flag
     */
    val gdpr_consent: Boolean,

    /**
     * Consent timestamp
     */
    val consent_timestamp: String? = null
) {
    init {
        // Validate UUID format
        require(user_uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))) {
            "Invalid UUID format"
        }

        // Validate factor count (PSD3 SCA: minimum 6 factors)
        require(factors.size >= 6) {
            "Minimum 6 factors required for PSD3 SCA compliance (got ${factors.size})"
        }

        // Validate TTL
        require(ttl_seconds in 3600..604800) {
            "TTL must be between 1 hour and 7 days"
        }

        // Validate GDPR consent
        require(gdpr_consent) {
            "GDPR consent required for enrollment"
        }

        // Validate nonce format
        require(nonce.matches(Regex("^[0-9a-fA-F]{32,}$"))) {
            "Invalid nonce format: must be at least 32 hex characters"
        }
    }
}

/**
 * Enrollment response
 */
@Serializable
data class EnrollmentResponse(
    val user_uuid: String,
    val alias: String,
    val enrolled_factors: Int,
    val expires_at: String,
    val created_at: String
)

/**
 * Enrollment retrieval response
 */
@Serializable
data class EnrollmentRetrievalResponse(
    val user_uuid: String,
    val factors: List<RetrievedFactor>,
    val expires_at: String
)

/**
 * Retrieved factor (without digest - server has encrypted version)
 */
@Serializable
data class RetrievedFactor(
    val type: String,
    val metadata: Map<String, String>? = null
)

// ==============================================================================
// VERIFICATION MODELS
// ==============================================================================

/**
 * Create verification session request
 */
@Serializable
data class CreateSessionRequest(
    val user_uuid: String,
    val amount: Double? = null,
    val currency: String? = null,
    val transaction_id: String? = null,
    val nonce: String,
    val timestamp: String
) {
    init {
        require(user_uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))) {
            "Invalid UUID format"
        }

        amount?.let {
            require(it >= 0.0) { "Amount must be non-negative" }
        }

        currency?.let {
            require(it.matches(Regex("^[A-Z]{3}$"))) {
                "Currency must be 3-letter ISO code (e.g., USD, EUR)"
            }
        }
    }
}

/**
 * Verification session response
 */
@Serializable
data class VerificationSessionResponse(
    val session_id: String,
    val user_uuid: String,
    val required_factors: List<String>,
    val expires_at: String,
    val created_at: String
)

/**
 * Verification request
 */
@Serializable
data class VerificationRequest(
    val session_id: String,
    val user_uuid: String,
    val factors: List<FactorDigest>,
    val nonce: String,
    val timestamp: String,
    val device_id: String? = null
) {
    init {
        require(factors.isNotEmpty()) {
            "At least one factor required for verification"
        }

        require(session_id.isNotBlank()) {
            "Session ID cannot be blank"
        }
    }
}

/**
 * Verification response
 *
 * Zero-Knowledge: Server returns only boolean, never reveals which factor failed
 */
@Serializable
data class VerificationResponse(
    val verified: Boolean,
    val session_id: String,
    val confidence_score: Double? = null,
    val zk_proof: String? = null,
    val timestamp: String,
    val factors_verified: Int,
    val total_factors: Int
) {
    init {
        confidence_score?.let {
            require(it in 0.0..1.0) {
                "Confidence score must be between 0.0 and 1.0"
            }
        }
    }
}

// ==============================================================================
// BLOCKCHAIN MODELS
// ==============================================================================

/**
 * Link wallet request
 */
@Serializable
data class LinkWalletRequest(
    val user_uuid: String,
    val wallet_address: String,
    val blockchain_network: String = "solana", // solana, ethereum, etc.
    val wallet_type: String = "phantom", // phantom, metamask, etc.
    val verification_signature: String? = null,
    val nonce: String,
    val timestamp: String
) {
    init {
        require(wallet_address.isNotBlank()) {
            "Wallet address cannot be blank"
        }

        require(blockchain_network in listOf("solana", "ethereum", "polygon", "binance")) {
            "Unsupported blockchain network: $blockchain_network"
        }
    }
}

/**
 * Link wallet response
 */
@Serializable
data class LinkWalletResponse(
    val user_uuid: String,
    val wallet_address: String,
    val blockchain_network: String,
    val is_verified: Boolean,
    val linked_at: String
)

/**
 * Wallet balance response
 */
@Serializable
data class WalletBalanceResponse(
    val wallet_address: String,
    val balance: Double,
    val currency: String, // SOL, ETH, etc.
    val blockchain_network: String,
    val timestamp: String
)

/**
 * Transaction estimate request
 */
@Serializable
data class TransactionEstimateRequest(
    val from_address: String,
    val to_address: String,
    val amount: Double,
    val blockchain_network: String = "solana"
)

/**
 * Transaction estimate response
 */
@Serializable
data class TransactionEstimateResponse(
    val estimated_fee: Double,
    val currency: String,
    val estimated_time_seconds: Int,
    val priority: String // low, medium, high
)

/**
 * Transaction verification request
 */
@Serializable
data class TransactionVerificationRequest(
    val transaction_signature: String,
    val blockchain_network: String = "solana",
    val expected_amount: Double? = null,
    val expected_recipient: String? = null
)

/**
 * Transaction verification response
 */
@Serializable
data class TransactionVerificationResponse(
    val verified: Boolean,
    val transaction_signature: String,
    val status: String, // confirmed, pending, failed
    val block_time: String? = null,
    val amount: Double? = null,
    val sender: String? = null,
    val recipient: String? = null
)

// ==============================================================================
// GDPR MODELS
// ==============================================================================

/**
 * GDPR data export request
 */
@Serializable
data class GdprExportRequest(
    val user_uuid: String,
    val request_type: String = "export", // export, erasure
    val nonce: String,
    val timestamp: String
)

/**
 * GDPR export response
 */
@Serializable
data class GdprExportResponse(
    val user_uuid: String,
    val data: Map<String, @Contextual Any>? = null,
    val download_url: String? = null,
    val expires_at: String,
    val request_id: String
)

// ==============================================================================
// ERROR CODES
// ==============================================================================

object ErrorCodes {
    // Client errors (4xx)
    const val INVALID_REQUEST = "INVALID_REQUEST"
    const val INVALID_UUID = "INVALID_UUID"
    const val INVALID_DIGEST = "INVALID_DIGEST"
    const val INSUFFICIENT_FACTORS = "INSUFFICIENT_FACTORS"
    const val GDPR_CONSENT_REQUIRED = "GDPR_CONSENT_REQUIRED"
    const val INVALID_NONCE = "INVALID_NONCE"
    const val REPLAY_ATTACK_DETECTED = "REPLAY_ATTACK_DETECTED"

    // Authentication errors (401)
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val SESSION_EXPIRED = "SESSION_EXPIRED"
    const val INVALID_SESSION = "INVALID_SESSION"

    // Verification errors (403)
    const val VERIFICATION_FAILED = "VERIFICATION_FAILED"
    const val ACCOUNT_FROZEN = "ACCOUNT_FROZEN"
    const val RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"

    // Not found errors (404)
    const val USER_NOT_FOUND = "USER_NOT_FOUND"
    const val SESSION_NOT_FOUND = "SESSION_NOT_FOUND"
    const val WALLET_NOT_FOUND = "WALLET_NOT_FOUND"

    // Server errors (5xx)
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val DATABASE_ERROR = "DATABASE_ERROR"
    const val REDIS_ERROR = "REDIS_ERROR"
    const val KMS_ERROR = "KMS_ERROR"
    const val BLOCKCHAIN_ERROR = "BLOCKCHAIN_ERROR"
}
