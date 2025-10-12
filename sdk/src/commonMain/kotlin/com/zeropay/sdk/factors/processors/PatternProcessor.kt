// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/PatternProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * PatternProcessor - Pattern Lock Processing
 * 
 * Processes pattern lock authentication factor (like Android pattern lock).
 * 
 * Pattern Format:
 * - Grid: 3x3 dots (positions 0-8)
 * - Format: "0,1,2,5,8" (comma-separated positions)
 * - Minimum: 3 connected dots
 * - Maximum: 9 dots (all dots)
 * 
 * Visual Grid:
 * ```
 * 0 - 1 - 2
 * |   |   |
 * 3 - 4 - 5
 * |   |   |
 * 6 - 7 - 8
 * ```
 * 
 * Validation Rules:
 * - Each position used only once
 * - Positions are 0-8
 * - Adjacent dots are connected
 * - No isolated dots
 * - Minimum 3 strokes
 * 
 * Weak Pattern Detection:
 * - L-shape: 0,1,2,5,8
 * - Z-shape: 0,1,2,4,6,7,8
 * - Straight line: 0,1,2 or 0,3,6
 * - Simple square: 0,1,2,5,8,7,6,3
 * 
 * Security:
 * - Only hash stored (never raw pattern)
 * - Position normalization (consistent order)
 * - Weak pattern detection
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object PatternProcessor {
    
    private const val MIN_STROKES = 3
    private const val MAX_STROKES = 9
    private const val GRID_SIZE = 9
    
    // Known weak patterns
    private val WEAK_PATTERNS = setOf(
        "0,1,2",           // Top row
        "3,4,5",           // Middle row
        "6,7,8",           // Bottom row
        "0,3,6",           // Left column
        "1,4,7",           // Middle column
        "2,5,8",           // Right column
        "0,4,8",           // Diagonal TL-BR
        "2,4,6",           // Diagonal TR-BL
        "0,1,2,5,8",       // L-shape (common)
        "0,1,2,5,8,7,6,3", // Square (common)
        "0,1,2,4,6,7,8",   // Z-shape (common)
        "0,1,4,7,8",       // Reverse L
        "6,7,8,5,2"        // Reverse L bottom
    )
    
    /**
     * Validate pattern
     * 
     * Checks:
     * - Format is valid (comma-separated numbers)
     * - Length within bounds (3-9)
     * - All positions are 0-8
     * - No duplicate positions
     * - Pattern is connected (optional warning)
     * 
     * @param pattern Pattern string (e.g., "0,1,2,5,8")
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = PatternProcessor.validate("0,1,2,5,8")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(pattern: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse positions
        val positions = try {
            pattern.split(",").map { it.trim().toInt() }
        } catch (e: NumberFormatException) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pattern must be comma-separated numbers (e.g., '0,1,2,5,8')"
            )
        }
        
        // Check length
        if (positions.size < MIN_STROKES) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pattern must have at least $MIN_STROKES strokes"
            )
        }
        
        if (positions.size > MAX_STROKES) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pattern cannot have more than $MAX_STROKES strokes"
            )
        }
        
        // Check all positions are valid (0-8)
        if (positions.any { it !in 0 until GRID_SIZE }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "All positions must be between 0 and 8"
            )
        }
        
        // Check no duplicates
        if (positions.size != positions.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Each position can only be used once"
            )
        }
        
        // Check if pattern is connected
        if (!isConnected(positions)) {
            warnings.add("Pattern has disconnected segments")
        }
        
        // Check if weak pattern
        val normalized = normalize(pattern)
        if (isWeak(normalized)) {
            warnings.add("This is a commonly used pattern. Consider a more unique pattern.")
        }
        
        // Check complexity
        val complexity = calculateComplexity(positions)
        if (complexity < 30) {
            warnings.add("Pattern complexity is low. Try adding more direction changes.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize pattern
     * 
     * Converts to consistent format:
     * - Removes whitespace
     * - Sorts nothing (order matters!)
     * - Lowercase
     * 
     * @param pattern Raw pattern string
     * @return Normalized pattern
     */
    fun normalize(pattern: String): String {
        return pattern.replace(" ", "").trim()
    }
    
    /**
     * Check if pattern is connected
     * 
     * Ensures each stroke is adjacent to the previous one.
     * 
     * Adjacent positions:
     * - Horizontal: differ by 1 (same row)
     * - Vertical: differ by 3 (same column)
     * - Diagonal: differ by 2, 4
     * 
     * @param positions List of positions
     * @return true if fully connected
     */
    private fun isConnected(positions: List<Int>): Boolean {
        for (i in 0 until positions.size - 1) {
            val current = positions[i]
            val next = positions[i + 1]
            
            if (!areAdjacent(current, next)) {
                return false
            }
        }
        return true
    }
    
    /**
     * Check if two positions are adjacent
     * 
     * @param a First position
     * @param b Second position
     * @return true if adjacent (including diagonal)
     */
    private fun areAdjacent(a: Int, b: Int): Boolean {
        val rowA = a / 3
        val colA = a % 3
        val rowB = b / 3
        val colB = b % 3
        
        val rowDiff = kotlin.math.abs(rowA - rowB)
        val colDiff = kotlin.math.abs(colA - colB)
        
        // Adjacent if difference is at most 1 in each direction
        return rowDiff <= 1 && colDiff <= 1 && (rowDiff + colDiff) > 0
    }
    
    /**
     * Check if pattern is weak
     * 
     * @param pattern Normalized pattern
     * @return true if pattern is commonly used
     */
    private fun isWeak(pattern: String): Boolean {
        return pattern in WEAK_PATTERNS
    }
    
    /**
     * Calculate pattern complexity
     * 
     * Factors:
     * - Length (more strokes = higher)
     * - Direction changes (more changes = higher)
     * - Coverage (more area = higher)
     * 
     * @param positions List of positions
     * @return Complexity score (0-100)
     */
    private fun calculateComplexity(positions: List<Int>): Int {
        var score = 0
        
        // Length score (max 30 points)
        score += (positions.size * 30) / MAX_STROKES
        
        // Direction changes score (max 40 points)
        val directionChanges = countDirectionChanges(positions)
        score += kotlin.math.min(directionChanges * 10, 40)
        
        // Coverage score (max 30 points)
        val uniqueRows = positions.map { it / 3 }.toSet().size
        val uniqueCols = positions.map { it % 3 }.toSet().size
        val coverage = (uniqueRows + uniqueCols) * 5
        score += kotlin.math.min(coverage, 30)
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Count direction changes
     * 
     * @param positions List of positions
     * @return Number of direction changes
     */
    private fun countDirectionChanges(positions: List<Int>): Int {
        if (positions.size < 3) return 0
        
        var changes = 0
        
        for (i in 0 until positions.size - 2) {
            val dir1 = getDirection(positions[i], positions[i + 1])
            val dir2 = getDirection(positions[i + 1], positions[i + 2])
            
            if (dir1 != dir2) {
                changes++
            }
        }
        
        return changes
    }
    
    /**
     * Get direction between two positions
     * 
     * @return Direction enum
     */
    private fun getDirection(from: Int, to: Int): Direction {
        val rowDiff = (to / 3) - (from / 3)
        val colDiff = (to % 3) - (from % 3)
        
        return when {
            rowDiff == 0 && colDiff > 0 -> Direction.RIGHT
            rowDiff == 0 && colDiff < 0 -> Direction.LEFT
            rowDiff > 0 && colDiff == 0 -> Direction.DOWN
            rowDiff < 0 && colDiff == 0 -> Direction.UP
            rowDiff > 0 && colDiff > 0 -> Direction.DOWN_RIGHT
            rowDiff > 0 && colDiff < 0 -> Direction.DOWN_LEFT
            rowDiff < 0 && colDiff > 0 -> Direction.UP_RIGHT
            rowDiff < 0 && colDiff < 0 -> Direction.UP_LEFT
            else -> Direction.SAME
        }
    }
    
    private enum class Direction {
        UP, DOWN, LEFT, RIGHT,
        UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT,
        SAME
    }
}
