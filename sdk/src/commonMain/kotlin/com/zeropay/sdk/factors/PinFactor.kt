package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object PinFactor {

    /**
     * Generates a secure hash digest from a PIN string.
     * The PIN should be numeric only and is converted to bytes before hashing.
     */
    fun digest(pin: String): ByteArray {
        require(pin.isNotEmpty()) { "PIN cannot be empty" }
        require(pin.all { it.isDigit() }) { "PIN must contain only digits" }
        require(pin.length >= 4) { "PIN must be at least 4 digits" }
        require(pin.length <= 12) { "PIN must be at most 12 digits" }
        
        val bytes = pin.encodeToByteArray()
        return CryptoUtils.sha256(bytes)
    }
    
    /**
     * Validates PIN format without generating a digest.
     */
    fun isValidPin(pin: String): Boolean {
        return pin.length in 4..12 && pin.all { it.isDigit() }
    }
}
