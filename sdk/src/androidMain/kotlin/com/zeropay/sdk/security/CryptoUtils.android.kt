// Path: sdk/src/androidMain/kotlin/com/zeropay/sdk/security/CryptoUtils.android.kt

package com.zeropay.sdk.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * CryptoUtils - Android Implementation
 * 
 * Platform-specific implementation using Android/JVM crypto APIs.
 * 
 * Uses:
 * - java.security.MessageDigest for SHA-256
 * - javax.crypto.SecretKeyFactory for PBKDF2
 * - java.security.SecureRandom for CSPRNG
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */
actual object CryptoUtils {
    
    private const val SHA_256_ALGORITHM = "SHA-256"
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SHA_256_DIGEST_SIZE = 32
    private const val NONCE_SIZE_BYTES = 16
    
    // Thread-local instances for thread safety
    private val sha256Digest = ThreadLocal.withInitial {
        MessageDigest.getInstance(SHA_256_ALGORITHM)
    }
    
    private val secureRandom = ThreadLocal.withInitial {
        SecureRandom()
    }
    
    // ==================== HASHING ====================
    
    actual fun sha256(data: ByteArray): ByteArray {
        require(data.isNotEmpty()) { "Cannot hash empty data" }
        
        val digest = sha256Digest.get()!!
        digest.reset()
        val hash = digest.digest(data)
        
        require(hash.size == SHA_256_DIGEST_SIZE) {
            "SHA-256 digest size mismatch: expected $SHA_256_DIGEST_SIZE, got ${hash.size}"
        }
        
        return hash
    }
    
    actual fun sha256(data: String): ByteArray {
        return sha256(data.toByteArray(Charsets.UTF_8))
    }
    
    actual fun multiHash(dataList: List<ByteArray>): ByteArray {
        require(dataList.isNotEmpty()) { "Cannot hash empty list" }
        
        val combined = dataList.reduce { acc, bytes -> acc + bytes }
        return sha256(combined)
    }
    
    // ==================== KEY DERIVATION ====================
    
    actual fun pbkdf2(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBytes: Int
    ): ByteArray {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(salt.isNotEmpty()) { "Salt cannot be empty" }
        require(iterations > 0) { "Iterations must be positive" }
        require(keyLengthBytes > 0) { "Key length must be positive" }
        
        val passwordChars = password.toString(Charsets.UTF_8).toCharArray()
        val spec = PBEKeySpec(passwordChars, salt, iterations, keyLengthBytes * 8)
        
        return try {
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val derivedKey = factory.generateSecret(spec).encoded
            
            // Wipe password from memory
            passwordChars.fill('0')
            spec.clearPassword()
            
            derivedKey
        } catch (e: Exception) {
            // Wipe password even on error
            passwordChars.fill('0')
            throw SecurityException("PBKDF2 key derivation failed: ${e.message}", e)
        }
    }
    
    // ==================== CONSTANT-TIME COMPARISON ====================
    
    actual fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return constantTimeEqualsCommon(a, b)
    }
    
    // ==================== MEMORY WIPING ====================
    
    actual fun wipeMemory(data: ByteArray) {
        wipeMemoryCommon(data)
    }
    
    actual fun wipeMemory(vararg arrays: ByteArray) {
        arrays.forEach { wipeMemoryCommon(it) }
    }
    
    // ==================== CSPRNG OPERATIONS ====================
    
    actual fun generateNonce(): ByteArray {
        val nonce = ByteArray(NONCE_SIZE_BYTES)
        secureRandom.get()!!.nextBytes(nonce)
        return nonce
    }
    
    actual fun generateRandomBytes(size: Int): ByteArray {
        require(size > 0) { "Size must be positive" }
        
        val bytes = ByteArray(size)
        secureRandom.get()!!.nextBytes(bytes)
        return bytes
    }
    
    actual fun <T> shuffleSecure(list: MutableList<T>): MutableList<T> {
        val random = secureRandom.get()!!
        
        // Fisher-Yates shuffle
        for (i in list.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = list[i]
            list[i] = list[j]
            list[j] = temp
        }
        
        return list
    }
    
    // ==================== VALIDATION ====================
    
    actual fun isValidSha256Digest(digest: ByteArray): Boolean {
        return digest.size == SHA_256_DIGEST_SIZE
    }
    
    actual fun isValidNonce(nonce: ByteArray): Boolean {
        return nonce.size == NONCE_SIZE_BYTES
    }
    
    // ==================== UTILITY ====================
    
    actual fun bytesToHex(bytes: ByteArray): String {
        return bytesToHexCommon(bytes)
    }
    
    actual fun hexToBytes(hex: String): ByteArray {
        return hexToBytesCommon(hex)
    }
}
