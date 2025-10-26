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

    const val MIN_POINTS = 10   // Minimum points for biometric quality
    const val MAX_POINTS = 300  // DoS protection

    /**
     * Generate digest including pressure data
     */
    fun digestFull(points: List<StylusPoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        require(points.size >= MIN_POINTS) { "Need at least $MIN_POINTS points for stylus pattern" }
        require(points.size <= MAX_POINTS) { "Too many points (max: $MAX_POINTS)" }

        // SECURITY: Validate pressure values (must be in 0.0-1.0 range)
        require(points.all { it.pressure in 0.0f..1.0f }) {
            "Invalid pressure values: all pressure values must be between 0.0 and 1.0"
        }
        
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
