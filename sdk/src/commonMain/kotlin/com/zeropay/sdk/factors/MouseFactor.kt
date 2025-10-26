package com.zeropay.sdk.factors

import com.zeropay.sdk.security.CryptoUtils

/**
 * Mouse Draw Factor - Logic Layer
 *
 * Captures mouse drawing patterns with timing analysis
 * Security: Mouse velocity/acceleration patterns are biometric
 *
 * Validation:
 * - Minimum: 3 points (reduced from 10 for better UX)
 * - Maximum: 300 points (DoS protection)
 */
object MouseFactor {

    const val MIN_POINTS = 3
    const val MAX_POINTS = 300

    data class MousePoint(val x: Float, val y: Float, val t: Long)

    fun digestMicroTiming(points: List<MousePoint>): ByteArray {
        require(points.isNotEmpty()) { "Mouse points cannot be empty" }
        require(points.size >= MIN_POINTS) { "Need at least $MIN_POINTS points for mouse pattern" }
        require(points.size <= MAX_POINTS) { "Too many points (max: $MAX_POINTS) - DoS protection" }

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
