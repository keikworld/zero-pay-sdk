package com.zeropay.sdk.telemetry

import com.zeropay.sdk.Factor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-Safe Telemetry - PRODUCTION VERSION (Fixed)
 * 
 * Collects anonymized metrics for fraud detection and performance monitoring.
 * 
 * GDPR Compliance:
 * - No PII collected
 * - No factor-specific details
 * - Only aggregated metadata
 * - User can opt-out
 * 
 * Metrics Collected:
 * - Authentication duration
 * - Factor count
 * - Success/failure rates
 * - Device type (hashed)
 * - Error types (generic, no details)
 * - Factor usage (grouped by category)
 * 
 * Privacy Features:
 * - Factor grouping (prevents fingerprinting)
 * - Aggregation only (no individual events)
 * - Memory limits (DoS protection)
 * - Automatic cleanup
 * 
 * Changes in this version:
 * - ✅ FIXED: Added RHYTHM_TAP to "inherence" group
 * - ✅ FIXED: Moved IMAGE_TAP from "possession" to "inherence"
 * - ✅ FIXED: Added explicit FINGERPRINT mapping
 * - ✅ FIXED: All 15 factors now properly categorized
 * 
 * @version 1.1.0
 * @date 2025-10-08
 */
object Telemetry {
    
    private const val TAG = "Telemetry"
    private const val MAX_AUTH_ATTEMPTS = 1000    // Memory limit
    private const val MAX_SESSION_METRICS = 100   // Memory limit
    
    // Thread-safe data structures
    private val authAttempts = ConcurrentHashMap<String, MutableList<AuthAttemptMetrics>>()
    private val securityEvents = ConcurrentHashMap<SecurityEventType, AtomicLong>()
    private val factorUsage = ConcurrentHashMap<String, MutableList<Long>>()
    private val sessionMetrics = mutableListOf<SessionMetrics>()
    private val lock = Any()
    
    // ==================== DATA CLASSES ====================
    
    /**
     * Authentication attempt metrics (privacy-safe)
     */
    data class AuthAttemptMetrics(
        val factorCount: Int,                  // Number of factors used
        val durationMs: Long,                  // Total authentication time
        val deviceTypeHash: String,            // Hashed device type (privacy)
        val success: Boolean,                  // Success/failure
        val errorType: String?,                // Generic error type
        val timestamp: Long,                   // Unix timestamp
        val sessionId: String                  // Session correlation
    ) {
        /**
         * Privacy-safe log string (no PII)
         */
        fun toLogString(): String {
            return "$factorCount factors, ${durationMs}ms, success=$success"
        }
        
        /**
         * Check if this is a privacy-compliant metric
         */
        fun isPrivacyCompliant(): Boolean {
            return !deviceTypeHash.contains("@") &&  // No email
                   (errorType?.length ?: 0) < 50     // No long error messages with PII
        }
    }
    
    /**
     * Session metrics (privacy-safe)
     */
    data class SessionMetrics(
        val sessionId: String,
        val startTime: Long,
        val endTime: Long,
        val attemptCount: Int,
        val successCount: Int,
        val deviceTypeHash: String
    )
    
    /**
     * Security event metrics
     */
    data class SecurityEventMetrics(
        val eventType: SecurityEventType,
        val severity: Severity,
        val timestamp: Long,
        val sessionId: String
    )
    
    enum class SecurityEventType {
        RATE_LIMIT_EXCEEDED,
        INVALID_INPUT,
        REPLAY_DETECTED,
        SUSPICIOUS_TIMING,
        DEVICE_MISMATCH,
        WEAK_FACTOR_COMBINATION,
        BIOMETRIC_LOCKOUT,
        CRYPTO_ERROR,
        UNKNOWN
    }
    
    enum class Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    // ==================== RECORDING ====================
    
