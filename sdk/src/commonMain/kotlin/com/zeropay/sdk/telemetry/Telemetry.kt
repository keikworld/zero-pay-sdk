package com.zeropay.sdk.telemetry

import com.zeropay.sdk.Factor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Privacy-Safe Telemetry
 * 
 * Collects anonymized metrics for fraud detection and performance monitoring.
 * GDPR-compliant: No PII, no factor details, only metadata.
 * 
 * Metrics collected:
 * - Authentication duration
 * - Factor count
 * - Success/failure rates
 * - Device type (hashed)
 * - Error types (no details)
 */
object Telemetry {
    
    data class AuthAttemptMetrics(
        val factorCount: Int,
        val durationMs: Long,
        val deviceTypeHash: String,  // Hashed to prevent fingerprinting
        val success: Boolean,
        val errorType: String?,       // Generic error type, no details
        val timestamp: Long,
        val sessionId: String         // For correlating multiple attempts
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
            // No PII in any field
            return !deviceTypeHash.contains("@") && // No email
                   errorType?.length ?: 0 < 50 &&   // No detailed stack traces
                   sessionId.length == 64            // Proper session ID format
        }
    }
    
    data class FactorUsageMetrics(
        val factorType: String,       // Generic: "pattern", "biometric", "knowledge"
        val averageDurationMs: Double,
        val successRate: Double,
        val usageCount: Long
    )
    
    data class SecurityEventMetrics(
        val eventType: SecurityEventType,
        val severity: Severity,
        val timestamp: Long,
        val deviceTypeHash: String
    )
    
    enum class SecurityEventType {
        ROOT_DETECTED,
        DEBUGGER_ATTACHED,
        RATE_LIMIT_TRIGGERED,
        REPLAY_ATTACK_BLOCKED,
        INVALID_SIGNATURE,
        SESSION_HIJACK_ATTEMPT,
        ANOMALOUS_TIMING,
        SAFETYNET_FAILED
    }
    
    enum class Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    // Thread-safe metrics storage
    private val authAttempts = ConcurrentHashMap<String, MutableList<AuthAttemptMetrics>>()
    private val securityEvents = ConcurrentHashMap<SecurityEventType, AtomicLong>()
    private val factorUsage = ConcurrentHashMap<String, MutableList<Long>>() // Factor type -> durations
    private val lock = Any()
    
    /**
     * Record authentication attempt (privacy-safe)
     */
    fun recordAuthAttempt(metrics: AuthAttemptMetrics) {
        // Validate privacy compliance
        require(metrics.isPrivacyCompliant()) {
            "Metrics must be privacy-compliant (no PII)"
        }
        
        synchronized(lock) {
            val sessionMetrics = authAttempts.getOrPut(metrics.sessionId) {
                mutableListOf()
            }
            sessionMetrics.add(metrics)
            
            // Limit per-session storage
            if (sessionMetrics.size > 100) {
                sessionMetrics.removeAt(0)
            }
        }
    }
    
    /**
     * Record security event
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
     * Record factor usage (generic type, no specifics)
     */
    fun recordFactorUsage(
        factorType: Factor,
        durationMs: Long,
        success: Boolean
    ) {
        // Generalize factor type to prevent fingerprinting
        val genericType = when (factorType) {
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL, 
            Factor.MOUSE_DRAW, Factor.STYLUS_DRAW -> "pattern"
            
            Factor.FACE, Factor.VOICE, Factor.BALANCE -> "biometric"
            
            Factor.PIN, Factor.COLOUR, Factor.EMOJI, 
            Factor.WORDS -> "knowledge"
            
            Factor.NFC, Factor.IMAGE_TAP -> "possession"
        }
        
        synchronized(lock) {
            val durations = factorUsage.getOrPut(genericType) {
                mutableListOf()
            }
            durations.add(durationMs)
            
            // Keep only last 1000 entries per type
            if (durations.size > 1000) {
                durations.removeAt(0)
            }
        }
    }
    
    /**
     * Get aggregated statistics (no individual data)
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
            
            val totalAttempts = allAttempts.size
            val successRate = allAttempts.count { it.success }.toDouble() / totalAttempts
            val averageDurationMs = allAttempts.map { it.durationMs }.average()
            val averageFactorCount = allAttempts.map { it.factorCount }.average()
            
            // Security events
            val eventCounts = securityEvents.mapValues { it.value.get() }
            
            // Factor usage stats
            val factorStats = factorUsage.mapValues { (_, durations) ->
                FactorUsageMetrics(
                    factorType = "",  // Will be set by key
                    averageDurationMs = durations.average(),
                    successRate = 0.0,  // TODO: Track success per factor type
                    usageCount = durations.size.toLong()
                )
            }
            
            return AggregatedStats(
                totalAttempts = totalAttempts,
                successRate = successRate,
                averageDurationMs = averageDurationMs,
                averageFactorCount = averageFactorCount,
                securityEventCounts = eventCounts,
                factorUsageStats = factorStats
            )
        }
    }
    
    /**
     * Detect anomalous patterns (for fraud detection)
     */
    fun detectAnomalies(sessionId: String): List<Anomaly> {
        synchronized(lock) {
            val sessionAttempts = authAttempts[sessionId] ?: return emptyList()
            val anomalies = mutableListOf<Anomaly>()
            
            // Check 1: Too many attempts in short time
            if (sessionAttempts.size > 10) {
                val timeSpan = sessionAttempts.last().timestamp - sessionAttempts.first().timestamp
                if (timeSpan < 60000) { // 1 minute
                    anomalies.add(Anomaly(
                        type = AnomalyType.TOO_MANY_ATTEMPTS,
                        severity = Severity.HIGH,
                        description = "${sessionAttempts.size} attempts in ${timeSpan}ms"
                    ))
                }
            }
            
            // Check 2: Suspiciously fast authentication
            val avgDuration = sessionAttempts.map { it.durationMs }.average()
            if (avgDuration < 500) { // Less than 500ms
                anomalies.add(Anomaly(
                    type = AnomalyType.SUSPICIOUSLY_FAST,
                    severity = Severity.MEDIUM,
                    description = "Average ${avgDuration}ms (possible automation)"
                ))
            }
            
            // Check 3: Consistent timing (robotic)
            if (sessionAttempts.size >= 3) {
                val durations = sessionAttempts.map { it.durationMs.toDouble() }
                val variance = calculateVariance(durations)
                if (variance < 100) { // Very consistent
                    anomalies.add(Anomaly(
                        type = AnomalyType.ROBOTIC_TIMING,
                        severity = Severity.HIGH,
                        description = "Timing variance: $variance (suspicious consistency)"
                    ))
                }
            }
            
            // Check 4: All failures
            if (sessionAttempts.size >= 5 && sessionAttempts.all { !it.success }) {
                anomalies.add(Anomaly(
                    type = AnomalyType.ALL_FAILURES,
                    severity = Severity.MEDIUM,
                    description = "${sessionAttempts.size} consecutive failures"
                ))
            }
            
            return anomalies
        }
    }
    
    /**
     * Export metrics for external monitoring system
     * (Prometheus, Datadog, etc.)
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
        }
    }
    
    // Helper function
    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
    }
    
    data class AggregatedStats(
        val totalAttempts: Int,
        val successRate: Double,
        val averageDurationMs: Double,
        val averageFactorCount: Double,
        val securityEventCounts: Map<SecurityEventType, Long>,
        val factorUsageStats: Map<String, FactorUsageMetrics>
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
 * Performance monitoring
 */
