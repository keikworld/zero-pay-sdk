package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

object EmojiFactor {

    private val EMOJIS = listOf(
        "😀", "😎", "🥳", "😇", "🤔", "😴",
        "🐶", "🐱", "🐼", "🦁", "🐸", "🦄",
        "🍕", "🍔", "🍰", "🍎", "🍌", "🍇",
        "⚽", "🏀", "🎮", "🎸", "🎨", "📚",
        "🚗", "✈️", "🚀", "⛵", "🏠", "🌳",
        "❤️", "⭐", "🌈", "🔥", "💎", "🎁"
    )

    fun digest(selectedIndices: List<Int>): ByteArray {
        require(selectedIndices.isNotEmpty()) { "Selected indices cannot be empty" }
        require(selectedIndices.all { it in EMOJIS.indices }) {
            "Invalid emoji index"
        }
        
        val bytes = selectedIndices.map { it.toByte() }.toByteArray()
        return CryptoUtils.sha256(bytes)
    }

    fun getEmojis(): List<String> = EMOJIS
}
