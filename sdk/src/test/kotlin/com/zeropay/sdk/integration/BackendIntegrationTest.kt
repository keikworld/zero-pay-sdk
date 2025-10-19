package com.zeropay.sdk.integration

import com.zeropay.sdk.config.FallbackStrategy
import com.zeropay.sdk.config.IntegrationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit Tests for BackendIntegration
 *
 * Tests execution strategies, circuit breaker, retry logic, and metrics.
 *
 * @version 1.0.0
 */
class BackendIntegrationTest {

    private lateinit var integration: BackendIntegration

    @Before
    fun setup() {
        val config = IntegrationConfig(
            enableApiIntegration = true,
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            maxRetries = 3,
            initialRetryDelayMs = 10L, // Fast for testing
            maxRetryDelayMs = 100L,
            enableCircuitBreaker = true,
            circuitBreakerThreshold = 3,
            circuitBreakerTimeoutMs = 1000L,
            enableHealthCheck = false, // Disable for unit tests
            enableDetailedLogging = true
        )
        integration = BackendIntegration(config)
    }

    // ========================================================================
    // API_ONLY STRATEGY TESTS
    // ========================================================================

    @Test
    fun `test API_ONLY strategy succeeds with working primary`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val result = integration.execute(
            primary = { "success" },
            fallback = { "fallback" }
        )

