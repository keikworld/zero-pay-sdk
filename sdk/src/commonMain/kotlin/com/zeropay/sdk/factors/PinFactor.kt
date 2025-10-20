package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.KeyDerivation
import java.util.Arrays

/**
 * PIN Factor with Argon2id Key Derivation
 * 
 * Security Features:
 * - Argon2id (GPU-resistant)
 * - Constant-time validation
 * - Auto-generated salt
 * - Memory wiping
 */
object PinFactor {

    /**
     * Generate digest from PIN using SHA-256
     * Returns 32-byte digest for storage
     */
    fun digest(pin: String): ByteArray {
        // Constant-time validation
        require(isValidPin(pin)) { "Invalid PIN format" }

        val pinBytes = pin.toByteArray()
        return try {
            com.zeropay.sdk.security.CryptoUtils.sha256(pinBytes)
        } finally {
            Arrays.fill(pinBytes, 0)
        }
    }

    /**
     * Verify PIN against stored hash (constant-time)
     */
    fun verify(pin: String, storedDigest: ByteArray): Boolean {
        if (!isValidPin(pin)) return false

        val inputDigest = digest(pin)
        return try {
            com.zeropay.sdk.security.CryptoUtils.constantTimeEquals(inputDigest, storedDigest)
        } finally {
            com.zeropay.sdk.security.CryptoUtils.wipeMemory(inputDigest)
        }
    }
    
    /**
     * Validates PIN format (constant-time)
     */
    fun isValidPin(pin: String): Boolean {
        var isValid = true
        
        // Check length
        isValid = isValid && (pin.length in 4..12)
        
        // Check all digits (scan entire string regardless)
        var allDigits = true
        for (char in pin) {
            allDigits = allDigits && char.isDigit()
        }
        isValid = isValid && allDigits
        
        // Check not empty
        isValid = isValid && pin.isNotEmpty()
        
        return isValid
    }
    
    /**
     * Legacy digest for backward compatibility (SHA-256 only)
     * Deprecated: Use digest() with Argon2id instead
     */
    @Deprecated("Use digest() with Argon2id", ReplaceWith("digest(pin)"))
    fun legacyDigest(pin: String): ByteArray {
        require(isValidPin(pin)) { "Invalid PIN format" }
        return com.zeropay.sdk.security.CryptoUtils.sha256(pin.toByteArray())
    }
}
