// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/RhythmProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult
import kotlin.math.abs

/**
 * RhythmProcessor - Tap Rhythm Processing
 * 
 * Processes tap rhythm authentication factor.
 * 
 * Format:
 * - Inter-tap intervals (milliseconds): "500,300,800,400,600"
 * - Minimum: 3 taps (2 intervals)
 * - Maximum: 12 taps (11 intervals)
 * 
 * NOTE: This is NOT biometric rhythm analysis
 * - Stores quantized rhythm pattern (like Morse code)
 * - NOT individual biometric timing
 * - User recreates same rhythm pattern
 * 
 * Rhythm Representation:
 * - SHORT: 0-400ms
 * - MEDIUM: 400-800ms
 * - LONG: 800ms+
 * - Pattern: "SHORT,MEDIUM,SHORT,LONG"
 * 
 * Validation Rules:
 * - At least 3 taps
 * - Reasonable timing (50ms - 3000ms between taps)
 * - Not all same interval (too simple)
 * - Reproducible pattern
 * 
 * Security:
 * - Only quantized pattern stored
 * - NOT precise biometric timing
 * - Tolerance windows for matching
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */
object RhythmProcessor {
    
    private const val MIN_TAPS = 3
    private const val MAX_TAPS = 12
    private const val MIN_INTERVAL_MS = 50
    private const val MAX_INTERVAL_MS = 3000
    
    /**
     * Validate rhythm pattern
     */
    fun validate(pattern: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse intervals
        val intervals = try {
            pattern.split(",").map { it.trim().toInt() }
        } catch (e: NumberFormatException) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Pattern must be comma-separated numbers (milliseconds)"
            )
        }
        
        // Check count (intervals = taps - 1)
        val tapCount = intervals.size + 1
        
        if (tapCount < MIN_TAPS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Rhythm must have at least $MIN_TAPS taps"
            )
        }
        
        if (tapCount > MAX_TAPS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Rhythm cannot have more than $MAX_TAPS taps"
            )
        }
        
        // Check all intervals in valid range
        val invalidIntervals = intervals.filter { it < MIN_INTERVAL_MS || it > MAX_INTERVAL_MS }
        if (invalidIntervals.isNotEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "All intervals must be between ${MIN_INTERVAL_MS}ms and ${MAX_INTERVAL_MS}ms"
            )
        }
        
        // Check if all same (too simple)
        if (intervals.all { abs(it - intervals[0]) < 100 }) {
            warnings.add("All tap intervals are similar. Add rhythm variety.")
        }
        
        // Check for complexity
        val quantized = quantizeRhythm(intervals)
        if (quantized.toSet().size == 1) {
            warnings.add("Rhythm has no variety. Try mixing short, medium, and long intervals.")
        }
        
        // Check total duration
        val totalDuration = intervals.sum()
        if (totalDuration < 1000) {
            warnings.add("Rhythm is very fast. Slower rhythms are easier to reproduce.")
        }
        if (totalDuration > 10000) {
            warnings.add("Rhythm is very long. Shorter rhythms are easier to remember.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize rhythm pattern
     * 
     * Quantizes timing into SHORT/MEDIUM/LONG categories.
     */
    fun normalize(pattern: String): String {
        val intervals = pattern.split(",").map { it.trim().toIntOrNull() ?: 0 }
        val quantized = quantizeRhythm(intervals)
        return quantized.joinToString(",")
    }
    
    /**
     * Quantize rhythm intervals
     * 
     * Converts precise milliseconds to categories:
     * - SHORT: 0-400ms
     * - MEDIUM: 400-800ms
     * - LONG: 800ms+
     */
    private fun quantizeRhythm(intervals: List<Int>): List<String> {
        return intervals.map { interval ->
            when {
                interval < 400 -> "SHORT"
                interval < 800 -> "MEDIUM"
                else -> "LONG"
            }
        }
    }
    
    /**
     * Calculate rhythm complexity
     */
    fun calculateComplexity(pattern: String): Int {
        val intervals = try {
            pattern.split(",").map { it.trim().toInt() }
        } catch (e: Exception) {
            return 0
        }
        
        var score = 0
        
        // Length score (max 30)
        score += (intervals.size * 30 / MAX_TAPS).coerceAtMost(30)
        
        // Variety score (max 40)
        val quantized = quantizeRhythm(intervals)
        val variety = quantized.toSet().size
        score += (variety * 40 / 3).coerceAtMost(40)
        
        // Duration score (max 30)
        val totalDuration = intervals.sum()
        val durationScore = when {
            totalDuration < 1000 -> 10
            totalDuration > 10000 -> 10
            else -> 30
        }
        score += durationScore
        
        return score.coerceIn(0, 100)
    }
}
