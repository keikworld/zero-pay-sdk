package com.zeropay.sdk

import com.zeropay.sdk.factors.RhythmTapFactor
import com.zeropay.sdk.factors.RhythmTapFactor.RhythmTap
import org.junit.Assert.*
import org.junit.Test

/**
 * Rhythm Tap Factor Unit Tests
 * 
 * Tests cover:
 * - Digest generation and validation
 * - Constant-time verification
 * - Edge cases and error handling
 * - Security properties
 * - Thread safety
 * - Zero-knowledge compliance
 * 
 * Path: sdk/src/test/kotlin/com/zeropay/sdk/RhythmTapFactorTest.kt
 * 
 * Run with: ./gradlew :sdk:test --tests RhythmTapFactorTest
 */
class RhythmTapFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidTaps_Returns32Bytes() {
        // Arrange - Simple rhythm: 4 taps with varying intervals
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),  // 250ms interval
            RhythmTap(1600),  // 350ms interval
            RhythmTap(1850)   // 250ms interval
        )
        
        // Act
        val digest = RhythmTapFactor.digest(taps)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_SameRhythm_DifferentDigests() {
        // Arrange - Same rhythm tapped twice
        val rhythm1 = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val rhythm2 = listOf(
            RhythmTap(2000),
            RhythmTap(2250),
            RhythmTap(2600),
            RhythmTap(2850)
        )
        
        // Act - Generate digest twice (different nonces)
        val digest1 = RhythmTapFactor.digest(rhythm1)
        val digest2 = RhythmTapFactor.digest(rhythm2)
        
        // Assert - Should be different due to random nonce
        assertFalse(
            "Same rhythm should produce different digests (nonce makes it unique)",
            digest1.contentEquals(digest2)
        )
    }
    
    @Test
    fun testDigest_DifferentRhythms_DifferentDigests() {
        // Arrange - Two completely different rhythms with varied intervals
        // SECURITY: Intervals must have sufficient variance (MIN_VARIANCE_THRESHOLD = 0.05)
        // to prevent trivial patterns like equal-spaced taps
        val rhythm1 = listOf(
            RhythmTap(1000),
            RhythmTap(1250),  // 250ms interval
            RhythmTap(1550),  // 300ms interval (20% variance from previous)
            RhythmTap(1900)   // 350ms interval (16% variance from previous)
        )
        val rhythm2 = listOf(
            RhythmTap(1000),
            RhythmTap(1400),  // 400ms interval
            RhythmTap(2100),  // 700ms interval (75% variance from previous)
            RhythmTap(3000)   // 900ms interval (28% variance from previous)
        )
        val nonce = 12345L

        // Act
        val digest1 = RhythmTapFactor.digest(rhythm1, nonce)
        val digest2 = RhythmTapFactor.digest(rhythm2, nonce)

        // Assert - SHA-256 avalanche effect ensures different inputs produce different outputs
        assertFalse(
            "Different rhythms should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinTaps_Valid() {
        // Arrange - Exactly 4 taps (minimum)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1900)
        )
        
        // Act & Assert
        assertTrue("4 taps should be valid (minimum)", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_MaxTaps_Valid() {
        // Arrange - Exactly 6 taps (maximum) with varied intervals
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1300),  // 300ms interval
            RhythmTap(1700),  // 400ms interval
            RhythmTap(2300),  // 600ms interval
            RhythmTap(3100),  // 800ms interval
            RhythmTap(4000)   // 900ms interval
        )

        // Act & Assert
        assertTrue("6 taps should be valid (maximum)", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_TooFewTaps_Invalid() {
        // Arrange - Only 3 taps (below minimum)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600)
        )
        
        // Act & Assert
        assertFalse("3 taps should be invalid (below minimum)", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_TooManyTaps_Invalid() {
        // Arrange - 7 taps (above maximum)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1200),
            RhythmTap(1400),
            RhythmTap(1600),
            RhythmTap(1800),
            RhythmTap(2000),
            RhythmTap(2200)
        )
        
        // Act & Assert
        assertFalse("7 taps should be invalid (above maximum)", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_ZeroInterval_Invalid() {
        // Arrange - Two taps at same time (impossible)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1000),  // Same timestamp!
            RhythmTap(1250),
            RhythmTap(1600)
        )
        
        // Act & Assert
        assertFalse("Zero interval should be invalid", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_NegativeInterval_Invalid() {
        // Arrange - Timestamps go backwards (time travel!)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(900),   // Earlier than previous!
            RhythmTap(1250),
            RhythmTap(1600)
        )
        
        // Act & Assert
        assertFalse("Negative interval should be invalid", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_IntervalTooShort_Invalid() {
        // Arrange - 10ms interval (likely accidental double-tap)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1010),  // Only 10ms later
            RhythmTap(1250),
            RhythmTap(1600)
        )
        
        // Act & Assert
        assertFalse("Interval < 50ms should be invalid", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_IntervalTooLong_Invalid() {
        // Arrange - 4000ms interval (user fell asleep?)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(5000),  // 4 seconds later!
            RhythmTap(5250),
            RhythmTap(5600)
        )
        
        // Act & Assert
        assertFalse("Interval > 3000ms should be invalid", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testValidation_LowVariance_Invalid() {
        // Arrange - All intervals exactly the same (robotic)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1200),  // 200ms
            RhythmTap(1400),  // 200ms
            RhythmTap(1600)   // 200ms
        )
        
        // Act & Assert
        assertFalse(
            "Rhythm with no variance should be invalid (prevents trivial rhythms)",
            RhythmTapFactor.isValidTaps(taps)
        )
    }
    
    @Test
    fun testValidation_SufficientVariance_Valid() {
        // Arrange - Varied intervals (natural rhythm)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1150),  // 150ms
            RhythmTap(1450),  // 300ms
            RhythmTap(1650)   // 200ms
        )
        
        // Act & Assert
        assertTrue(
            "Rhythm with variance should be valid",
            RhythmTapFactor.isValidTaps(taps)
        )
    }
    
    // ==================== VERIFICATION TESTS ====================
    
    @Test
    fun testVerify_MatchingRhythm_ReturnsTrue() {
        // Arrange - Enroll with specific nonce
        val enrollmentTaps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val nonce = 12345L
        val storedDigest = RhythmTapFactor.digest(enrollmentTaps, nonce)
        
        // Act - Authenticate with same rhythm (different absolute times, same intervals)
        val authTaps = listOf(
            RhythmTap(5000),
            RhythmTap(5250),
            RhythmTap(5600),
            RhythmTap(5850)
        )
        val isValid = RhythmTapFactor.verify(authTaps, storedDigest, nonce)
        
        // Assert
        assertTrue("Matching rhythm should verify successfully", isValid)
    }
    
    @Test
    fun testVerify_DifferentRhythm_ReturnsFalse() {
        // Arrange - Enroll with one rhythm
        val enrollmentTaps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val nonce = 12345L
        val storedDigest = RhythmTapFactor.digest(enrollmentTaps, nonce)
        
        // Act - Try to authenticate with different rhythm
        val authTaps = listOf(
            RhythmTap(5000),
            RhythmTap(5500),  // Different intervals!
            RhythmTap(6000),
            RhythmTap(6500)
        )
        val isValid = RhythmTapFactor.verify(authTaps, storedDigest, nonce)
        
        // Assert
        assertFalse("Different rhythm should fail verification", isValid)
    }
    
    @Test
    fun testVerify_InvalidTaps_ReturnsFalse() {
        // Arrange - Valid stored digest
        val validTaps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val nonce = 12345L
        val storedDigest = RhythmTapFactor.digest(validTaps, nonce)
        
        // Act - Try to verify with invalid taps (too few)
        val invalidTaps = listOf(
            RhythmTap(5000),
            RhythmTap(5250)
        )
        val isValid = RhythmTapFactor.verify(invalidTaps, storedDigest, nonce)
        
        // Assert
        assertFalse("Invalid taps should fail verification gracefully", isValid)
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_ConstantTimeVerification() {
        // Arrange - Both with varied intervals
        val taps1 = listOf(
            RhythmTap(1000),
            RhythmTap(1300),  // 300ms interval
            RhythmTap(1800),  // 500ms interval
            RhythmTap(2500)   // 700ms interval
        )
        val taps2 = listOf(
            RhythmTap(1000),
            RhythmTap(1400),  // 400ms interval
            RhythmTap(2100),  // 700ms interval
            RhythmTap(3000)   // 900ms interval
        )
        val nonce = 12345L
        val digest1 = RhythmTapFactor.digest(taps1, nonce)

        // Act - Measure verification time for correct and incorrect rhythms
        val iterations = 1000

        val startCorrect = System.nanoTime()
        repeat(iterations) {
            RhythmTapFactor.verify(taps1, digest1, nonce)
        }
        val timeCorrect = System.nanoTime() - startCorrect

        val startIncorrect = System.nanoTime()
        repeat(iterations) {
            RhythmTapFactor.verify(taps2, digest1, nonce)
        }
        val timeIncorrect = System.nanoTime() - startIncorrect

        // Assert - Timing should be similar (within 30% variance for timing sensitivity)
        val ratio = timeCorrect.toDouble() / timeIncorrect.toDouble()
        assertTrue(
            "Verification should be constant-time (ratio: $ratio)",
            ratio in 0.7..1.3
        )
    }
    
    @Test
    fun testSecurity_ZeroKnowledgeCompliance() {
        // Arrange - Specific rhythm with known intervals
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1234),  // 234ms
            RhythmTap(1567),  // 333ms
            RhythmTap(1890)   // 323ms
        )
        val nonce = 12345L
        
        // Act
        val digest = RhythmTapFactor.digest(taps, nonce)
        val digestHex = digest.joinToString("") { "%02x".format(it) }
        
        // Assert - Digest should NOT reveal interval values
        assertFalse(
            "Digest should not contain '234' interval (zero-knowledge)",
            digestHex.contains("234")
        )
        assertFalse(
            "Digest should not contain '333' interval (zero-knowledge)",
            digestHex.contains("333")
        )
        assertFalse(
            "Digest should not contain '323' interval (zero-knowledge)",
            digestHex.contains("323")
        )
    }
    
    @Test
    fun testSecurity_ReplayProtection() {
        // Arrange - Same rhythm, different nonces
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val nonce1 = 11111L
        val nonce2 = 22222L
        
        // Act
        val digest1 = RhythmTapFactor.digest(taps, nonce1)
        val digest2 = RhythmTapFactor.digest(taps, nonce2)
        
        // Assert - Different nonces should produce different digests
        assertFalse(
            "Same rhythm with different nonces should produce different digests (replay protection)",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== UTILITY TESTS ====================
    
    @Test
    fun testUtility_GetRecommendedDuration() {
        // Act
        val duration = RhythmTapFactor.getRecommendedDurationMs()

        // Assert
        assertEquals("Recommended duration should be 15 seconds", 15_000L, duration)
    }
    
    @Test
    fun testUtility_GetMinMaxTaps() {
        // Act & Assert
        assertEquals(4, RhythmTapFactor.getMinTaps())
        assertEquals(6, RhythmTapFactor.getMaxTaps())
    }
    
    @Test
    fun testUtility_EstimateEntropy() {
        // Arrange - 4-tap rhythm
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        
        // Act
        val entropy = RhythmTapFactor.estimateEntropy(taps)
        
        // Assert - 3 intervals * ~4.3 bits each = ~13 bits
        assertTrue("Entropy should be reasonable (10-20 bits)", entropy in 10.0..20.0)
    }
    
    // ==================== EDGE CASES ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testEdgeCase_EmptyTaps_ThrowsException() {
        // Arrange
        val emptyTaps = emptyList<RhythmTap>()
        
        // Act - Should throw exception
        RhythmTapFactor.digest(emptyTaps)
    }
    
    @Test
    fun testEdgeCase_ExactlyAtBoundaries_Valid() {
        // Arrange - Intervals exactly at min/max boundaries
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1050),   // Exactly 50ms (minimum)
            RhythmTap(4050),   // Exactly 3000ms (maximum)
            RhythmTap(4150)    // 100ms
        )
        
        // Act & Assert
        assertTrue("Boundary intervals should be valid", RhythmTapFactor.isValidTaps(taps))
    }
    
    @Test
    fun testEdgeCase_VeryFastTapping_Valid() {
        // Arrange - Fast but valid rhythm (gaming-style tapping)
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1100),  // 100ms
            RhythmTap(1180),  // 80ms
            RhythmTap(1260)   // 80ms
        )
        
        // Act & Assert
        assertTrue("Fast tapping should be valid if above 50ms", RhythmTapFactor.isValidTaps(taps))
    }
    
    // ==================== PERFORMANCE TESTS ====================
    
    @Test
    fun testPerformance_DigestGeneration() {
        // Arrange
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        
        // Act - Measure time for 1000 digest generations
        val startTime = System.currentTimeMillis()
        repeat(1000) {
            RhythmTapFactor.digest(taps)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Assert - Should complete 1000 digests in under 1 second
        assertTrue(
            "1000 digests should complete in < 1 second (actual: ${elapsedTime}ms)",
            elapsedTime < 1000
        )
    }
    
    @Test
    fun testPerformance_Validation() {
        // Arrange
        val validTaps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        
        // Act - Measure time for 10000 validations
        val startTime = System.currentTimeMillis()
        repeat(10_000) {
            RhythmTapFactor.isValidTaps(validTaps)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Assert - Should complete 10000 validations in under 500ms
        assertTrue(
            "10000 validations should complete in < 500ms (actual: ${elapsedTime}ms)",
            elapsedTime < 500
        )
    }
    
    // ==================== THREAD SAFETY TESTS ====================
    
    @Test
    fun testThreadSafety_ConcurrentDigestGeneration() {
        // Arrange
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250),
            RhythmTap(1600),
            RhythmTap(1850)
        )
        val results = mutableListOf<ByteArray>()
        val lock = Any()
        
        // Act - Multiple threads generating digests simultaneously
        val threads = (1..10).map {
            Thread {
                val digest = RhythmTapFactor.digest(taps)
                synchronized(lock) {
                    results.add(digest)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) } // 5 second timeout
        
        // Assert
        assertEquals("All threads should complete", 10, results.size)
        results.forEach { digest ->
            assertEquals("All digests should be 32 bytes", 32, digest.size)
        }
    }
    
    // ==================== EXCEPTION HANDLING TESTS ====================
    
    @Test(expected = RhythmTapFactor.RhythmInputException::class)
    fun testException_ValidateOrThrow_TooFewTaps() {
        // Arrange
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250)
        )
        
        // Act - Should throw exception
        RhythmTapFactor.validateOrThrow(taps)
    }
    
    @Test(expected = RhythmTapFactor.RhythmInputException::class)
    fun testException_ValidateOrThrow_InvalidInterval() {
        // Arrange - Interval too short
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1010),  // Only 10ms
            RhythmTap(1250),
            RhythmTap(1600)
        )
        
        // Act - Should throw exception
        RhythmTapFactor.validateOrThrow(taps)
    }
    
    @Test
    fun testException_CustomExceptionMessage() {
        // Arrange
        val taps = listOf(
            RhythmTap(1000),
            RhythmTap(1250)
        )
        
        try {
            // Act
            RhythmTapFactor.validateOrThrow(taps)
            fail("Should have thrown exception")
        } catch (e: RhythmTapFactor.RhythmInputException) {
            // Assert - Check error message is informative
            assertTrue(
                "Error message should mention tap count",
                e.message?.contains("tap count") == true
            )
        }
    }
}
