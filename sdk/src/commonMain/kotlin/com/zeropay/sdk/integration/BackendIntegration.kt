package com.zeropay.sdk.integration

import com.zeropay.sdk.config.FallbackStrategy
import com.zeropay.sdk.config.IntegrationConfig
import kotlinx.coroutines.*
import kotlin.math.min
import kotlin.math.pow

/**
 * Backend Integration Utility
 *
 * Provides unified interface for backend operations with automatic fallback,
 * retry logic, and circuit breaker pattern.
 *
 * Features:
 * - Primary/fallback execution strategy
 * - Exponential backoff retry
 * - Circuit breaker for failing backends
 * - Health check monitoring
 * - Metrics collection
 * - Thread-safe operations
 *
 * Usage:
 * ```kotlin
 * val integration = BackendIntegration(config)
 *
 * val result = integration.execute(
 *     primary = { enrollmentClient.enroll(request) },
 *     fallback = { redisCacheClient.store(data) }
 * )
 * ```
 *
 * @param config Integration configuration
 * @version 1.0.0
 */
class BackendIntegration(
    private val config: IntegrationConfig
) {
    companion object {
        private const val TAG = "BackendIntegration"
    }

    // Circuit breaker state
    private val circuitBreaker = CircuitBreaker(
        threshold = config.circuitBreakerThreshold,
        timeout = config.circuitBreakerTimeoutMs,
        successThreshold = config.circuitBreakerSuccessThreshold
    )

    // Metrics tracking
    private val metrics = IntegrationMetrics()

    // Health check job
    private var healthCheckJob: Job? = null

    init {
        config.validate()

        if (config.enableHealthCheck) {
            startHealthCheck()
        }
    }

    /**
     * Execute operation with fallback strategy
     *
     * @param T Return type
     * @param primary Primary operation (usually API call)
     * @param fallback Fallback operation (usually cache)
     * @param operationName Name for logging/metrics
     * @return Result of primary or fallback operation
     */
    suspend fun <T> execute(
        primary: suspend () -> T,
        fallback: (suspend () -> T)? = null,
        operationName: String = "operation"
    ): T = withContext(Dispatchers.IO) {
        when (config.fallbackStrategy) {
            FallbackStrategy.API_ONLY -> {
                executeApiOnly(primary, operationName)
            }

            FallbackStrategy.CACHE_ONLY -> {
                executeCacheOnly(fallback, operationName)
            }

            FallbackStrategy.API_FIRST_CACHE_FALLBACK -> {
                executeApiFirstCacheFallback(primary, fallback, operationName)
            }

            FallbackStrategy.CACHE_FIRST_API_SYNC -> {
                executeCacheFirstApiSync(primary, fallback, operationName)
            }
        }
    }

    /**
     * Execute API-only strategy
     */
    private suspend fun <T> executeApiOnly(
        primary: suspend () -> T,
        operationName: String
    ): T {
        if (!config.enableApiIntegration) {
            throw IllegalStateException("API integration disabled but API_ONLY strategy selected")
        }

        return executeWithRetryAndCircuitBreaker(primary, operationName, isApi = true)
    }

    /**
     * Execute cache-only strategy
     */
    private suspend fun <T> executeCacheOnly(
        fallback: (suspend () -> T)?,
        operationName: String
    ): T {
        if (fallback == null) {
            throw IllegalArgumentException("Fallback operation required for CACHE_ONLY strategy")
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = fallback()
            metrics.recordCacheSuccess(System.currentTimeMillis() - startTime)
            result
        } catch (e: Exception) {
            metrics.recordCacheFailure()
            throw IntegrationException("Cache operation failed: ${e.message}", e)
        }
    }

    /**
     * Execute API-first with cache fallback strategy (RECOMMENDED)
     */
    private suspend fun <T> executeApiFirstCacheFallback(
        primary: suspend () -> T,
        fallback: (suspend () -> T)?,
        operationName: String
    ): T {
        // Try API first
        if (config.enableApiIntegration) {
            try {
                val result = executeWithRetryAndCircuitBreaker(primary, operationName, isApi = true)
                log("$operationName: API success")
                return result
            } catch (e: Exception) {
                log("$operationName: API failed (${e.message}), trying fallback")
                // Continue to fallback
            }
        }

        // Fallback to cache
        if (fallback != null) {
            return try {
                val startTime = System.currentTimeMillis()
                val result = fallback()
                metrics.recordCacheSuccess(System.currentTimeMillis() - startTime)
                log("$operationName: Cache fallback success")
                result
            } catch (e: Exception) {
                metrics.recordCacheFailure()
                throw IntegrationException(
                    "Both API and cache failed. API: Check logs. Cache: ${e.message}",
                    e
                )
            }
        }

        throw IntegrationException("No fallback available and API failed")
    }

    /**
     * Execute cache-first with background API sync
     */
    private suspend fun <T> executeCacheFirstApiSync(
        primary: suspend () -> T,
        fallback: (suspend () -> T)?,
        operationName: String
    ): T {
        // Get from cache immediately
        if (fallback != null) {
            val cacheResult = try {
                val startTime = System.currentTimeMillis()
                val result = fallback()
                metrics.recordCacheSuccess(System.currentTimeMillis() - startTime)
                result
            } catch (e: Exception) {
                metrics.recordCacheFailure()
                null
            }

            // Sync to API in background
            if (config.enableApiIntegration && config.syncCacheToApi) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        executeWithRetryAndCircuitBreaker(primary, operationName, isApi = true)
                        log("$operationName: Background API sync successful")
                    } catch (e: Exception) {
                        log("$operationName: Background API sync failed: ${e.message}")
                    }
                }
            }

            if (cacheResult != null) {
                return cacheResult
            }
        }

        // Cache failed, try API as fallback
        if (config.enableApiIntegration) {
            return executeWithRetryAndCircuitBreaker(primary, operationName, isApi = true)
        }

        throw IntegrationException("Cache failed and API integration disabled")
    }

    /**
     * Execute operation with retry and circuit breaker
     */
    private suspend fun <T> executeWithRetryAndCircuitBreaker(
        operation: suspend () -> T,
        operationName: String,
        isApi: Boolean
    ): T {
        // Check circuit breaker
        if (config.enableCircuitBreaker && circuitBreaker.isOpen()) {
            throw IntegrationException("Circuit breaker open - backend unavailable")
        }

        // Execute with retry
        var lastException: Exception? = null
        val maxAttempts = if (isApi) config.maxRetries + 1 else 1

        repeat(maxAttempts) { attempt ->
            try {
                val startTime = System.currentTimeMillis()
                val result = operation()
                val latency = System.currentTimeMillis() - startTime

                if (isApi) {
                    metrics.recordApiSuccess(latency)
                    circuitBreaker.recordSuccess()
                } else {
                    metrics.recordCacheSuccess(latency)
                }

                return result
            } catch (e: Exception) {
                lastException = e

                if (isApi) {
                    metrics.recordApiFailure()
                    circuitBreaker.recordFailure()
                } else {
                    metrics.recordCacheFailure()
                }

                // Check if retryable
                if (attempt < maxAttempts - 1 && isRetryable(e)) {
                    val delay = calculateRetryDelay(attempt)
                    log("$operationName failed (attempt ${attempt + 1}/$maxAttempts), retrying in ${delay}ms: ${e.message}")
                    delay(delay)
                } else {
                    // Not retryable or max attempts reached
                    break
                }
            }
        }

        throw IntegrationException(
            "Operation failed after $maxAttempts attempts: ${lastException?.message}",
            lastException
        )
    }

    /**
     * Check if exception is retryable
     */
    private fun isRetryable(exception: Exception): Boolean {
        val message = exception.message ?: return false
        return config.retryableErrors.any { error ->
            message.contains(error, ignoreCase = true)
        }
    }

    /**
     * Calculate retry delay with exponential backoff
     */
    private fun calculateRetryDelay(attempt: Int): Long {
        val exponentialDelay = config.initialRetryDelayMs * (2.0.pow(attempt).toLong())
        return min(exponentialDelay, config.maxRetryDelayMs)
    }

    /**
     * Start background health check
     */
    private fun startHealthCheck() {
        healthCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    // TODO: Implement actual health check endpoint
                    // For now, just check circuit breaker state
                    val isHealthy = !circuitBreaker.isOpen()
                    metrics.recordHealthCheck(isHealthy)

                    if (!isHealthy) {
                        log("Health check: Backend unhealthy (circuit breaker open)")
                    }
                } catch (e: Exception) {
                    log("Health check failed: ${e.message}")
                }

                delay(config.healthCheckIntervalMs)
            }
        }
    }

    /**
     * Stop background health check
     */
    fun stopHealthCheck() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    /**
     * Get current metrics
     */
    fun getMetrics(): IntegrationMetrics {
        return metrics.copy()
    }

    /**
     * Get circuit breaker state
     */
    fun getCircuitBreakerState(): CircuitBreakerState {
        return circuitBreaker.getState()
    }

    /**
     * Reset metrics
     */
    fun resetMetrics() {
        metrics.reset()
    }

    /**
     * Force close circuit breaker (for testing)
     */
    fun forceCloseCircuitBreaker() {
        circuitBreaker.forceClose()
    }

    /**
     * Logging helper
     */
    private fun log(message: String) {
        if (config.enableDetailedLogging) {
            println("[$TAG] $message")
        }
    }
}

