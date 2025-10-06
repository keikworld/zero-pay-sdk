package com.zeropay.enrollment.security

import com.zeropay.enrollment.models.User
import kotlin.random.Random

/**
 * UUID Manager - Generates and validates UUIDs
 */
object UUIDManager {
    
    /**
     * Generates a UUID v4 (random)
     */
    fun generateUUID(): String {
        val random = Random.Default
        
        // Generate 16 random bytes
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        
        // Set version to 4 (random)
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
        
        // Set variant to RFC 4122
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        
        // Format as UUID string
        return formatUUID(bytes)
    }
    
    /**
     * Formats byte array as UUID string
     */
    private fun formatUUID(bytes: ByteArray): String {
        fun toHex(b: Byte): String = String.format("%02x", b)
        
        return buildString {
            append(toHex(bytes[0]))
            append(toHex(bytes[1]))
            append(toHex(bytes[2]))
            append(toHex(bytes[3]))
            append('-')
            append(toHex(bytes[4]))
            append(toHex(bytes[5]))
            append('-')
            append(toHex(bytes[6]))
            append(toHex(bytes[7]))
            append('-')
            append(toHex(bytes[8]))
            append(toHex(bytes[9]))
            append('-')
            append(toHex(bytes[10]))
            append(toHex(bytes[11]))
            append(toHex(bytes[12]))
            append(toHex(bytes[13]))
            append(toHex(bytes[14]))
            append(toHex(bytes[15]))
        }
    }
    
    /**
     * Validates UUID format
     */
    fun validateUUID(uuid: String): Boolean {
        return User.isValidUUID(uuid)
    }
}
