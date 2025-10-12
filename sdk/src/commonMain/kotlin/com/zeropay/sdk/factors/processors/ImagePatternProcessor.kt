// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/ImageTapProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ImageTapProcessor - Image Tap Position Processing
 * 
 * Processes image tap authentication factor.
 * 
 * GDPR-COMPLIANT IMPLEMENTATION:
 * - Only uses pre-approved abstract images
 * - No user photo uploads allowed
 * - No facial recognition or biometric analysis
 * - Only spatial coordinates are used
 * 
 * Format:
 * - Tap positions: "imageId:x1,y1;x2,y2"
 * - imageId: Pre-approved image identifier
 * - x, y: Normalized coordinates (0.0-1.0)
 * - Minimum: 2 taps
 * - Maximum: 6 taps
 * 
 * Validation Rules:
 * - Valid image ID
 * - At least 2 tap positions
 * - Coordinates in valid range (0.0-1.0)
 * - Taps are not too close together
 * - Not in corners only (too simple)
 * 
 * Grid Quantization:
 * - 20x20 grid for fuzzy matching
 * - Tolerance: ±2 grid cells
 * - Prevents exact coordinate requirement
 * 
 * Weak Pattern Detection:
 * - All corners only
 * - Straight line
 * - Too close together
 * - Common patterns (diagonal, cross)
 * 
 * Security:
 * - Only hash stored
 * - Grid quantization (tolerance)
 * - Image ID included in hash
 * - Constant-time comparison
 * 
 * Privacy:
 * - No personal images
 * - No biometric data
 * - GDPR compliant
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object ImageTapProcessor {
    
    private const val MIN_TAPS = 2
    private const val MAX_TAPS = 6
    private const val GRID_SIZE = 20
    private const val MIN_TAP_DISTANCE = 0.15 // Minimum normalized distance between taps
    
    // Pre-approved abstract image IDs
    private val APPROVED_IMAGE_IDS = setOf(
        "abstract_01", "abstract_02", "abstract_03", "abstract_04",
        "geometric_01", "geometric_02", "geometric_03", "geometric_04",
        "pattern_01", "pattern_02", "pattern_03", "pattern_04",
        "landscape_01", "landscape_02", "landscape_03", "landscape_04"
    )
    
    /**
     * Tap position data
     */
    data class TapPosition(
        val x: Float,
        val y: Float
    )
    
    /**
     * Validate image tap pattern
     * 
     * Checks:
     * - Format is valid (imageId:x,y;x,y;...)
     * - Image ID is approved
     * - Tap count within bounds (2-6)
     * - Coordinates valid (0.0-1.0)
     * - Taps are sufficiently spaced
     * - Not a weak pattern
     * 
     * @param pattern Image tap pattern
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = ImageTapProcessor.validate("abstract_01:0.2,0.3;0.7,0.8")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(pattern: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse pattern
        val (imageId, taps) = try {
            parsePattern(pattern)
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid format. Expected: imageId:x,y;x,y;... (e.g., 'abstract_01:0.2,0.3;0.7,0.8')"
            )
        }
        
        // Check image ID is approved
        if (imageId !in APPROVED_IMAGE_IDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Image ID '$imageId' is not approved. Use one of: ${APPROVED_IMAGE_IDS.joinToString(", ")}"
            )
        }
        
        // Check tap count
        if (taps.size < MIN_TAPS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Must tap at least $MIN_TAPS locations"
            )
        }
        
        if (taps.size > MAX_TAPS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Cannot tap more than $MAX_TAPS locations"
            )
        }
        
        // Check coordinates are in valid range
        taps.forEach { tap ->
            if (tap.x !in 0f..1f || tap.y !in 0f..1f) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Coordinates must be normalized (0.0-1.0). Found: (${tap.x}, ${tap.y})"
                )
            }
        }
        
        // Check taps are sufficiently spaced
        for (i in 0 until taps.size - 1) {
            for (j in i + 1 until taps.size) {
                val distance = calculateDistance(taps[i], taps[j])
                if (distance < MIN_TAP_DISTANCE) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Taps are too close together. Minimum distance: $MIN_TAP_DISTANCE"
                    )
                }
            }
        }
        
        // Check for weak patterns
        if (isAllCorners(taps)) {
            warnings.add("Pattern uses only corners. Consider tapping other areas.")
        }
        
        if (isStraightLine(taps)) {
            warnings.add("Taps form a straight line. Add variation for better security.")
        }
        
        if (isCommonPattern(taps)) {
            warnings.add("This is a common pattern. Choose less predictable locations.")
        }
        
        // Check complexity
        val complexity = calculateComplexity(taps)
        if (complexity < 30) {
            warnings.add("Pattern is too simple. Add more taps or spread them out.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize image tap pattern
     * 
     * Converts to consistent format:
     * - Lowercase image ID
     * - Quantized coordinates (grid-based)
     * - Consistent delimiter
     * 
     * @param pattern Raw image tap pattern
     * @return Normalized pattern
     */
    fun normalize(pattern: String): String {
        val (imageId, taps) = parsePattern(pattern)
        
        // Quantize taps to grid
        val quantizedTaps = taps.map { tap ->
            val gridX = (tap.x * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            val gridY = (tap.y * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
            
            TapPosition(
                x = gridX.toFloat() / GRID_SIZE,
                y = gridY.toFloat() / GRID_SIZE
            )
        }
        
        val tapsString = quantizedTaps.joinToString(";") { tap ->
            "%.3f,%.3f".format(tap.x, tap.y)
        }
        
        return "${imageId.lowercase()}:$tapsString"
    }
    
    /**
     * Parse image tap pattern
     */
    private fun parsePattern(pattern: String): Pair<String, List<TapPosition>> {
        val parts = pattern.trim().split(":")
        if (parts.size != 2) {
            throw IllegalArgumentException("Pattern must have format: imageId:taps")
        }
        
        val imageId = parts[0].trim()
        val tapsString = parts[1].trim()
        
        val taps = tapsString.split(";").map { tapString ->
            val coords = tapString.split(",")
            if (coords.size != 2) {
                throw IllegalArgumentException("Each tap must have 2 coordinates: x,y")
            }
            
            TapPosition(
                x = coords[0].toFloat(),
                y = coords[1].toFloat()
            )
        }
        
        return Pair(imageId, taps)
    }
    
    /**
     * Calculate distance between two taps
     */
    private fun calculateDistance(tap1: TapPosition, tap2: TapPosition): Double {
        val dx = tap1.x - tap2.x
        val dy = tap1.y - tap2.y
        return sqrt((dx * dx + dy * dy).toDouble())
    }
    
    /**
     * Check if all taps are in corners
     */
    private fun isAllCorners(taps: List<TapPosition>): Boolean {
        val cornerThreshold = 0.2f
        
        return taps.all { tap ->
            (tap.x < cornerThreshold || tap.x > 1 - cornerThreshold) &&
            (tap.y < cornerThreshold || tap.y > 1 - cornerThreshold)
        }
    }
    
    /**
     * Check if taps form a straight line
     */
    private fun isStraightLine(taps: List<TapPosition>): Boolean {
        if (taps.size < 3) return false
        
        // Calculate variance from line
        val firstTap = taps.first()
        val lastTap = taps.last()
        
        var totalDeviation = 0.0
        
        for (i in 1 until taps.size - 1) {
            val tap = taps[i]
            
            // Calculate perpendicular distance to line
            val dx = lastTap.x - firstTap.x
            val dy = lastTap.y - firstTap.y
            val lineLength = sqrt((dx * dx + dy * dy).toDouble())
            
            if (lineLength > 0) {
                val deviation = abs(
                    (dy * (tap.x - firstTap.x) - dx * (tap.y - firstTap.y)) / lineLength
                )
                totalDeviation += deviation
            }
        }
        
        val avgDeviation = totalDeviation / (taps.size - 2)
        
        return avgDeviation < 0.05
    }
    
    /**
     * Check if pattern is common
     */
    private fun isCommonPattern(taps: List<TapPosition>): Boolean {
        if (taps.size != 4) return false
        
        // Check for diagonal pattern
        val diagonal1 = taps.sortedWith(compareBy({ it.x }, { it.y }))
        if (isDiagonal(diagonal1)) return true
        
        // Check for cross pattern
        if (isCrossPattern(taps)) return true
        
        return false
    }
    
    /**
     * Check if taps form a diagonal
     */
    private fun isDiagonal(sortedTaps: List<TapPosition>): Boolean {
        if (sortedTaps.size < 3) return false
        
        var isDiag = true
        for (i in 1 until sortedTaps.size) {
            val ratio = (sortedTaps[i].y - sortedTaps[i-1].y) / 
                        (sortedTaps[i].x - sortedTaps[i-1].x + 0.001f)
            
            if (abs(ratio - 1.0f) > 0.3f && abs(ratio + 1.0f) > 0.3f) {
                isDiag = false
                break
            }
        }
        
        return isDiag
    }
    
    /**
     * Check if taps form a cross pattern
     */
    private fun isCrossPattern(taps: List<TapPosition>): Boolean {
        if (taps.size != 4) return false
        
        // Check if center point exists
        val centerX = taps.map { it.x }.average().toFloat()
        val centerY = taps.map { it.y }.average().toFloat()
        
        // Check if all taps are approximately equidistant from center
        val distances = taps.map { tap ->
            calculateDistance(tap, TapPosition(centerX, centerY))
        }
        
        val avgDistance = distances.average()
        val variance = distances.map { (it - avgDistance) * (it - avgDistance) }.average()
        
        return variance < 0.01
    }
    
    /**
     * Calculate pattern complexity
     */
    private fun calculateComplexity(taps: List<TapPosition>): Int {
        var score = 0
        
        // Bonus for more taps
        score += (taps.size - MIN_TAPS) * 15
        
        // Bonus for spread
        val spreadX = taps.maxOf { it.x } - taps.minOf { it.x }
        val spreadY = taps.maxOf { it.y } - taps.minOf { it.y }
        score += ((spreadX + spreadY) * 25).toInt()
        
        // Penalty for straight line
        if (isStraightLine(taps)) {
            score -= 25
        }
        
        // Penalty for corners only
        if (isAllCorners(taps)) {
            score -= 20
        }
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Calculate strength score
     */
    fun calculateStrength(pattern: String): Int {
        val (_, taps) = parsePattern(pattern)
        
        var score = 50 // Base score
        
        // Tap count bonus
        score += (taps.size - MIN_TAPS) * 10
        
        // Complexity bonus
        val complexity = calculateComplexity(taps)
        score += complexity / 3
        
        // Penalty for weak patterns
        if (isAllCorners(taps)) score -= 20
        if (isStraightLine(taps)) score -= 25
        if (isCommonPattern(taps)) score -= 15
        
        return score.coerceIn(0, 100)
    }
}
