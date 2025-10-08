package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Rhythm Tap Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation from tap timing
 * - Interval normalization
 * - DoS protection (MIN/MAX taps)
 * - Validation
 * - Edge cases
 */
class RhythmTapFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidTaps_ReturnsCorrectSize() {
        // Arrange - 4 taps
        val taps = listOf(1000L, 1500L, 2000L, 2500L)
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val taps = listOf(1000L, 1500L, 2000L, 2500L)
        
        // Act
        val digest1 = RhythmTapFactor.digest(taps)
        val digest2 = RhythmTapFactor.digest(taps)
        
        // Assert
        assertArrayEquals("Same taps should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentRhythms_ProduceDifferentDigests() {
        // Arrange
        val rhythm1 = listOf(1000L, 1100L, 1200L, 1300L) // Fast taps
        val rhythm2 = listOf(1000L, 1500L, 2000L, 2500L) // Slow taps
        
        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1)
        val digest2 = RhythmTapFactor.digest(rhythm2)
        
        // Assert
        assertFalse(
            "Different rhythms should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingRhythm_ReturnsTrue() {
        // Arrange
        val taps = listOf(1000L, 1500L, 2000L, 2500L)
        val digest = RhythmTapFactor.digest(taps)
        
        // Act
        val result = RhythmTapFactor.verify(taps, digest)
        
        // Assert
        assertTrue("Matching rhythm should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingRhythm_ReturnsFalse() {
        // Arrange
        val rhythm1 = listOf(1000L, 1500L, 2000L, 2500L)
        val rhythm2 = listOf(1000L, 1200L, 1400L, 1600L)
        val digest = RhythmTapFactor.digest(rhythm1)
        
        // Act
        val result = RhythmTapFactor.verify(rhythm2, digest)
        
        // Assert
        assertFalse("Non-matching rhythm should fail verification", result)
    }
    
    // ==================== INTERVAL NORMALIZATION ====================
    
    @Test
    fun testDigest_Normalization_ProportionalRhythmsProduceSameDigest() {
        // Arrange - Same rhythm at different speeds (proportional intervals)
        val rhythm1 = listOf(1000L, 1200L, 1400L, 1600L) // Intervals: 200, 200, 200
        val rhythm2 = listOf(2000L, 2400L, 2800L, 3200L) // Intervals: 400, 400, 400 (2x)
        
        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1)
        val digest2 = RhythmTapFactor.digest(rhythm2)
        
        // Assert
        assertArrayEquals(
            "Proportional rhythms should produce same digest (normalized)",
            digest1,
            digest2
        )
    }
    
    @Test
    fun testDigest_Normalization_DifferentProportionsProduceDifferentDigests() {
        // Arrange - Different rhythm patterns
        val rhythm1 = listOf(1000L, 1200L, 1400L, 1600L) // Intervals: 200, 200, 200 (even)
        val rhythm2 = listOf(1000L, 1100L, 1300L, 1600L) // Intervals: 100, 200, 300 (uneven)
        
        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1)
        val digest2 = RhythmTapFactor.digest(rhythm2)
        
        // Assert
        assertFalse(
            "Different rhythm proportions should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyTaps_ThrowsException() {
        // Arrange
        val taps = emptyList<Long>()
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewTaps_ThrowsException() {
        // Arrange - Only 3 taps (minimum is 4)
        val taps = listOf(1000L, 1500L, 2000L)
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test
    fun testDigest_MinimumTaps_WorksCorrectly() {
        // Arrange - Exactly 4 taps (minimum)
        val taps = listOf(1000L, 1500L, 2000L, 2500L)
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumTaps_WorksCorrectly() {
        // Arrange - 6 taps (maximum)
        val taps = listOf(1000L, 1500L, 2000L, 2500L, 3000L, 3500L)
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooManyTaps_ThrowsException() {
        // Arrange - 7 taps (exceeds maximum of 6)
        val taps = listOf(1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L)
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (DoS protection)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_NonMonotonic_ThrowsException() {
        // Arrange - Timestamps not in order
        val taps = listOf(1000L, 2000L, 1500L, 2500L)
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_DuplicateTimestamps_ThrowsException() {
        // Arrange - Same timestamp twice
        val taps = listOf(1000L, 1000L, 2000L, 3000L)
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TapsTooFast_ThrowsException() {
        // Arrange - Taps too close together (<50ms)
        val taps = listOf(1000L, 1020L, 1040L, 1060L) // 20ms intervals
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (humanly impossible)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TapsTooSlow_ThrowsException() {
        // Arrange - Total duration exceeds 10 seconds
        val taps = listOf(0L, 5000L, 10000L, 15000L) // 15 seconds total
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (timeout)
    }
    
    @Test
    fun testDigest_FastButValid_WorksCorrectly() {
        // Arrange - 100ms intervals (valid, just above minimum)
        val taps = listOf(1000L, 1100L, 1200L, 1300L)
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_SlowButValid_WorksCorrectly() {
        // Arrange - 2 second intervals (valid, just under max)
        val taps = listOf(0L, 2000L, 4000L, 6000L) // 6 seconds total
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val taps = listOf(1000L, 1357L, 2468L, 3579L)
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
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
    
    @Test
    fun testDigest_SmallTimingChange_LargeHashChange() {
        // Arrange - Only 1ms difference in one interval
        val rhythm1 = listOf(1000L, 1500L, 2000L, 2500L)
        val rhythm2 = listOf(1000L, 1501L, 2000L, 2500L) // 1ms difference
        
        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1)
        val digest2 = RhythmTapFactor.digest(rhythm2)
        
        // Assert - Check avalanche effect
        var differentBytes = 0
        for (i in digest1.indices) {
            if (digest1[i] != digest2[i]) differentBytes++
        }
        
        assertTrue(
            "Small timing change should cause large hash change (avalanche effect). " +
            "Different bytes: $differentBytes/32",
            differentBytes >= 16
        )
    }
    
    // ==================== GETTERS ====================
    
    @Test
    fun testGetters_ReturnCorrectValues() {
        // Act & Assert
        assertEquals(4, RhythmTapFactor.getMinTaps())
        assertEquals(6, RhythmTapFactor.getMaxTaps())
        assertEquals(50L, RhythmTapFactor.getMinIntervalMs())
        assertEquals(10_000L, RhythmTapFactor.getMaxDurationMs())
    }
}
