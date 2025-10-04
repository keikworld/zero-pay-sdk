package com.zeropay.sdk.crypto

/**
 * Constant-Time Operations
 * 
 * Prevents timing attacks by ensuring operations take the same time
 * regardless of input values.
 * 
 * CRITICAL: These functions MUST NOT:
 * - Use early returns based on data
 * - Use conditional branches based on secret data
 * - Access memory at secret-dependent addresses
 */
object ConstantTime {
    
    /**
     * Constant-time byte array comparison
     * 
     * Time taken is independent of:
     * - Where arrays differ
     * - Number of differences
     * - Array contents
     * 
     * @return true if arrays are equal, false otherwise
     */
    fun equals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        
        return result == 0
    }
    
    /**
     * Constant-time integer comparison
     */
    fun equals(a: Int, b: Int): Boolean {
        val diff = a xor b
        return diff == 0
    }
    
    /**
     * Constant-time long comparison
     */
    fun equals(a: Long, b: Long): Boolean {
        val diff = a xor b
        return diff == 0L
    }
    
    /**
     * Constant-time list comparison
     */
    fun equals(a: List<Int>, b: List<Int>): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i] xor b[i])
        }
        
        return result == 0
    }
    
    /**
     * Constant-time string comparison
     */
    fun equals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray()
        val bBytes = b.toByteArray()
        val result = equals(aBytes, bBytes)
        
        // Zero out sensitive data
        java.util.Arrays.fill(aBytes, 0)
        java.util.Arrays.fill(bBytes, 0)
        
        return result
    }
    
    /**
     * Constant-time conditional selection (ternary operator)
     * Returns a if condition is true, b otherwise
     * Time taken is independent of condition
     */
    fun select(condition: Boolean, a: Int, b: Int): Int {
        val mask = if (condition) -1 else 0
        return (a and mask) or (b and mask.inv())
    }
    
    /**
     * Constant-time byte selection
     */
    fun select(condition: Boolean, a: Byte, b: Byte): Byte {
        val mask = if (condition) -1 else 0
        return ((a.toInt() and mask) or (b.toInt() and mask.inv())).toByte()
    }
    
    /**
     * Constant-time array copy with masking
     */
    fun selectArray(condition: Boolean, a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == b.size) { "Arrays must be same size" }
        
        val result = ByteArray(a.size)
        for (i in a.indices) {
            result[i] = select(condition, a[i], b[i])
        }
        return result
    }
    
    /**
     * Check if value is zero (constant-time)
     */
    fun isZero(value: Int): Boolean {
        // If value is 0, all bits are 0
        // If value is non-zero, at least one bit is 1
        // This operation is constant-time
        val normalized = (value or -value) ushr 31
        return normalized == 0
    }
    
    /**
     * Check if value is non-zero (constant-time)
     */
    fun isNonZero(value: Int): Boolean {
        return !isZero(value)
    }
    
    /**
     * Constant-time minimum
     */
    fun min(a: Int, b: Int): Int {
        val diff = a - b
        val mask = (diff shr 31)  // -1 if a < b, 0 otherwise
        return b xor ((a xor b) and mask)
    }
    
    /**
     * Constant-time maximum
     */
    fun max(a: Int, b: Int): Int {
        val diff = a - b
        val mask = (diff shr 31)  // -1 if a < b, 0 otherwise
        return a xor ((a xor b) and mask)
    }
}
