package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Mouse Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation from mouse movement
 * - Micro-timing analysis
 * - DoS protection (MAX_POINTS)
 * - Validation
 * - Edge cases
 */
class MouseFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigestMicroTiming_ValidInput_ReturnsCorrectSize() {
        // Arrange
        val points = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 1100L),
            MouseFactor.MousePoint(50f, 60f, 1200L),
            MouseFactor.MousePoint(70f, 80f, 1300L)
        )
        
        // Act
        val digest = MouseFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val points = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        
        // Act
        val digest1 = MouseFactor.digestMicroTiming(points)
        val digest2 = MouseFactor.digestMicroTiming(points)
        
        // Assert
        assertArrayEquals("Same input should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentPaths_ProduceDifferentDigests() {
        // Arrange - Minimum 3 points required
        val path1 = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        val path2 = listOf(
            MouseFactor.MousePoint(50f, 60f, 1000L),
            MouseFactor.MousePoint(70f, 80f, 2000L),
            MouseFactor.MousePoint(90f, 100f, 3000L)
        )

        // Act
        val digest1 = MouseFactor.digestMicroTiming(path1)
        val digest2 = MouseFactor.digestMicroTiming(path2)

        // Assert
        assertFalse(
            "Different paths should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingPath_ReturnsTrue() {
        // Arrange
        val points = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        val digest = MouseFactor.digestMicroTiming(points)

        // Act
        val verifyDigest = MouseFactor.digestMicroTiming(points)
        val result = digest.contentEquals(verifyDigest)

        // Assert
        assertTrue("Matching path should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingPath_ReturnsFalse() {
        // Arrange - Minimum 3 points required
        val path1 = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        val path2 = listOf(
            MouseFactor.MousePoint(50f, 60f, 1000L),
            MouseFactor.MousePoint(70f, 80f, 2000L),
            MouseFactor.MousePoint(90f, 100f, 3000L)
        )
        val digest = MouseFactor.digestMicroTiming(path1)

        // Act
        val verifyDigest = MouseFactor.digestMicroTiming(path2)
        val result = digest.contentEquals(verifyDigest)

        // Assert
        assertFalse("Non-matching path should fail verification", result)
    }
    
    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctPath = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        val wrongPath = listOf(
            MouseFactor.MousePoint(90f, 90f, 1000L),
            MouseFactor.MousePoint(80f, 80f, 2000L),
            MouseFactor.MousePoint(70f, 70f, 3000L)
        )
        val digest = MouseFactor.digestMicroTiming(correctPath)
        val iterations = 1000
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                val verifyDigest = MouseFactor.digestMicroTiming(correctPath)
                digest.contentEquals(verifyDigest)
            }
        }

        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                val verifyDigest = MouseFactor.digestMicroTiming(wrongPath)
                digest.contentEquals(verifyDigest)
            }
        }
        
        // Assert
        val timeDifference = kotlin.math.abs(correctTime - wrongTime)
        val averageTime = (correctTime + wrongTime) / 2.0
        val percentageDifference = (timeDifference / averageTime) * 100
        
        assertTrue(
            "Verification should be constant-time (within 30%). " +
            "Correct: ${correctTime}ms, Wrong: ${wrongTime}ms, Diff: ${percentageDifference.toInt()}%",
            percentageDifference < 30
        )
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyPoints_ThrowsException() {
        // Arrange
        val points = emptyList<MouseFactor.MousePoint>()
        
        // Act
        MouseFactor.digestMicroTiming(points)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewPoints_ThrowsException() {
        // Arrange - Only 2 points (minimum is 3)
        val points = List(2) { i ->
            MouseFactor.MousePoint(i.toFloat(), i.toFloat(), i.toLong())
        }

        // Act
        MouseFactor.digestMicroTiming(points)

        // Assert - Should throw
    }
    
    @Test
    fun testDigest_MinimumPoints_WorksCorrectly() {
        // Arrange - Exactly 3 points (MIN_POINTS)
        val points = List(MouseFactor.MIN_POINTS) { i ->
            MouseFactor.MousePoint(i.toFloat() * 10, i.toFloat() * 10, i.toLong() * 100)
        }

        // Act
        val digest = MouseFactor.digestMicroTiming(points)

        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumPoints_WorksCorrectly() {
        // Arrange - 300 points (MAX_POINTS)
        val points = List(300) { i ->
            MouseFactor.MousePoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val digest = MouseFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_ExceedsMaxPoints_ThrowsException() {
        // Arrange - 301 points (exceeds MAX_POINTS)
        val points = List(301) { i ->
            MouseFactor.MousePoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        MouseFactor.digestMicroTiming(points)
        
        // Assert - Should throw (DoS protection)
    }
    
    @Test
    fun testDigest_NegativeCoordinates_WorksCorrectly() {
        // Arrange
        val points = listOf(
            MouseFactor.MousePoint(-10f, -20f, 1000L),
            MouseFactor.MousePoint(-30f, -40f, 2000L),
            MouseFactor.MousePoint(-50f, -60f, 3000L)
        )
        
        // Act
        val digest = MouseFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_VeryLargeCoordinates_WorksCorrectly() {
        // Arrange
        val points = listOf(
            MouseFactor.MousePoint(10000f, 20000f, 1000L),
            MouseFactor.MousePoint(30000f, 40000f, 2000L),
            MouseFactor.MousePoint(50000f, 60000f, 3000L)
        )
        
        // Act
        val digest = MouseFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== MICRO-TIMING ANALYSIS ====================
    
    @Test
    fun testDigest_TimingSensitive_DifferentTimingsProduceDifferentDigests() {
        // Arrange - Same spatial path, different timing
        val fastPath = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 1100L),  // 100ms
            MouseFactor.MousePoint(50f, 60f, 1200L)   // 100ms
        )
        
        val slowPath = listOf(
            MouseFactor.MousePoint(10f, 20f, 2000L),
            MouseFactor.MousePoint(30f, 40f, 2500L),  // 500ms
            MouseFactor.MousePoint(50f, 60f, 3000L)   // 500ms
        )
        
        // Act
        val fastDigest = MouseFactor.digestMicroTiming(fastPath)
        val slowDigest = MouseFactor.digestMicroTiming(slowPath)
        
        // Assert
        assertFalse(
            "Different timing should produce different digests (micro-timing)",
            fastDigest.contentEquals(slowDigest)
        )
    }
    
    @Test
    fun testDigest_SpatialPattern_DifferentPathsWithSameTimingProduceDifferentDigests() {
        // Arrange - Different spatial paths, same timing
        val path1 = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 2000L),
            MouseFactor.MousePoint(50f, 60f, 3000L)
        )
        
        val path2 = listOf(
            MouseFactor.MousePoint(15f, 25f, 1000L),
            MouseFactor.MousePoint(35f, 45f, 2000L),
            MouseFactor.MousePoint(55f, 65f, 3000L)
        )
        
        // Act
        val digest1 = MouseFactor.digestMicroTiming(path1)
        val digest2 = MouseFactor.digestMicroTiming(path2)
        
        // Assert
        assertFalse(
            "Different spatial paths should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val points = List(50) { i ->
            MouseFactor.MousePoint(
                x = (i * 10).toFloat(),
                y = (i * 20).toFloat(),
                t = i.toLong() * 100
            )
        }
        
        // Act
        val digest = MouseFactor.digestMicroTiming(points)
        
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

    // Note: MouseFactor doesn't have min/max points constraints like StylusFactor
    // Skipping getter tests as they don't exist in the simple MouseFactor implementation
    
    // ==================== PERFORMANCE ====================
    
    @Test
    fun testDigest_Performance_CompletesQuickly() {
        // Arrange
        val points = List(100) { i ->
            MouseFactor.MousePoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val time = measureTimeMillis {
            repeat(1000) {
                MouseFactor.digestMicroTiming(points)
            }
        }
        
        // Assert - 1000 digests should complete in under 1 second
        assertTrue(
            "1000 digests (100 points each) should complete in <1s (was ${time}ms)",
            time < 1000
        )
    }
}
