// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/security/CryptoUtils.kt

package com.zeropay.sdk.security

import kotlin.experimental.xor

/**
 * CryptoUtils - Production-Ready Cryptographic Utilities
 * 
 * Provides secure cryptographic operations for ZeroPay authentication engine.
 * 
 * Features:
 * - SHA-256 hashing (32-byte digests)
 * - PBKDF2 key derivation (100K iterations)
 * - Constant-time comparison (timing-attack resistant)
 * - Secure memory wiping
 * - CSPRNG operations (Fisher-Yates shuffle)
 * - Nonce generation (replay protection)
 * 
 * Security Principles:
 * - No raw data stored
 * - Constant-time operations
 * - Memory wiping after use
 * - Cryptographically secure randomness
 * - Side-channel attack resistance
 * 
 * Compliance:
 * - GDPR: No PII storage
 * - PSD3: SCA-compliant hashing
 * - NIST: Approved algorithms (SHA-256, PBKDF2)
 * 
 * Thread Safety:
 * - All methods are thread-safe
 * - No shared mutable state
 * - Safe for concurrent use
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
expect object CryptoUtils {
    
    // ==================== HASHING ====================
    
    /**
     * SHA-256 Hash - Generate 32-byte digest
     * 
     * Used for:
     * - Factor digests (patterns, emojis, colors, voice)
     * - UUID hashing
     * - Proof hashing
     * 
     * Security:
     * - Collision-resistant
     * - Pre-image resistant
     * - Avalanche effect
     * 
     * @param data Input data to hash
     * @return 32-byte SHA-256 digest
     * @throws IllegalArgumentException if data is empty
     * 
     * Example:
     * ```kotlin
     * val pattern = "12345678"
     * val digest = CryptoUtils.sha256(pattern.toByteArray())
     * // digest.size == 32
     * ```
     */
    fun sha256(data: ByteArray): ByteArray
    
    /**
     * SHA-256 Hash (String) - Convenience wrapper
     * 
     * @param data Input string to hash (UTF-8 encoding)
     * @return 32-byte SHA-256 digest
     */
    fun sha256(data: String): ByteArray
    
    /**
     * Multi-Hash - Hash concatenated data
     * 
     * Used for combining multiple factors into single digest.
     * 
     * @param dataList List of byte arrays to hash
     * @return 32-byte SHA-256 digest of concatenated data
     * @throws IllegalArgumentException if list is empty
     * 
     * Example:
     * ```kotlin
     * val factors = listOf(
     *     "pattern123".toByteArray(),
     *     "emoji🎨🔥".toByteArray(),
     *     "color#FF5733".toByteArray()
     * )
     * val combinedDigest = CryptoUtils.multiHash(factors)
     * ```
     */
    fun multiHash(dataList: List<ByteArray>): ByteArray
    
    // ==================== KEY DERIVATION ====================
    
    /**
     * PBKDF2 Key Derivation - Derive encryption key from password
     * 
     * Used for:
     * - Deriving encryption keys from user factors
     * - Password-based key generation
     * - Slow key derivation (brute-force resistant)
     * 
     * Parameters:
     * - Algorithm: PBKDF2WithHmacSHA256
     * - Iterations: 100,000 (NIST recommended)
     * - Output: 256 bits (32 bytes)
     * 
     * Security:
     * - Computationally expensive (brute-force resistant)
     * - Salted (prevents rainbow tables)
     * - Deterministic (same input = same output)
     * 
     * @param password Password/factors to derive from
     * @param salt Salt for key derivation (UUID recommended)
     * @param iterations Number of iterations (default: 100,000)
     * @param keyLengthBytes Output key length in bytes (default: 32)
     * @return Derived key
     * @throws IllegalArgumentException if inputs invalid
     * 
     * Example:
     * ```kotlin
     * val factors = listOf("pattern", "emoji", "color")
     * val combinedFactors = factors.joinToString("").toByteArray()
     * val uuid = "user-uuid-here"
     * val derivedKey = CryptoUtils.pbkdf2(
     *     password = combinedFactors,
     *     salt = uuid.toByteArray(),
     *     iterations = 100_000,
     *     keyLengthBytes = 32
     * )
     * ```
     */
    fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int = 100_000,
        keyLengthBytes: Int = 32
    ): ByteArray
    
    // ==================== CONSTANT-TIME COMPARISON ====================
    
    /**
     * Constant-Time Comparison - Timing-attack resistant equality check
     * 
     * Used for:
     * - Digest comparison (authentication)
     * - Proof verification
     * - Secret comparison
     * 
     * Security:
     * - Constant execution time (prevents timing attacks)
     * - Side-channel resistant
     * - Safe for cryptographic comparisons
     * 
     * Algorithm:
     * - XOR all bytes
     * - Accumulate differences
     * - Always checks all bytes
     * 
     * @param a First byte array
     * @param b Second byte array
     * @return true if arrays are equal, false otherwise
     * 
     * Example:
     * ```kotlin
     * val storedDigest = // from Redis
     * val inputDigest = CryptoUtils.sha256(userInput)
     * val isValid = CryptoUtils.constantTimeEquals(storedDigest, inputDigest)
     * ```
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
    
    // ==================== MEMORY WIPING ====================
    
    /**
     * Secure Memory Wipe - Overwrite sensitive data
     * 
     * Used for:
     * - Wiping keys after use
     * - Clearing sensitive factors
     * - Removing passwords from memory
     * 
     * Security:
     * - Overwrites with zeros
     * - Prevents memory dumps
     * - Defense against cold boot attacks
     * 
     * Note: JVM/Kotlin may optimize away zeros, but this is best-effort
     * 
     * @param data Byte array to wipe
     * 
     * Example:
     * ```kotlin
     * val password = "user-password".toByteArray()
     * // Use password...
     * CryptoUtils.wipeMemory(password)
     * // password is now all zeros
     * ```
     */
    fun wipeMemory(data: ByteArray)
    
    /**
     * Secure Memory Wipe (Multiple Arrays)
     * 
     * Convenience method to wipe multiple arrays at once.
     * 
     * @param arrays Byte arrays to wipe
     */
    fun wipeMemory(vararg arrays: ByteArray)
    
    // ==================== CSPRNG OPERATIONS ====================
    
    /**
     * Generate Nonce - Cryptographically secure random nonce
     * 
     * Used for:
     * - Replay protection
     * - Session IDs
     * - Challenge generation
     * 
     * Security:
     * - Cryptographically secure random (CSPRNG)
     * - 16 bytes (128 bits of entropy)
     * - Unpredictable
     * 
     * @return 16-byte random nonce
     * 
     * Example:
     * ```kotlin
     * val sessionNonce = CryptoUtils.generateNonce()
     * val challengeId = CryptoUtils.sha256(sessionNonce)
     * ```
     */
    fun generateNonce(): ByteArray
    
    /**
     * Generate Random Bytes - Custom-size random data
     * 
     * @param size Number of bytes to generate
     * @return Random byte array
     * @throws IllegalArgumentException if size <= 0
     */
    fun generateRandomBytes(size: Int): ByteArray
    
    /**
     * CSPRNG Shuffle - Cryptographically secure Fisher-Yates shuffle
     * 
     * Used for:
     * - Shuffling authentication factors
     * - Randomizing challenge order
     * - Preventing pattern recognition
     * 
     * Security:
     * - Cryptographically secure random (CSPRNG)
     * - Unbiased shuffle
     * - No predictable patterns
     * 
     * Algorithm: Fisher-Yates (Knuth) shuffle with SecureRandom
     * 
     * @param list List to shuffle (modified in-place)
     * @return Shuffled list (same reference)
     * 
     * Example:
     * ```kotlin
     * val factors = mutableListOf("pattern", "emoji", "color", "voice")
     * CryptoUtils.shuffleSecure(factors)
     * // factors is now randomly shuffled
     * ```
     */
    fun <T> shuffleSecure(list: MutableList<T>): MutableList<T>
    
    // ==================== VALIDATION ====================
    
    /**
     * Validate SHA-256 Digest - Check if byte array is valid digest
     * 
     * @param digest Byte array to validate
     * @return true if valid 32-byte digest
     */
    fun isValidSha256Digest(digest: ByteArray): Boolean
    
    /**
     * Validate Nonce - Check if byte array is valid nonce
     * 
     * @param nonce Byte array to validate
     * @return true if valid 16-byte nonce
     */
    fun isValidNonce(nonce: ByteArray): Boolean
    
    // ==================== UTILITY ====================
    
    /**
     * Bytes to Hex - Convert byte array to hex string
     * 
     * Used for logging and debugging (NOT for storage).
     * 
     * @param bytes Byte array to convert
     * @return Hex string representation (lowercase)
     * 
     * Example:
     * ```kotlin
     * val digest = CryptoUtils.sha256("test".toByteArray())
     * val hexString = CryptoUtils.bytesToHex(digest)
     * // hexString = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
     * ```
     */
    fun bytesToHex(bytes: ByteArray): String
    
    /**
     * Hex to Bytes - Convert hex string to byte array
     *
     * @param hex Hex string to convert
     * @return Byte array
     * @throws IllegalArgumentException if hex string invalid
     */
    fun hexToBytes(hex: String): ByteArray

    // ==================== ADDITIONAL UTILITIES ====================

    /**
     * HMAC-SHA256 - Hash-based Message Authentication Code
     *
     * Used for:
     * - Message authentication
     * - API request signing
     * - Secure token generation
     *
     * @param key HMAC key
     * @param data Data to authenticate
     * @return 32-byte HMAC-SHA256 digest
     * @throws IllegalArgumentException if key or data is empty
     *
     * Example:
     * ```kotlin
     * val key = "secret-key".toByteArray()
     * val message = "important-data".toByteArray()
     * val hmac = CryptoUtils.hmacSha256(key, message)
     * ```
     */
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray

    /**
     * Float to Bytes - Convert float to byte array (big-endian)
     *
     * Used for:
     * - Serializing accelerometer data (Balance factor)
     * - Storing timing data (Rhythm factor)
     * - Pressure data (Stylus factor)
     *
     * @param value Float value to convert
     * @return 4-byte array (big-endian)
     *
     * Example:
     * ```kotlin
     * val pressure = 0.75f
     * val bytes = CryptoUtils.floatToBytes(pressure)
     * // bytes.size == 4
     * ```
     */
    fun floatToBytes(value: Float): ByteArray

    /**
     * Long to Bytes - Convert long to byte array (big-endian)
     *
     * Used for:
     * - Timestamp serialization
     * - Nonce generation
     * - Session ID encoding
     *
     * @param value Long value to convert
     * @return 8-byte array (big-endian)
     *
     * Example:
     * ```kotlin
     * val timestamp = System.currentTimeMillis()
     * val bytes = CryptoUtils.longToBytes(timestamp)
     * // bytes.size == 8
     * ```
     */
    fun longToBytes(value: Long): ByteArray
}

/**
 * Common implementation of constant-time equals
 * (Used by platform-specific implementations)
 */
internal fun constantTimeEqualsCommon(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) {
        return false
    }
    
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    
    return result == 0
}

/**
 * Common implementation of memory wiping
 * (Used by platform-specific implementations)
 */
internal fun wipeMemoryCommon(data: ByteArray) {
    data.fill(0)
}

/**
 * Common implementation of hex conversion
 * (Used by platform-specific implementations)
 */
internal fun bytesToHexCommon(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Common implementation of hex parsing
 * (Used by platform-specific implementations)
 */
internal fun hexToBytesCommon(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }
    require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        "Hex string contains invalid characters"
    }
    
    return hex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}
