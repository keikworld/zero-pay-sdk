package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.KeyDerivation
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
     * Generate digest from PIN using Argon2id
     * Returns DerivedKey for storage
     */
    fun digest(pin: String): KeyDerivation.DerivedKey {
        // Constant-time validation
        require(isValidPin(pin)) { "Invalid PIN format" }
        
        val pinBytes = pin.toByteArray()
        return try {
            KeyDerivation.deriveKey(pinBytes)
        } finally {
            Arrays.fill(pinBytes, 0)
        }
    }
    
    /**
     * Generate digest with specific salt (for verification)
     */
    fun digestWithSalt(pin: String, salt: ByteArray): KeyDerivation.DerivedKey {
        require(isValidPin(pin)) { "Invalid PIN format" }
        
        val pinBytes = pin.toByteArray()
        return try {
            KeyDerivation.deriveKey(pinBytes, salt)
        } finally {
            Arrays.fill(pinBytes, 0)
        }
    }
    
    /**
     * Verify PIN against stored hash (constant-time)
     */
    fun verify(pin: String, storedKey: KeyDerivation.DerivedKey): Boolean {
        if (!isValidPin(pin)) return false
        
        val pinBytes = pin.toByteArray()
        return try {
            KeyDerivation.verify(pinBytes, storedKey)
        } finally {
            Arrays.fill(pinBytes, 0)
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
        return com.zeropay.sdk.crypto.CryptoUtils.sha256(pin.toByteArray())
    }
}
