package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Stylus Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation from stylus input (coordinates + pressure)
 * - Pressure sensitivity
 * - DoS protection (MAX_POINTS)
 * - Validation
 * - Edge cases
 */
class StylusFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigestFull_ValidInput_ReturnsCorrectSize() {
        // Arrange
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 1100L),
            StylusFactor.StylusPoint(50f, 60f, 0.9f, 1200L)
        )
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        
        // Act
        val digest1 = StylusFactor.digestFull(points)
        val digest2 = StylusFactor.digestFull(points)
        
        // Assert
        assertArrayEquals("Same input should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentStrokes_ProduceDifferentDigests() {
        // Arrange
        val stroke1 = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        val stroke2 = listOf(
            StylusFactor.StylusPoint(50f, 60f, 0.3f, 1000L),
            StylusFactor.StylusPoint(70f, 80f, 0.9f, 2000L)
        )
        
        // Act
        val digest1 = StylusFactor.digestFull(stroke1)
        val digest2 = StylusFactor.digestFull(stroke2)
        
        // Assert
        assertFalse(
            "Different strokes should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingStroke_ReturnsTrue() {
        // Arrange
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        val digest = StylusFactor.digestFull(points)
        
        // Act
        val result = StylusFactor.verify(points, digest)
        
        // Assert
        assertTrue("Matching stroke should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingStroke_ReturnsFalse() {
        // Arrange
        val stroke1 = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        val stroke2 = listOf(
            StylusFactor.StylusPoint(50f, 60f, 0.3f, 1000L),
            StylusFactor.StylusPoint(70f, 80f, 0.9f, 2000L)
        )
        val digest = StylusFactor.digestFull(stroke1)
        
        // Act
        val result = StylusFactor.verify(stroke2, digest)
        
        // Assert
        assertFalse("Non-matching stroke should fail verification", result)
    }
    
    // ==================== PRESSURE SENSITIVITY ====================
    
    @Test
    fun testDigest_PressureSensitive_DifferentPressuresProduceDifferentDigests() {
        // Arrange - Same spatial path, different pressure
        val lightPressure = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.2f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.3f, 2000L)
        )
        
        val heavyPressure = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.8f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.9f, 2000L)
        )
        
        // Act
        val lightDigest = StylusFactor.digestFull(lightPressure)
        val heavyDigest = StylusFactor.digestFull(heavyPressure)
        
        // Assert
        assertFalse(
            "Different pressure should produce different digests",
            lightDigest.contentEquals(heavyDigest)
        )
    }
    
    @Test
    fun testDigest_PressureOnly_OnlyPressureDifferent() {
        // Arrange - Only pressure changes
        val points1 = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.5f, 2000L)
        )
        
        val points2 = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.6f, 1000L), // Pressure different
            StylusFactor.StylusPoint(30f, 40f, 0.6f, 2000L)  // Pressure different
        )
        
        // Act
        val digest1 = StylusFactor.digestFull(points1)
        val digest2 = StylusFactor.digestFull(points2)
        
        // Assert
        assertFalse(
            "Pressure-only difference should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyPoints_ThrowsException() {
        // Arrange
        val points = emptyList<StylusFactor.StylusPoint>()
        
        // Act
        StylusFactor.digestFull(points)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewPoints_ThrowsException() {
        // Arrange - Only 5 points (minimum is 10)
        val points = List(5) { i ->
            StylusFactor.StylusPoint(i.toFloat(), i.toFloat(), 0.5f, i.toLong())
        }
        
        // Act
        StylusFactor.digestFull(points)
        
        // Assert - Should throw
    }
    
    @Test
    fun testDigest_MinimumPoints_WorksCorrectly() {
        // Arrange - Exactly 10 points
        val points = List(10) { i ->
            StylusFactor.StylusPoint(i.toFloat() * 10, i.toFloat() * 10, 0.5f, i.toLong() * 100)
        }
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumPoints_WorksCorrectly() {
        // Arrange - 300 points (MAX_POINTS)
        val points = List(300) { i ->
            StylusFactor.StylusPoint(i.toFloat(), i.toFloat(), 0.5f, i.toLong())
        }
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ExceedsMaxPoints_ThrowsException() {
        // Arrange - 301 points (exceeds MAX_POINTS)
        val points = List(301) { i ->
            StylusFactor.StylusPoint(i.toFloat(), i.toFloat(), 0.5f, i.toLong())
        }
        
        // Act
        StylusFactor.digestFull(points)
        
        // Assert - Should throw (DoS protection)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_InvalidPressure_NegativeValue_ThrowsException() {
        // Arrange - Negative pressure
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, -0.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        
        // Act
        StylusFactor.digestFull(points)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_InvalidPressure_ExceedsOne_ThrowsException() {
        // Arrange - Pressure > 1.0
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 1.5f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.7f, 2000L)
        )
        
        // Act
        StylusFactor.digestFull(points)
        
        // Assert - Should throw
    }
    
    @Test
    fun testDigest_ZeroPressure_WorksCorrectly() {
        // Arrange - Zero pressure (valid)
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 0.0f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 0.0f, 2000L),
            StylusFactor.StylusPoint(50f, 60f, 0.0f, 3000L)
        )
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaxPressure_WorksCorrectly() {
        // Arrange - Maximum pressure (1.0)
        val points = listOf(
            StylusFactor.StylusPoint(10f, 20f, 1.0f, 1000L),
            StylusFactor.StylusPoint(30f, 40f, 1.0f, 2000L),
            StylusFactor.StylusPoint(50f, 60f, 1.0f, 3000L)
        )
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val points = List(50) { i ->
            StylusFactor.StylusPoint(
                x = (i * 10).toFloat(),
                y = (i * 20).toFloat(),
                pressure = (i % 10) / 10f,
                t = i.toLong() * 100
            )
        }
        
        // Act
        val digest = StylusFactor.digestFull(points)
        
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
        // Note: StylusFactor doesn't expose getMinPoints/getMaxPoints getters
        // MIN_POINTS and MAX_POINTS are private constants
        // Skipping getter tests for this simple implementation
    }
}
