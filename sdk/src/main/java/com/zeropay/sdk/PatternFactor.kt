package com.zeropay.sdk

import androidx.compose.ui.geometry.Offset
import java.security.MessageDigest

object PatternFactor {

    private const val STROKE_TOLERANCE = 20f   // px
    private const val MIN_STROKES       = 3
    private const val MIN_POINTS_PER_STROKE = 10

    data class Point(val x: Float, val y: Float, val t: Long)

    // Accepts List<Offset> (from Compose), simulates strokes splitting based on distance
    fun isValidStroke(offsets: List<Offset>): Boolean =
        splitStrokes(offsets).size >= MIN_STROKES

    fun digest(offsets: List<Offset>): ByteArray {
        val strokes = splitStrokes(offsets)
        val baos = mutableListOf<Byte>()
        val now = System.currentTimeMillis()
        strokes.forEach { stroke ->
            stroke.forEachIndexed { idx, pt ->
                val t = now + idx * 20 // Simulate a timestamp (if needed)
                baos.addAll(floatToBytes(pt.x).toList())
                baos.addAll(floatToBytes(pt.y).toList())
                baos.addAll(longToBytes(t).toList())
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
    }

    // Split strokes based on large jumps in position (simulate finger lifts)
    private fun splitStrokes(offsets: List<Offset>): List<List<Offset>> {
        if (offsets.isEmpty()) return emptyList()
        val strokes = mutableListOf<MutableList<Offset>>()
        var current = mutableListOf(offsets.first())
        for (i in 1 until offsets.size) {
            val prev = offsets[i - 1]
            val curr = offsets[i]
            if (distance(prev, curr) > STROKE_TOLERANCE) {
                if (current.size >= MIN_POINTS_PER_STROKE) strokes.add(current)
                current = mutableListOf(curr)
            } else {
                current.add(curr)
            }
        }
        if (current.size >= MIN_POINTS_PER_STROKE) strokes.add(current)
        return strokes
    }

    private fun distance(a: Offset, b: Offset): Float =
        kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    private fun floatToBytes(f: Float): ByteArray = java.nio.ByteBuffer.allocate(4).putFloat(f).array()
    private fun longToBytes(l: Long): ByteArray   = java.nio.ByteBuffer.allocate(8).putLong(l).array()
}
