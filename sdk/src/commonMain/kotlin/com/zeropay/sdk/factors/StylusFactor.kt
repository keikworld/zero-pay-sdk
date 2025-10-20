package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Stylus Factor with Pressure Data
 * 
 * Security:
 * - Constant-time verification
 * - Includes pressure biometric
 * - Memory wiping
 */
object StylusFactor {

    data class StylusPoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val t: Long
    )
    
    private const val MAX_POINTS = 300

    /**
     * Generate digest including pressure data
     */
    fun digestFull(points: List<StylusPoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        require(points.size <= MAX_POINTS) { "Too many points (max: $MAX_POINTS)" }
        
        val bytes = mutableListOf<Byte>()
        
        try {
            for (point in points) {
                bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
                bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
                bytes.addAll(CryptoUtils.floatToBytes(point.pressure).toList())
                bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
            }
            
            return CryptoUtils.sha256(bytes.toByteArray())
        } finally {
            bytes.clear()
        }
    }
    
    /**
     * Verify stylus pattern (constant-time)
     */
    fun verify(points: List<StylusPoint>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digestFull(points)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }
}
