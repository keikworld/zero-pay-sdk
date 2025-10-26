package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Balance Factor Tests - PRODUCTION VERSION (NEW)
 * 
 * Tests:
 * - Digest generation from accelerometer data
 * - Constant-time verification
 * - DoS protection (MIN/MAX samples)
 * - Pattern validation (variance check)
 * - Quantization for consistency
 * - Edge cases
 */
class BalanceFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidBalance_ReturnsCorrectSize() {
        // Arrange
        val points = List(20) { i ->
            BalanceFactor.BalancePoint(
                x = 0.1f + (i * 0.01f),
                y = 0.2f + (i * 0.01f),
                z = 9.8f + (i * 0.01f),
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val digest = BalanceFactor.digest(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val points = List(15) { i ->
            BalanceFactor.BalancePoint(
                x = 1.0f,
                y = 2.0f,
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }

        // SECURITY NOTE: Using fixed timestamp for test determinism.
        // In production, digest() uses current timestamp for replay protection (default).
        val fixedTimestamp = 0L

        // Act
        val digest1 = BalanceFactor.digestWithTimestamp(points, fixedTimestamp)
        val digest2 = BalanceFactor.digestWithTimestamp(points, fixedTimestamp)

        // Assert
        assertArrayEquals("Same input should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentPatterns_ProduceDifferentDigests() {
        // Arrange
        val pattern1 = List(15) { i ->
            BalanceFactor.BalancePoint(1.0f, 2.0f, 9.8f, 1000L + (i * 100L))
        }
        val pattern2 = List(15) { i ->
            BalanceFactor.BalancePoint(2.0f, 3.0f, 9.5f, 1000L + (i * 100L))
        }
        
        // Act
        val digest1 = BalanceFactor.digest(pattern1)
        val digest2 = BalanceFactor.digest(pattern2)
        
        // Assert
        assertFalse(
            "Different patterns should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingPattern_ReturnsTrue() {
        // Arrange
        val points = List(20) { i ->
            BalanceFactor.BalancePoint(
                x = 0.5f + (i * 0.01f),
                y = 1.0f + (i * 0.01f),
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }
        val digest = BalanceFactor.digest(points)
        
        // Act
        val result = BalanceFactor.verify(points, digest)
        
        // Assert
        assertTrue("Matching pattern should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingPattern_ReturnsFalse() {
        // Arrange
        val points1 = List(15) { i ->
            BalanceFactor.BalancePoint(1.0f, 2.0f, 9.8f, 1000L + (i * 100L))
        }
        val points2 = List(15) { i ->
            BalanceFactor.BalancePoint(2.0f, 3.0f, 9.5f, 1000L + (i * 100L))
        }
        val digest = BalanceFactor.digest(points1)
        
        // Act
        val result = BalanceFactor.verify(points2, digest)
        
        // Assert
        assertFalse("Non-matching pattern should fail verification", result)
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyPoints_ThrowsException() {
        // Arrange
        val points = emptyList<BalanceFactor.BalancePoint>()
        
        // Act
        BalanceFactor.digest(points)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewSamples_ThrowsException() {
        // Arrange - Only 5 samples (minimum is 10)
        val points = List(5) { i ->
            BalanceFactor.BalancePoint(1.0f, 2.0f, 9.8f, 1000L + (i * 100L))
        }
        
        // Act
        BalanceFactor.digest(points)
        
        // Assert - Should throw
    }
    
    @Test
    fun testDigest_MinimumSamples_WorksCorrectly() {
        // Arrange - Exactly 10 samples (minimum)
        val points = List(10) { i ->
            BalanceFactor.BalancePoint(
                x = 0.5f + (i * 0.1f),
                y = 1.0f,
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val digest = BalanceFactor.digest(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumSamples_WorksCorrectly() {
        // Arrange - 500 samples (maximum)
        val points = List(500) { i ->
            BalanceFactor.BalancePoint(
                x = (i % 10).toFloat(),
                y = (i % 20).toFloat(),
                z = 9.8f,
                timestamp = 1000L + (i * 10L)
            )
        }
        
        // Act
        val digest = BalanceFactor.digest(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ExceedsMaxSamples_ThrowsException() {
        // Arrange - 501 samples (exceeds maximum)
        val points = List(501) { i ->
            BalanceFactor.BalancePoint(1.0f, 2.0f, 9.8f, 1000L + (i * 10L))
        }
        
        // Act
        BalanceFactor.digest(points)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ExceedsDuration_ThrowsException() {
        // Arrange - Duration exceeds 30 seconds
        val points = List(15) { i ->
            BalanceFactor.BalancePoint(
                x = 1.0f,
                y = 2.0f,
                z = 9.8f,
                timestamp = (i * 3000L) // 3 seconds apart = 42 seconds total
            )
        }
        
        // Act
        BalanceFactor.digest(points)
        
        // Assert - Should throw
    }
    
    // ==================== VALIDATION ====================
    
    @Test
    fun testIsValidPattern_ValidPattern_ReturnsTrue() {
        // Arrange - Pattern with movement
        val points = List(20) { i ->
            BalanceFactor.BalancePoint(
                x = kotlin.math.sin(i.toDouble()).toFloat(),
                y = kotlin.math.cos(i.toDouble()).toFloat(),
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val result = BalanceFactor.isValidPattern(points)
        
        // Assert
        assertTrue("Pattern with movement should be valid", result)
    }
    
    @Test
    fun testIsValidPattern_NoMovement_ReturnsFalse() {
        // Arrange - All points identical (no movement)
        val points = List(20) {
            BalanceFactor.BalancePoint(
                x = 1.0f,
                y = 2.0f,
                z = 9.8f,
                timestamp = 1000L
            )
        }
        
        // Act
        val result = BalanceFactor.isValidPattern(points)
        
        // Assert
        assertFalse("Pattern without movement should be invalid", result)
    }
    
    @Test
    fun testIsValidPattern_UnrealisticValues_ReturnsFalse() {
        // Arrange - Unrealistic acceleration (>20g)
        val points = List(15) { i ->
            BalanceFactor.BalancePoint(
                x = 100f, // Unrealistic
                y = 2.0f,
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val result = BalanceFactor.isValidPattern(points)
        
        // Assert
        assertFalse("Pattern with unrealistic values should be invalid", result)
    }
    
    // ==================== QUANTIZATION ====================
    
    @Test
    fun testDigest_Quantization_SimilarValuesProduceSameDigest() {
        // Arrange - Values differ by <0.1g (quantization threshold)
        val points1 = List(15) { i ->
            BalanceFactor.BalancePoint(
                x = 1.01f,
                y = 2.02f,
                z = 9.81f,
                timestamp = 1000L + (i * 100L)
            )
        }
        
        val points2 = List(15) { i ->
            BalanceFactor.BalancePoint(
                x = 1.04f, // Within 0.1g
                y = 2.03f, // Within 0.1g
                z = 9.84f, // Within 0.1g
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val digest1 = BalanceFactor.digest(points1)
        val digest2 = BalanceFactor.digest(points2)
        
        // Assert
        assertArrayEquals(
            "Values within quantization threshold should produce same digest",
            digest1,
            digest2
        )
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val points = List(20) { i ->
            BalanceFactor.BalancePoint(
                x = 0.5f + (i * 0.05f),
                y = 1.0f,
                z = 9.8f,
                timestamp = 1000L + (i * 100L)
            )
        }
        
        // Act
        val digest = BalanceFactor.digest(points)
        
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
        assertEquals(10, BalanceFactor.getMinSamples())
        assertEquals(500, BalanceFactor.getMaxSamples())
        assertEquals(30_000L, BalanceFactor.getMaxDurationMs())
    }
}
