// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/ImagePatternProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * ImagePatternProcessor - Image Grid Selection Processing
 * 
 * Processes image grid authentication factor (like CAPTCHA selection).
 * 
 * Format:
 * - Grid positions: "0,5,10,15" (selected cells)
 * - Grid size: 4x4 (16 cells), 5x5 (25 cells), or 6x6 (36 cells)
 * - Minimum: 4 selections
 * - Maximum: 16 selections
 * 
 * Grid Layout (4x4 example):
 * ```
 *  0  1  2  3
 *  4  5  6  7
 *  8  9 10 11
 * 12 13 14 15
 * ```
 * 
 * Validation Rules:
 * - All positions valid for grid size
 * - No duplicates
 * - At least 4 selections
 * - Not all adjacent (too simple)
 * 
 * Security:
 * - Only hash of selections stored
 * - Position order matters
 * - Grid size agnostic
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */
object ImagePatternProcessor {
    
    private const val MIN_SELECTIONS = 4
    private const val MAX_SELECTIONS = 16
    
    private val SUPPORTED_GRID_SIZES = setOf(16, 25, 36) // 4x4, 5x5, 6x6
    
    /**
     * Validate image pattern
     */
    fun validate(pattern: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse positions
        val positions = try {
            pattern.split(",").map { it.trim().toInt() }
        } catch (e: NumberFormatException) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pattern must be comma-separated numbers"
            )
        }
        
        // Check count
        if (positions.size < MIN_SELECTIONS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Must select at least $MIN_SELECTIONS images"
            )
        }
        
        if (positions.size > MAX_SELECTIONS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Cannot select more than $MAX_SELECTIONS images"
            )
        }
        
        // Determine grid size
        val maxPosition = positions.maxOrNull() ?: 0
        val gridSize = when {
            maxPosition < 16 -> 16  // 4x4
            maxPosition < 25 -> 25  // 5x5
            maxPosition < 36 -> 36  // 6x6
            else -> return ValidationResult(
                isValid = false,
                errorMessage = "Position $maxPosition exceeds maximum grid size"
            )
        }
        
        // Check all positions valid
        if (positions.any { it < 0 || it >= gridSize }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "All positions must be between 0 and ${gridSize - 1}"
            )
        }
        
        // Check no duplicates
        if (positions.size != positions.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Each position can only be selected once"
            )
        }
        
        // Check if all adjacent (too simple)
        if (areAllAdjacent(positions, gridSize)) {
            warnings.add("All selections are adjacent. Spread out selections for better security.")
        }
        
        // Check for simple patterns
        if (hasSimplePattern(positions, gridSize)) {
            warnings.add("Selection pattern is predictable. Try a more random pattern.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize pattern
     */
    fun normalize(pattern: String): String {
        return pattern.replace(" ", "").trim()
    }
    
    /**
     * Check if all positions are adjacent
     */
    private fun areAllAdjacent(positions: List<Int>, gridSize: Int): Boolean {
        if (positions.size < 2) return false
        
        val gridWidth = when (gridSize) {
            16 -> 4
            25 -> 5
            36 -> 6
            else -> 4
        }
        
        val sorted = positions.sorted()
        
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            
            if (!areAdjacent(current, next, gridWidth)) {
                return false
            }
        }
        
        return true
    }
    
    /**
     * Check if two positions are adjacent
     */
    private fun areAdjacent(pos1: Int, pos2: Int, gridWidth: Int): Boolean {
        val row1 = pos1 / gridWidth
        val col1 = pos1 % gridWidth
        val row2 = pos2 / gridWidth
        val col2 = pos2 % gridWidth
        
        val rowDiff = abs(row1 - row2)
        val colDiff = abs(col1 - col2)
        
        return rowDiff <= 1 && colDiff <= 1 && (rowDiff + colDiff) > 0
    }
    
    /**
     * Check for simple patterns
     */
    private fun hasSimplePattern(positions: List<Int>, gridSize: Int): Boolean {
        val gridWidth = when (gridSize) {
            16 -> 4
            25 -> 5
            36 -> 6
            else -> 4
        }
        
        // All in same row
        if (positions.map { it / gridWidth }.toSet().size == 1) return true
        
        // All in same column
        if (positions.map { it % gridWidth }.toSet().size == 1) return true
        
        // Diagonal pattern
        val sorted = positions.sorted()
        if (sorted.zipWithNext().all { (a, b) -> b - a == gridWidth + 1 }) return true
        if (sorted.zipWithNext().all { (a, b) -> b - a == gridWidth - 1 }) return true
        
        // Four corners (for 4x4 grid)
        if (gridSize == 16 && positions.sorted() == listOf(0, 3, 12, 15)) return true
        
        return false
    }
    
    private fun abs(value: Int) = if (value < 0) -value else value
}
