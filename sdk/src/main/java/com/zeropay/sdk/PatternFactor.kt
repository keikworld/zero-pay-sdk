package com.zeropay.sdk

import android.view.MotionEvent
import java.security.MessageDigest
import kotlin.math.abs

object PatternFactor {

    private const val STROKE_TOLERANCE = 20f   // px
    private const val MIN_STROKES       = 3

    data class Point(val x: Float, val y: Float, val t: Long)

    fun isValidStroke(events: List<MotionEvent>): Boolean =
        splitStrokes(events).size >= MIN_STROKES

    fun digest(events: List<MotionEvent>): ByteArray {
        val strokes = splitStrokes(events)
        val baos = mutableListOf<Byte>()
        strokes.forEach { stroke ->
            stroke.forEach { p ->
                baos.addAll(floatToBytes(p.x).toList()) // Convert ByteArray to List<Byte>
                baos.addAll(floatToBytes(p.y).toList()) // Convert ByteArray to List<Byte>
                baos.addAll(longToBytes(p.t).toList())   // Convert ByteArray to List<Byte>
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(baos.toByteArray())
    }

    private fun splitStrokes(events: List<MotionEvent>): List<List<Point>> {
        val strokes = mutableListOf<MutableList<Point>>()
        var current = mutableListOf<Point>()
        var lastTime = 0L
        for (ev in events) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    current = mutableListOf(Point(ev.x, ev.y, ev.eventTime))
                    lastTime = ev.eventTime
                }
                MotionEvent.ACTION_MOVE -> {
                    // throttle: ignore moves < 20 ms apart
                    if (ev.eventTime - lastTime >= 20) {
                        current.add(Point(ev.x, ev.y, ev.eventTime))
                        lastTime = ev.eventTime
                    }
                }
                MotionEvent.ACTION_UP -> {
                    current.add(Point(ev.x, ev.y, ev.eventTime))
                    if (current.size >= 10) strokes.add(current)
                    current = mutableListOf()
                }
            }
        }
        return strokes
    }

    private fun floatToBytes(f: Float): ByteArray = java.nio.ByteBuffer.allocate(4).putFloat(f).array()
    private fun longToBytes(l: Long): ByteArray   = java.nio.ByteBuffer.allocate(8).putLong(l).array()
}