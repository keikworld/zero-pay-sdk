package com.zeropay.enrollment.factors

import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Emoji Factor - Handles emoji sequence authentication
 */
object EmojiFactor {
    
    private const val MIN_SEQUENCE_LENGTH = 3
    private const val MAX_SEQUENCE_LENGTH = 6
    
    val EMOJI_SET = listOf(
        "😀", "😂", "😍", "🎉", "🔥", "💯", "🚀", "⭐", "❤️",
        "👍", "🎵", "🌟", "🍕", "🏆", "💪", "🌈", "🎁", "🎸"
    )
    
    /**
     * Validates and hashes emoji sequence
     * @param emojis List of emoji strings
     * @return SHA-256 digest or null if invalid
     */
    fun processEmojiSequence(emojis: List<String>): Result<ByteArray> {
        // Validate length
        if (emojis.size < MIN_SEQUENCE_LENGTH) {
            return Result.failure(Exception("Emoji sequence must have at least $MIN_SEQUENCE_LENGTH emojis"))
        }
        
        if (emojis.size > MAX_SEQUENCE_LENGTH) {
            return Result.failure(Exception("Emoji sequence cannot exceed $MAX_SEQUENCE_LENGTH emojis"))
        }
        
        // Validate emojis are from set
        if (emojis.any { it !in EMOJI_SET }) {
            return Result.failure(Exception("Invalid emoji in sequence"))
        }
        
        // Convert to string and hash
        val sequenceString = emojis.joinToString(",")
        val digest = CryptoUtils.sha256(sequenceString.toByteArray(Charsets.UTF_8))
        
        return Result.success(digest)
    }
    
    /**
     * Verifies emoji sequence against stored digest
     */
    fun verifyEmojiSequence(inputEmojis: List<String>, storedDigest: ByteArray): Boolean {
        val result = processEmojiSequence(inputEmojis)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        return inputDigest.contentEquals(storedDigest)
    }
    
    /**
     * Generates shuffled emoji grid indices
     */
    fun generateShuffleSequence(): List<Int> {
        val indices = EMOJI_SET.indices.toMutableList()
        
        // Fisher-Yates shuffle
        for (i in indices.size - 1 downTo 1) {
            val j = kotlin.random.Random.nextInt(i + 1)
            val temp = indices[i]
            indices[i] = indices[j]
            indices[j] = temp
        }
        
        return indices
    }
}
