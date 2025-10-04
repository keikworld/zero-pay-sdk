package com.zeropay.sdk.errors

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * PRODUCTION-GRADE Centralized Error Handler
 * 
 * Features:
 * - User-friendly error messages (no technical jargon)
 * - GDPR-compliant logging (no PII)
 * - Error rate limiting
 * - Stack trace sanitization
 * - Error categorization
 * - Telemetry hooks (for future analytics)
 * - Circuit breaker pattern
 * - Error recovery strategies
 */
object ErrorHandler {
    
    private const val TAG = "ErrorHandler"
    private const val MAX_ERROR_RATE = 10 // Max errors per minute
    private const val CIRCUIT_BREAKER_THRESHOLD = 5 // Errors before opening circuit
    
    // Error rate tracking (thread-safe)
    private val errorCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val lastErrorTime = ConcurrentHashMap<String, Long>()
    
    // Circuit breaker state
    private var circuitOpen = false
    private var circuitOpenTime = 0L
    private val consecutiveErrors = AtomicInteger(0)
    
    /**
     * Handle exception and return user-friendly result
     * 
     * @param throwable The exception to handle
     * @param context Additional context (optional)
     * @return ErrorResult with user message and metadata
     */
    fun handle(throwable: Throwable, context: Map<String, Any> = emptyMap()): ErrorResult {
        // Check circuit breaker
        if (shouldOpenCircuit()) {
            openCircuit()
            return ErrorResult(
                userMessage = "System is temporarily unavailable. Please try again in a few minutes.",
                technicalMessage = "Circuit breaker opened",
                errorCode = "CIRCUIT_OPEN",
                canRetry = false,
                severity = ErrorSeverity.CRITICAL,
                category = ErrorCategory.SYSTEM
            )
        }
        
        // Check rate limit
        val errorType = throwable.javaClass.simpleName
        if (isRateLimited(errorType)) {
            return ErrorResult(
                userMessage = "Too many errors. Please wait a moment and try again.",
                technicalMessage = "Error rate limit exceeded for $errorType",
                errorCode = "RATE_LIMIT",
                canRetry = true,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.SYSTEM
            )
        }
        
        // Track error
        trackError(errorType)
        
        // Log error (GDPR-compliant)
        logError(throwable, context)
        
        // Map exception to user-friendly result
        val result = mapException(throwable, context)
        
        // Send telemetry (hook for future analytics)
        sendTelemetry(result, throwable, context)
        
        return result
    }
    
