// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/ColorProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ColorProcessor - Color Sequence Processing
 * 
 * Processes color sequence authentication factor.
 * 
 * Format:
 * - Hex colors: "#FF5733,#00FF00,#0000FF"
 * - Minimum: 2 colors
 * - Maximum: 6 colors
 * 
 * Color Format:
 * - Hex: #RRGGBB (6 characters)
 * - Case-insensitive
 * - No alpha channel
 * 
 * Validation Rules:
 * - Valid hex format
 * - No duplicate colors
 * - Colors are visually distinct (min distance)
 * - No grayscale-only sequences
 * 
 * Weak Pattern Detection:
 * - Rainbow sequence (red, orange, yellow, green, blue)
 * - Monochrome (all similar shades)
 * - Too similar colors (hard to distinguish)
 * - Primary colors only (red, green, blue)
 * 
 * Security:
 * - Only hash stored
 * - Normalized to uppercase
 * - Color distance validation
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object ColorProcessor {
    
    private const val MIN_COLORS = 2
    private const val MAX_COLORS = 6
    private const val MIN_COLOR_DISTANCE = 50.0 // Perceptual distance threshold
    
    // Known weak color sequences
    private val WEAK_SEQUENCES = setOf(
        "#FF0000,#00FF00,#0000FF",     // Primary colors
        "#FF0000,#FF7F00,#FFFF00,#00FF00,#0000FF", // Rainbow
        "#000000,#FFFFFF",             // Black and white
        "#FF0000,#00FF00",             // Red and green (common)
        "#0000FF,#FFFF00"              // Blue and yellow (common)
    )
    
    /**
     * Validate color sequence
     * 
     * Checks:
     * - Format is valid (#RRGGBB,#RRGGBB,...)
     * - Length within bounds (2-6)
     * - All are valid hex colors
     * - No duplicates
     * - Colors are visually distinct
     * - Not a weak sequence
     * 
     * @param sequence Color sequence
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = ColorProcessor.validate("#FF5733,#00FF00,#0000FF")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(sequence: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse colors
        val colors = sequence.split(",").map { it.trim() }
        
        // Check length
        if (colors.size < MIN_COLORS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Color sequence must have at least $MIN_COLORS colors"
            )
        }
        
        if (colors.size > MAX_COLORS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Color sequence cannot have more than $MAX_COLORS colors"
            )
        }
        
        // Check all are valid hex colors
        for (color in colors) {
            if (!isValidHexColor(color)) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Invalid color format: $color (must be #RRGGBB)"
                )
            }
        }
        
        // Check no duplicates (case-insensitive)
        val normalizedColors = colors.map { it.uppercase() }
        if (normalizedColors.size != normalizedColors.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Each color can only be used once"
            )
        }
        
        // Check colors are distinct (perceptual distance)
        for (i in colors.indices) {
            for (j in i + 1 until colors.size) {
                val distance = calculateColorDistance(colors[i], colors[j])
                if (distance < MIN_COLOR_DISTANCE) {
                    warnings.add("Colors ${colors[i]} and ${colors[j]} are very similar. Consider more distinct colors.")
                }
            }
        }
        
        // Check if all grayscale
        if (colors.all { isGrayscale(it) }) {
            warnings.add("All colors are grayscale. Consider adding some colored options.")
        }
        
        // Check if weak sequence
        val normalized = normalize(sequence)
        if (isWeak(normalized)) {
            warnings.add("This is a commonly used color sequence")
        }
        
        // Check color diversity
        val hueVariety = calculateHueVariety(colors)
        if (hueVariety < 30) {
            warnings.add("Colors are in similar hue range. Mix different color families for better security.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize color sequence
     * 
     * Converts to consistent format:
     * - Uppercase hex
     * - Comma-separated
     * - No spaces
     * 
     * @param sequence Raw color sequence
     * @return Normalized sequence
     */
    fun normalize(sequence: String): String {
        return sequence.split(",")
            .map { it.trim().uppercase() }
            .joinToString(",")
    }
    
    /**
     * Check if valid hex color
     * 
     * @param color Color string
     * @return true if valid #RRGGBB format
     */
    private fun isValidHexColor(color: String): Boolean {
        val hexPattern = Regex("^#[0-9A-Fa-f]{6}$")
        return color.matches(hexPattern)
    }
    
    /**
     * Calculate perceptual color distance
     * 
     * Uses Euclidean distance in RGB space (simple approximation).
     * For production, consider using Delta-E (CIE Lab color space).
     * 
     * @param color1 First color (#RRGGBB)
     * @param color2 Second color (#RRGGBB)
     * @return Distance (0-441, where 441 is black vs white)
     */
    private fun calculateColorDistance(color1: String, color2: String): Double {
        val rgb1 = parseHexColor(color1)
        val rgb2 = parseHexColor(color2)
        
        val rDiff = rgb1.r - rgb2.r
        val gDiff = rgb1.g - rgb2.g
        val bDiff = rgb1.b - rgb2.b
        
        return sqrt((rDiff * rDiff + gDiff * gDiff + bDiff * bDiff).toDouble())
    }
    
    /**
     * Parse hex color to RGB
     * 
     * @param color Hex color string
     * @return RGB components
     */
    private fun parseHexColor(color: String): RGB {
        val hex = color.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return RGB(r, g, b)
    }
    
    /**
     * Check if color is grayscale
     * 
     * @param color Hex color string
     * @return true if R=G=B (grayscale)
     */
    private fun isGrayscale(color: String): Boolean {
        val rgb = parseHexColor(color)
        return rgb.r == rgb.g && rgb.g == rgb.b
    }
    
    /**
     * Calculate hue variety
     * 
     * Measures how spread out the hues are (0-360 degrees).
     * 
     * @param colors List of colors
     * @return Variety score (0-100)
     */
    private fun calculateHueVariety(colors: List<String>): Int {
        val hues = colors.map { calculateHue(it) }
        
        if (hues.size < 2) return 0
        
        // Calculate standard deviation of hues
        val mean = hues.average()
        val variance = hues.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        
        // Normalize to 0-100
        return (stdDev / 360.0 * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * Calculate hue from RGB
     * 
     * @param color Hex color string
     * @return Hue in degrees (0-360)
     */
    private fun calculateHue(color: String): Double {
        val rgb = parseHexColor(color)
        
        val r = rgb.r / 255.0
        val g = rgb.g / 255.0
        val b = rgb.b / 255.0
        
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        
        if (delta == 0.0) return 0.0
        
        val hue = when (max) {
            r -> 60 * (((g - b) / delta) % 6)
            g -> 60 * (((b - r) / delta) + 2)
            b -> 60 * (((r - g) / delta) + 4)
            else -> 0.0
        }
        
        return if (hue < 0) hue + 360 else hue
    }
    
    /**
     * Check if sequence is weak
     * 
     * @param sequence Normalized sequence
     * @return true if commonly used
     */
    private fun isWeak(sequence: String): Boolean {
        return sequence in WEAK_SEQUENCES
    }
    
    /**
     * RGB color representation
     */
    private data class RGB(val r: Int, val g: Int, val b: Int)
}
