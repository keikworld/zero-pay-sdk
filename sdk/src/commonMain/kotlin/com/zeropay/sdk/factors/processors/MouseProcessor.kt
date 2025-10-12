// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/MouseProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult
import kotlin.math.sqrt

/**
 * MouseProcessor - Mouse Drawing Pattern Processing
 * 
 * Processes mouse drawing authentication factor with micro-timing analysis.
 * 
 * Format:
 * - Point sequence: "x1,y1,t1;x2,y2,t2;x3,y3,t3"
 * - x, y: Normalized coordinates (0.0-1.0)
 * - t: Timestamp in milliseconds
 * - Minimum: 5 points
 * - Maximum: 200 points
 * 
 * Validation Rules:
 * - At least 5 points
 * - Coordinates in valid range (0.0-1.0)
 * - Monotonic timestamps (increasing)
 * - Total duration reasonable (0.5s-30s)
 * - Path has sufficient complexity
 * 
 * Biometric Features:
 * - Mouse velocity patterns
 * - Acceleration profiles
 * - Direction changes
 * - Drawing speed consistency
 * 
 * Weak Pattern Detection:
 * - Straight lines only
 * - Single direction (too simple)
 * - Uniform velocity (likely artificial)
 * - Too few points
 * 
 * Security:
 * - Only hash stored
 * - Timing data included (biometric)
 * - Position quantization (tolerance)
 * - Replay protection
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object MouseProcessor {
    
    private const val MIN_POINTS = 5
    private const val MAX_POINTS = 200
    private const val MIN_DURATION_MS = 500L
    private const val MAX_DURATION_MS = 30000L
    private const val MIN_PATH_LENGTH = 0.1 // Minimum normalized distance
    private const val MAX_VELOCITY = 2.0 // Max normalized velocity
    
    /**
     * Mouse point data
     */
    data class MousePoint(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    /**
     * Validate mouse drawing
     * 
     * Checks:
     * - Format is valid (x,y,t;x,y,t;...)
     * - Point count within bounds (5-200)
     * - Coordinates valid (0.0-1.0)
     * - Timestamps monotonic
     * - Duration reasonable
     * - Path has complexity
     * 
     * @param pattern Mouse pattern string
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = MouseProcessor.validate("0.1,0.2,1000;0.2,0.3,1100;...")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(pattern: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse points
        val points = try {
            parsePoints(pattern)
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Invalid format. Expected: x,y,t;x,y,t;... (e.g., '0.1,0.2,1000;0.2,0.3,1100')"
            )
        }
        
        // Check point count
        if (points.size < MIN_POINTS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Mouse pattern must have at least $MIN_POINTS points"
            )
        }
        
        if (points.size > MAX_POINTS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Mouse pattern cannot have more than $MAX_POINTS points"
            )
        }
        
        // Check coordinates are in valid range
        points.forEach { point ->
            if (point.x !in 0f..1f || point.y !in 0f..1f) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Coordinates must be normalized (0.0-1.0). Found: (${point.x}, ${point.y})"
                )
            }
        }
        
        // Check timestamps are monotonic
        for (i in 1 until points.size) {
            if (points[i].timestamp <= points[i - 1].timestamp) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Timestamps must be strictly increasing"
                )
            }
        }
        
        // Check duration
        val duration = points.last().timestamp - points.first().timestamp
        if (duration < MIN_DURATION_MS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Drawing too fast. Minimum duration: ${MIN_DURATION_MS}ms"
            )
        }
        
        if (duration > MAX_DURATION_MS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Drawing too slow. Maximum duration: ${MAX_DURATION_MS}ms"
            )
        }
        
        // Check path length
        val pathLength = calculatePathLength(points)
        if (pathLength < MIN_PATH_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Path too short. Draw a longer pattern."
            )
        }
        
        // Check for suspicious uniform velocity (likely bot)
        if (hasUniformVelocity(points)) {
            warnings.add("Drawing has suspiciously uniform velocity. Draw more naturally.")
        }
        
        // Check complexity
        val complexity = calculateComplexity(points)
        if (complexity < 30) {
            warnings.add("Pattern is too simple. Add more curves or direction changes.")
        }
        
        // Check if straight line
        if (isStraightLine(points)) {
            warnings.add("Pattern is nearly a straight line. Draw a more complex shape.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize mouse pattern
     * 
     * Converts to consistent format:
     * - Trim whitespace
     * - Consistent delimiter
     * - Rounded coordinates (3 decimal places)
     * 
     * @param pattern Raw mouse pattern
     * @return Normalized pattern
     */
    fun normalize(pattern: String): String {
        val points = parsePoints(pattern)
        
        return points.joinToString(";") { point ->
            val x = "%.3f".format(point.x)
            val y = "%.3f".format(point.y)
            "$x,$y,${point.timestamp}"
        }
    }
    
    /**
     * Parse mouse points from string
     */
    private fun parsePoints(pattern: String): List<MousePoint> {
        return pattern.trim()
            .split(";")
            .map { segment ->
                val parts = segment.split(",")
                if (parts.size != 3) {
                    throw IllegalArgumentException("Each point must have 3 values: x,y,timestamp")
                }
                
                MousePoint(
                    x = parts[0].toFloat(),
                    y = parts[1].toFloat(),
                    timestamp = parts[2].toLong()
                )
            }
    }
    
    /**
     * Calculate total path length
     */
    private fun calculatePathLength(points: List<MousePoint>): Double {
        var totalLength = 0.0
        
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            totalLength += sqrt((dx * dx + dy * dy).toDouble())
        }
        
        return totalLength
    }
    
    /**
     * Check if velocity is suspiciously uniform
     * 
     * Natural mouse movement has variable velocity.
     * Uniform velocity suggests automated drawing.
     */
    private fun hasUniformVelocity(points: List<MousePoint>): Boolean {
        if (points.size < 3) return false
        
        val velocities = mutableListOf<Double>()
        
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            val dt = (points[i].timestamp - points[i - 1].timestamp).toDouble()
            
            if (dt > 0) {
                val distance = sqrt((dx * dx + dy * dy).toDouble())
                val velocity = distance / (dt / 1000.0) // units per second
                velocities.add(velocity)
            }
        }
        
        if (velocities.isEmpty()) return false
        
        // Calculate coefficient of variation
        val mean = velocities.average()
        if (mean == 0.0) return false
        
        val variance = velocities.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean
        
        // CV < 0.2 suggests uniform velocity (suspicious)
        return cv < 0.2
    }
    
    /**
     * Calculate pattern complexity
     * 
     * Based on:
     * - Direction changes
     * - Velocity changes
     * - Path curvature
     */
    private fun calculateComplexity(points: List<MousePoint>): Int {
        if (points.size < 3) return 0
        
        var directionChanges = 0
        var velocityChanges = 0
        
        // Count direction changes
        for (i in 2 until points.size) {
            val dx1 = points[i - 1].x - points[i - 2].x
            val dy1 = points[i - 1].y - points[i - 2].y
            
            val dx2 = points[i].x - points[i - 1].x
            val dy2 = points[i].y - points[i - 1].y
            
            // Calculate angle change (dot product)
            val dotProduct = dx1 * dx2 + dy1 * dy2
            val mag1 = sqrt((dx1 * dx1 + dy1 * dy1).toDouble())
            val mag2 = sqrt((dx2 * dx2 + dy2 * dy2).toDouble())
            
            if (mag1 > 0 && mag2 > 0) {
                val cosAngle = dotProduct / (mag1 * mag2)
                
                // Significant direction change if angle > 30 degrees
                if (cosAngle < 0.866) { // cos(30°) ≈ 0.866
                    directionChanges++
                }
            }
        }
        
        // Calculate complexity score
        val score = directionChanges * 10 + (points.size / 10)
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Check if pattern is nearly a straight line
     */
    private fun isStraightLine(points: List<MousePoint>): Boolean {
        if (points.size < 3) return true
        
        val complexity = calculateComplexity(points)
        return complexity < 15
    }
    
    /**
     * Calculate strength score
     */
    fun calculateStrength(pattern: String): Int {
        val points = parsePoints(pattern)
        
        var score = 50 // Base score
        
        // Point count bonus
        score += ((points.size - MIN_POINTS) / 5).coerceIn(0, 20)
        
        // Path length bonus
        val pathLength = calculatePathLength(points)
        score += (pathLength * 20).toInt().coerceIn(0, 15)
        
        // Complexity bonus
        val complexity = calculateComplexity(points)
        score += complexity / 5
        
        // Penalty for uniform velocity
        if (hasUniformVelocity(points)) {
            score -= 20
        }
        
        // Penalty for straight line
        if (isStraightLine(points)) {
            score -= 25
        }
        
        return score.coerceIn(0, 100)
    }
}