/**
 * Circuit Breaker
 *
 * Prevents repeated calls to failing backend.
 *
 * States:
 * - CLOSED: Normal operation
 * - OPEN: Backend failing, reject all calls
 * - HALF_OPEN: Testing if backend recovered
 */
private class CircuitBreaker(
    private val threshold: Int,
    private val timeout: Long,
    private val successThreshold: Int
) {
    private var state = CircuitBreakerState.CLOSED
    private var failureCount = 0
    private var successCount = 0
    private var lastFailureTime = 0L

    @Synchronized
    fun isOpen(): Boolean {
        if (state == CircuitBreakerState.OPEN) {
            // Check if timeout elapsed
            if (System.currentTimeMillis() - lastFailureTime >= timeout) {
                state = CircuitBreakerState.HALF_OPEN
                successCount = 0
                return false
            }
            return true
        }
        return false
    }

    @Synchronized
    fun recordSuccess() {
        when (state) {
            CircuitBreakerState.HALF_OPEN -> {
                successCount++
                if (successCount >= successThreshold) {
                    state = CircuitBreakerState.CLOSED
                    failureCount = 0
                }
            }
            CircuitBreakerState.CLOSED -> {
                failureCount = 0
            }
            else -> {}
        }
    }

    @Synchronized
    fun recordFailure() {
        lastFailureTime = System.currentTimeMillis()

        when (state) {
            CircuitBreakerState.CLOSED -> {
                failureCount++
                if (failureCount >= threshold) {
                    state = CircuitBreakerState.OPEN
                }
            }
            CircuitBreakerState.HALF_OPEN -> {
                state = CircuitBreakerState.OPEN
                failureCount = threshold
            }
            else -> {}
        }
    }

    @Synchronized
    fun getState(): CircuitBreakerState {
        return state
    }

    @Synchronized
    fun forceClose() {
        state = CircuitBreakerState.CLOSED
        failureCount = 0
        successCount = 0
    }
}

