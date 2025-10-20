package com.zeropay.enrollment.security

import com.zeropay.sdk.security.CryptoUtils

/**
 * Alias Generator - Creates memorable aliases from UUIDs
 * Uses SHA-256 hash + base62 encoding
 */
object AliasGenerator {
    
    private const val BASE62_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private const val ALIAS_BYTE_LENGTH = 8
    
    /**
     * Generates alias from UUID
     * @param uuid User's UUID
     * @return Base62-encoded alias (8-12 chars)
     */
    fun generateAlias(uuid: String): String {
        // Hash UUID with SHA-256
        val hash = CryptoUtils.sha256(uuid.toByteArray(Charsets.UTF_8))
        
        // Take first 8 bytes
        val truncated = hash.copyOfRange(0, ALIAS_BYTE_LENGTH)
        
        // Convert to base62
        return encodeBase62(truncated)
    }
    
    /**
     * Encodes byte array to base62
     */
    private fun encodeBase62(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "0"
        
        // Convert bytes to BigInteger equivalent
        var num = 0L
        for (byte in bytes) {
            num = (num shl 8) or (byte.toInt() and 0xFF).toLong()
        }
        
        if (num == 0L) return "0"
        
        val result = StringBuilder()
        var remaining = num
        
        while (remaining > 0) {
            val digit = (remaining % 62).toInt()
            result.append(BASE62_CHARS[digit])
            remaining /= 62
        }
        
        return result.reverse().toString()
    }
    
    /**
     * Validates alias format
     */
    fun isValidAlias(alias: String): Boolean {
        return alias.length in 8..12 && alias.all { it in BASE62_CHARS }
    }
}
