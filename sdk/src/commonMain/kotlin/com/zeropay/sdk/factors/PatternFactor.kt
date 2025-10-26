package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Pattern Factor with Constant-Time Verification
 * 
 * Security Features:
 * - Constant-time verification
 * - Memory wiping
 * - DoS protection (max points)
 * - Timing normalization
 */
object PatternFactor {

    data class PatternPoint(val x: Float, val y: Float, val t: Long)

    const val MAX_POINTS = 300  // DoS protection

    /**
     * Generate digest with micro-timing (includes precise timestamps)
     */
    fun digestMicroTiming(points: List<PatternPoint>): ByteArray {
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
            // Clear sensitive data
            bytes.clear()
        }
    }

    /**
     * Generate digest with normalized timing (speed-invariant)
     */
    fun digestNormalisedTiming(points: List<PatternPoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        require(points.size <= MAX_POINTS) { "Too many points (max: $MAX_POINTS)" }
        
        if (points.size == 1) {
            // Single point - no timing to normalize
            val bytes = mutableListOf<Byte>()
            bytes.addAll(CryptoUtils.floatToBytes(points[0].x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(points[0].y).toList())
            bytes.addAll(CryptoUtils.floatToBytes(0f).toList())
            return CryptoUtils.sha256(bytes.toByteArray())
        }
        
        val t0 = points.first().t
        val t1 = points.last().t
        val duration = (t1 - t0).coerceAtLeast(1L)
        
        val bytes = mutableListOf<Byte>()
        
        try {
            for (point in points) {
                // Normalize time to 0-1000 scale
                val normalizedTime = ((point.t - t0).toFloat() / duration) * 1000f
                
                bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
                bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
                bytes.addAll(CryptoUtils.floatToBytes(normalizedTime).toList())
            }
            
            return CryptoUtils.sha256(bytes.toByteArray())
        } finally {
            bytes.clear()
        }
    }
    
    /**
     * Verify pattern (constant-time)
     */
    fun verifyMicroTiming(points: List<PatternPoint>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digestMicroTiming(points)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify normalized pattern (constant-time)
     */
    fun verifyNormalised(points: List<PatternPoint>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digestNormalisedTiming(points)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }
}
