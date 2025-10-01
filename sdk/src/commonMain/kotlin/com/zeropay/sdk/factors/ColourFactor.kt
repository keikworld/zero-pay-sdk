package com.zeropay.sdk.factors  // Changed from com.zeropay.sdk

import com.zeropay.sdk.crypto.CryptoUtils

object ColourFactor {

    private val COLOURS = listOf(
        "#FF0000", // Red
        "#00FF00", // Green
        "#0000FF", // Blue
        "#FFFF00", // Yellow
        "#FF00FF", // Magenta
        "#00FFFF"  // Cyan
    )

    fun digest(selectedIndices: List<Int>): ByteArray {
        require(selectedIndices.isNotEmpty()) { "Selected indices cannot be empty" }
        require(selectedIndices.all { it in COLOURS.indices }) {
            "Invalid colour index"
        }
        
        val bytes = selectedIndices.map { it.toByte() }.toByteArray()
        return CryptoUtils.sha256(bytes)
    }

    fun getColours(): List<String> = COLOURS
}
