package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object PatternFactor {

    data class PatternPoint(val x: Float, val y: Float, val t: Long)

    /**
     * Generates a digest from pattern data including micro-timing.
     * Captures x, y coordinates and precise timestamps.
     */
    fun digestMicroTiming(points: List<PatternPoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        
        val bytes = mutableListOf<Byte>()
        
        for (point in points) {
            bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
            bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }

    /**
     * Generates a digest from pattern data with normalized timing.
     * Timing is normalized to a 0-1000 scale relative to the gesture duration.
     * This makes the digest more resistant to variations in gesture speed.
     */
    fun digestNormalisedTiming(points: List<PatternPoint>): ByteArray {
        if (points.isEmpty()) {
            return ByteArray(0)
        }
        
        if (points.size == 1) {
            // Single point, no timing to normalize
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
        
        for (point in points) {
            // Normalize time to 0-1000 scale
            val normalizedTime = ((point.t - t0).toFloat() / duration) * 1000f
            
            bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
            bytes.addAll(CryptoUtils.floatToBytes(normalizedTime).toList())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }
}