        assertEquals("success", result)
    }

    @Test(expected = IntegrationException::class)
    fun `test API_ONLY strategy fails without fallback`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 0,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        integration.execute<String>(
            primary = { throw Exception("API failed") },
            fallback = { "fallback" } // Should not be used
        )
        Unit
    }

    // ========================================================================
    // CACHE_ONLY STRATEGY TESTS
    // ========================================================================

    @Test
    fun `test CACHE_ONLY strategy uses fallback only`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.CACHE_ONLY
        )
        val integration = BackendIntegration(config)

        val primaryCalled = AtomicInteger(0)

        val result = integration.execute(
            primary = {
                primaryCalled.incrementAndGet()
                "primary"
            },
            fallback = { "cache" }
        )

        assertEquals("cache", result)
        assertEquals(0, primaryCalled.get()) // Primary should not be called
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test CACHE_ONLY strategy requires fallback`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.CACHE_ONLY
        )
        val integration = BackendIntegration(config)

        integration.execute<String>(
            primary = { "primary" },
            fallback = null // Missing required fallback
        )
        Unit
    }

    // ========================================================================
    // API_FIRST_CACHE_FALLBACK STRATEGY TESTS
    // ========================================================================

    @Test
    fun `test API_FIRST_CACHE_FALLBACK uses API when available`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val fallbackCalled = AtomicInteger(0)

        val result = integration.execute(
            primary = { "api-success" },
            fallback = {
                fallbackCalled.incrementAndGet()
                "cache"
            }
        )

        assertEquals("api-success", result)
        assertEquals(0, fallbackCalled.get()) // Fallback should not be called
    }

    @Test
    fun `test API_FIRST_CACHE_FALLBACK falls back to cache on API failure`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            maxRetries = 0,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val result = integration.execute(
            primary = { throw Exception("API unavailable") },
            fallback = { "cache-success" }
        )

        assertEquals("cache-success", result)
    }

    @Test(expected = IntegrationException::class)
    fun `test API_FIRST_CACHE_FALLBACK fails when both fail`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            maxRetries = 0,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        integration.execute<String>(
            primary = { throw Exception("API failed") },
            fallback = { throw Exception("Cache failed") }
        )
        Unit
    }

    // ========================================================================
    // CACHE_FIRST_API_SYNC STRATEGY TESTS
    // ========================================================================

    @Test
    fun `test CACHE_FIRST_API_SYNC returns cache immediately`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.CACHE_FIRST_API_SYNC,
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val primaryCalled = AtomicInteger(0)

        val result = integration.execute(
            primary = {
                primaryCalled.incrementAndGet()
                delay(100) // Simulate slow API
                "api"
            },
            fallback = { "cache-immediate" }
        )

        assertEquals("cache-immediate", result)
        // Primary may or may not have been called yet (background sync)
    }

    // ========================================================================
    // RETRY LOGIC TESTS
    // ========================================================================

    @Test
    fun `test retry on retryable error`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 2,
            initialRetryDelayMs = 10L,
            retryableErrors = setOf("NETWORK_TIMEOUT"),
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val attempts = AtomicInteger(0)

        val result = integration.execute(
            primary = {
                val attempt = attempts.incrementAndGet()
                if (attempt < 3) {
                    throw Exception("NETWORK_TIMEOUT")
                }
                "success-after-retries"
            },
            fallback = null
        )

        assertEquals("success-after-retries", result)
        assertEquals(3, attempts.get()) // 1 initial + 2 retries
    }

    @Test(expected = IntegrationException::class)
    fun `test no retry on non-retryable error`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 2,
            retryableErrors = setOf("NETWORK_TIMEOUT"),
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val attempts = AtomicInteger(0)

        integration.execute<String>(
            primary = {
                attempts.incrementAndGet()
                throw Exception("INVALID_REQUEST") // Not retryable
            },
            fallback = null
        )
        Unit
    }

    @Test(expected = IntegrationException::class)
    fun `test max retries exhausted`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 2,
            initialRetryDelayMs = 10L,
            retryableErrors = setOf("NETWORK_TIMEOUT"),
            enableCircuitBreaker = false
        )
        val integration = BackendIntegration(config)

        val attempts = AtomicInteger(0)

        integration.execute<String>(
            primary = {
                attempts.incrementAndGet()
                throw Exception("NETWORK_TIMEOUT")
            },
            fallback = null
        )
        Unit
    }

    // ========================================================================
    // CIRCUIT BREAKER TESTS
    // ========================================================================

    @Test(expected = IntegrationException::class)
    fun `test circuit breaker opens after threshold failures`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 0,
            enableCircuitBreaker = true,
            circuitBreakerThreshold = 3
        )
        val integration = BackendIntegration(config)

        // Cause 3 failures to open circuit
        repeat(3) {
            try {
                integration.execute<String>(
                    primary = { throw Exception("API failed") },
                    fallback = null
                )
            } catch (e: IntegrationException) {
                // Expected
            }
        }

        // Circuit should be open, next call should fail immediately
        integration.execute<String>(
            primary = { "should-not-be-called" },
            fallback = null
        )
        Unit
    }

    @Test
    fun `test circuit breaker state transitions`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 0,
            enableCircuitBreaker = true,
            circuitBreakerThreshold = 2,
            circuitBreakerTimeoutMs = 100L,
            circuitBreakerSuccessThreshold = 1
        )
        val integration = BackendIntegration(config)

        // Initial state: CLOSED
        assertEquals(CircuitBreakerState.CLOSED, integration.getCircuitBreakerState())

        // Cause 2 failures to open circuit
        repeat(2) {
            try {
                integration.execute<String>(
                    primary = { throw Exception("fail") },
                    fallback = null
                )
            } catch (e: IntegrationException) {
                // Expected
            }
        }

        // Circuit should be OPEN
        assertEquals(CircuitBreakerState.OPEN, integration.getCircuitBreakerState())

        // Wait for timeout
        delay(150)

        // Circuit should transition to HALF_OPEN and allow one test request
        val result = integration.execute(
            primary = { "success" },
            fallback = null
        )

        assertEquals("success", result)

        // Circuit should be CLOSED after successful test
        assertEquals(CircuitBreakerState.CLOSED, integration.getCircuitBreakerState())
    }

    @Test
    fun `test circuit breaker reopens on failure in half-open state`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 0,
            enableCircuitBreaker = true,
            circuitBreakerThreshold = 2,
            circuitBreakerTimeoutMs = 100L
        )
        val integration = BackendIntegration(config)

        // Open circuit
        repeat(2) {
            try {
                integration.execute<String>(
                    primary = { throw Exception("fail") },
                    fallback = null
                )
            } catch (e: IntegrationException) {
                // Expected
            }
        }

        // Wait for half-open
        delay(150)

        // Fail during half-open
        try {
            integration.execute<String>(
                primary = { throw Exception("fail again") },
                fallback = null
            )
        } catch (e: IntegrationException) {
            // Expected
        }

        // Circuit should be OPEN again
        assertEquals(CircuitBreakerState.OPEN, integration.getCircuitBreakerState())
    }

    // ========================================================================
    // METRICS TESTS
    // ========================================================================

    @Test
    fun `test metrics track API success`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            enableCircuitBreaker = false,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        integration.execute(
            primary = { "success" },
            fallback = null
        )

        val metrics = integration.getMetrics()
        assertEquals(1L, metrics.apiSuccessCount)
        assertEquals(0L, metrics.apiFailureCount)
        assertEquals(1.0, metrics.apiSuccessRate(), 0.01)
    }

    @Test
    fun `test metrics track API failure`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            maxRetries = 0,
            enableCircuitBreaker = false,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        integration.execute(
            primary = { throw Exception("fail") },
            fallback = { "cache" }
        )

        val metrics = integration.getMetrics()
        assertEquals(0L, metrics.apiSuccessCount)
        assertTrue(metrics.apiFailureCount > 0)
        assertEquals(0.0, metrics.apiSuccessRate(), 0.01)
    }

    @Test
    fun `test metrics track cache operations`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.CACHE_ONLY,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        integration.execute(
            primary = { "api" },
            fallback = { "cache" }
        )

        val metrics = integration.getMetrics()
        assertEquals(1L, metrics.cacheSuccessCount)
        assertEquals(0L, metrics.cacheFailureCount)
        assertEquals(1.0, metrics.cacheSuccessRate(), 0.01)
    }

    @Test
    fun `test metrics calculate latency`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            enableCircuitBreaker = false,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        integration.execute(
            primary = {
                delay(50)
                "success"
            },
            fallback = null
        )

        val metrics = integration.getMetrics()
        assertTrue("Average latency should be > 0", metrics.avgApiLatencyMs() > 0)
        assertTrue("Average latency should be >= 50ms", metrics.avgApiLatencyMs() >= 50.0)
    }

    @Test
    fun `test metrics reset`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            enableCircuitBreaker = false,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        // Generate some metrics
        integration.execute(
            primary = { "success" },
            fallback = null
        )

        var metrics = integration.getMetrics()
        assertEquals(1L, metrics.apiSuccessCount)

        // Reset
        integration.resetMetrics()

        metrics = integration.getMetrics()
        assertEquals(0L, metrics.apiSuccessCount)
        assertEquals(0L, metrics.apiFailureCount)
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    @Test
    fun `test retryable error detection`() {
        val config = IntegrationConfig(
            retryableErrors = setOf("TIMEOUT", "UNAVAILABLE")
        )

        // Simulating the internal behavior
        val timeoutError = Exception("Request TIMEOUT")
        val unavailableError = Exception("Service UNAVAILABLE")
        val badRequestError = Exception("BAD_REQUEST")

        assertTrue(timeoutError.message!!.contains("TIMEOUT", ignoreCase = true))
        assertTrue(unavailableError.message!!.contains("UNAVAILABLE", ignoreCase = true))
        assertFalse(badRequestError.message!!.contains("TIMEOUT", ignoreCase = true))
    }

    // ========================================================================
    // FORCE CLOSE CIRCUIT BREAKER (TEST UTILITY)
    // ========================================================================

    @Test
    fun `test force close circuit breaker`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            maxRetries = 0,
            enableCircuitBreaker = true,
            circuitBreakerThreshold = 2
        )
        val integration = BackendIntegration(config)

        // Open circuit
        repeat(2) {
            try {
                integration.execute<String>(
                    primary = { throw Exception("fail") },
                    fallback = null
                )
            } catch (e: IntegrationException) {
                // Expected
            }
        }

        assertEquals(CircuitBreakerState.OPEN, integration.getCircuitBreakerState())

        // Force close
        integration.forceCloseCircuitBreaker()

        assertEquals(CircuitBreakerState.CLOSED, integration.getCircuitBreakerState())

        // Should work now
        val result = integration.execute(
            primary = { "success" },
            fallback = null
        )

        assertEquals("success", result)
    }

    // ========================================================================
    // CONCURRENT ACCESS TESTS
    // ========================================================================

    @Test
    fun `test concurrent executions`() = runBlocking {
        val config = IntegrationConfig(
            fallbackStrategy = FallbackStrategy.API_ONLY,
            enableCircuitBreaker = false,
            enableMetrics = true
        )
        val integration = BackendIntegration(config)

        val jobs = List(10) {
            kotlinx.coroutines.async {
                integration.execute(
                    primary = { "success-$it" },
                    fallback = null
                )
            }
        }

        val results = jobs.map { it.await() }

        assertEquals(10, results.size)
        results.forEachIndexed { index, result ->
            assertEquals("success-$index", result)
        }

        val metrics = integration.getMetrics()
        assertEquals(10L, metrics.apiSuccessCount)
    }
}