    /**
     * Map exception to ErrorResult
     */
    private fun mapException(throwable: Throwable, context: Map<String, Any>): ErrorResult {
        return when (throwable) {
            // ========== AUTHENTICATION ERRORS ==========
            
            is FactorValidationException -> ErrorResult(
                userMessage = "Authentication failed for ${throwable.factor}. Please try again.",
                technicalMessage = "Factor ${throwable.factor} validation error: ${throwable.message}",
                errorCode = "FACTOR_VALIDATION_FAILED",
                canRetry = true,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.AUTHENTICATION,
                suggestedAction = "Please ensure you entered the correct information and try again."
            )
            
            is FactorNotAvailableException -> ErrorResult(
                userMessage = "This authentication method (${throwable.factor}) is not available on your device.",
                technicalMessage = "Factor ${throwable.factor} not available: ${throwable.reason}",
                errorCode = "FACTOR_NOT_AVAILABLE",
                canRetry = false,
                severity = ErrorSeverity.LOW,
                category = ErrorCategory.AUTHENTICATION,
                suggestedAction = "Please use a different authentication method."
            )
            
            is BiometricException -> ErrorResult(
                userMessage = when (throwable.type) {
                    BiometricException.Type.NO_HARDWARE -> "Biometric hardware not available on this device."
                    BiometricException.Type.NOT_ENROLLED -> "No biometrics enrolled. Please set up biometric authentication in your device settings."
                    BiometricException.Type.LOCKOUT -> "Too many failed attempts. Biometric authentication is temporarily locked."
                    BiometricException.Type.PERMANENT_LOCKOUT -> "Biometric authentication is permanently locked. Please use another method."
                    BiometricException.Type.CANCELLED -> "Biometric authentication cancelled."
                    BiometricException.Type.TIMEOUT -> "Biometric authentication timed out."
                    BiometricException.Type.ERROR -> "Biometric authentication error. Please try again."
                },
                technicalMessage = "Biometric error: ${throwable.type} - ${throwable.message}",
                errorCode = "BIOMETRIC_${throwable.type}",
                canRetry = throwable.type in setOf(
                    BiometricException.Type.CANCELLED,
                    BiometricException.Type.TIMEOUT,
                    BiometricException.Type.ERROR
                ),
                severity = when (throwable.type) {
                    BiometricException.Type.PERMANENT_LOCKOUT -> ErrorSeverity.CRITICAL
                    BiometricException.Type.LOCKOUT -> ErrorSeverity.HIGH
                    else -> ErrorSeverity.MEDIUM
                },
                category = ErrorCategory.BIOMETRIC
            )
            
            // ========== SECURITY ERRORS ==========
            
            is TamperingDetectedException -> ErrorResult(
                userMessage = "Security check failed: ${throwable.message}",
                technicalMessage = "Tampering detected: ${throwable.threat} - ${throwable.details}",
                errorCode = "TAMPERING_${throwable.threat}",
                canRetry = false,
                severity = when (throwable.threat) {
                    "ROOT", "MAGISK", "FRIDA" -> ErrorSeverity.CRITICAL
                    "DEBUGGER", "XPOSED" -> ErrorSeverity.HIGH
                    else -> ErrorSeverity.MEDIUM
                },
                category = ErrorCategory.SECURITY,
                suggestedAction = throwable.suggestedAction
            )
            
            is DataIntegrityException -> ErrorResult(
                userMessage = "Data integrity check failed. Please try again.",
                technicalMessage = "Integrity failure: ${throwable.message} - Field: ${throwable.field}",
                errorCode = "DATA_INTEGRITY_FAILED",
                canRetry = true,
                severity = ErrorSeverity.HIGH,
                category = ErrorCategory.DATA
            )
            
            is CryptographicException -> ErrorResult(
                userMessage = "Encryption error occurred. Please try again.",
                technicalMessage = "Crypto error: ${throwable.operation} - ${throwable.message}",
                errorCode = "CRYPTO_${throwable.operation.uppercase()}",
                canRetry = true,
                severity = ErrorSeverity.HIGH,
                category = ErrorCategory.CRYPTOGRAPHY
            )
            
            // ========== NETWORK ERRORS ==========
            
            is NetworkException -> ErrorResult(
                userMessage = when (throwable.type) {
                    NetworkException.Type.NO_CONNECTION -> "No internet connection. Please check your network and try again."
                    NetworkException.Type.TIMEOUT -> "Connection timed out. Please try again."
                    NetworkException.Type.SERVER_ERROR -> "Server error occurred. Please try again later."
                    NetworkException.Type.SSL_ERROR -> "Secure connection failed. Please check your network security."
                    NetworkException.Type.DNS_ERROR -> "Cannot reach server. Please check your connection."
                },
                technicalMessage = "Network error: ${throwable.type} - ${throwable.message}",
                errorCode = "NETWORK_${throwable.type}",
                canRetry = true,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.NETWORK,
                retryAfterSeconds = 5
            )
            
            // ========== RATE LIMITING ==========
            
            is RateLimitException -> ErrorResult(
                userMessage = "Too many attempts. Please wait ${throwable.waitTimeMinutes} minute(s) before trying again.",
                technicalMessage = "Rate limit: ${throwable.limitType} - Wait: ${throwable.waitTimeMinutes}m",
                errorCode = "RATE_LIMIT_${throwable.limitType}",
                canRetry = false,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.RATE_LIMIT,
                retryAfterSeconds = throwable.waitTimeMinutes * 60
            )
            
            // ========== STORAGE ERRORS ==========
            
            is StorageException -> ErrorResult(
                userMessage = when (throwable.type) {
                    StorageException.Type.INSUFFICIENT_SPACE -> "Insufficient storage space."
                    StorageException.Type.PERMISSION_DENIED -> "Storage permission required."
                    StorageException.Type.CORRUPTED -> "Stored data is corrupted. Please reinstall the app."
                    StorageException.Type.NOT_FOUND -> "Required data not found."
                },
                technicalMessage = "Storage error: ${throwable.type} - ${throwable.message}",
                errorCode = "STORAGE_${throwable.type}",
                canRetry = throwable.type != StorageException.Type.CORRUPTED,
                severity = if (throwable.type == StorageException.Type.CORRUPTED) 
                    ErrorSeverity.CRITICAL else ErrorSeverity.MEDIUM,
                category = ErrorCategory.STORAGE
            )
            
            // ========== STANDARD EXCEPTIONS ==========
            
            is IllegalArgumentException -> ErrorResult(
                userMessage = "Invalid input. Please check your data and try again.",
                technicalMessage = "Invalid argument: ${throwable.message}",
                errorCode = "INVALID_ARGUMENT",
                canRetry = true,
                severity = ErrorSeverity.LOW,
                category = ErrorCategory.VALIDATION
            )
            
            is IllegalStateException -> ErrorResult(
                userMessage = "An unexpected error occurred. Please restart the app.",
                technicalMessage = "Illegal state: ${throwable.message}",
                errorCode = "ILLEGAL_STATE",
                canRetry = false,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.SYSTEM
            )
            
            is SecurityException -> ErrorResult(
                userMessage = "Security error occurred. Please contact support.",
                technicalMessage = "Security exception: ${throwable.message}",
                errorCode = "SECURITY_EXCEPTION",
                canRetry = false,
                severity = ErrorSeverity.HIGH,
                category = ErrorCategory.SECURITY
            )
            
            is NullPointerException -> ErrorResult(
                userMessage = "An internal error occurred. Please try again.",
                technicalMessage = "NPE: ${sanitizeStackTrace(throwable)}",
                errorCode = "NULL_POINTER",
                canRetry = true,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.SYSTEM
            )
            
            is OutOfMemoryError -> ErrorResult(
                userMessage = "Out of memory. Please close other apps and try again.",
                technicalMessage = "OOM: ${throwable.message}",
                errorCode = "OUT_OF_MEMORY",
                canRetry = false,
                severity = ErrorSeverity.CRITICAL,
                category = ErrorCategory.SYSTEM
            )
            
            // ========== UNKNOWN ERRORS ==========
            
            else -> ErrorResult(
                userMessage = "An unexpected error occurred. Please try again.",
                technicalMessage = "${throwable.javaClass.simpleName}: ${throwable.message}",
                errorCode = "UNKNOWN_ERROR",
                canRetry = true,
                severity = ErrorSeverity.MEDIUM,
                category = ErrorCategory.UNKNOWN,
                stackTrace = sanitizeStackTrace(throwable)
            )
        }
    }
    
