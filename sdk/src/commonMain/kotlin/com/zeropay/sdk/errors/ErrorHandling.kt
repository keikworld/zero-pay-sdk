package com.zeropay.sdk.errors

import android.util.Log

/**
 * ZeroPay SDK Error Handling System
 * 
 * Principles:
 * - User-friendly messages (no technical jargon)
 * - Graceful degradation (fallback options)
 * - Privacy-safe logging (no PII)
 * - Actionable guidance (what user should do)
 */

/**
 * Base ZeroPay Exception
 */
sealed class ZeroPayException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Get user-friendly error message
     */
    abstract fun getUserMessage(): String
    
    /**
     * Get suggested action for user
     */
    abstract fun getSuggestedAction(): String
    
    /**
     * Get error severity
     */
    abstract fun getSeverity(): ErrorSeverity
    
    /**
     * Check if error is recoverable
     */
    abstract fun isRecoverable(): Boolean
}

/**
 * Error severity levels
 */
enum class ErrorSeverity {
    LOW,        // Informational, user can continue
    MEDIUM,     // Warning, user should be aware
    HIGH,       // Error, user action needed
    CRITICAL    // Fatal, cannot continue
}

// ============== Authentication Errors ==============

class FactorNotAvailableException(
    val factor: String,
    val reason: String
) : ZeroPayException("Factor $factor not available: $reason") {
    
    override fun getUserMessage() = 
        "This authentication method is not available on your device."
    
    override fun getSuggestedAction() = 
        "Please try a different authentication method."
    
    override fun getSeverity() = ErrorSeverity.MEDIUM
    
    override fun isRecoverable() = true
}

class FactorValidationException(
    val factor: String,
    cause: Throwable? = null
) : ZeroPayException("Factor validation failed: $factor", cause) {
    
    override fun getUserMessage() = 
        "Authentication failed. Please try again."
    
    override fun getSuggestedAction() = 
        "Make sure you're entering the correct information."
    
    override fun getSeverity() = ErrorSeverity.HIGH
    
    override fun isRecoverable() = true
}

class RateLimitException(
    val remainingTime: Long
) : ZeroPayException("Rate limit exceeded") {
    
    override fun getUserMessage() = 
        "Too many authentication attempts."
    
    override fun getSuggestedAction(): String {
        val minutes = remainingTime / 60000
        return when {
            minutes < 1 -> "Please wait a moment and try again."
            minutes < 60 -> "Please wait $minutes minutes and try again."
            else -> "Please wait ${minutes / 60} hours and try again."
        }
    }
    
    override fun getSeverity() = ErrorSeverity.HIGH
    
    override fun isRecoverable() = true
}

class BiometricNotEnrolledException(
    val biometricType: String
) : ZeroPayException("Biometric not enrolled: $biometricType") {
    
    override fun getUserMessage() = 
        "No $biometricType enrolled on this device."
    
    override fun getSuggestedAction() = 
        "Please enroll $biometricType in your device settings, or use a different authentication method."
    
    override fun getSeverity() = ErrorSeverity.MEDIUM
    
    override fun isRecoverable() = true
}

class BiometricAuthenticationException(
    val reason: String,
    cause: Throwable? = null
) : ZeroPayException("Biometric authentication failed: $reason", cause) {
    
    override fun getUserMessage() = when (reason.lowercase()) {
        "canceled" -> "Authentication was cancelled."
        "lockout" -> "Too many failed attempts. Biometric authentication is temporarily locked."
        "no_hardware" -> "Biometric hardware is not available."
        "timeout" -> "Authentication timed out."
        else -> "Biometric authentication failed."
    }
    
    override fun getSuggestedAction() = when (reason.lowercase()) {
        "canceled" -> "Please try again when ready."
        "lockout" -> "Please wait a few moments or use your PIN."
        "no_hardware" -> "Please use a different authentication method."
        "timeout" -> "Please try again."
        else -> "Please try again or use a different method."
    }
    
    override fun getSeverity() = when (reason.lowercase()) {
        "lockout" -> ErrorSeverity.HIGH
        "no_hardware" -> ErrorSeverity.MEDIUM
        else -> ErrorSeverity.MEDIUM
    }
    
    override fun isRecoverable() = reason.lowercase() != "no_hardware"
}

