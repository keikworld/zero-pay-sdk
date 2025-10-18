package com.zeropay.sdk.api

/**
 * API Configuration
 *
 * Centralized configuration for all network requests to ZeroPay backend.
 *
 * Security Features:
 * - HTTPS-only URLs (enforced)
 * - Certificate pinning support
 * - Timeout configuration
 * - Environment-aware (dev/staging/prod)
 * - User-Agent tracking for analytics
 *
 * Zero-Knowledge:
 * - No sensitive data in URLs
 * - All data sent in encrypted request body
 * - TLS 1.3 minimum
 *
 * Compliance:
 * - GDPR: No PII in logs
 * - PSD3: Secure channel enforcement
 *
 * @version 1.0.0
 */
data class ApiConfig(
    /**
     * Base URL of ZeroPay backend
     * Must use HTTPS in production
     * Examples:
     * - Development: http://10.0.2.2:3000 (Android emulator)
     * - Development: http://localhost:3000 (Desktop)
     * - Staging: https://staging-api.zeropay.com
     * - Production: https://api.zeropay.com
     */
    val baseUrl: String = "http://10.0.2.2:3000",

    /**
     * API version prefix (e.g., "v1", "v2")
     */
    val apiVersion: String = "v1",

    /**
     * Environment name
     */
    val environment: Environment = Environment.DEVELOPMENT,

    /**
     * Request timeout in milliseconds
     * PSD3 Recommendation: 30 seconds for authentication flows
     */
    val connectTimeoutMs: Long = 30_000,

    /**
     * Read timeout for response
     */
    val readTimeoutMs: Long = 30_000,

    /**
     * Write timeout for uploads
     */
    val writeTimeoutMs: Long = 30_000,

    /**
     * Enable certificate pinning (production-only)
     * Prevents MITM attacks
     */
    val enableCertificatePinning: Boolean = environment == Environment.PRODUCTION,

    /**
     * Certificate pins (SHA-256 hashes)
     * Generate with: openssl s_client -connect api.zeropay.com:443 | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
     */
    val certificatePins: List<String> = emptyList(),

    /**
     * Enable retry on failure
     * Recommended: true for production resilience
     */
    val enableRetry: Boolean = true,

    /**
     * Maximum retry attempts
     */
    val maxRetries: Int = 3,

    /**
     * Retry backoff multiplier (exponential)
     */
    val retryBackoffMs: Long = 1000,

    /**
     * User-Agent string
     * Format: ZeroPay-Android/{version} (Android {osVersion})
     */
    val userAgent: String = "ZeroPay-SDK/1.0.0",

    /**
     * Enable debug logging
     * WARNING: Disable in production (may leak sensitive info)
     */
    val enableDebugLogging: Boolean = environment != Environment.PRODUCTION,

    /**
     * Enable request/response logging
     * Only logs metadata, never request bodies
     */
    val enableRequestLogging: Boolean = environment == Environment.DEVELOPMENT
) {

    /**
     * Environment enum
     */
    enum class Environment {
        DEVELOPMENT,
        STAGING,
        PRODUCTION
    }

    /**
     * Validate configuration
     * Throws IllegalStateException if invalid
     */
    fun validate() {
        // Enforce HTTPS in production
        if (environment == Environment.PRODUCTION && !baseUrl.startsWith("https://")) {
            throw IllegalStateException("Production environment must use HTTPS: $baseUrl")
        }

        // Validate base URL format
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "baseUrl must start with http:// or https://"
        }

        // Validate timeouts
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
        require(writeTimeoutMs > 0) { "writeTimeoutMs must be positive" }

        // Validate retry config
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        require(retryBackoffMs > 0) { "retryBackoffMs must be positive" }

        // Enforce certificate pinning in production
        if (environment == Environment.PRODUCTION && enableCertificatePinning && certificatePins.isEmpty()) {
            throw IllegalStateException("Certificate pinning enabled but no pins configured")
        }
    }

    /**
     * Build full API URL
     */
    fun buildUrl(endpoint: String): String {
        val sanitizedEndpoint = endpoint.trimStart('/')
        return "$baseUrl/api/$apiVersion/$sanitizedEndpoint"
    }

    /**
     * Common endpoints
     */
    object Endpoints {
        // Enrollment
        const val ENROLLMENT_STORE = "enrollment/store"
        const val ENROLLMENT_RETRIEVE = "enrollment/retrieve" // + /:uuid
        const val ENROLLMENT_UPDATE = "enrollment/update"
        const val ENROLLMENT_DELETE = "enrollment/delete" // + /:uuid
        const val ENROLLMENT_EXPORT = "enrollment/export" // + /:uuid

        // Verification
        const val VERIFICATION_CREATE_SESSION = "verification/session/create"
        const val VERIFICATION_VERIFY = "verification/verify"
        const val VERIFICATION_SESSION_STATUS = "verification/session/status" // + /:sessionId

        // Blockchain
        const val BLOCKCHAIN_WALLET_LINK = "blockchain/wallets/link"
        const val BLOCKCHAIN_WALLET_UNLINK = "blockchain/wallets/unlink"
        const val BLOCKCHAIN_WALLET_GET = "blockchain/wallets" // + /:uuid
        const val BLOCKCHAIN_BALANCE = "blockchain/balance" // + /:address
        const val BLOCKCHAIN_TX_ESTIMATE = "blockchain/transactions/estimate"
        const val BLOCKCHAIN_TX_STATUS = "blockchain/transactions" // + /:signature
        const val BLOCKCHAIN_TX_VERIFY = "blockchain/transactions/verify"

        // Admin/GDPR
        const val ADMIN_GDPR_REQUEST = "admin/gdpr/request"
        const val ADMIN_AUDIT_LOG = "admin/audit-log"
    }

    companion object {
        /**
         * Create development configuration
         */
        fun development(
            baseUrl: String = "http://10.0.2.2:3000",
            enableLogging: Boolean = true
        ): ApiConfig {
            return ApiConfig(
                baseUrl = baseUrl,
                environment = Environment.DEVELOPMENT,
                enableDebugLogging = enableLogging,
                enableRequestLogging = enableLogging,
                enableCertificatePinning = false
            )
        }

        /**
         * Create staging configuration
         */
        fun staging(
            baseUrl: String = "https://staging-api.zeropay.com"
        ): ApiConfig {
            return ApiConfig(
                baseUrl = baseUrl,
                environment = Environment.STAGING,
                enableDebugLogging = true,
                enableRequestLogging = false,
                enableCertificatePinning = false
            )
        }

        /**
         * Create production configuration
         */
        fun production(
            baseUrl: String = "https://api.zeropay.com",
            certificatePins: List<String>
        ): ApiConfig {
            return ApiConfig(
                baseUrl = baseUrl,
                environment = Environment.PRODUCTION,
                certificatePins = certificatePins,
                enableCertificatePinning = true,
                enableDebugLogging = false,
                enableRequestLogging = false,
                maxRetries = 3
            ).also { it.validate() }
        }
    }
}