/**
 * Circuit breaker state
 */
enum class CircuitBreakerState {
    CLOSED,   // Normal operation
    OPEN,     // Failing, reject calls
    HALF_OPEN // Testing recovery
}

/**
 * Integration metrics
 */
data class IntegrationMetrics(
    var apiSuccessCount: Long = 0,
    var apiFailureCount: Long = 0,
    var apiTotalLatencyMs: Long = 0,

    var cacheSuccessCount: Long = 0,
    var cacheFailureCount: Long = 0,
    var cacheTotalLatencyMs: Long = 0,

    var healthCheckCount: Long = 0,
    var healthCheckFailureCount: Long = 0,

    var lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * API success rate (0.0 to 1.0)
     */
    fun apiSuccessRate(): Double {
        val total = apiSuccessCount + apiFailureCount
        return if (total > 0) apiSuccessCount.toDouble() / total else 0.0
    }

    /**
     * Cache success rate (0.0 to 1.0)
     */
    fun cacheSuccessRate(): Double {
        val total = cacheSuccessCount + cacheFailureCount
        return if (total > 0) cacheSuccessCount.toDouble() / total else 0.0
    }

    /**
     * Average API latency (milliseconds)
     */
    fun avgApiLatencyMs(): Double {
        return if (apiSuccessCount > 0) apiTotalLatencyMs.toDouble() / apiSuccessCount else 0.0
    }

    /**
     * Average cache latency (milliseconds)
     */
    fun avgCacheLatencyMs(): Double {
        return if (cacheSuccessCount > 0) cacheTotalLatencyMs.toDouble() / cacheSuccessCount else 0.0
    }

    @Synchronized
    fun recordApiSuccess(latencyMs: Long) {
        apiSuccessCount++
        apiTotalLatencyMs += latencyMs
        lastUpdated = System.currentTimeMillis()
    }

    @Synchronized
    fun recordApiFailure() {
        apiFailureCount++
        lastUpdated = System.currentTimeMillis()
    }

    @Synchronized
    fun recordCacheSuccess(latencyMs: Long) {
        cacheSuccessCount++
        cacheTotalLatencyMs += latencyMs
        lastUpdated = System.currentTimeMillis()
    }

    @Synchronized
    fun recordCacheFailure() {
        cacheFailureCount++
        lastUpdated = System.currentTimeMillis()
    }

    @Synchronized
    fun recordHealthCheck(success: Boolean) {
        healthCheckCount++
        if (!success) {
            healthCheckFailureCount++
        }
        lastUpdated = System.currentTimeMillis()
    }

    @Synchronized
    fun reset() {
        apiSuccessCount = 0
        apiFailureCount = 0
        apiTotalLatencyMs = 0
        cacheSuccessCount = 0
        cacheFailureCount = 0
        cacheTotalLatencyMs = 0
        healthCheckCount = 0
        healthCheckFailureCount = 0
        lastUpdated = System.currentTimeMillis()
    }

    fun copy(): IntegrationMetrics {
        return IntegrationMetrics(
            apiSuccessCount = apiSuccessCount,
            apiFailureCount = apiFailureCount,
            apiTotalLatencyMs = apiTotalLatencyMs,
            cacheSuccessCount = cacheSuccessCount,
            cacheFailureCount = cacheFailureCount,
            cacheTotalLatencyMs = cacheTotalLatencyMs,
            healthCheckCount = healthCheckCount,
            healthCheckFailureCount = healthCheckFailureCount,
            lastUpdated = lastUpdated
        )
    }
}

/**
 * Integration exception
 */
class IntegrationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
