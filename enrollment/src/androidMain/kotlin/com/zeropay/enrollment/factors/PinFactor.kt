package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils

/**
 * PIN Factor - Handles PIN-based authentication
 */
object PinFactor {
    
    private const val MIN_LENGTH = 4
    private const val MAX_LENGTH = 8
    
    /**
     * Validates and hashes PIN
     * @return SHA-256 digest or null if invalid
     */
    fun processPin(pin: String): Result<ByteArray> {
        // Sanitize
        val sanitized = pin.trim()
        
        // Validate length
        if (sanitized.length < MIN_LENGTH) {
            return Result.failure(Exception("PIN must be at least $MIN_LENGTH digits"))
        }
        
        if (sanitized.length > MAX_LENGTH) {
            return Result.failure(Exception("PIN cannot exceed $MAX_LENGTH digits"))
        }
        
        // Validate digits only
        if (!sanitized.all { it.isDigit() }) {
            return Result.failure(Exception("PIN must contain only digits"))
        }
        
        // Check for weak PINs
        if (isWeakPin(sanitized)) {
            return Result.failure(Exception("PIN is too weak"))
        }
        
        // Hash PIN
        val digest = CryptoUtils.sha256(sanitized.toByteArray(Charsets.UTF_8))
        
        return Result.success(digest)
    }
    
    /**
     * Checks for weak PINs
     */
    private fun isWeakPin(pin: String): Boolean {
        // All same digit
        if (pin.all { it == pin[0] }) return true
        
        // Common sequences
        val weak = listOf("1234", "0000", "1111", "1234567", "0123456")
        if (pin in weak) return true
        
        return false
    }
    
    /**
     * Verifies PIN against stored digest
     */
    fun verifyPin(inputPin: String, storedDigest: ByteArray): Boolean {
        val result = processPin(inputPin)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        return inputDigest.contentEquals(storedDigest)
    }
}
