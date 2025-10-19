package com.zeropay.sdk.config

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit Tests for IntegrationConfig
 *
 * Tests configuration validation, preset configs, and edge cases.
 *
 * @version 1.0.0
 */
class IntegrationConfigTest {

    @Test
    fun `test default configuration is valid`() {
        val config = IntegrationConfig()

        // Should not throw
        config.validate()

        // Verify defaults
        assertTrue(config.enableApiIntegration)
        assertEquals(FallbackStrategy.API_FIRST_CACHE_FALLBACK, config.fallbackStrategy)
        assertEquals(3, config.maxRetries)
        assertEquals(1000L, config.initialRetryDelayMs)
        assertEquals(5000L, config.maxRetryDelayMs)
        assertTrue(config.enableCircuitBreaker)
        assertEquals(5, config.circuitBreakerThreshold)
        assertEquals(30000L, config.circuitBreakerTimeoutMs)
        assertTrue(config.enableHealthCheck)
        assertEquals(60000L, config.healthCheckIntervalMs)
        assertEquals(10000L, config.apiTimeoutMs)
        assertEquals(5000L, config.cacheTimeoutMs)
    }

    @Test
    fun `test production configuration`() {
        val config = IntegrationConfig.production()

        config.validate()

        assertTrue(config.enableApiIntegration)
        assertEquals(FallbackStrategy.API_FIRST_CACHE_FALLBACK, config.fallbackStrategy)
        assertTrue(config.enableCircuitBreaker)
        assertTrue(config.enableHealthCheck)
        assertTrue(config.enableMetrics)
        assertFalse(config.enableDetailedLogging)
        assertTrue(config.reportMetrics)
    }

    @Test
    fun `test development configuration`() {
        val config = IntegrationConfig.development()

        config.validate()

        assertTrue(config.enableApiIntegration)
        assertEquals(5000L, config.apiTimeoutMs) // Faster timeout
        assertTrue(config.enableDetailedLogging)
        assertFalse(config.reportMetrics)
    }

    @Test
    fun `test offline configuration`() {
        val config = IntegrationConfig.offline()

        config.validate()

        assertFalse(config.enableApiIntegration)
        assertEquals(FallbackStrategy.CACHE_ONLY, config.fallbackStrategy)
    }

    @Test
    fun `test apiOnly configuration`() {
        val config = IntegrationConfig.apiOnly()

        config.validate()

        assertTrue(config.enableApiIntegration)
        assertEquals(FallbackStrategy.API_ONLY, config.fallbackStrategy)
        assertFalse(config.syncApiToCache)
    }

