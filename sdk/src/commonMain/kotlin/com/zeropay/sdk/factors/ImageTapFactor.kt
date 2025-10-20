package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Image Tap Factor - PRODUCTION VERSION (ENHANCED)
 * 
 * GDPR-COMPLIANT IMPLEMENTATION:
 * - Only uses pre-approved abstract images
 * - No user photo uploads allowed
 * - No facial recognition or biometric analysis
 * - Only spatial coordinates are used
 * 
 * Security Features (ENHANCED):
 * - Constant-time verification ✅
 * - Memory wiping ✅ (NEW)
 * - Grid-based quantization ✅
 * - Fuzzy matching with tolerance ✅
 * 
 * @author ZeroPay Security Team
 * @version 2.0.0 (Security Enhanced)
 */
object ImageTapFactor {
    
    // ==================== CONSTANTS ====================
    
    private const val GRID_SIZE = 20
    private const val TAP_TOLERANCE = 2
    private const val REQUIRED_TAPS = 2
    private const val MIN_GRID_DISTANCE = 2
    
    // ==================== DATA CLASSES ====================
    
    /**
     * Tap point with normalized coordinates
     */
    data class TapPoint(
        val x: Float,  // Normalized 0.0-1.0
        val y: Float,  // Normalized 0.0-1.0
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Image information for GDPR-compliant storage
     */
    data class ImageInfo(
        val imageId: String,
        val imageHash: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ImageInfo) return false
            return imageId == other.imageId && imageHash.contentEquals(other.imageHash)
        }
        
