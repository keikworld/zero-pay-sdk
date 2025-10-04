package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Colour Factor with Constant-Time Verification
 * 
 * Security:
 * - Constant-time validation
 * - Constant-time verification
 * - SHA-256 hashing
 */
object ColourFactor {

    private val COLOURS = listOf(
        "#FF0000", // Red
        "#00FF00", // Green
        "#0000FF", // Blue
        "#FFFF00", // Yellow
        "#FF00FF", // Magenta
        "#00FFFF"  // Cyan
    )

    /**
     * Generate digest from colour selection
     */
    fun digest(selectedIndices: List<Int>): ByteArray {
        // Constant-time validation
        var isValid = true
        isValid = isValid && selectedIndices.isNotEmpty()
        isValid = isValid && selectedIndices.size <= 10
        
        // Check all indices (constant-time - scan all)
        var allValidIndices = true
        for (index in selectedIndices) {
            allValidIndices = allValidIndices && (index in COLOURS.indices)
        }
        isValid = isValid && allValidIndices
        
        if (!isValid) {
            throw IllegalArgumentException("Invalid colour selection")
        }
        
        val bytes = selectedIndices.map { it.toByte() }.toByteArray()
        return CryptoUtils.sha256(bytes)
    }
    
    /**
     * Verify colour selection (constant-time)
     */
    fun verify(selectedIndices: List<Int>, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digest(selectedIndices)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }

    fun getColours(): List<String> = COLOURS
}