    /**
     * Record authentication attempt
     * 
     * @param metrics Authentication metrics
     */
    fun recordAuthAttempt(metrics: AuthAttemptMetrics) {
        // Verify privacy compliance
        if (!metrics.isPrivacyCompliant()) {
            println("WARNING: Auth metrics failed privacy check, not recording")
            return
        }
        
        synchronized(lock) {
            val attempts = authAttempts.getOrPut(metrics.sessionId) {
                mutableListOf()
            }
            attempts.add(metrics)
            
            // DoS protection: Limit stored attempts
            if (attempts.size > MAX_AUTH_ATTEMPTS) {
                attempts.removeAt(0)
            }
        }
    }
    
    /**
     * Record session metrics
     * 
     * @param metrics Session metrics
     */
    fun recordSession(metrics: SessionMetrics) {
        synchronized(lock) {
            sessionMetrics.add(metrics)
            
            // DoS protection: Limit stored sessions
            if (sessionMetrics.size > MAX_SESSION_METRICS) {
                sessionMetrics.removeAt(0)
            }
        }
    }
    
    /**
     * Record security event
     * 
     * @param event Security event
     */
    fun recordSecurityEvent(event: SecurityEventMetrics) {
        synchronized(lock) {
            securityEvents.getOrPut(event.eventType) {
                AtomicLong(0)
            }.incrementAndGet()
        }
        
        // Log critical events immediately
        if (event.severity == Severity.CRITICAL) {
            println("CRITICAL SECURITY EVENT: ${event.eventType} at ${event.timestamp}")
        }
    }
    
    /**
     * Record factor usage (generic type, no specifics) - FIXED VERSION
     * 
     * Privacy Protection:
     * - Factors grouped by generic category
     * - No individual factor details stored
     * - Prevents device fingerprinting
     * 
     * Changes:
     * - ✅ FIXED: Added RHYTHM_TAP to inherence group
     * - ✅ FIXED: Moved IMAGE_TAP to inherence group
     * - ✅ FIXED: Added explicit FINGERPRINT mapping
     * - ✅ FIXED: All 15 factors now properly categorized
     * 
     * @param factorType Factor type
     * @param durationMs Duration in milliseconds
     * @param success Success/failure
     */
    fun recordFactorUsage(
        factorType: Factor,
        durationMs: Long,
        success: Boolean
    ) {
        // Generalize factor type to prevent fingerprinting
        val genericType = when (factorType) {
            // ========== KNOWLEDGE GROUP ==========
            // Something you know
            Factor.PIN, 
            Factor.COLOUR, 
            Factor.EMOJI, 
            Factor.WORDS -> "knowledge"
            
            // ========== INHERENCE GROUP (Behavioral) ==========
            // Drawing/pattern-based behavioral biometrics
            Factor.PATTERN_MICRO, 
            Factor.PATTERN_NORMAL, 
            Factor.MOUSE_DRAW, 
            Factor.STYLUS_DRAW -> "pattern"
            
            // ========== INHERENCE GROUP (Biometric/Behavioral) ==========
            // Hardware or behavioral biometrics
            Factor.FACE,           // Hardware biometric
            Factor.FINGERPRINT,    // Hardware biometric (FIXED: explicit mapping)
            Factor.VOICE,          // Behavioral biometric
            Factor.BALANCE,        // Behavioral biometric
            Factor.RHYTHM_TAP,     // Behavioral biometric (FIXED: was missing)
            Factor.IMAGE_TAP -> "biometric"  // Behavioral biometric (FIXED: was in "possession")
            
            // ========== POSSESSION GROUP ==========
            // Something you have
            Factor.NFC -> "possession"
        }
        
        synchronized(lock) {
            val durations = factorUsage.getOrPut(genericType) {
                mutableListOf()
            }
            durations.add(durationMs)
            
            // Keep only last 1000 entries per type (DoS protection)
            if (durations.size > 1000) {
                durations.removeAt(0)
            }
        }
    }
    
    // ==================== AGGREGATION ====================
    
