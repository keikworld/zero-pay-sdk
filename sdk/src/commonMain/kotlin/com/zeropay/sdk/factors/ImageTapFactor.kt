package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Image Tap Factor - Tap 2 locations on an image
 * 
 * IMPORTANT GDPR CONSIDERATIONS:
 * - DO NOT allow users to upload personal photos (faces, family, etc.)
 * - Only use abstract patterns, nature scenes, or geometric images
 * - No biometric data collection from images
 * - Images are only used as spatial reference, not analyzed
 * 
 * Security:
 * - User taps 2 locations during enrollment
 * - During authentication, must tap same locations (with tolerance)
 * - Image hash + tap coordinates create unique authentication
 * - Constant-time verification
 * - Fuzzy matching with tolerance
 * - Grid-based quantization
 * 
 * This implementation uses a GDPR-safe approach:
 * - Pre-approved abstract images only (no user uploads)
 * - No facial recognition or biometric analysis
 * - Only coordinate-based authentication
 */

object ImageTapFactor {
    
    private const val GRID_SIZE = 20
    private const val TAP_TOLERANCE = 2
    
    data class TapPoint(
        val x: Float,  // Normalized 0.0-1.0
        val y: Float,  // Normalized 0.0-1.0
        val timestamp: Long
    )
    
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
    
    /**
     * Generate digest from tap points and image
     */
    fun digest(tapPoints: List<TapPoint>, imageInfo: ImageInfo): ByteArray {
        require(tapPoints.size == 2) { "Must have exactly 2 tap points" }
        require(tapPoints.all { it.x in 0f..1f && it.y in 0f..1f }) {
            "Tap coordinates must be normalized (0.0-1.0)"
        }
        
        // Quantize tap points to grid
        val quantizedPoints = tapPoints.map { point ->
            quantizeToGrid(point.x, point.y)
        }
        
        // Ensure points are different
        val distance = calculateGridDistance(quantizedPoints[0], quantizedPoints[1])
        require(distance >= 2) { "Tap points must be at least 2 grid cells apart" }
        
        // Build digest
        val bytes = mutableListOf<Byte>()
        
        // Add image hash
        bytes.addAll(imageInfo.imageHash.toList())
        
        // Add quantized tap points (sorted for consistency)
        val sortedPoints = quantizedPoints.sorted()
        sortedPoints.forEach { (gridX, gridY) ->
            bytes.add(gridX.toByte())
            bytes.add(gridY.toByte())
        }
        
        return CryptoUtils.sha256(bytes.toByteArray())
    }
    
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
        require(authTapPoints.size == 2) { "Must have exactly 2 tap points" }
        
        // Quantize auth tap points
        val authQuantized = authTapPoints.map { point ->
            quantizeToGrid(point.x, point.y)
        }
        
        // Generate all possible candidate positions within tolerance
        val candidates = generateToleranceCandidates(authQuantized)
        
        // Check if any candidate matches (constant-time for each check)
        for (candidate in candidates) {
            val candidateBytes = mutableListOf<Byte>()
            candidateBytes.addAll(imageInfo.imageHash.toList())
            
            val sortedPoints = candidate.sorted()
            sortedPoints.forEach { (gridX, gridY) ->
                candidateBytes.add(gridX.toByte())
                candidateBytes.add(gridY.toByte())
            }
            
            val candidateDigest = CryptoUtils.sha256(candidateBytes.toByteArray())
            
            if (ConstantTime.equals(enrolledDigest, candidateDigest)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get list of pre-approved GDPR-safe images
     */
    fun getApprovedImages(): List<ImageInfo> {
        return listOf(
            ImageInfo(
                imageId = "abstract_pattern_001",
                imageHash = CryptoUtils.sha256("abstract_pattern_001".toByteArray())
            ),
            ImageInfo(
                imageId = "nature_landscape_001",
                imageHash = CryptoUtils.sha256("nature_landscape_001".toByteArray())
            ),
            ImageInfo(
                imageId = "geometric_shapes_001",
                imageHash = CryptoUtils.sha256("geometric_shapes_001".toByteArray())
            ),
            ImageInfo(
                imageId = "abstract_art_001",
                imageHash = CryptoUtils.sha256("abstract_art_001".toByteArray())
            ),
            ImageInfo(
                imageId = "color_gradient_001",
                imageHash = CryptoUtils.sha256("color_gradient_001".toByteArray())
            )
        )
    }
    
    /**
     * Validate image is GDPR-compliant
     */
    fun isGDPRCompliant(imageData: ByteArray): Boolean {
        val imageHash = CryptoUtils.sha256(imageData)
        val approvedHashes = getApprovedImages().map { it.imageHash }
        
        return approvedHashes.any { approvedHash ->
            ConstantTime.equals(imageHash, approvedHash)
        }
    }
    
    // ============== Private Helper Methods ==============
    
    private fun quantizeToGrid(x: Float, y: Float): Pair<Int, Int> {
        val gridX = (x * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val gridY = (y * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        return gridX to gridY
    }
    
    private fun calculateGridDistance(p1: Pair<Int, Int>, p2: Pair<Int, Int>): Int {
        return kotlin.math.abs(p1.first - p2.first) + kotlin.math.abs(p1.second - p2.second)
    }
    
    private fun generateToleranceCandidates(
        quantizedPoints: List<Pair<Int, Int>>
    ): List<List<Pair<Int, Int>>> {
        val candidates = mutableListOf<List<Pair<Int, Int>>>()
        
        val point1Variants = generatePointVariants(quantizedPoints[0], TAP_TOLERANCE)
        val point2Variants = generatePointVariants(quantizedPoints[1], TAP_TOLERANCE)
        
        for (p1 in point1Variants) {
            for (p2 in point2Variants) {
                if (calculateGridDistance(p1, p2) >= 2) {
                    candidates.add(listOf(p1, p2))
                }
            }
        }
        
        return candidates
    }
    
    private fun generatePointVariants(
        point: Pair<Int, Int>,
        tolerance: Int
    ): List<Pair<Int, Int>> {
        val variants = mutableListOf<Pair<Int, Int>>()
        
        for (dx in -tolerance..tolerance) {
            for (dy in -tolerance..tolerance) {
                val newX = (point.first + dx).coerceIn(0, GRID_SIZE - 1)
                val newY = (point.second + dy).coerceIn(0, GRID_SIZE - 1)
                variants.add(newX to newY)
            }
        }
        
        return variants
    }
}
