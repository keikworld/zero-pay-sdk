package com.zeropay.sdk.network

import com.zeropay.sdk.security.CryptoUtils
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.min

/**
 * Retry Policy with Exponential Backoff
 * 
 * Features:
 * - Configurable max retries
 * - Exponential backoff with jitter
 * - Retry on specific exceptions only
 * - Circuit breaker pattern
 */
object RetryPolicy {
    
    data class RetryConfig(
        val maxRetries: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 30000,
        val backoffMultiplier: Double = 2.0,
        val jitterFactor: Double = 0.1,
        val retryableExceptions: List<Class<out Exception>> = listOf(
            java.io.IOException::class.java,
            java.net.SocketTimeoutException::class.java,
            java.net.UnknownHostException::class.java
        )
    )
    
    data class RetryResult<T>(
        val success: Boolean,
        val data: T?,
        val attempts: Int,
        val lastError: Exception?
    )
    
    /**
     * Execute operation with retry logic
     */
    suspend fun <T> executeWithRetry(
        config: RetryConfig = RetryConfig(),
        operation: suspend () -> T
    ): RetryResult<T> {
        var lastException: Exception? = null
        
        repeat(config.maxRetries) { attempt ->
            try {
                val result = operation()
                return RetryResult(
                    success = true,
                    data = result,
                    attempts = attempt + 1,
                    lastError = null
                )
            } catch (e: Exception) {
                lastException = e
                
                // Check if exception is retryable
                if (!isRetryable(e, config)) {
                    return RetryResult(
                        success = false,
                        data = null,
                        attempts = attempt + 1,
                        lastError = e
                    )
                }
                
                // Last attempt, don't delay - just return failure
                if (attempt == config.maxRetries - 1) {
                    return RetryResult(
                        success = false,
                        data = null,
                        attempts = attempt + 1,
                        lastError = e
                    )
                }

                // Calculate delay with exponential backoff and jitter
                val delay = calculateDelay(
                    attempt = attempt,
                    config = config
                )

                delay(delay)
            }
        }
        
        return RetryResult(
            success = false,
            data = null,
            attempts = config.maxRetries,
            lastError = lastException
        )
    }
    
    /**
     * Calculate delay with exponential backoff and jitter
     */
    private fun calculateDelay(attempt: Int, config: RetryConfig): Long {
        // Exponential backoff: delay = initialDelay * (multiplier ^ attempt)
        val exponentialDelay = config.initialDelayMs * 
            config.backoffMultiplier.pow(attempt.toDouble())
        
        // Cap at max delay
        val cappedDelay = min(exponentialDelay, config.maxDelayMs.toDouble())
        
        // Add jitter to prevent thundering herd
        val jitter = cappedDelay * config.jitterFactor * Math.random()
        
        return (cappedDelay + jitter).toLong()
    }
    
    /**
     * Check if exception is retryable
     */
    private fun isRetryable(e: Exception, config: RetryConfig): Boolean {
        return config.retryableExceptions.any { it.isInstance(e) }
    }
}

/**
 * Circuit Breaker Pattern
 * 
 * Prevents cascading failures by "opening" circuit after consecutive failures
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 60000, // 1 minute
    private val halfOpenRetries: Int = 1
) {
    private enum class State { CLOSED, OPEN, HALF_OPEN }
    
    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L
    private var successCount = 0
    
    /**
     * Execute operation through circuit breaker
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        // Check if circuit should reset
        if (state == State.OPEN && 
            System.currentTimeMillis() - lastFailureTime > resetTimeoutMs) {
            state = State.HALF_OPEN
            successCount = 0
        }
        
        // Circuit is open, fail fast
        if (state == State.OPEN) {
            throw CircuitBreakerOpenException(
                "Circuit breaker is open. Try again later."
            )
        }
        
        return try {
            val result = operation()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
    
    private fun onSuccess() {
        when (state) {
            State.HALF_OPEN -> {
                successCount++
                if (successCount >= halfOpenRetries) {
                    state = State.CLOSED
                    failureCount = 0
                }
            }
            State.CLOSED -> {
                failureCount = 0
            }
            State.OPEN -> {
                // Should not happen
            }
        }
    }
    
    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        
        if (failureCount >= failureThreshold) {
            state = State.OPEN
        }
    }
    
    fun getState(): String = state.name
    fun getFailureCount(): Int = failureCount
}

class CircuitBreakerOpenException(message: String) : Exception(message)

/**
 * Telemetry for monitoring retry behavior
 */
