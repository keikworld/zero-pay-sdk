package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Mouse Draw Factor - Logic Layer
 * 
 * Captures mouse drawing patterns with timing analysis
 * Security: Mouse velocity/acceleration patterns are biometric
 */
object MouseFactor {
    
    data class MousePoint(val x: Float, val y: Float, val t: Long)
    
    fun digestMicroTiming(points: List<MousePoint>): ByteArray {
        require(points.isNotEmpty()) { "Mouse points cannot be empty" }
        require(points.size >= 3) { "Need at least 3 points for mouse pattern" }
        
        val bytes = mutableListOf<Byte>()
        
        // Include position and timing
        points.forEach { point ->
            bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
            bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }
}
