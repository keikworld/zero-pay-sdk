package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Image Tap Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation from tap coordinates
 * - Grid-based quantization
 * - Fuzzy matching with tolerance
 * - GDPR compliance (abstract images only)
 * - Edge cases
 */
class ImageTapFactorTest {
    
    // Helper to create ImageInfo
    private fun createTestImageInfo(): ImageTapFactor.ImageInfo {
        val imageHash = CryptoUtils.sha256("test_image".toByteArray())
        return ImageTapFactor.ImageInfo("test_image", imageHash)
    }
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidTaps_ReturnsCorrectSize() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.2f, 0.3f),
            ImageTapFactor.TapPoint(0.7f, 0.8f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest = ImageTapFactor.digest(taps, imageInfo)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.5f, 0.5f),
            ImageTapFactor.TapPoint(0.9f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest1 = ImageTapFactor.digest(taps, imageInfo)
        val digest2 = ImageTapFactor.digest(taps, imageInfo)
        
        // Assert
        assertArrayEquals("Same taps should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentTaps_ProduceDifferentDigests() {
        // Arrange
        val taps1 = listOf(
            ImageTapFactor.TapPoint(0.2f, 0.3f),
            ImageTapFactor.TapPoint(0.7f, 0.8f)
        )
        val taps2 = listOf(
            ImageTapFactor.TapPoint(0.3f, 0.4f),
            ImageTapFactor.TapPoint(0.8f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest1 = ImageTapFactor.digest(taps1, imageInfo)
        val digest2 = ImageTapFactor.digest(taps2, imageInfo)
        
        // Assert
        assertFalse(
            "Different taps should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    @Test
    fun testDigest_DifferentImages_ProduceDifferentDigests() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.5f, 0.5f),
            ImageTapFactor.TapPoint(0.9f, 0.9f)
        )
        
        val image1Hash = CryptoUtils.sha256("image1".toByteArray())
        val image1Info = ImageTapFactor.ImageInfo("image1", image1Hash)
        
        val image2Hash = CryptoUtils.sha256("image2".toByteArray())
        val image2Info = ImageTapFactor.ImageInfo("image2", image2Hash)
        
        // Act
        val digest1 = ImageTapFactor.digest(taps, image1Info)
        val digest2 = ImageTapFactor.digest(taps, image2Info)
        
        // Assert
        assertFalse(
            "Same taps on different images should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_ExactMatch_ReturnsTrue() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.5f, 0.5f),
            ImageTapFactor.TapPoint(0.9f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        val digest = ImageTapFactor.digest(taps, imageInfo)
        
        // Act
        val result = ImageTapFactor.verify(digest, taps, imageInfo)
        
        // Assert
        assertTrue("Exact match should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatch_ReturnsFalse() {
        // Arrange
        val enrollTaps = listOf(
            ImageTapFactor.TapPoint(0.2f, 0.3f),
            ImageTapFactor.TapPoint(0.7f, 0.8f)
        )
        val authTaps = listOf(
            ImageTapFactor.TapPoint(0.3f, 0.4f),
            ImageTapFactor.TapPoint(0.8f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        val digest = ImageTapFactor.digest(enrollTaps, imageInfo)
        
        // Act
        val result = ImageTapFactor.verify(digest, authTaps, imageInfo)
        
        // Assert
        assertFalse("Non-matching taps should fail verification", result)
    }
    
    @Test
    fun testVerifyFuzzy_WithinTolerance_ReturnsTrue() {
        // Arrange - Taps slightly off (within tolerance)
        val enrollTaps = listOf(
            ImageTapFactor.TapPoint(0.50f, 0.50f),
            ImageTapFactor.TapPoint(0.90f, 0.90f)
        )
        val authTaps = listOf(
            ImageTapFactor.TapPoint(0.51f, 0.51f), // Slightly off
            ImageTapFactor.TapPoint(0.91f, 0.91f)  // Slightly off
        )
        val imageInfo = createTestImageInfo()
        val digest = ImageTapFactor.digest(enrollTaps, imageInfo)
        
        // Act
        val result = ImageTapFactor.verifyFuzzy(digest, authTaps, imageInfo)
        
        // Assert
        assertTrue("Taps within tolerance should verify with fuzzy matching", result)
    }
    
    // ==================== VALIDATION ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_OnlyOneTap_ThrowsException() {
        // Arrange
        val taps = listOf(ImageTapFactor.TapPoint(0.5f, 0.5f))
        val imageInfo = createTestImageInfo()
        
        // Act
        ImageTapFactor.digest(taps, imageInfo)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ThreeTaps_ThrowsException() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.2f, 0.3f),
            ImageTapFactor.TapPoint(0.5f, 0.6f),
            ImageTapFactor.TapPoint(0.8f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        ImageTapFactor.digest(taps, imageInfo)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_CoordinatesOutOfRange_ThrowsException() {
        // Arrange - Coordinates > 1.0
        val taps = listOf(
            ImageTapFactor.TapPoint(1.5f, 0.5f),
            ImageTapFactor.TapPoint(0.9f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        ImageTapFactor.digest(taps, imageInfo)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_NegativeCoordinates_ThrowsException() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(-0.1f, 0.5f),
            ImageTapFactor.TapPoint(0.9f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        ImageTapFactor.digest(taps, imageInfo)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TapsTooClose_ThrowsException() {
        // Arrange - Taps too close together
        val taps = listOf(
            ImageTapFactor.TapPoint(0.5f, 0.5f),
            ImageTapFactor.TapPoint(0.51f, 0.51f) // Too close
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        ImageTapFactor.digest(taps, imageInfo)
        
        // Assert - Should throw (minimum distance not met)
    }
    
    // ==================== EDGE CASES ====================
    
    @Test
    fun testDigest_CornerTaps_WorksCorrectly() {
        // Arrange - Taps at corners
        val taps = listOf(
            ImageTapFactor.TapPoint(0.0f, 0.0f),  // Top-left
            ImageTapFactor.TapPoint(1.0f, 1.0f)   // Bottom-right
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest = ImageTapFactor.digest(taps, imageInfo)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_CenterTaps_WorksCorrectly() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.5f, 0.5f),
            ImageTapFactor.TapPoint(0.5f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest = ImageTapFactor.digest(taps, imageInfo)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== GRID QUANTIZATION ====================
    
    @Test
    fun testDigest_Quantization_NearbyTapsProduceSameDigest() {
        // Arrange - Taps very close (within same grid cell)
        val taps1 = listOf(
            ImageTapFactor.TapPoint(0.50f, 0.50f),
            ImageTapFactor.TapPoint(0.90f, 0.90f)
        )
        val taps2 = listOf(
            ImageTapFactor.TapPoint(0.501f, 0.501f), // Within grid tolerance
            ImageTapFactor.TapPoint(0.901f, 0.901f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest1 = ImageTapFactor.digest(taps1, imageInfo)
        val digest2 = ImageTapFactor.digest(taps2, imageInfo)
        
        // Assert
        assertArrayEquals(
            "Nearby taps within grid cell should produce same digest",
            digest1,
            digest2
        )
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val taps = listOf(
            ImageTapFactor.TapPoint(0.3f, 0.4f),
            ImageTapFactor.TapPoint(0.8f, 0.9f)
        )
        val imageInfo = createTestImageInfo()
        
        // Act
        val digest = ImageTapFactor.digest(taps, imageInfo)
        
        // Assert
        val uniqueBytes = digest.toSet().size
        assertTrue(
            "Digest should have high entropy (>=20 unique bytes)",
            uniqueBytes >= 20
        )
        
        assertFalse(
            "Digest should not be all zeros",
            digest.all { it == 0.toByte() }
        )
    }
    
    // ==================== GETTERS ====================
    
    @Test
    fun testGetters_ReturnCorrectValues() {
        // Act & Assert
        assertEquals(20, ImageTapFactor.getGridSize())
        assertEquals(2, ImageTapFactor.getTapTolerance())
        assertEquals(2, ImageTapFactor.getRequiredTaps())
    }
}
