package com.zeropay.sdk.gateway

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.math.min
import kotlin.math.pow

/**
 * Network Retry Handler
 * 
 * Handles retry logic for network failures only.
 * Does NOT retry on gateway errors (invalid token, etc.).
 * 
 * Strategy:
 * - Exponential backoff: 1s, 2s, 4s
 * - Jitter to prevent thundering herd
 * - Max 3 attempts by default
 */
object NetworkRetryHandler {
    
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 1000L
    private const val MAX_DELAY_MS = 8000L
    private const val JITTER_FACTOR = 0.1
    
    /**
     * Execute operation with automatic retry on network failures
     * 
     * @param operation Suspending operation to execute
     * @return Operation result
     * @throws Exception if all retries fail
     */
    suspend fun <T> withRetry(operation: suspend (attempt: Int) -> T): T {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                return operation(attempt)
            } catch (e: IOException) {
                // Network error - retry
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(calculateBackoff(attempt))
                }
            } catch (e: SocketTimeoutException) {
                // Timeout - retry
                lastException = e
                if (attempt < MAX_RETRIES - 1) {
                    delay(calculateBackoff(attempt))
                }
            } catch (e: Exception) {
                // Non-network error - don't retry
                throw e
            }
        }
        
        // All retries failed
        throw GatewayException(
            "Network request failed after $MAX_RETRIES attempts",
            gatewayId = "unknown",
            cause = lastException
        )
    }
    
    /**
     * Calculate exponential backoff with jitter
     * 
     * Formula: min(MAX_DELAY, BASE * 2^attempt) * (1 + random jitter)
     */
    private fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = BASE_DELAY_MS * 2.0.pow(attempt).toLong()
        val cappedDelay = min(exponentialDelay, MAX_DELAY_MS)
        
        // Add jitter (-10% to +10%)
        val jitter = (Math.random() * 2 - 1) * JITTER_FACTOR
        val finalDelay = (cappedDelay * (1 + jitter)).toLong()
        
        return finalDelay.coerceAtLeast(0)
    }
}
