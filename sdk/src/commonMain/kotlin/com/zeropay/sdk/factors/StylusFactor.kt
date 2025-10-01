package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object StylusFactor {

    data class StylusPoint(val x: Float, val y: Float, val pressure: Float, val t: Long)

    /**
     * Generates a digest from stylus input data including pressure and timing.
     * Captures x, y coordinates, pressure, and precise timestamps.
     * Pressure data is a key biometric feature of stylus input.
     */
    fun digestFull(points: List<StylusPoint>): ByteArray {
        require(points.isNotEmpty()) { "Points list cannot be empty" }
        
        val bytes = mutableListOf<Byte>()
        
        for (point in points) {
            bytes.addAll(CryptoUtils.floatToBytes(point.x).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.y).toList())
            bytes.addAll(CryptoUtils.floatToBytes(point.pressure).toList())
            bytes.addAll(CryptoUtils.longToBytes(point.t).toList())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }
}