    @Test
    fun `test validation rejects negative maxRetries`() {
        val config = IntegrationConfig(maxRetries = -1)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("maxRetries"))
    }

    @Test
    fun `test validation rejects zero initialRetryDelayMs`() {
        val config = IntegrationConfig(initialRetryDelayMs = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("initialRetryDelayMs"))
    }

    @Test
    fun `test validation rejects maxRetryDelayMs less than initialRetryDelayMs`() {
        val config = IntegrationConfig(
            initialRetryDelayMs = 5000L,
            maxRetryDelayMs = 1000L
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("maxRetryDelayMs"))
    }

    @Test
    fun `test validation rejects zero circuitBreakerThreshold`() {
        val config = IntegrationConfig(circuitBreakerThreshold = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("circuitBreakerThreshold"))
    }

    @Test
    fun `test validation rejects zero circuitBreakerTimeoutMs`() {
        val config = IntegrationConfig(circuitBreakerTimeoutMs = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("circuitBreakerTimeoutMs"))
    }

    @Test
    fun `test validation rejects zero apiTimeoutMs`() {
        val config = IntegrationConfig(apiTimeoutMs = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("apiTimeoutMs"))
    }

    @Test
    fun `test validation accepts valid custom config`() {
        val config = IntegrationConfig(
            maxRetries = 5,
            initialRetryDelayMs = 500L,
            maxRetryDelayMs = 10000L,
            circuitBreakerThreshold = 10,
            circuitBreakerTimeoutMs = 60000L,
            apiTimeoutMs = 15000L,
            cacheTimeoutMs = 3000L
        )

        // Should not throw
        config.validate()

        assertEquals(5, config.maxRetries)
        assertEquals(500L, config.initialRetryDelayMs)
        assertEquals(10000L, config.maxRetryDelayMs)
    }

    @Test
    fun `test retryableErrors default set`() {
        val config = IntegrationConfig()

        assertTrue(config.retryableErrors.contains("NETWORK_TIMEOUT"))
        assertTrue(config.retryableErrors.contains("CONNECTION_FAILED"))
        assertTrue(config.retryableErrors.contains("SERVICE_UNAVAILABLE"))
        assertTrue(config.retryableErrors.contains("RATE_LIMIT_EXCEEDED"))
    }

    @Test
    fun `test custom retryableErrors`() {
        val customErrors = setOf("CUSTOM_ERROR_1", "CUSTOM_ERROR_2")
        val config = IntegrationConfig(retryableErrors = customErrors)

        assertEquals(customErrors, config.retryableErrors)
        assertTrue(config.retryableErrors.contains("CUSTOM_ERROR_1"))
        assertFalse(config.retryableErrors.contains("NETWORK_TIMEOUT"))
    }

    @Test
    fun `test all fallback strategies`() {
        val strategies = listOf(
            FallbackStrategy.API_ONLY,
            FallbackStrategy.CACHE_ONLY,
            FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            FallbackStrategy.CACHE_FIRST_API_SYNC
        )

        strategies.forEach { strategy ->
            val config = IntegrationConfig(fallbackStrategy = strategy)
            config.validate()
            assertEquals(strategy, config.fallbackStrategy)
        }
    }

    @Test
    fun `test sync configuration flags`() {
        val config1 = IntegrationConfig(
            syncApiToCache = true,
            syncCacheToApi = true
        )
        assertTrue(config1.syncApiToCache)
        assertTrue(config1.syncCacheToApi)

        val config2 = IntegrationConfig(
            syncApiToCache = false,
            syncCacheToApi = false
        )
        assertFalse(config2.syncApiToCache)
        assertFalse(config2.syncCacheToApi)
    }

    @Test
    fun `test metrics and logging flags`() {
        val config = IntegrationConfig(
            enableMetrics = false,
            enableDetailedLogging = true,
            reportMetrics = false
        )

        assertFalse(config.enableMetrics)
        assertTrue(config.enableDetailedLogging)
        assertFalse(config.reportMetrics)
    }

    @Test
    fun `test circuit breaker success threshold validation`() {
        val config = IntegrationConfig(circuitBreakerSuccessThreshold = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("circuitBreakerSuccessThreshold"))
    }

    @Test
    fun `test health check interval validation`() {
        val config = IntegrationConfig(healthCheckIntervalMs = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("healthCheckIntervalMs"))
    }

    @Test
    fun `test sync interval validation`() {
        val config = IntegrationConfig(syncIntervalMs = 0)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            config.validate()
        }

        assertTrue(exception.message!!.contains("syncIntervalMs"))
    }

    @Test
    fun `test extreme values within bounds`() {
        val config = IntegrationConfig(
            maxRetries = 100,
            initialRetryDelayMs = 1L,
            maxRetryDelayMs = 1000000L,
            circuitBreakerThreshold = 1000,
            circuitBreakerTimeoutMs = 1000000L,
            apiTimeoutMs = 1000000L,
            cacheTimeoutMs = 1000000L
        )

        // Should not throw
        config.validate()
    }

    @Test
    fun `test production config is production-ready`() {
        val config = IntegrationConfig.production()

        // Production requirements
        assertTrue("API integration should be enabled", config.enableApiIntegration)
        assertTrue("Circuit breaker should be enabled", config.enableCircuitBreaker)
        assertTrue("Health checks should be enabled", config.enableHealthCheck)
        assertTrue("Metrics should be enabled", config.enableMetrics)
        assertFalse("Detailed logging should be disabled in production", config.enableDetailedLogging)
        assertEquals("Should use API-first fallback",
            FallbackStrategy.API_FIRST_CACHE_FALLBACK,
            config.fallbackStrategy)
    }

    @Test
    fun `test development config is dev-friendly`() {
        val config = IntegrationConfig.development()

        assertTrue("Detailed logging should be enabled for development",
            config.enableDetailedLogging)
        assertFalse("Metrics reporting should be disabled for development",
            config.reportMetrics)
        assertTrue("API timeout should be lower for faster feedback",
            config.apiTimeoutMs < IntegrationConfig().apiTimeoutMs)
    }

    @Test
    fun `test offline config disables API`() {
        val config = IntegrationConfig.offline()

        assertFalse("API integration should be disabled in offline mode",
            config.enableApiIntegration)
        assertEquals("Should use cache-only strategy in offline mode",
            FallbackStrategy.CACHE_ONLY,
            config.fallbackStrategy)
    }

    @Test
    fun `test apiOnly config disables cache sync`() {
        val config = IntegrationConfig.apiOnly()

        assertTrue("API integration should be enabled", config.enableApiIntegration)
        assertEquals("Should use API-only strategy",
            FallbackStrategy.API_ONLY,
            config.fallbackStrategy)
        assertFalse("Should not sync to cache in API-only mode",
            config.syncApiToCache)
    }
}