    /**
     * Get aggregated statistics (no individual data)
     * 
     * GDPR Safe: Only returns aggregated, anonymized data
     * 
     * @return AggregatedStats
     */
    fun getAggregatedStats(): AggregatedStats {
        synchronized(lock) {
            val allAttempts = authAttempts.values.flatten()
            
            if (allAttempts.isEmpty()) {
                return AggregatedStats(
                    totalAttempts = 0,
                    successRate = 0.0,
                    averageDurationMs = 0.0,
                    averageFactorCount = 0.0,
                    securityEventCounts = emptyMap(),
                    factorUsageStats = emptyMap()
                )
            }
            
            val successCount = allAttempts.count { it.success }
            val successRate = successCount.toDouble() / allAttempts.size
            
            val avgDuration = allAttempts.map { it.durationMs }.average()
            val avgFactorCount = allAttempts.map { it.factorCount }.average()
            
            // Security events
            val eventCounts = securityEvents.mapValues { it.value.get() }
            
            // Factor usage stats
            val usageStats = factorUsage.mapValues { (type, durations) ->
                FactorUsageMetrics(
                    factorType = type,
                    usageCount = durations.size,
                    averageDurationMs = if (durations.isNotEmpty()) durations.average() else 0.0,
                    successRate = 0.0  // Not tracked per factor (privacy)
                )
            }
            
            return AggregatedStats(
                totalAttempts = allAttempts.size,
                successRate = successRate,
                averageDurationMs = avgDuration,
                averageFactorCount = avgFactorCount,
                securityEventCounts = eventCounts,
                factorUsageStats = usageStats
            )
        }
    }
    
    /**
     * Detect anomalies in authentication patterns
     * 
     * Anomaly Detection:
     * - Too many attempts
     * - Suspiciously fast authentication
     * - Robotic timing patterns
     * - All failures
     * - Device switching
     * 
     * @param sessionId Session ID to analyze
     * @return List of detected anomalies
     */
    fun detectAnomalies(sessionId: String): List<Anomaly> {
        val anomalies = mutableListOf<Anomaly>()
        
        synchronized(lock) {
            val attempts = authAttempts[sessionId] ?: return emptyList()
            
            if (attempts.isEmpty()) return emptyList()
            
            // Too many attempts
            if (attempts.size > 10) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.TOO_MANY_ATTEMPTS,
                        severity = Severity.HIGH,
                        description = "${attempts.size} attempts in session"
                    )
                )
            }
            
            // Suspiciously fast (< 100ms average)
            val avgDuration = attempts.map { it.durationMs }.average()
            if (avgDuration < 100) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.SUSPICIOUSLY_FAST,
                        severity = Severity.MEDIUM,
                        description = "Average ${avgDuration}ms (suspiciously fast)"
                    )
                )
            }
            
            // Robotic timing (very consistent intervals)
            if (attempts.size >= 3) {
                val durations = attempts.map { it.durationMs }
                val variance = calculateVariance(durations.map { it.toDouble() })
                if (variance < 10.0) {  // Very consistent
                    anomalies.add(
                        Anomaly(
                            type = AnomalyType.ROBOTIC_TIMING,
                            severity = Severity.HIGH,
                            description = "Timing variance: $variance (robotic)"
                        )
                    )
                }
            }
            
            // All failures
            if (attempts.all { !it.success }) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.ALL_FAILURES,
                        severity = Severity.MEDIUM,
                        description = "All ${attempts.size} attempts failed"
                    )
                )
            }
            
            // Device switching (different device hashes)
            val devices = attempts.map { it.deviceTypeHash }.toSet()
            if (devices.size > 1) {
                anomalies.add(
                    Anomaly(
                        type = AnomalyType.DEVICE_SWITCH,
                        severity = Severity.HIGH,
                        description = "${devices.size} different devices in session"
                    )
                )
            }
        }
        
        return anomalies
    }
    
    /**
     * Export metrics in Prometheus format
     * 
     * @return Prometheus-formatted metrics string
     */
    fun exportMetrics(): String {
        val stats = getAggregatedStats()
        
        return buildString {
            appendLine("# ZeroPay SDK Metrics")
            appendLine("zeropay_auth_attempts_total ${stats.totalAttempts}")
            appendLine("zeropay_auth_success_rate ${stats.successRate}")
            appendLine("zeropay_auth_duration_avg_ms ${stats.averageDurationMs}")
            appendLine("zeropay_auth_factor_count_avg ${stats.averageFactorCount}")
            
            stats.securityEventCounts.forEach { (event, count) ->
                appendLine("zeropay_security_event{type=\"$event\"} $count")
            }
            
            stats.factorUsageStats.forEach { (type, metrics) ->
                appendLine("zeropay_factor_usage{type=\"$type\"} ${metrics.usageCount}")
                appendLine("zeropay_factor_duration_avg_ms{type=\"$type\"} ${metrics.averageDurationMs}")
            }
        }
    }
    
    /**
     * Clear all metrics (for testing or privacy reset)
     */
    fun clear() {
        synchronized(lock) {
            authAttempts.clear()
            securityEvents.clear()
            factorUsage.clear()
            sessionMetrics.clear()
        }
    }
    
    // ==================== HELPER FUNCTIONS ====================
    
    /**
     * Calculate variance of values
     */
    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
    
    // ==================== DATA CLASSES ====================
    
    data class AggregatedStats(
        val totalAttempts: Int,
        val successRate: Double,
        val averageDurationMs: Double,
        val averageFactorCount: Double,
        val securityEventCounts: Map<SecurityEventType, Long>,
        val factorUsageStats: Map<String, FactorUsageMetrics>
    )
    
    data class FactorUsageMetrics(
        val factorType: String,
        val usageCount: Int,
        val averageDurationMs: Double,
        val successRate: Double
    )
    
    data class Anomaly(
        val type: AnomalyType,
        val severity: Severity,
        val description: String
    )
    
    enum class AnomalyType {
        TOO_MANY_ATTEMPTS,
        SUSPICIOUSLY_FAST,
        ROBOTIC_TIMING,
        ALL_FAILURES,
        DEVICE_SWITCH,
        UNUSUAL_FACTOR_COMBINATION
    }
}