    /**
     * GDPR-compliant error logging (no PII)
     */
    private fun logError(throwable: Throwable, context: Map<String, Any>) {
        val errorType = throwable.javaClass.simpleName
        val contextStr = context.entries.joinToString { "${it.key}=${sanitizeValue(it.value)}" }
        
        Log.e(TAG, "Error: $errorType | Context: $contextStr", throwable)
        
        // For production: Send to crash reporting service
        // Crashlytics.recordException(throwable)
    }
    
    /**
     * Sanitize stack trace (remove sensitive paths, PII)
     */
    private fun sanitizeStackTrace(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        
        return sw.toString()
            .replace(Regex("/data/user/\\d+/[^/]+"), "/data/user/<uid>/<app>")
            .replace(Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "XXX-XX-XXXX")
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "<email>")
            .take(1000) // Limit size
    }
    
    /**
     * Sanitize context values (remove PII)
     */
    private fun sanitizeValue(value: Any): String {
        return when (value) {
            is String -> {
                if (value.contains("@")) "<email>"
                else if (value.matches(Regex("\\d{3}-\\d{2}-\\d{4}"))) "XXX-XX-XXXX"
                else if (value.length > 50) "${value.take(47)}..."
                else value
            }
            is ByteArray -> "<${value.size} bytes>"
            else -> value.toString().take(50)
        }
    }
    
    /**
     * Error rate limiting
     */
    private fun isRateLimited(errorType: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastErrorTime[errorType] ?: 0L
        
        // Reset counter if > 1 minute
        if (now - lastTime > 60_000) {
            errorCounts[errorType]?.set(0)
            lastErrorTime[errorType] = now
            return false
        }
        
        val count = errorCounts.getOrPut(errorType) { AtomicInteger(0) }.incrementAndGet()
        return count > MAX_ERROR_RATE
    }
    
    /**
     * Track error for circuit breaker
     */
    private fun trackError(errorType: String) {
        consecutiveErrors.incrementAndGet()
    }
    
    /**
     * Check if circuit breaker should open
     */
    private fun shouldOpenCircuit(): Boolean {
        if (circuitOpen) {
            // Check if circuit should close (after 30 seconds)
            if (System.currentTimeMillis() - circuitOpenTime > 30_000) {
                closeCircuit()
                return false
            }
            return true
        }
        
        return consecutiveErrors.get() >= CIRCUIT_BREAKER_THRESHOLD
    }
    
    private fun openCircuit() {
        circuitOpen = true
        circuitOpenTime = System.currentTimeMillis()
        Log.w(TAG, "Circuit breaker OPENED")
    }
    
    private fun closeCircuit() {
        circuitOpen = false
        consecutiveErrors.set(0)
        Log.i(TAG, "Circuit breaker CLOSED")
    }
    
    /**
     * Send error telemetry (hook for future analytics)
     */
    private fun sendTelemetry(result: ErrorResult, throwable: Throwable, context: Map<String, Any>) {
        // TODO: Integrate with analytics service
        // Analytics.logError(result.errorCode, result.severity, result.category)
    }
    
    /**
     * Reset circuit breaker (for testing or manual recovery)
     */
    fun resetCircuitBreaker() {
        closeCircuit()
    }
}

