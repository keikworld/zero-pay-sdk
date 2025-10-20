package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Colour Factor - PRODUCTION VERSION
 * 
 * Handles color sequence authentication.
 * 
 * Security Features:
 * - Input validation (min/max length)
 * - Weak sequence detection
 * - Constant-time verification
 * - Memory wiping after use
 * - DoS protection (bounded input)
 * 
 * GDPR Compliance:
 * - Only stores irreversible SHA-256 hash
 * - No raw color sequences stored
 * - User can unenroll (right to erasure)
 * 
 * PSD3 Category: KNOWLEDGE (something you know)
 * 
 * Color Set: 6 colors (Red, Blue, Green, Yellow, Purple, Orange)
 * Sequence: 3-5 colors
 * Combinations: 6^3 to 6^5 (216 to 7,776)
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
object ColourFactor {
    
    // ==================== CONSTANTS ====================
    
    private const val MIN_SEQUENCE_LENGTH = 3
    private const val MAX_SEQUENCE_LENGTH = 5
    
    /**
     * Available colors (6 colors)
     * Indices: 0-5
     */
    val COLOUR_SET = listOf(
        "RED",      // 0
        "BLUE",     // 1
        "GREEN",    // 2
        "YELLOW",   // 3
        "PURPLE",   // 4
        "ORANGE"    // 5
    )
    
    // Color codes for UI display
    val COLOUR_CODES = mapOf(
        "RED" to "#FF0000",
        "BLUE" to "#0000FF",
        "GREEN" to "#00FF00",
        "YELLOW" to "#FFFF00",
        "PURPLE" to "#800080",
        "ORANGE" to "#FFA500"
    )
    
    // ==================== ENROLLMENT ====================
    
    /**
     * Process color sequence for enrollment
     * 
     * Validates sequence and generates SHA-256 digest.
     * 
     * Security:
     * - Input validation (length, range)
     * - Weak sequence detection
     * - Memory wiping after generation
     * 
     * @param selectedIndices List of color indices (0-5)
     * @return Result with SHA-256 digest or error
     * 
     * @throws IllegalArgumentException if validation fails
     */
    fun processColourSequence(selectedIndices: List<Int>): Result<ByteArray> {
        try {
            // ========== INPUT VALIDATION ==========
            
            // Validate length
            if (selectedIndices.size < MIN_SEQUENCE_LENGTH) {
                return Result.failure(
                    IllegalArgumentException(
                        "Colour sequence must have at least $MIN_SEQUENCE_LENGTH colors"
                    )
                )
            }
            
            if (selectedIndices.size > MAX_SEQUENCE_LENGTH) {
                return Result.failure(
                    IllegalArgumentException(
                        "Colour sequence cannot exceed $MAX_SEQUENCE_LENGTH colors"
                    )
                )
            }
            
            // Validate indices are in range
            if (selectedIndices.any { it < 0 || it >= COLOUR_SET.size }) {
                return Result.failure(
                    IllegalArgumentException(
                        "Invalid color index. Must be 0-${COLOUR_SET.size - 1}"
                    )
                )
            }
            
            // ========== WEAK SEQUENCE DETECTION ==========
            
            if (isWeakSequence(selectedIndices)) {
                return Result.failure(
                    IllegalArgumentException(
                        "Colour sequence is too weak. Avoid patterns like all same color or sequential."
                    )
                )
            }
            
            // ========== DIGEST GENERATION ==========
            
            // Convert indices to bytes
            val sequenceBytes = selectedIndices.joinToString(",")
                .toByteArray(Charsets.UTF_8)
            
            // Generate SHA-256 digest
            val digest = CryptoUtils.sha256(sequenceBytes)
            
            // Memory wiping (best effort - JVM limitation)
            Arrays.fill(sequenceBytes, 0.toByte())
            
            return Result.success(digest)
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Verify color sequence against stored digest
     * 
     * Uses constant-time comparison to prevent timing attacks.
     * 
     * @param inputIndices User input color indices
     * @param storedDigest Stored SHA-256 digest
     * @return true if sequence matches
     */
    fun verifyColourSequence(
        inputIndices: List<Int>,
        storedDigest: ByteArray
    ): Boolean {
        // Generate digest from input
        val result = processColourSequence(inputIndices)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        
        // Constant-time comparison
        return CryptoUtils.constantTimeEquals(inputDigest, storedDigest)
    }
    
    // ==================== VALIDATION HELPERS ====================
    
    /**
     * Detect weak color sequences
     * 
     * Weak patterns:
     * - All same color (RED, RED, RED)
     * - Sequential indices (0, 1, 2)
     * - Reverse sequential (5, 4, 3)
     * - Alternating two colors (RED, BLUE, RED, BLUE)
     * 
     * @param indices Color indices
     * @return true if sequence is weak
     */
    private fun isWeakSequence(indices: List<Int>): Boolean {
        if (indices.size < 2) return false
        
        // All same color
        if (indices.all { it == indices[0] }) {
            return true
        }
        
        // Sequential (0,1,2,3 or 1,2,3,4)
        val isSequential = indices.zipWithNext().all { (a, b) -> b == a + 1 }
        if (isSequential) {
            return true
        }
        
        // Reverse sequential (5,4,3,2)
        val isReverseSequential = indices.zipWithNext().all { (a, b) -> b == a - 1 }
        if (isReverseSequential) {
            return true
        }
        
        // Alternating two colors (0,1,0,1 or 2,3,2,3)
        if (indices.size >= 4) {
            val uniqueColors = indices.toSet()
            if (uniqueColors.size == 2) {
                val isAlternating = indices.zipWithNext().all { (a, b) -> a != b }
                if (isAlternating) {
                    return true
                }
            }
        }
        
        return false
    }
    
    // ==================== UI HELPERS ====================
    
    /**
     * Get color name by index
     * 
     * @param index Color index (0-5)
     * @return Color name
     */
    fun getColourName(index: Int): String {
        require(index in COLOUR_SET.indices) {
            "Invalid color index: $index"
        }
        return COLOUR_SET[index]
    }
    
    /**
     * Get color hex code by index
     * 
     * @param index Color index (0-5)
     * @return Hex color code (e.g., "#FF0000")
     */
    fun getColourCode(index: Int): String {
        val name = getColourName(index)
        return COLOUR_CODES[name] ?: "#000000"
    }
    
    /**
     * Generate shuffled color indices for display
     * 
     * Uses CSPRNG for cryptographically secure shuffling.
     * 
     * @return Shuffled list of indices (0-5)
     */
    fun generateShuffledIndices(): List<Int> {
        val indices = COLOUR_SET.indices.toMutableList()
        
        // Fisher-Yates shuffle with CSPRNG
        val random = java.security.SecureRandom()
        for (i in indices.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = indices[i]
            indices[i] = indices[j]
            indices[j] = temp
        }
        
        return indices
    }
    
    /**
     * Get minimum sequence length
     */
    fun getMinSequenceLength(): Int = MIN_SEQUENCE_LENGTH
    
    /**
     * Get maximum sequence length
     */
    fun getMaxSequenceLength(): Int = MAX_SEQUENCE_LENGTH
    
    /**
     * Get total number of colors
     */
    fun getColourCount(): Int = COLOUR_SET.size
}
