package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.CryptoUtils

/**
 * Emoji Factor with Constant-Time Verification
 */
object EmojiFactor {

    private val EMOJIS = listOf(
        "😀", "😎", "🥳", "😇", "🤔", "😴",
        "🐶", "🐱", "🐼", "🦁", "🐸", "🦄",
        "🍕", "🍔", "🍰", "🍎", "🍌", "🍇",
        "⚽", "🏀", "🎮", "🎸", "🎨", "📚",
        "🚗", "✈️", "🚀", "⛵", "🏠", "🌳",
        "❤️", "⭐", "🌈", "🔥", "💎", "🎁"
    )

    /**
     * Generate digest from emoji selection
     */
    fun digest(selectedIndices: List<Int>): ByteArray {
        // Constant-time validation
        var isValid = true
        isValid = isValid && selectedIndices.isNotEmpty()
        isValid = isValid && selectedIndices.size <= 10
        
        var allValidIndices = true
        for (index in selectedIndices) {
            allValidIndices = allValidIndices && (index in EMOJIS.indices)
        }
        isValid = isValid && allValidIndices
        
        if (!isValid) {
            throw IllegalArgumentException("Invalid emoji selection")
        }
        
        val bytes = selectedIndices.map { it.toByte() }.toByteArray()
        return CryptoUtils.sha256(bytes)
    }
    
    /**
     * Verify emoji selection (constant-time)
     */
    fun verify(selectedIndices: List<Int>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digest(selectedIndices)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }

    fun getEmojis(): List<String> = EMOJIS
}