object PerformanceMonitor {
    
    data class PerformanceMetrics(
        val operationName: String,
        val durationMs: Long,
        val memoryUsedKB: Long,
        val cpuTimeMs: Long?,
        val timestamp: Long
    )
    
    private val metrics = mutableListOf<PerformanceMetrics>()
    private val lock = Any()
    
    /**
     * Measure operation performance
     */
    inline fun <T> measure(
        operationName: String,
        operation: () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val result = operation()
        
        val endTime = System.currentTimeMillis()
        val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val metrics = PerformanceMetrics(
            operationName = operationName,
            durationMs = endTime - startTime,
            memoryUsedKB = (endMemory - startMemory) / 1024,
            cpuTimeMs = null, // TODO: Measure CPU time
            timestamp = startTime
        )
        
        recordMetrics(metrics)
        
        return result
    }
    
    private fun recordMetrics(metrics: PerformanceMetrics) {
        synchronized(lock) {
            this.metrics.add(metrics)
            
            // Keep only last 1000 entries
            if (this.metrics.size > 1000) {
                this.metrics.removeAt(0)
            }
        }
    }
    
    /**
     * Get performance statistics
     */
    fun getStats(operationName: String? = null): PerformanceStats {
        synchronized(lock) {
            val filtered = if (operationName != null) {
                metrics.filter { it.operationName == operationName }
            } else {
                metrics
            }
            
            if (filtered.isEmpty()) {
                return PerformanceStats(
                    operationName = operationName,
                    count = 0,
                    avgDurationMs = 0.0,
                    maxDurationMs = 0,
                    minDurationMs = 0,
                    avgMemoryKB = 0.0
                )
            }
            
            return PerformanceStats(
                operationName = operationName,
                count = filtered.size,
                avgDurationMs = filtered.map { it.durationMs }.average(),
                maxDurationMs = filtered.maxOf { it.durationMs },
                minDurationMs = filtered.minOf { it.durationMs },
                avgMemoryKB = filtered.map { it.memoryUsedKB }.average()
            )
        }
    }
    
    data class PerformanceStats(
        val operationName: String?,
        val count: Int,
        val avgDurationMs: Double,
        val maxDurationMs: Long,
        val minDurationMs: Long,
        val avgMemoryKB: Double
    )
}
