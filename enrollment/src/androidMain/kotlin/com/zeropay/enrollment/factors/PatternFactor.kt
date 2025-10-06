package com.zeropay.enrollment.factors

import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Pattern Factor - Handles pattern-based authentication
 */
object PatternFactor {
    
    private const val MIN_STROKES = 3
    private const val MAX_STROKES = 9
    private const val GRID_SIZE = 9
    
    /**
     * Validates and hashes pattern
     * @param coordinates List of grid positions (0-8)
     * @return SHA-256 digest or null if invalid
     */
    fun processPattern(coordinates: List<Int>): Result<ByteArray> {
        // Validate count
        if (coordinates.size < MIN_STROKES) {
            return Result.failure(Exception("Pattern must have at least $MIN_STROKES strokes"))
        }
        
        if (coordinates.size > MAX_STROKES) {
            return Result.failure(Exception("Pattern cannot exceed $MAX_STROKES strokes"))
        }
        
        // Validate range
        if (coordinates.any { it < 0 || it >= GRID_SIZE }) {
            return Result.failure(Exception("Pattern contains invalid coordinates"))
        }
        
        // Validate uniqueness
        if (coordinates.distinct().size != coordinates.size) {
            return Result.failure(Exception("Pattern contains duplicate coordinates"))
        }
        
        // Convert to string and hash
        val patternString = coordinates.joinToString(",")
        val digest = CryptoUtils.sha256(patternString.toByteArray(Charsets.UTF_8))
        
        return Result.success(digest)
    }
    
    /**
     * Verifies pattern against stored digest
     */
    fun verifyPattern(inputCoordinates: List<Int>, storedDigest: ByteArray): Boolean {
        val result = processPattern(inputCoordinates)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        return inputDigest.contentEquals(storedDigest)
    }
    
    /**
     * Generates shuffled grid indices (for display randomization)
     */
    fun generateShuffleSequence(): List<Int> {
        val indices = (0 until GRID_SIZE).toMutableList()
        
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
