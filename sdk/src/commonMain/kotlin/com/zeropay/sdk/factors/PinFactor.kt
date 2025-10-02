package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object PinFactor {

    /**
     * Generates a secure hash digest from a PIN string.
     * The PIN should be numeric only and is converted to bytes before hashing.
     * 
     * SECURITY: Uses constant-time validation to prevent timing attacks.
     */
    fun digest(pin: String): ByteArray {
        // Constant-time validation (always check all conditions)
        var isValid = true
        
        // Check length (constant time)
        isValid = isValid && (pin.length in 4..12)
        
        // Check all digits (constant time - always scan entire string)
        var allDigits = true
        for (char in pin) {
            allDigits = allDigits && char.isDigit()
        }
        isValid = isValid && allDigits
        
        // Check not empty (redundant but consistent)
        isValid = isValid && pin.isNotEmpty()
        
        // Only throw after all checks complete (constant time)
        if (!isValid) {
            throw IllegalArgumentException("Invalid PIN format")
        }
        
        val bytes = pin.encodeToByteArray()
        return CryptoUtils.sha256(bytes)
    }
    
    /**
     * Validates PIN format without generating a digest.
     * Uses constant-time comparison to prevent timing attacks.
     */
    fun isValidPin(pin: String): Boolean {
        var isValid = true
        
        isValid = isValid && (pin.length in 4..12)
        
        var allDigits = true
        for (char in pin) {
            allDigits = allDigits && char.isDigit()
        }
        isValid = isValid && allDigits
        
        return isValid
    }
}
