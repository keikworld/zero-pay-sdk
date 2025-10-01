package com.zeropay.sdk.factors

import java.security.MessageDigest
import java.nio.ByteBuffer

object PatternFactor {

    data class PatternPoint(val x: Float, val y: Float, val t: Long)

    fun digestMicroTiming(points: List<PatternPoint>): ByteArray {
        val baos = mutableListOf<Byte>()
        for (p in points) {
            baos += floatToBytes(p.x).toList()
            baos += floatToBytes(p.y).toList()
            baos += longToBytes(p.t).toList()
        }
        return MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
    }

    fun digestNormalisedTiming(points: List<PatternPoint>): ByteArray {
        if (points.isEmpty()) return ByteArray(0)
        val t0 = points.first().t
        val t1 = points.last().t
        val duration = (t1 - t0).coerceAtLeast(1L)
        val baos = mutableListOf<Byte>()
        for (p in points) {
            val norm = ((p.t - t0).toFloat() / duration) * 1000f
            baos += floatToBytes(p.x).toList()
            baos += floatToBytes(p.y).toList()
            baos += floatToBytes(norm).toList()
        }
        return MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
    }

    private fun floatToBytes(f: Float): ByteArray = ByteBuffer.allocate(4).putFloat(f).array()
    private fun longToBytes(l: Long): ByteArray = ByteBuffer.allocate(8).putLong(l).array()
}