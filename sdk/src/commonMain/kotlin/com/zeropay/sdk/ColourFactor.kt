package com.zeropay.sdk

import java.security.MessageDigest

object ColourFactor {

    private val COLOURS = listOf("#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF")

    fun digest(selectedIndices: List<Int>): ByteArray {
        val baos = selectedIndices.map { it.toByte() }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(baos)
    }

    fun getColours() = COLOURS
}