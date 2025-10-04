package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.CryptoUtils
import java.util.Arrays

/**
 * Mouse Factor with Constant-Time Verification
 * 
 * Security:
 * - Constant-time verification
 * - Memory wiping
 * - DoS protection
 */
object MouseFactor {

    data class MousePoint(val x: Float, val y: Float, val t: Long)
    
    private const val MAX_POINTS = 300

    /**
     * Generate digest from mouse movement with micro-timing
     */
    fun digestMicroTiming(points: List<MousePoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        require(points.size <= MAX_POINTS) { "Too many points (max: $MAX_POINTS)" }
        
        val bytes = mutableListOf<Byte>()
        
        try {
            for (point in points) {
                bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
                bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
                bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
            }
            
            return CryptoUtils.sha256(bytes.toByteArray())
        } finally {
            bytes.clear()
        }
    }
    
    /**
     * Verify mouse pattern (constant-time)
     */
    fun verify(points: List<MousePoint>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digestMicroTiming(points)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }
}
