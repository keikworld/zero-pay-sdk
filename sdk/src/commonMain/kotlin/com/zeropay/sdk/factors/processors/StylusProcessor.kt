// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/StylusProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult
import kotlin.math.sqrt

/**
 * StylusProcessor - Stylus Drawing Pattern Processing
 * 
 * Processes stylus drawing authentication factor with pressure data.
 * 
 * Format:
 * - Point sequence: "x1,y1,p1,t1;x2,y2,p2,t2;x3,y3,p3,t3"
 * - x, y: Normalized coordinates (0.0-1.0)
 * - p: Pressure (0.0-1.0)
 * - t: Timestamp in milliseconds
 * - Minimum: 5 points
 * - Maximum: 300 points
 * 
 * Validation Rules:
 * - At least 5 points
 * - Coordinates in valid range (0.0-1.0)
 * - Pressure in valid range (0.0-1.0)
 * - Monotonic timestamps (increasing)
 * - Total duration reasonable (0.5s-30s)
 * - Pressure variation (not constant)
 * 
 * Biometric Features:
 * - Pressure patterns (unique to user)
 * - Pressure velocity (rate of change)
 * - Drawing speed with pressure
 * - Pressure consistency
 * 
 * Weak Pattern Detection:
 * - Constant pressure (likely mouse, not stylus)
 * - Straight lines only
 * - Too simple shape
 * - Uniform velocity
 * 
 * Security:
 * - Only hash stored
 * - Pressure data included (biometric)
 * - Timing data included
 * - Replay protection
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object StylusProcessor {
    
    private const val MIN_POINTS = 5
    private const val MAX_POINTS = 300
    private const val MIN_DURATION_MS = 500L
    private const val MAX_DURATION_MS = 30000L
    private const val MIN_PATH_LENGTH = 0.1
    private const val MIN_PRESSURE_VARIANCE = 0.05 // Minimum variance to detect stylus
    
    /**
     * Stylus point data
     */
    data class StylusPoint(
        val x: Float,
        val y: Float,
        val pressure: Float,
        val timestamp: Long
    )
    
    /**
     * Validate stylus drawing
     * 
     * Checks:
     * - Format is valid (x,y,p,t;x,y,p,t;...)
     * - Point count within bounds (5-300)
     * - Coordinates valid (0.0-1.0)
     * - Pressure valid (0.0-1.0)
     * - Timestamps monotonic
     * - Duration reasonable
     * - Pressure varies (not constant)
     * - Path has complexity
     * 
     * @param pattern Stylus pattern string
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = StylusProcessor.validate("0.1,0.2,0.8,1000;0.2,0.3,0.9,1100;...")
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
                errorMessage = "Invalid format. Expected: x,y,pressure,t;x,y,pressure,t;... (e.g., '0.1,0.2,0.8,1000;0.2,0.3,0.9,1100')"
            )
        }
        
        // Check point count
        if (points.size < MIN_POINTS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Stylus pattern must have at least $MIN_POINTS points"
            )
        }
        
        if (points.size > MAX_POINTS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Stylus pattern cannot have more than $MAX_POINTS points"
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
            
            if (point.pressure !in 0f..1f) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Pressure must be between 0.0 and 1.0. Found: ${point.pressure}"
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
        
        // Check pressure variation (detect if stylus vs mouse)
        val pressureVariance = calculatePressureVariance(points)
        if (pressureVariance < MIN_PRESSURE_VARIANCE) {
            warnings.add("Pressure is nearly constant. This may not be a real stylus input.")
        }
        
        // Check for suspicious uniform velocity
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
     * Normalize stylus pattern
     * 
     * Converts to consistent format:
     * - Trim whitespace
     * - Consistent delimiter
     * - Rounded coordinates (3 decimal places)
     * - Rounded pressure (3 decimal places)
     * 
     * @param pattern Raw stylus pattern
     * @return Normalized pattern
     */
    fun normalize(pattern: String): String {
        val points = parsePoints(pattern)
        
        return points.joinToString(";") { point ->
            val x = "%.3f".format(point.x)
            val y = "%.3f".format(point.y)
            val p = "%.3f".format(point.pressure)
            "$x,$y,$p,${point.timestamp}"
        }
    }
    
    /**
     * Parse stylus points from string
     */
    private fun parsePoints(pattern: String): List<StylusPoint> {
        return pattern.trim()
            .split(";")
            .map { segment ->
                val parts = segment.split(",")
                if (parts.size != 4) {
                    throw IllegalArgumentException("Each point must have 4 values: x,y,pressure,timestamp")
                }
                
                StylusPoint(
                    x = parts[0].toFloat(),
                    y = parts[1].toFloat(),
                    pressure = parts[2].toFloat(),
                    timestamp = parts[3].toLong()
                )
            }
    }
    
    /**
     * Calculate total path length
     */
    private fun calculatePathLength(points: List<StylusPoint>): Double {
        var totalLength = 0.0
        
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            totalLength += sqrt((dx * dx + dy * dy).toDouble())
        }
        
        return totalLength
    }
    
    /**
     * Calculate pressure variance
     * 
     * Detects if pressure is constant (mouse) or varies (stylus).
     */
    private fun calculatePressureVariance(points: List<StylusPoint>): Double {
        if (points.isEmpty()) return 0.0
        
        val pressures = points.map { it.pressure.toDouble() }
        val mean = pressures.average()
        
        val variance = pressures.map { (it - mean) * (it - mean) }.average()
        
        return sqrt(variance)
    }
    
    /**
     * Check if velocity is suspiciously uniform
     */
    private fun hasUniformVelocity(points: List<StylusPoint>): Boolean {
        if (points.size < 3) return false
        
        val velocities = mutableListOf<Double>()
        
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            val dt = (points[i].timestamp - points[i - 1].timestamp).toDouble()
            
            if (dt > 0) {
                val distance = sqrt((dx * dx + dy * dy).toDouble())
                val velocity = distance / (dt / 1000.0)
                velocities.add(velocity)
            }
        }
        
        if (velocities.isEmpty()) return false
        
        val mean = velocities.average()
        if (mean == 0.0) return false
        
        val variance = velocities.map { (it - mean) * (it - mean) }.average()
        val stdDev = sqrt(variance)
        val cv = stdDev / mean
        
        return cv < 0.2
    }
    
    /**
     * Calculate pattern complexity
     */
    private fun calculateComplexity(points: List<StylusPoint>): Int {
        if (points.size < 3) return 0
        
        var directionChanges = 0
        var pressureChanges = 0
        
        // Count direction changes
        for (i in 2 until points.size) {
            val dx1 = points[i - 1].x - points[i - 2].x
            val dy1 = points[i - 1].y - points[i - 2].y
            
            val dx2 = points[i].x - points[i - 1].x
            val dy2 = points[i].y - points[i - 1].y
            
            val dotProduct = dx1 * dx2 + dy1 * dy2
            val mag1 = sqrt((dx1 * dx1 + dy1 * dy1).toDouble())
            val mag2 = sqrt((dx2 * dx2 + dy2 * dy2).toDouble())
            
            if (mag1 > 0 && mag2 > 0) {
                val cosAngle = dotProduct / (mag1 * mag2)
                
                if (cosAngle < 0.866) {
                    directionChanges++
                }
            }
        }
        
        // Count pressure changes
        for (i in 1 until points.size) {
            val pressureDiff = kotlin.math.abs(points[i].pressure - points[i - 1].pressure)
            if (pressureDiff > 0.1) {
                pressureChanges++
            }
        }
        
        val score = directionChanges * 10 + pressureChanges * 5 + (points.size / 10)
        
        return score.coerceIn(0, 100)
    }
    
    /**
     * Check if pattern is nearly a straight line
     */
    private fun isStraightLine(points: List<StylusPoint>): Boolean {
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
        score += ((points.size - MIN_POINTS) / 10).coerceIn(0, 15)
        
        // Path length bonus
        val pathLength = calculatePathLength(points)
        score += (pathLength * 20).toInt().coerceIn(0, 15)
        
        // Pressure variance bonus (biometric feature)
        val pressureVariance = calculatePressureVariance(points)
        score += (pressureVariance * 30).toInt().coerceIn(0, 15)
        
        // Complexity bonus
        val complexity = calculateComplexity(points)
        score += complexity / 5
        
        // Penalty for constant pressure
        if (pressureVariance < MIN_PRESSURE_VARIANCE) {
            score -= 20
        }
        
        // Penalty for uniform velocity
        if (hasUniformVelocity(points)) {
            score -= 15
        }
        
        // Penalty for straight line
        if (isStraightLine(points)) {
            score -= 20
        }
        
        return score.coerceIn(0, 100)
    }
}
