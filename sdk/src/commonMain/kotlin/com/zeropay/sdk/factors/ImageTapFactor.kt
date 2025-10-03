package com.zeropay.sdk.factors

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
 * - Coordinates quantized to grid to allow fuzzy matching
 * 
 * This implementation uses a GDPR-safe approach:
 * - Pre-approved abstract images only (no user uploads)
 * - No facial recognition or biometric analysis
 * - Only coordinate-based authentication
 */
object ImageTapFactor {
    
    // Grid size for coordinate quantization (more tolerance = easier auth)
    private const val GRID_SIZE = 20 // Divide image into 20x20 grid
    
    // Tolerance for tap position (in grid cells)
    private const val TAP_TOLERANCE = 2 // Allow 2 cells deviation
    
    data class TapPoint(
        val x: Float,  // Normalized 0.0-1.0
        val y: Float,  // Normalized 0.0-1.0
        val timestamp: Long
    )
    
    data class ImageInfo(
        val imageId: String,      // Pre-approved image identifier
        val imageHash: ByteArray  // SHA-256 of image data
    )
    
    /**
     * Generate digest from tap points and image
     * 
     * @param tapPoints List of 2 tap locations (normalized 0.0-1.0)
     * @param imageInfo Information about the image used
     * @return SHA-256 hash (32 bytes)
     */
    fun digest(tapPoints: List<TapPoint>, imageInfo: ImageInfo): ByteArray {
        require(tapPoints.size == 2) { "Must have exactly 2 tap points" }
        require(tapPoints.all { it.x in 0f..1f && it.y in 0f..1f }) {
            "Tap coordinates must be normalized (0.0-1.0)"
        }
        
        // Quantize tap points to grid for tolerance
        val quantizedPoints = tapPoints.map { point ->
            quantizeToGrid(point.x, point.y)
        }
        
        // Ensure points are different (at least 2 grid cells apart)
        val distance = calculateGridDistance(quantizedPoints[0], quantizedPoints[1])
        require(distance >= 2) { "Tap points must be at least 2 grid cells apart" }
        
        // Build digest from: image hash + quantized coordinates
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
     * Verify authentication taps against enrolled taps
     * Allows tolerance for slight variations in tap location
     */
    fun verify(
        enrolledDigest: ByteArray,
        authTapPoints: List<TapPoint>,
        imageInfo: ImageInfo
    ): Boolean {
        require(authTapPoints.size == 2) { "Must have exactly 2 tap points" }
        
        // Generate digest from auth taps
        val authDigest = digest(authTapPoints, imageInfo)
        
        // Constant-time comparison
        return constantTimeEquals(enrolledDigest, authDigest)
    }
    
    /**
     * Verify with fuzzy matching - allows small coordinate deviations
     * Generates multiple candidate digests within tolerance range
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
        
        // Check if any candidate matches the enrolled digest
        for (candidate in candidates) {
            val candidateBytes = mutableListOf<Byte>()
            candidateBytes.addAll(imageInfo.imageHash.toList())
            
            // Sort points for consistency
            val sortedPoints = candidate.sorted()
            sortedPoints.forEach { (gridX, gridY) ->
                candidateBytes.add(gridX.toByte())
                candidateBytes.add(gridY.toByte())
            }
            
            val candidateDigest = CryptoUtils.sha256(candidateBytes.toByteArray())
            
            if (constantTimeEquals(enrolledDigest, candidateDigest)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get list of pre-approved GDPR-safe images
     * These are abstract patterns, not personal photos
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
     * Validate image is GDPR-compliant (no faces, no personal data)
     * In production, this would use ML to detect faces/people
     */
    fun isGDPRCompliant(imageData: ByteArray): Boolean {
        // TODO: Implement face detection / people detection
        // For now, only allow pre-approved images
        val imageHash = CryptoUtils.sha256(imageData)
        val approvedHashes = getApprovedImages().map { it.imageHash }
        
        return approvedHashes.any { approvedHash ->
            constantTimeEquals(imageHash, approvedHash)
        }
    }
    
    // ============== Private Helper Methods ==============
    
    /**
     * Quantize coordinates to grid cell
     */
    private fun quantizeToGrid(x: Float, y: Float): Pair<Int, Int> {
        val gridX = (x * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        val gridY = (y * GRID_SIZE).toInt().coerceIn(0, GRID_SIZE - 1)
        return gridX to gridY
    }
    
    /**
     * Calculate Manhattan distance between two grid points
     */
    private fun calculateGridDistance(p1: Pair<Int, Int>, p2: Pair<Int, Int>): Int {
        return kotlin.math.abs(p1.first - p2.first) + kotlin.math.abs(p1.second - p2.second)
    }
    
    /**
     * Generate all candidate tap positions within tolerance
     */
    private fun generateToleranceCandidates(
        quantizedPoints: List<Pair<Int, Int>>
    ): List<List<Pair<Int, Int>>> {
        val candidates = mutableListOf<List<Pair<Int, Int>>>()
        
        // Generate variants for each point within tolerance
        val point1Variants = generatePointVariants(quantizedPoints[0], TAP_TOLERANCE)
        val point2Variants = generatePointVariants(quantizedPoints[1], TAP_TOLERANCE)
        
        // Combine all variants
        for (p1 in point1Variants) {
            for (p2 in point2Variants) {
                // Ensure points are different
                if (calculateGridDistance(p1, p2) >= 2) {
                    candidates.add(listOf(p1, p2))
                }
            }
        }
        
        return candidates
    }
    
    /**
     * Generate point variants within tolerance radius
     */
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
    
    /**
     * Constant-time byte array comparison
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
