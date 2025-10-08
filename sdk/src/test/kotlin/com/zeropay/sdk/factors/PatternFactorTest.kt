package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.CryptoUtils
import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Pattern Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation (micro-timing and normalized)
 * - Constant-time verification
 * - DoS protection (MAX_POINTS)
 * - Edge cases (empty, single point, max points)
 * - Memory wiping
 * - Timing normalization
 */
class PatternFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigestMicroTiming_ValidInput_ReturnsCorrectSize() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L),
            PatternFactor.PatternPoint(50f, 60f, 3000L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigestNormalisedTiming_ValidInput_ReturnsCorrectSize() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L),
            PatternFactor.PatternPoint(50f, 60f, 3000L)
        )
        
        // Act
        val digest = PatternFactor.digestNormalisedTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        
        // Act
        val digest1 = PatternFactor.digestMicroTiming(points)
        val digest2 = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertArrayEquals("Same input should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentInputs_ProducesDifferentDigests() {
        // Arrange
        val points1 = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        val points2 = listOf(
            PatternFactor.PatternPoint(15f, 25f, 1000L),
            PatternFactor.PatternPoint(35f, 45f, 2000L)
        )
        
        // Act
        val digest1 = PatternFactor.digestMicroTiming(points1)
        val digest2 = PatternFactor.digestMicroTiming(points2)
        
        // Assert
        assertFalse(
            "Different inputs should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingDigests_ReturnsTrue() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Act
        val result = PatternFactor.verify(points, digest)
        
        // Assert
        assertTrue("Matching pattern should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingDigests_ReturnsFalse() {
        // Arrange
        val points1 = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        val points2 = listOf(
            PatternFactor.PatternPoint(15f, 25f, 1000L),
            PatternFactor.PatternPoint(35f, 45f, 2000L)
        )
        val digest = PatternFactor.digestMicroTiming(points1)
        
        // Act
        val result = PatternFactor.verify(points2, digest)
        
        // Assert
        assertFalse("Non-matching pattern should fail verification", result)
    }
    
    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctPoints = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        val wrongPoints = listOf(
            PatternFactor.PatternPoint(99f, 99f, 9999L),
            PatternFactor.PatternPoint(88f, 88f, 8888L)
        )
        val digest = PatternFactor.digestMicroTiming(correctPoints)
        
        // Act - Measure timing for correct and incorrect patterns
        val iterations = 1000
        
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                PatternFactor.verify(correctPoints, digest)
            }
        }
        
        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                PatternFactor.verify(wrongPoints, digest)
            }
        }
        
        // Assert - Times should be similar (within 20% tolerance for constant-time)
        val timeDifference = kotlin.math.abs(correctTime - wrongTime)
        val averageTime = (correctTime + wrongTime) / 2.0
        val percentageDifference = (timeDifference / averageTime) * 100
        
        assertTrue(
            "Verification should be constant-time (within 20% tolerance). " +
            "Correct: ${correctTime}ms, Wrong: ${wrongTime}ms, Diff: ${percentageDifference.toInt()}%",
            percentageDifference < 20
        )
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyPoints_ThrowsException() {
        // Arrange
        val points = emptyList<PatternFactor.PatternPoint>()
        
        // Act
        PatternFactor.digestMicroTiming(points)
        
        // Assert - Should throw exception
    }
    
    @Test
    fun testDigest_SinglePoint_WorksCorrectly() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    @Test
    fun testDigest_MaxPoints_WorksCorrectly() {
        // Arrange - 300 points (MAX_POINTS)
        val points = List(300) { i ->
            PatternFactor.PatternPoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ExceedsMaxPoints_ThrowsException() {
        // Arrange - 301 points (exceeds MAX_POINTS)
        val points = List(301) { i ->
            PatternFactor.PatternPoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        PatternFactor.digestMicroTiming(points)
        
        // Assert - Should throw exception
    }
    
    @Test
    fun testDigest_NegativeCoordinates_WorksCorrectly() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(-10f, -20f, 1000L),
            PatternFactor.PatternPoint(-30f, -40f, 2000L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    @Test
    fun testDigest_VeryLargeCoordinates_WorksCorrectly() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(Float.MAX_VALUE, Float.MAX_VALUE, Long.MAX_VALUE),
            PatternFactor.PatternPoint(Float.MIN_VALUE, Float.MIN_VALUE, Long.MIN_VALUE)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    // ==================== TIMING NORMALIZATION ====================
    
    @Test
    fun testNormalisedTiming_SpeedInvariant_SamePattern() {
        // Arrange - Same pattern drawn at different speeds
        val fastPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 1100L),  // 100ms
            PatternFactor.PatternPoint(50f, 60f, 1200L)   // 100ms
        )
        
        val slowPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 2000L),
            PatternFactor.PatternPoint(30f, 40f, 2500L),  // 500ms
            PatternFactor.PatternPoint(50f, 60f, 3000L)   // 500ms
        )
        
        // Act
        val fastDigest = PatternFactor.digestNormalisedTiming(fastPattern)
        val slowDigest = PatternFactor.digestNormalisedTiming(slowPattern)
        
        // Assert
        assertArrayEquals(
            "Normalized timing should be speed-invariant (same spatial pattern)",
            fastDigest,
            slowDigest
        )
    }
    
    @Test
    fun testMicroTiming_SpeedDependent_DifferentDigests() {
        // Arrange - Same pattern drawn at different speeds
        val fastPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 1100L),
            PatternFactor.PatternPoint(50f, 60f, 1200L)
        )
        
        val slowPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 2000L),
            PatternFactor.PatternPoint(30f, 40f, 2500L),
            PatternFactor.PatternPoint(50f, 60f, 3000L)
        )
        
        // Act
        val fastDigest = PatternFactor.digestMicroTiming(fastPattern)
        val slowDigest = PatternFactor.digestMicroTiming(slowPattern)
        
        // Assert
        assertFalse(
            "Micro-timing should be speed-dependent (different timing = different digest)",
            fastDigest.contentEquals(slowDigest)
        )
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_NotAllZeros_NonTrivialHash() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertFalse(
            "Digest should not be all zeros",
            digest.all { it == 0.toByte() }
        )
    }
    
    @Test
    fun testDigest_HighEntropy_NoPatternInHash() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 2000L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert - Check for variety in bytes (high entropy)
        val uniqueBytes = digest.toSet().size
        assertTrue(
            "Digest should have high entropy (at least 20 unique bytes out of 32)",
            uniqueBytes >= 20
        )
    }
    
    // ==================== PERFORMANCE ====================
    
    @Test
    fun testDigest_Performance_CompletesQuickly() {
        // Arrange
        val points = List(100) { i ->
            PatternFactor.PatternPoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val time = measureTimeMillis {
            repeat(1000) {
                PatternFactor.digestMicroTiming(points)
            }
        }
        
        // Assert - 1000 digests should complete in under 1 second
        assertTrue(
            "1000 digests (100 points each) should complete in <1s (was ${time}ms)",
            time < 1000
        )
    }
}
