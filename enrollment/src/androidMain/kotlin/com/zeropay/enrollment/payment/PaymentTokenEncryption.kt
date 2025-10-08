package com.zeropay.enrollment.payment

import com.zeropay.sdk.Factor
import com.zeropay.sdk.crypto.CryptoUtils
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Payment Token Encryption - PRODUCTION VERSION
 * 
 * Encrypts payment provider tokens using keys derived from UUID + factors.
 * 
 * Security Model:
 * - Encryption Key = PBKDF2(UUID + All Factor Digests)
 * - AES-256-GCM encryption
 * - Unique nonce per token
 * - 128-bit authentication tag
 * 
 * Zero-Knowledge:
 * - Token decryption requires UUID + correct factors
 * - Attacker needs BOTH to decrypt
 * - If factors change, tokens become inaccessible
 * 
 * GDPR Compliance:
 * - Encrypted at rest
 * - User can delete (right to erasure)
 * - No plaintext storage
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
object PaymentTokenEncryption {
    
    private const val AES_KEY_LENGTH = 32 // 256 bits
    private const val GCM_NONCE_SIZE = 12 // 96 bits
    private const val GCM_TAG_SIZE = 128 // 128 bits
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_PREFIX = "zeropay.payment.v1"
    
    /**
     * Derive encryption key from UUID and factor digests
     * 
     * Key Derivation:
     * 1. Concatenate: UUID + all factor digests
     * 2. Apply PBKDF2-HMAC-SHA256 (100,000 iterations)
     * 3. Output: 256-bit AES key
     * 
     * Security:
     * - Computationally expensive (prevents brute force)
     * - Requires knowledge of UUID + all factors
     * - Different factor combinations = different keys
     * 
     * @param uuid User UUID
     * @param factorDigests Map of factors to SHA-256 digests
     * @return 32-byte AES-256 key
     */
    fun deriveEncryptionKey(
        uuid: String,
        factorDigests: Map<Factor, ByteArray>
    ): ByteArray {
        require(uuid.isNotBlank()) { "UUID cannot be blank" }
        require(factorDigests.isNotEmpty()) { "At least one factor required" }
        
        // Concatenate UUID + all factor digests (sorted by factor name for consistency)
        val sortedFactors = factorDigests.toSortedMap(compareBy { it.name })
        val combined = mutableListOf<Byte>()
        
        // Add UUID bytes
        combined.addAll(uuid.toByteArray().toList())
        
        // Add factor digests
        sortedFactors.forEach { (_, digest) ->
            require(digest.size == 32) { "Factor digest must be 32 bytes (SHA-256)" }
            combined.addAll(digest.toList())
        }
        
        // Derive key using PBKDF2
        val password = combined.toByteArray()
        val salt = SALT_PREFIX.toByteArray()
        
        return CryptoUtils.pbkdf2(
            password = password,
            salt = salt,
            iterations = PBKDF2_ITERATIONS,
            keyLength = AES_KEY_LENGTH
        )
    }
    
    /**
     * Encrypt payment token
     * 
     * Encryption: AES-256-GCM
     * - Authenticated encryption (prevents tampering)
     * - Random nonce per encryption
     * - 128-bit authentication tag
     * 
     * @param token Plaintext payment token
     * @param key 32-byte AES key (from deriveEncryptionKey)
     * @return Encrypted data (nonce + ciphertext + tag)
     */
    fun encryptToken(token: String, key: ByteArray): EncryptedToken {
        require(token.isNotBlank()) { "Token cannot be blank" }
        require(key.size == AES_KEY_LENGTH) { "Key must be 32 bytes" }
        
        // Generate random nonce
        val nonce = ByteArray(GCM_NONCE_SIZE)
        SecureRandom().nextBytes(nonce)
        
        // Encrypt with AES-GCM
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, nonce)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        
        return EncryptedToken(
            nonce = nonce,
            ciphertext = ciphertext
        )
    }
    
    /**
     * Decrypt payment token
     * 
     * @param encryptedToken Encrypted token data
     * @param key 32-byte AES key (from deriveEncryptionKey)
     * @return Decrypted plaintext token
     * @throws Exception if authentication fails or key is wrong
     */
    fun decryptToken(encryptedToken: EncryptedToken, key: ByteArray): String {
        require(key.size == AES_KEY_LENGTH) { "Key must be 32 bytes" }
        require(encryptedToken.nonce.size == GCM_NONCE_SIZE) { "Invalid nonce size" }
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_SIZE, encryptedToken.nonce)
        
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        val plaintext = cipher.doFinal(encryptedToken.ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
    
    /**
     * Securely wipe key from memory
     * 
     * @param key Key to wipe
     */
    fun wipeKey(key: ByteArray) {
        key.fill(0)
    }
    
    /**
     * Encrypted token container
     */
    data class EncryptedToken(
        val nonce: ByteArray,      // 12 bytes (96 bits)
        val ciphertext: ByteArray  // Variable length (includes 16-byte auth tag)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EncryptedToken
            if (!nonce.contentEquals(other.nonce)) return false
            if (!ciphertext.contentEquals(other.ciphertext)) return false
            return true
        }
        
        override fun hashCode(): Int {
            var result = nonce.contentHashCode()
            result = 31 * result + ciphertext.contentHashCode()
            return result
        }
        
        /**
         * Convert to base64 for storage
         */
        fun toBase64(): String {
            val combined = nonce + ciphertext
            return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
        }
        
        companion object {
            /**
             * Parse from base64
             */
            fun fromBase64(base64: String): EncryptedToken {
                val combined = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                require(combined.size > GCM_NONCE_SIZE) { "Invalid encrypted token format" }
                
                val nonce = combined.sliceArray(0 until GCM_NONCE_SIZE)
                val ciphertext = combined.sliceArray(GCM_NONCE_SIZE until combined.size)
                
                return EncryptedToken(nonce, ciphertext)
            }
        }
    }
}