// ============== Network Errors ==============

class NetworkException(
    val httpCode: Int? = null,
    cause: Throwable? = null
) : ZeroPayException("Network error: ${httpCode ?: "unknown"}", cause) {
    
    override fun getUserMessage() = when (httpCode) {
        null -> "Unable to connect to server."
        400 -> "Invalid request. Please try again."
        401, 403 -> "Authentication failed. Please sign in again."
        404 -> "Service not found. Please update the app."
        429 -> "Too many requests. Please wait a moment."
        500, 502, 503 -> "Server is temporarily unavailable."
        else -> "Connection error occurred."
    }
    
    override fun getSuggestedAction() = when (httpCode) {
        null -> "Check your internet connection and try again."
        401, 403 -> "Sign out and sign in again."
        404 -> "Update to the latest version."
        429 -> "Please wait a few moments."
        500, 502, 503 -> "Try again in a few minutes."
        else -> "Try again or contact support if the problem persists."
    }
    
    override fun getSeverity() = when (httpCode) {
        401, 403, 404 -> ErrorSeverity.CRITICAL
        429, 500, 502, 503 -> ErrorSeverity.HIGH
        else -> ErrorSeverity.MEDIUM
    }
    
    override fun isRecoverable() = httpCode !in listOf(401, 403, 404)
}

class CertificatePinningException(
    cause: Throwable
) : ZeroPayException("Certificate validation failed", cause) {
    
    override fun getUserMessage() = 
        "Secure connection could not be established."
    
    override fun getSuggestedAction() = 
        "Check your network connection. If using VPN or proxy, try disabling it."
    
    override fun getSeverity() = ErrorSeverity.CRITICAL
    
    override fun isRecoverable() = false
}

// ============== Security Errors ==============

class TamperingDetectedException(
    val threats: List<String>
) : ZeroPayException("Tampering detected: ${threats.joinToString()}") {
    
    override fun getUserMessage() = 
        "Device security check failed."
    
    override fun getSuggestedAction() = when {
        threats.contains("ROOT") -> "This app cannot run on rooted devices."
        threats.contains("DEBUGGER") -> "Please close all debugging tools."
        threats.contains("EMULATOR") -> "This app must run on a physical device."
        else -> "Please use a secure, unmodified device."
    }
    
    override fun getSeverity() = ErrorSeverity.CRITICAL
    
    override fun isRecoverable() = false
}

class DataIntegrityException(
    val dataType: String
) : ZeroPayException("Data integrity check failed: $dataType") {
    
    override fun getUserMessage() = 
        "Data verification failed."
    
    override fun getSuggestedAction() = 
        "Please clear app data and try again, or reinstall the app."
    
    override fun getSeverity() = ErrorSeverity.HIGH
    
    override fun isRecoverable() = true
}

// ============== Storage Errors ==============

class StorageException(
    val operation: String,
    cause: Throwable? = null
) : ZeroPayException("Storage operation failed: $operation", cause) {
    
    override fun getUserMessage() = 
        "Could not save your authentication settings."
    
    override fun getSuggestedAction() = 
        "Check available storage space and app permissions."
    
    override fun getSeverity() = ErrorSeverity.HIGH
    
    override fun isRecoverable() = true
}

class KeyStoreException(
    val reason: String,
    cause: Throwable? = null
) : ZeroPayException("KeyStore error: $reason", cause) {
    
    override fun getUserMessage() = 
        "Secure storage is not available."
    
    override fun getSuggestedAction() = 
        "Your device's secure storage may be locked. Please unlock your device and try again."
    
    override fun getSeverity() = ErrorSeverity.CRITICAL
    
    override fun isRecoverable() = false
}

// ============== Configuration Errors ==============

class ConfigurationException(
    val parameter: String
) : ZeroPayException("Invalid configuration: $parameter") {
    
    override fun getUserMessage() = 
        "App configuration error."
    
    override fun getSuggestedAction() = 
        "Please reinstall the app or contact support."
    
    override fun getSeverity() = ErrorSeverity.CRITICAL
    
    override fun isRecoverable() = false
}

// ============== Error Handler ==============