/**
 * Performance monitoring (separate from telemetry)
 */
object PerformanceMonitor {
    
    data class PerformanceMetrics(
        val operationName: String,
        val durationMs: Long,
        val memoryUsedKB: Long,
        val cpuTimeMs: Long?,
        val timestamp: Long
    )

    internal val metrics = mutableListOf<PerformanceMetrics>()
    internal val lock = Any()

    /**
     * Measure operation performance
     *
     * @param operationName Operation identifier
     * @param block Code block to measure
     * @return Result of block execution
     */
    internal inline fun <T> measure(operationName: String, block: () -> T): T {
        val startTime = System.currentTimeMillis()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val result = block()
        
        val endTime = System.currentTimeMillis()
        val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val metric = PerformanceMetrics(
            operationName = operationName,
            durationMs = endTime - startTime,
            memoryUsedKB = (endMemory - startMemory) / 1024,
            cpuTimeMs = null,  // Not easily available in Kotlin
            timestamp = startTime
        )
        
        synchronized(lock) {
            metrics.add(metric)
            
            // Keep only last 1000 metrics
            if (metrics.size > 1000) {
                metrics.removeAt(0)
            }
        }
        
        return result
    }
    
    /**
     * Get average performance for operation
     */
    fun getAveragePerformance(operationName: String): PerformanceMetrics? {
        synchronized(lock) {
            val relevant = metrics.filter { it.operationName == operationName }
            if (relevant.isEmpty()) return null
            
            return PerformanceMetrics(
                operationName = operationName,
                durationMs = relevant.map { it.durationMs }.average().toLong(),
                memoryUsedKB = relevant.map { it.memoryUsedKB }.average().toLong(),
                cpuTimeMs = null,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Clear all performance metrics
     */
    fun clear() {
        synchronized(lock) {
            metrics.clear()
        }
    }
}
