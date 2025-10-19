package com.zeropay.sdk.config

/**
 * Integration Configuration
 *
 * Controls how the SDK integrates with the backend API and local cache.
 *
 * Strategies:
 * - API_ONLY: Only use backend API (fail if unavailable)
 * - CACHE_ONLY: Only use local cache (offline mode)
 * - API_FIRST_CACHE_FALLBACK: Try API first, fall back to cache (recommended)
 * - CACHE_FIRST_API_SYNC: Use cache immediately, sync to API in background
 *
 * Circuit Breaker:
 * - Prevents repeated calls to failing backend
 * - Opens after threshold failures
 * - Automatically retries after timeout
 * - Protects app from cascading failures
 *
 * Retry Logic:
 * - Exponential backoff for transient failures
 * - Configurable max attempts
 * - Respects rate limits
 *
 * @version 1.0.0
 */
data class IntegrationConfig(
    /**
     * Enable backend API integration
     *
     * If false, SDK operates in offline mode (cache only)
     */
    val enableApiIntegration: Boolean = true,

    /**
     * Fallback strategy for handling API failures
     */
    val fallbackStrategy: FallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,

    // ========================================================================
    // RETRY CONFIGURATION
    // ========================================================================

    /**
     * Maximum number of retry attempts for API calls
     *
     * Default: 3 attempts
     */
    val maxRetries: Int = 3,

    /**
     * Initial delay before first retry (milliseconds)
     *
     * Exponential backoff: delay doubles on each retry
     * Default: 1000ms (1 second)
     */
    val initialRetryDelayMs: Long = 1000L,

    /**
     * Maximum delay between retries (milliseconds)
     *
     * Prevents unbounded exponential growth
     * Default: 5000ms (5 seconds)
     */
    val maxRetryDelayMs: Long = 5000L,

    /**
     * Retry only on specific errors
     *
     * Examples: network timeout, 503 Service Unavailable
     * Do NOT retry on: 400 Bad Request, 401 Unauthorized
     */
    val retryableErrors: Set<String> = setOf(
        "NETWORK_TIMEOUT",
        "CONNECTION_FAILED",
        "SERVICE_UNAVAILABLE",
        "RATE_LIMIT_EXCEEDED"
    ),

    // ========================================================================
    // CIRCUIT BREAKER
    // ========================================================================

    /**
     * Enable circuit breaker pattern
     *
     * Prevents repeated calls to failing backend
     */
    val enableCircuitBreaker: Boolean = true,

    /**
     * Number of consecutive failures before opening circuit
     *
     * Default: 5 failures
     */
    val circuitBreakerThreshold: Int = 5,

    /**
     * Time before attempting to close circuit (milliseconds)
     *
     * After this timeout, circuit enters "half-open" state
     * and allows one test request
     *
     * Default: 30000ms (30 seconds)
     */
    val circuitBreakerTimeoutMs: Long = 30000L,

    /**
     * Number of consecutive successes needed to close circuit
     *
     * While in half-open state
     * Default: 2 successes
     */
    val circuitBreakerSuccessThreshold: Int = 2,

    // ========================================================================
    // HEALTH CHECK
    // ========================================================================

    /**
     * Enable periodic backend health checks
     *
     * Proactively detects backend availability
     */
    val enableHealthCheck: Boolean = true,

    /**
     * Interval between health checks (milliseconds)
     *
     * Default: 60000ms (1 minute)
     */
    val healthCheckIntervalMs: Long = 60000L,

    /**
     * Health check timeout (milliseconds)
     *
     * Default: 5000ms (5 seconds)
     */
    val healthCheckTimeoutMs: Long = 5000L,

    // ========================================================================
    // TIMEOUTS
    // ========================================================================

    /**
     * Timeout for API calls (milliseconds)
     *
     * Includes connection + read + write time
     * Default: 10000ms (10 seconds)
     */
    val apiTimeoutMs: Long = 10000L,

    /**
     * Timeout for cache operations (milliseconds)
     *
     * Should be much lower than API timeout
     * Default: 5000ms (5 seconds)
     */
    val cacheTimeoutMs: Long = 5000L,

    // ========================================================================
    // SYNC BEHAVIOR
    // ========================================================================

    /**
     * Always sync to local cache after successful API call
     *
     * Ensures cache is up-to-date for offline use
     */
    val syncApiToCache: Boolean = true,

    /**
     * Background sync from cache to API (CACHE_FIRST mode)
     *
     * When using cache-first strategy, sync changes to API
     */
    val syncCacheToApi: Boolean = true,

    /**
     * Sync interval for background operations (milliseconds)
     *
     * Default: 300000ms (5 minutes)
     */
    val syncIntervalMs: Long = 300000L,

    // ========================================================================
    // METRICS & MONITORING
    // ========================================================================

    /**
     * Enable metrics collection
     *
     * Tracks API/cache success rates, latency, etc.
     */
    val enableMetrics: Boolean = true,

    /**
     * Enable detailed logging
     *
     * WARNING: May log sensitive operation details
     * Only enable in development
     */
    val enableDetailedLogging: Boolean = false,

    /**
     * Report metrics to backend
     *
     * Helps monitor SDK health in production
     */
    val reportMetrics: Boolean = false
) {
    /**
     * Validate configuration
     *
     * Throws IllegalArgumentException if invalid
     */
    fun validate() {
        require(maxRetries >= 0) { "maxRetries must be >= 0" }
        require(initialRetryDelayMs > 0) { "initialRetryDelayMs must be > 0" }
        require(maxRetryDelayMs >= initialRetryDelayMs) {
            "maxRetryDelayMs must be >= initialRetryDelayMs"
        }
        require(circuitBreakerThreshold > 0) { "circuitBreakerThreshold must be > 0" }
        require(circuitBreakerTimeoutMs > 0) { "circuitBreakerTimeoutMs must be > 0" }
        require(circuitBreakerSuccessThreshold > 0) { "circuitBreakerSuccessThreshold must be > 0" }
        require(healthCheckIntervalMs > 0) { "healthCheckIntervalMs must be > 0" }
        require(healthCheckTimeoutMs > 0) { "healthCheckTimeoutMs must be > 0" }
        require(apiTimeoutMs > 0) { "apiTimeoutMs must be > 0" }
        require(cacheTimeoutMs > 0) { "cacheTimeoutMs must be > 0" }
        require(syncIntervalMs > 0) { "syncIntervalMs must be > 0" }
    }

    companion object {
        /**
         * Production configuration
         *
         * - API first with cache fallback
         * - Circuit breaker enabled
         * - Health checks enabled
         * - Metrics enabled
         */
        fun production() = IntegrationConfig(
            enableApiIntegration = true,
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            enableCircuitBreaker = true,
            enableHealthCheck = true,
            enableMetrics = true,
            enableDetailedLogging = false,
            reportMetrics = true
        )

        /**
         * Development configuration
         *
         * - API first with cache fallback
         * - Faster timeouts
         * - Detailed logging enabled
         * - No metrics reporting
         */
        fun development() = IntegrationConfig(
            enableApiIntegration = true,
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            apiTimeoutMs = 5000L,
            healthCheckIntervalMs = 30000L,
            enableDetailedLogging = true,
            reportMetrics = false
        )

        /**
         * Offline mode configuration
         *
         * - Cache only (no API calls)
         * - Useful for testing or offline scenarios
         */
        fun offline() = IntegrationConfig(
            enableApiIntegration = false,
            fallbackStrategy = FallbackStrategy.CACHE_ONLY
        )

        /**
         * API only configuration
         *
         * - No cache fallback
         * - Fails if backend unavailable
         * - Useful for strict backend enforcement
         */
        fun apiOnly() = IntegrationConfig(
            enableApiIntegration = true,
            fallbackStrategy = FallbackStrategy.API_ONLY,
            syncApiToCache = false
        )
    }
}

/**
 * Fallback strategy for handling API failures
 */
enum class FallbackStrategy {
    /**
     * Only use backend API
     *
     * Fails if API is unavailable
     * No cache fallback
     */
    API_ONLY,

    /**
     * Only use local cache
     *
     * Offline mode
     * No API calls
     */
    CACHE_ONLY,

    /**
     * Try API first, fall back to cache on failure
     *
     * RECOMMENDED for production
     *
     * Flow:
     * 1. Attempt API call
     * 2. If successful, sync to cache and return
     * 3. If failed, try cache
     * 4. If cache succeeds, return with offline flag
     * 5. If both fail, return error
     */
    API_FIRST_CACHE_FALLBACK,

    /**
     * Use cache immediately, sync to API in background
     *
     * Best for low-latency requirements
     *
     * Flow:
     * 1. Read from cache (fast)
     * 2. Return cached data immediately
     * 3. Sync to API in background
     * 4. Update cache if API returns newer data
     */
    CACHE_FIRST_API_SYNC
}
