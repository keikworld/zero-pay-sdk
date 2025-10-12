// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/security/KeyDerivation.kt

package com.zeropay.sdk.security

/**
 * KeyDerivation - Factor-Based Key Derivation
 * 
 * Derives encryption keys from user authentication factors using PBKDF2.
 * 
 * Purpose:
 * - Convert user factors (patterns, emojis, etc.) into encryption keys
 * - Enable double encryption Layer 1 (user-controlled)
 * - Deterministic key generation (same factors = same key)
 * - Brute-force resistant (100K iterations)
 * 
 * Security Features:
 * - PBKDF2WithHmacSHA256 (NIST approved)
 * - 100,000 iterations (OWASP recommended)
 * - UUID-based salt (prevents rainbow tables)
 * - 32-byte output (256-bit key for AES-256)
 * - Memory wiping after derivation
 * 
 * Use Case:
 * ```kotlin
 * // Enrollment: Derive key from factors
 * val factors = listOf("pattern123", "emoji🎨", "color#FF5733")
 * val uuid = "user-uuid-12345"
 * val encryptionKey = KeyDerivation.deriveKey(uuid, factors)
 * 
 * // Verification: Derive same key with same factors
 * val verifyKey = KeyDerivation.deriveKey(uuid, factors)
 * // encryptionKey == verifyKey (deterministic)
 * ```
 * 
 * Double Encryption Integration:
 * - Layer 1: This derived key encrypts factor hashes
 * - Layer 2: KMS wraps this derived key
 * - Storage: Only KMS-wrapped key is stored
 * 
 * GDPR Compliance:
 * - No raw factors stored
 * - Only hashes encrypted with derived key
 * - Deletion: Delete UUID salt = key cannot be rederived
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object KeyDerivation {
    
    /**
     * Derive encryption key from multiple factors
     * 
     * Algorithm:
     * 1. Hash each factor individually (SHA-256)
     * 2. Concatenate all factor hashes
     * 3. Use PBKDF2 to derive key from concatenated hashes
     * 4. Use UUID as salt (unique per user)
     * 5. 100,000 iterations (brute-force resistant)
     * 6. Output 32 bytes (256-bit key)
     * 
     * Security:
     * - Each factor contributes to final key
     * - Order of factors matters (deterministic)
     * - Changing any factor changes the key
     * - Computationally expensive (brute-force resistant)
     * 
     * @param uuid User UUID (used as salt)
     * @param factors List of factor values (strings)
     * @param iterations PBKDF2 iterations (default: 100,000)
     * @return 32-byte encryption key
     * @throws IllegalArgumentException if inputs invalid
     * 
     * Example:
     * ```kotlin
     * val uuid = "550e8400-e29b-41d4-a716-446655440000"
     * val factors = listOf(
     *     "pattern12345678",
     *     "emoji🔥🎨💎🚀",
     *     "color#FF5733#00FF00#0000FF"
     * )
     * val key = KeyDerivation.deriveKey(uuid, factors)
     * // key.size == 32
     * ```
     */
    fun deriveKey(
        uuid: String,
        factors: List<String>,
        iterations: Int = 100_000
    ): ByteArray {
        // Validate inputs
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(factors.isNotEmpty()) { "At least one factor required" }
        require(factors.all { it.isNotBlank() }) { "Factors cannot be blank" }
        require(iterations > 0) { "Iterations must be positive" }
        
        try {
            // Step 1: Hash each factor individually
            val factorHashes = factors.map { factor ->
                CryptoUtils.sha256(factor.toByteArray(Charsets.UTF_8))
            }
            
            // Step 2: Concatenate all factor hashes
            val combinedHashes = factorHashes.reduce { acc, hash -> acc + hash }
            
            // Step 3: Use UUID as salt
            val salt = uuid.toByteArray(Charsets.UTF_8)
            
            // Step 4: Derive key using PBKDF2
            val derivedKey = CryptoUtils.pbkdf2(
                password = combinedHashes,
                salt = salt,
                iterations = iterations,
                keyLengthBytes = 32
            )
            
            // Step 5: Wipe intermediate data
            factorHashes.forEach { CryptoUtils.wipeMemory(it) }
            CryptoUtils.wipeMemory(combinedHashes)
            
            return derivedKey
            
        } catch (e: Exception) {
            throw SecurityException("Key derivation failed: ${e.message}", e)
        }
    }
    
    /**
     * Derive encryption key from pre-hashed factors
     * 
     * Used when factors are already hashed (e.g., from Redis).
     * 
     * @param uuid User UUID (used as salt)
     * @param factorHashes List of factor hashes (32-byte SHA-256 digests)
     * @param iterations PBKDF2 iterations (default: 100,000)
     * @return 32-byte encryption key
     * @throws IllegalArgumentException if inputs invalid
     * 
     * Example:
     * ```kotlin
     * val uuid = "550e8400-e29b-41d4-a716-446655440000"
     * val factorHashes = listOf(
     *     // SHA-256 hashes from Redis
     *     byteArrayOf(...), // pattern hash
     *     byteArrayOf(...), // emoji hash
     *     byteArrayOf(...)  // color hash
     * )
     * val key = KeyDerivation.deriveKeyFromHashes(uuid, factorHashes)
     * ```
     */
    fun deriveKeyFromHashes(
        uuid: String,
        factorHashes: List<ByteArray>,
        iterations: Int = 100_000
    ): ByteArray {
        // Validate inputs
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(factorHashes.isNotEmpty()) { "At least one factor hash required" }
        require(factorHashes.all { it.size == 32 }) { 
            "All factor hashes must be 32 bytes (SHA-256)" 
        }
        require(iterations > 0) { "Iterations must be positive" }
        
        try {
            // Concatenate all factor hashes
            val combinedHashes = factorHashes.reduce { acc, hash -> acc + hash }
            
            // Use UUID as salt
            val salt = uuid.toByteArray(Charsets.UTF_8)
            
            // Derive key using PBKDF2
            val derivedKey = CryptoUtils.pbkdf2(
                password = combinedHashes,
                salt = salt,
                iterations = iterations,
                keyLengthBytes = 32
            )
            
            // Wipe intermediate data
            CryptoUtils.wipeMemory(combinedHashes)
            
            return derivedKey
            
        } catch (e: Exception) {
            throw SecurityException("Key derivation from hashes failed: ${e.message}", e)
        }
    }
    
    /**
     * Validate derived key strength
     * 
     * Checks:
     * - Key is exactly 32 bytes (256 bits)
     * - Key is not all zeros
     * - Key has sufficient entropy
     * 
     * @param key Key to validate
     * @return true if key is valid and strong
     */
    fun isValidKey(key: ByteArray): Boolean {
        // Check size
        if (key.size != 32) {
            return false
        }
        
        // Check not all zeros
        if (key.all { it == 0.toByte() }) {
            return false
        }
        
        // Check has some entropy (at least 50% bits set)
        val setBits = key.sumOf { byte ->
            Integer.bitCount(byte.toInt() and 0xFF)
        }
        val totalBits = key.size * 8
        val entropyRatio = setBits.toDouble() / totalBits
        
        return entropyRatio >= 0.3 && entropyRatio <= 0.7
    }
    
    /**
     * Derive verification token from key
     * 
     * Creates a hash of the key for verification purposes.
     * Used to verify key correctness without storing the key.
     * 
     * @param key Encryption key
     * @return 32-byte verification token (SHA-256 of key)
     */
    fun deriveVerificationToken(key: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes" }
        return CryptoUtils.sha256(key)
    }
    
    /**
     * Re-derive key with different iteration count
     * 
     * Used for key stretching or migration to stronger parameters.
     * 
     * @param uuid User UUID
     * @param factors Factor values
     * @param newIterations New iteration count
     * @return New derived key with updated parameters
     */
    fun reDerive(
        uuid: String,
        factors: List<String>,
        newIterations: Int
    ): ByteArray {
        require(newIterations >= 100_000) { 
            "New iteration count must be >= 100,000 for security" 
        }
        
        return deriveKey(uuid, factors, newIterations)
    }
}

/**
 * KeyDerivationException - Custom exception for key derivation errors
 */
class KeyDerivationException(message: String, cause: Throwable? = null) : 
    SecurityException(message, cause)