/**
 * Centralized error handler
 * 
 * Features:
 * - User-friendly messages
 * - Privacy-safe logging
 * - Error reporting
 * - Fallback strategies
 */
object ErrorHandler {
    
    private const val TAG = "ZeroPay"
    private var errorLogger: ErrorLogger? = null
    
    /**
     * Set error logger (optional)
     */
    fun setLogger(logger: ErrorLogger) {
        errorLogger = logger
    }
    
    /**
     * Handle exception and get user-facing result
     */
    fun handle(exception: Throwable): ErrorResult {
        val zeroPayException = when (exception) {
            is ZeroPayException -> exception
            is java.net.UnknownHostException -> NetworkException(cause = exception)
            is java.net.SocketTimeoutException -> NetworkException(cause = exception)
            is javax.net.ssl.SSLException -> CertificatePinningException(exception)
            is IllegalArgumentException -> ConfigurationException(exception.message ?: "unknown")
            else -> UnknownException(exception)
        }
        
        // Log error (privacy-safe)
        logError(zeroPayException)
        
        // Report to monitoring (if configured)
        errorLogger?.log(zeroPayException)
        
        return ErrorResult(
            userMessage = zeroPayException.getUserMessage(),
            suggestedAction = zeroPayException.getSuggestedAction(),
            severity = zeroPayException.getSeverity(),
            isRecoverable = zeroPayException.isRecoverable(),
            exception = zeroPayException
        )
    }
    
    /**
     * Log error (privacy-safe)
     */
    private fun logError(exception: ZeroPayException) {
        val severity = exception.getSeverity()
        val message = "${exception.javaClass.simpleName}: ${exception.message}"
        
        when (severity) {
            ErrorSeverity.LOW -> Log.i(TAG, message)
            ErrorSeverity.MEDIUM -> Log.w(TAG, message)
            ErrorSeverity.HIGH, ErrorSeverity.CRITICAL -> Log.e(TAG, message, exception)
        }
    }
    
    /**
     * Try operation with automatic error handling
     */
    inline fun <T> tryWithHandling(
        operation: () -> T,
        onError: (ErrorResult) -> T
    ): T {
        return try {
            operation()
        } catch (e: Exception) {
            val errorResult = handle(e)
            onError(errorResult)
        }
    }
    
    /**
     * Try operation with fallback
     */
    inline fun <T> tryWithFallback(
        primary: () -> T,
        fallback: () -> T
    ): T {
        return try {
            primary()
        } catch (e: Exception) {
            Log.w(TAG, "Primary operation failed, using fallback", e)
            try {
                fallback()
            } catch (fe: Exception) {
                Log.e(TAG, "Fallback also failed", fe)
                throw e // Throw original exception
            }
        }
    }
}

/**
 * Error result for UI display
 */
data class ErrorResult(
    val userMessage: String,
    val suggestedAction: String,
    val severity: ErrorSeverity,
    val isRecoverable: Boolean,
    val exception: ZeroPayException
)

/**
 * Unknown exception wrapper
 */
class UnknownException(
    cause: Throwable
) : ZeroPayException("Unknown error: ${cause.message}", cause) {
    
    override fun getUserMessage() = 
        "An unexpected error occurred."
    
    override fun getSuggestedAction() = 
        "Please try again. If the problem persists, contact support."
    
    override fun getSeverity() = ErrorSeverity.HIGH
    
    override fun isRecoverable() = true
}

/**
 * Error logger interface
 * Implement to send errors to monitoring system (Sentry, Firebase, etc.)
 */
interface ErrorLogger {
    fun log(exception: ZeroPayException)
}

/**
 * Example: Sentry error logger
 */
class SentryErrorLogger : ErrorLogger {
    override fun log(exception: ZeroPayException) {
        // Example integration:
        // Sentry.captureException(exception)
        
        // For now, just log
        Log.d("SentryLogger", "Would send to Sentry: ${exception.message}")
    }
}

/**
 * Example: Firebase Crashlytics logger
 */
class FirebaseCrashlyticsLogger : ErrorLogger {
    override fun log(exception: ZeroPayException) {
        // Example integration:
        // FirebaseCrashlytics.getInstance().recordException(exception)
        
        // For now, just log
        Log.d("FirebaseLogger", "Would send to Firebase: ${exception.message}")
    }
}