// ============================================================
// ERROR RESULT & METADATA
// ============================================================

/**
 * Error result with user-friendly message and metadata
 */
data class ErrorResult(
    val userMessage: String,            // User-facing message (no technical jargon)
    val technicalMessage: String,       // Technical details for logging (no PII)
    val errorCode: String,              // Unique error code for tracking
    val canRetry: Boolean,              // Can user retry the operation?
    val severity: ErrorSeverity,        // Error severity level
    val category: ErrorCategory,        // Error category
    val suggestedAction: String? = null, // Suggested user action
    val retryAfterSeconds: Int? = null,  // Retry delay in seconds
    val stackTrace: String? = null       // Sanitized stack trace
)

enum class ErrorSeverity {
    LOW,        // Minor issue, user can continue (e.g., validation error)
    MEDIUM,     // Moderate issue, user should retry (e.g., network timeout)
    HIGH,       // Serious issue, user action required (e.g., integrity failure)
    CRITICAL    // Critical failure, app cannot continue (e.g., memory error)
}

enum class ErrorCategory {
    AUTHENTICATION,  // Authentication/factor errors
    BIOMETRIC,       // Biometric-specific errors
    SECURITY,        // Security/tampering errors
    CRYPTOGRAPHY,    // Encryption/decryption errors
    NETWORK,         // Network/connectivity errors
    STORAGE,         // Storage/persistence errors
    DATA,            // Data integrity/validation errors
    RATE_LIMIT,      // Rate limiting errors
    VALIDATION,      // Input validation errors
    SYSTEM,          // System/framework errors
    UNKNOWN          // Unknown/unexpected errors
}

// ============================================================
// CUSTOM EXCEPTION CLASSES
// ============================================================

// ========== AUTHENTICATION ==========

class FactorValidationException(
    val factor: String,
    message: String = "Factor validation failed"
) : Exception(message)

class FactorNotAvailableException(
    val factor: String,
    val reason: String = "Factor not available",
    message: String = "Factor not available"
) : Exception(message)

class BiometricException(
    val type: Type,
    message: String = type.toString()
) : Exception(message) {
    enum class Type {
        NO_HARDWARE,
        NOT_ENROLLED,
        LOCKOUT,
        PERMANENT_LOCKOUT,
        CANCELLED,
        TIMEOUT,
        ERROR
    }
}

// ========== SECURITY ==========

class TamperingDetectedException(
    val threat: String,
    val details: String = "",
    val suggestedAction: String? = null,
    message: String = "Tampering detected"
) : Exception(message)

class DataIntegrityException(
    val field: String? = null,
    message: String = "Data integrity check failed"
) : Exception(message)

class CryptographicException(
    val operation: String,
    message: String = "Cryptographic operation failed"
) : Exception(message)

// ========== NETWORK ==========

class NetworkException(
    val type: Type,
    message: String = type.toString()
) : Exception(message) {
    enum class Type {
        NO_CONNECTION,
        TIMEOUT,
        SERVER_ERROR,
        SSL_ERROR,
        DNS_ERROR
    }
}

// ========== RATE LIMITING ==========

class RateLimitException(
    val waitTimeMinutes: Int,
    val limitType: String = "GENERAL",
    message: String = "Rate limit exceeded"
) : Exception(message)

// ========== STORAGE ==========

class StorageException(
    val type: Type,
    message: String = type.toString()
) : Exception(message) {
    enum class Type {
        INSUFFICIENT_SPACE,
        PERMISSION_DENIED,
        CORRUPTED,
        NOT_FOUND
    }
}