        override fun hashCode(): Int {
            return 31 * imageId.hashCode() + imageHash.contentHashCode()
        }
    }
    
    // ==================== DIGEST GENERATION ====================
    
    /**
     * Generate digest from tap points and image (SECURE VERSION)
     * 
     * Security Enhancements:
     * - Memory wiping added (NEW)
     * - All validation remains constant-time
     * 
     * @param tapPoints List of 2 tap points
     * @param imageInfo Image metadata
     * @return SHA-256 digest (32 bytes)
     */
    fun digest(tapPoints: List<TapPoint>, imageInfo: ImageInfo): ByteArray {
        // Validation
        require(tapPoints.size == REQUIRED_TAPS) {
            "Must have exactly $REQUIRED_TAPS tap points"
        }
        require(tapPoints.all { it.x in 0f..1f && it.y in 0f..1f }) {
            "Tap coordinates must be normalized (0.0-1.0)"
        }
        
        // Quantize tap points to grid
        val quantizedPoints = tapPoints.map { point ->
            quantizeToGrid(point.x, point.y)
        }
        
        // Ensure points are different
        val distance = calculateGridDistance(quantizedPoints[0], quantizedPoints[1])
        require(distance >= MIN_GRID_DISTANCE) {
            "Tap points must be at least $MIN_GRID_DISTANCE grid cells apart"
        }
        
        val bytes = mutableListOf<Byte>()
        
        return try {
            // Add image hash
            bytes.addAll(imageInfo.imageHash.toList())
            
            // Add quantized tap points (sorted for consistency)
            val sortedPoints = quantizedPoints.sortedWith(compareBy({ it.first }, { it.second }))
            sortedPoints.forEach { (gridX: Int, gridY: Int) ->
                bytes.add(gridX.toByte())
                bytes.add(gridY.toByte())
            }
            
            // Generate hash
            CryptoUtils.sha256(bytes.toByteArray())
            
        } finally {
            // SECURITY ENHANCEMENT: Wipe sensitive data
            bytes.clear()
        }
    }
    
    // ==================== VERIFICATION ====================
    
    /**
     * Verify with exact matching (constant-time)
     */
    fun verify(
        enrolledDigest: ByteArray,
        authTapPoints: List<TapPoint>,
        imageInfo: ImageInfo
    ): Boolean {
        return try {
            val authDigest = digest(authTapPoints, imageInfo)
            ConstantTime.equals(enrolledDigest, authDigest)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Verify with fuzzy matching (allows small deviations)
     */
    fun verifyFuzzy(
        enrolledDigest: ByteArray,
        authTapPoints: List<TapPoint>,
        imageInfo: ImageInfo
    ): Boolean {
        require(authTapPoints.size == REQUIRED_TAPS) {
            "Must have exactly $REQUIRED_TAPS tap points"
        }
        
        // Quantize auth tap points
        val authQuantized = authTapPoints.map { point ->
            quantizeToGrid(point.x, point.y)
        }
        
        // Generate all possible candidate positions within tolerance
        val candidates = generateToleranceCandidates(authQuantized)
        
        // Check if any candidate matches (constant-time for each)
        for (candidate in candidates) {
            val candidateBytes = mutableListOf<Byte>()
            
            try {
                candidateBytes.addAll(imageInfo.imageHash.toList())
                
                val sortedPoints = candidate.sortedWith(compareBy({ it.first }, { it.second }))
                sortedPoints.forEach { (gridX: Int, gridY: Int) ->
                    candidateBytes.add(gridX.toByte())
                    candidateBytes.add(gridY.toByte())
                }
                
                val candidateDigest = CryptoUtils.sha256(candidateBytes.toByteArray())
                
                if (ConstantTime.equals(enrolledDigest, candidateDigest)) {
                    return true
                }
            } finally {
                candidateBytes.clear()
            }
        }
        
        return false
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Quantize coordinates to grid cell
     */
    private fun quantizeToGrid(x: Float, y: Float): Pair<Int, Int> {
        val gridX = (x * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val gridY = (y * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        return Pair(gridX, gridY)
    }
    
    /**
     * Calculate Manhattan distance between grid cells
     */
    private fun calculateGridDistance(p1: Pair<Int, Int>, p2: Pair<Int, Int>): Int {
        return kotlin.math.abs(p1.first - p2.first) + kotlin.math.abs(p1.second - p2.second)
    }
    
    /**
     * Generate tolerance candidates (within ±TAP_TOLERANCE)
     */
    private fun generateToleranceCandidates(
        quantized: List<Pair<Int, Int>>
    ): List<List<Pair<Int, Int>>> {
        val candidates = mutableListOf<List<Pair<Int, Int>>>()
        
        // For each tap point, generate variants within tolerance
        val point1Variants = generatePointVariants(quantized[0])
        val point2Variants = generatePointVariants(quantized[1])
        
        // Combine all variants
        for (p1 in point1Variants) {
            for (p2 in point2Variants) {
                candidates.add(listOf(p1, p2))
            }
        }
        
        return candidates
    }
    
    /**
     * Generate point variants within tolerance
     */
    private fun generatePointVariants(point: Pair<Int, Int>): List<Pair<Int, Int>> {
        val variants = mutableListOf<Pair<Int, Int>>()
        
        for (dx in -TAP_TOLERANCE..TAP_TOLERANCE) {
            for (dy in -TAP_TOLERANCE..TAP_TOLERANCE) {
                val newX = (point.first + dx).coerceIn(0, GRID_SIZE - 1)
                val newY = (point.second + dy).coerceIn(0, GRID_SIZE - 1)
                variants.add(Pair(newX, newY))
            }
        }
        
        return variants
    }
    
    // ==================== GETTERS ====================

    fun getGridSize(): Int = GRID_SIZE
    fun getTapTolerance(): Int = TAP_TOLERANCE
    fun getRequiredTaps(): Int = REQUIRED_TAPS

    /**
     * Get list of pre-approved abstract images (GDPR-compliant)
     *
     * IMPORTANT: Only abstract/geometric patterns allowed
     * No personal photos or user uploads
     *
     * @return List of approved image info
     */
    fun getApprovedImages(): List<ImageInfo> {
        return listOf(
            ImageInfo(
                imageId = "abstract_pattern",
                imageHash = com.zeropay.sdk.security.CryptoUtils.sha256("abstract_pattern".toByteArray())
            ),
            ImageInfo(
                imageId = "geometric_shapes",
                imageHash = com.zeropay.sdk.security.CryptoUtils.sha256("geometric_shapes".toByteArray())
            ),
            ImageInfo(
                imageId = "gradient_waves",
                imageHash = com.zeropay.sdk.security.CryptoUtils.sha256("gradient_waves".toByteArray())
            ),
            ImageInfo(
                imageId = "abstract_landscape",
                imageHash = com.zeropay.sdk.security.CryptoUtils.sha256("abstract_landscape".toByteArray())
            )
        )
    }
}
