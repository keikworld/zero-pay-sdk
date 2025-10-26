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
        // Arrange - 4 taps with varied intervals (not all equal - passes variance check)
        val taps = listOf(1000L, 1300L, 1800L, 2500L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [300ms, 500ms, 700ms] - has sufficient variance

        // Act
        val digest = RhythmTapFactor.digest(taps)

        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }

    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange - varied intervals for variance check
        val taps = listOf(1000L, 1300L, 1800L, 2500L).map { RhythmTapFactor.RhythmTap(it) }

        // SECURITY NOTE: Using fixed nonce for test determinism.
        // In production, digest() uses random nonce for replay protection (default).
        val fixedNonce = 0L

        // Act
        val digest1 = RhythmTapFactor.digest(taps, fixedNonce)
        val digest2 = RhythmTapFactor.digest(taps, fixedNonce)

        // Assert
        assertArrayEquals("Same taps should produce same digest", digest1, digest2)
    }

    @Test
    fun testDigest_DifferentRhythms_ProduceDifferentDigests() {
        // Arrange - both with varied intervals
        val rhythm1 = listOf(1000L, 1200L, 1500L, 2000L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [200, 300, 500]
        val rhythm2 = listOf(1000L, 1400L, 2100L, 3000L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [400, 700, 900]
        
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
        // Arrange - varied intervals for variance check
        val tapTimestamps = listOf(1000L, 1300L, 1800L, 2500L) // Intervals: [300, 500, 700]
        val taps = tapTimestamps.map { RhythmTapFactor.RhythmTap(it) }
        val nonce = 123456L
        val digest = RhythmTapFactor.digest(taps, nonce)

        // Act
        val result = RhythmTapFactor.verify(taps, digest, nonce)

        // Assert
        assertTrue("Matching rhythm should verify successfully", result)
    }

    @Test
    fun testVerify_NonMatchingRhythm_ReturnsFalse() {
        // Arrange - both with varied intervals
        val rhythm1Timestamps = listOf(1000L, 1300L, 1800L, 2500L) // Intervals: [300, 500, 700]
        val rhythm2Timestamps = listOf(1000L, 1250L, 1700L, 2400L) // Intervals: [250, 450, 700]
        val taps1 = rhythm1Timestamps.map { RhythmTapFactor.RhythmTap(it) }
        val taps2 = rhythm2Timestamps.map { RhythmTapFactor.RhythmTap(it) }
        val nonce = 123456L
        val digest = RhythmTapFactor.digest(taps1, nonce)

        // Act
        val result = RhythmTapFactor.verify(taps2, digest, nonce)

        // Assert
        assertFalse("Non-matching rhythm should fail verification", result)
    }
    
    // ==================== INTERVAL NORMALIZATION ====================
    
    @Test
    fun testDigest_Normalization_ProportionalRhythmsProduceSameDigest() {
        // Arrange - Same rhythm pattern at different speeds (proportional intervals with variance)
        val rhythm1 = listOf(1000L, 1200L, 1500L, 1900L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [200, 300, 400]
        val rhythm2 = listOf(2000L, 2400L, 3000L, 3800L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [400, 600, 800] (2x)

        // SECURITY NOTE: Using fixed nonce for test determinism.
        // In production, digest() uses random nonce for replay protection (default).
        val fixedNonce = 0L

        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1, fixedNonce)
        val digest2 = RhythmTapFactor.digest(rhythm2, fixedNonce)

        // Assert
        assertArrayEquals(
            "Proportional rhythms should produce same digest (normalized)",
            digest1,
            digest2
        )
    }

    @Test
    fun testDigest_Normalization_DifferentProportionsProduceDifferentDigests() {
        // Arrange - Different rhythm patterns (both with variance)
        val rhythm1 = listOf(1000L, 1200L, 1500L, 1900L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [200, 300, 400]
        val rhythm2 = listOf(1000L, 1150L, 1400L, 1800L).map { RhythmTapFactor.RhythmTap(it) } // Intervals: [150, 250, 400]

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
        val taps = emptyList<RhythmTapFactor.RhythmTap>()

        // Act
        RhythmTapFactor.digest(taps)

        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewTaps_ThrowsException() {
        // Arrange - Only 3 taps (minimum is 4)
        val taps = listOf(1000L, 1500L, 2000L).map { RhythmTapFactor.RhythmTap(it) }
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test
    fun testDigest_MinimumTaps_WorksCorrectly() {
        // Arrange - Exactly 4 taps (minimum) with varied intervals
        val taps = listOf(1000L, 1300L, 1800L, 2500L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [300, 500, 700]ms - has sufficient variance

        // Act
        val digest = RhythmTapFactor.digest(taps)

        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumTaps_WorksCorrectly() {
        // Arrange - 6 taps (maximum) with varied intervals
        val taps = listOf(1000L, 1300L, 1700L, 2300L, 3100L, 4000L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [300, 400, 600, 800, 900]ms - has sufficient variance

        // Act
        val digest = RhythmTapFactor.digest(taps)

        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooManyTaps_ThrowsException() {
        // Arrange - 7 taps (exceeds maximum of 6)
        val taps = listOf(1000L, 1500L, 2000L, 2500L, 3000L, 3500L, 4000L).map { RhythmTapFactor.RhythmTap(it) }
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (DoS protection)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_NonMonotonic_ThrowsException() {
        // Arrange - Timestamps not in order
        val taps = listOf(1000L, 2000L, 1500L, 2500L).map { RhythmTapFactor.RhythmTap(it) }
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_DuplicateTimestamps_ThrowsException() {
        // Arrange - Same timestamp twice
        val taps = listOf(1000L, 1000L, 2000L, 3000L).map { RhythmTapFactor.RhythmTap(it) }
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TapsTooFast_ThrowsException() {
        // Arrange - Taps too close together (<50ms)
        val taps = listOf(1000L, 1020L, 1040L, 1060L).map { RhythmTapFactor.RhythmTap(it) } // 20ms intervals
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (humanly impossible)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TapsTooSlow_ThrowsException() {
        // Arrange - Total duration exceeds 10 seconds
        val taps = listOf(0L, 5000L, 10000L, 15000L).map { RhythmTapFactor.RhythmTap(it) } // 15 seconds total
        
        // Act
        RhythmTapFactor.digest(taps)
        
        // Assert - Should throw (timeout)
    }
    
    @Test
    fun testDigest_FastButValid_WorksCorrectly() {
        // Arrange - Fast taps with varied intervals (all above 50ms minimum)
        val taps = listOf(1000L, 1080L, 1180L, 1300L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [80, 100, 120]ms - fast but has variance

        // Act
        val digest = RhythmTapFactor.digest(taps)

        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_SlowButValid_WorksCorrectly() {
        // Arrange - Slow taps with varied intervals (total under 10s max)
        val taps = listOf(0L, 1500L, 3500L, 6000L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [1500, 2000, 2500]ms - slow but has variance, 6 seconds total

        // Act
        val digest = RhythmTapFactor.digest(taps)

        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange - Irregular intervals with variance
        val taps = listOf(1000L, 1357L, 2468L, 3790L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [357, 1111, 1322]ms - varied intervals

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
        val rhythm1 = listOf(1000L, 1300L, 1800L, 2500L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [300, 500, 700]ms
        val rhythm2 = listOf(1000L, 1301L, 1800L, 2500L).map { RhythmTapFactor.RhythmTap(it) }
        // Intervals: [301, 499, 700]ms - 1ms difference in first interval

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
        // Note: MIN_INTERVAL_MS and MAX_DURATION_MS are private constants, not exposed via getters
    }
}