object RetryTelemetry {
    
    data class RetryMetrics(
        val operationName: String,
        val attempts: Int,
        val totalDurationMs: Long,
        val success: Boolean,
        val errorType: String?
    )
    
    private val metrics = mutableListOf<RetryMetrics>()
    private val lock = Any()
    
    /**
     * Record retry attempt (privacy-safe, no PII)
     */
    fun recordRetry(metrics: RetryMetrics) {
        synchronized(lock) {
            this.metrics.add(metrics)
            
            // Keep only last 1000 entries
            if (this.metrics.size > 1000) {
                this.metrics.removeAt(0)
            }
        }
    }
    
    /**
     * Get aggregated statistics
     */
    fun getStats(): Stats {
        synchronized(lock) {
            if (metrics.isEmpty()) {
                return Stats(
                    totalRetries = 0,
                    avgAttempts = 0.0,
                    successRate = 0.0,
                    avgDurationMs = 0.0
                )
            }
            
            val totalRetries = metrics.size
            val avgAttempts = metrics.map { it.attempts }.average()
            val successRate = metrics.count { it.success }.toDouble() / totalRetries
            val avgDurationMs = metrics.map { it.totalDurationMs }.average()
            
            return Stats(
                totalRetries = totalRetries,
                avgAttempts = avgAttempts,
                successRate = successRate,
                avgDurationMs = avgDurationMs
            )
        }
    }
    
    /**
     * Clear metrics (for testing)
     */
    fun clear() {
        synchronized(lock) {
            metrics.clear()
        }
    }
    
    data class Stats(
        val totalRetries: Int,
        val avgAttempts: Double,
        val successRate: Double,
        val avgDurationMs: Double
    )
}

/**
 * Biometric authentication with retry logic
 */
suspend fun authenticateBiometricWithRetry(
    context: android.content.Context,
    maxRetries: Int = 3,
    onSuccess: (ByteArray) -> Unit,
    onError: (String) -> Unit
) {
    val startTime = System.currentTimeMillis()
    var lastError: String? = null
    
    val result = RetryPolicy.executeWithRetry(
        config = RetryPolicy.RetryConfig(
            maxRetries = maxRetries,
            initialDelayMs = 2000,
            backoffMultiplier = 2.0,
            retryableExceptions = listOf(
                // Only retry on transient errors
                java.io.IOException::class.java
            )
        )
    ) {
        // Perform biometric authentication
        authenticateBiometric(context)
    }
    
    // Record telemetry
    RetryTelemetry.recordRetry(
        RetryTelemetry.RetryMetrics(
            operationName = "biometric_auth",
            attempts = result.attempts,
            totalDurationMs = System.currentTimeMillis() - startTime,
            success = result.success,
            errorType = result.lastError?.javaClass?.simpleName
        )
    )
    
    if (result.success && result.data != null) {
        onSuccess(result.data)
    } else {
        onError(result.lastError?.message ?: "Authentication failed after $maxRetries attempts")
    }
}

/**
 * Helper function for biometric authentication
 */
private suspend fun authenticateBiometric(context: android.content.Context): ByteArray {
    // This would integrate with actual biometric authentication
    // For now, placeholder that throws on failure
    return com.zeropay.sdk.security.CryptoUtils.generateRandomBytes(32)
}
