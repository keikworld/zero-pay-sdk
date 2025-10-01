package com.zeropay.sdk.crypto

/**
 * Platform-agnostic cryptographic utilities.
 * Each platform must provide actual implementations.
 */

expect object CryptoUtils {
    /**
     * Computes SHA-256 hash of the input data.
     */
    fun sha256(data: ByteArray): ByteArray
    
    /**
     * Generates cryptographically secure random bytes.
     */
    fun secureRandomBytes(size: Int): ByteArray
    
    /**
     * Computes HMAC-SHA256 of the data with the given key.
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    
    /**
     * Converts a Float to its byte representation (4 bytes, big-endian).
     */
    fun floatToBytes(value: Float): ByteArray
    
    /**
     * Converts a Long to its byte representation (8 bytes, big-endian).
     */
    fun longToBytes(value: Long): ByteArray
}
