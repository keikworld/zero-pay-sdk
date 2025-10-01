package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object MouseFactor {

    data class MousePoint(val x: Float, val y: Float, val t: Long)

    /**
     * Generates a digest from mouse movement data including micro-timing.
     * Captures x, y coordinates and precise timestamps.
     */
    fun digestMicroTiming(points: List<MousePoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        
        val bytes = mutableListOf<Byte>()
        
        for (point in points) {
            bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
            bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }
}
