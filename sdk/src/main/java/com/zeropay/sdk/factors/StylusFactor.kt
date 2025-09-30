package com.zeropay.sdk.factors

import java.security.MessageDigest
import java.nio.ByteBuffer

object StylusFactor {

    data class StylusPoint(val x: Float, val y: Float, val pressure: Float, val t: Long)

    fun digestFull(points: List<StylusPoint>): ByteArray {
        val baos = mutableListOf<Byte>()
        for (p in points) {
            baos += floatToBytes(p.x).toList()
            baos += floatToBytes(p.y).toList()
            baos += floatToBytes(p.pressure).toList()
            baos += longToBytes(p.t).toList()
        }
        return MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
    }

    private fun floatToBytes(f: Float): ByteArray = ByteBuffer.allocate(4).putFloat(f).array()
    private fun longToBytes(l: Long): ByteArray = ByteBuffer.allocate(8).putLong(l).array()
}