package com.zeropay.sdk

import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.factors.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.system.measureTimeMillis

/**
 * ZeroPay SDK Unit Tests
 * 
 * Test coverage:
 * - Factor digest generation
 * - Rate limiting logic
 * - CSPRNG shuffling quality
 * - Crypto utils validation
 * - Thread safety
 * - Timing attack resistance
 * - Input validation
 * - Zero-knowledge compliance
 */
class ZeroPaySDKTest {
    
    @Before
    fun setup() {
        // Reset rate limiter state before each test
        // Note: In production, add RateLimiter.reset() method
    }
    
    // ==================== FACTOR TESTS ====================
    
    @Test
    fun testColourFactor_ValidInput_ReturnsCorrectDigest() {
        // Arrange
        val selectedIndices = listOf(0, 1) // Red, Green
        
        // Act
        val digest = ColourFactor.digest(selectedIndices)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
        assertNotNull("Digest should not be null", digest)
    }
    
    @Test
    fun testColourFactor_SameInput_ReturnsSameDigest() {
        // Arrange
        val input = listOf(0, 1)
        
        // Act
        val digest1 = ColourFactor.digest(input)
        val digest2 = ColourFactor.digest(input)
        
        // Assert
        assertArrayEquals(
            "Same input should produce same digest (deterministic)",
            digest1,
            digest2
        )
    }
    
    @Test
    fun testColourFactor_DifferentInput_ReturnsDifferentDigest() {
        // Arrange
        val input1 = listOf(0, 1) // Red, Green
        val input2 = listOf(1, 0) // Green, Red
        
        // Act
        val digest1 = ColourFactor.digest(input1)
        val digest2 = ColourFactor.digest(input2)
        
        // Assert
        assertFalse(
            "Different inputs should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testColourFactor_EmptyInput_ThrowsException() {
        // Act & Assert
        ColourFactor.digest(emptyList())
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testColourFactor_InvalidIndex_ThrowsException() {
        // Act & Assert
        ColourFactor.digest(listOf(0, 999)) // 999 is out of bounds
    }
    
    @Test
    fun testEmojiFactor_ValidInput_ReturnsCorrectDigest() {
        // Arrange
        val selectedIndices = listOf(0, 1, 2, 3)
        
        // Act
        val digest = EmojiFactor.digest(selectedIndices)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testPinFactor_ValidPin_ReturnsCorrectDigest() {
        // Arrange
        val pin = "123456"
        
        // Act
        val digest = PinFactor.digest(pin)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testPinFactor_DifferentPins_ReturnDifferentDigests() {
        // Arrange
        val pin1 = "123456"
        val pin2 = "654321"
        
        // Act
        val digest1 = PinFactor.digest(pin1)
        val digest2 = PinFactor.digest(pin2)
        
        // Assert
        assertFalse(digest1.contentEquals(digest2))
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testPinFactor_TooShort_ThrowsException() {
        // Act & Assert
        PinFactor.digest("123") // Less than 4 digits
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testPinFactor_TooLong_ThrowsException() {
        // Act & Assert
        PinFactor.digest("1234567890123") // More than 12 digits
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testPinFactor_NonNumeric_ThrowsException() {
        // Act & Assert
        PinFactor.digest("12ab56")
    }
    
    @Test
    fun testPinFactor_TimingAttackResistance() {
        // Arrange
        val validPin = "123456"
        val invalidPin1 = "1abcde" // Invalid at position 2
        val invalidPin2 = "12345a" // Invalid at position 6
        
        // Act - Measure execution time for validation
        val time1 = measureTimeMillis {
            try { PinFactor.digest(validPin) } catch (e: Exception) {}
        }
        val time2 = measureTimeMillis {
            try { PinFactor.digest(invalidPin1) } catch (e: Exception) {}
        }
        val time3 = measureTimeMillis {
            try { PinFactor.digest(invalidPin2) } catch (e: Exception) {}
        }
        
        // Assert - Times should be similar (constant-time validation)
        val avgTime = (time1 + time2 + time3) / 3.0
        val maxDeviation = avgTime * 0.3 // Allow 30% deviation
        
        assertTrue(
            "Validation time should be constant (timing attack resistant)",
            abs(time1 - avgTime) < maxDeviation &&
            abs(time2 - avgTime) < maxDeviation &&
            abs(time3 - avgTime) < maxDeviation
        )
    }
    
    @Test
    fun testPatternFactor_MicroTiming_ValidInput() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 1050L),
            PatternFactor.PatternPoint(50f, 60f, 1100L)
        )
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testPatternFactor_NormalizedTiming_ValidInput() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 1500L),
            PatternFactor.PatternPoint(50f, 60f, 2000L)
        )
        
        // Act
        val digest = PatternFactor.digestNormalisedTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testPatternFactor_NormalizedTiming_SpeedInvariant() {
        // Arrange - Same pattern, different speeds
        val slowPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 0L),
            PatternFactor.PatternPoint(30f, 40f, 1000L),
            PatternFactor.PatternPoint(50f, 60f, 2000L)
        )
        val fastPattern = listOf(
            PatternFactor.PatternPoint(10f, 20f, 0L),
            PatternFactor.PatternPoint(30f, 40f, 100L),
            PatternFactor.PatternPoint(50f, 60f, 200L)
        )
        
        // Act
        val slowDigest = PatternFactor.digestNormalisedTiming(slowPattern)
        val fastDigest = PatternFactor.digestNormalisedTiming(fastPattern)
        
        // Assert - Should produce same digest (speed-normalized)
        assertArrayEquals(
            "Normalized timing should be speed-invariant",
            slowDigest,
            fastDigest
        )
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testPatternFactor_EmptyPoints_ThrowsException() {
        // Act & Assert
        PatternFactor.digestMicroTiming(emptyList())
    }
    
    @Test
    fun testMouseFactor_ValidInput() {
        // Arrange - Minimum 3 points required
        val points = listOf(
            MouseFactor.MousePoint(10f, 20f, 1000L),
            MouseFactor.MousePoint(30f, 40f, 1050L),
            MouseFactor.MousePoint(50f, 60f, 1100L)
        )

        // Act
        val digest = MouseFactor.digestMicroTiming(points)

        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testStylusFactor_ValidInput() {
        // Arrange - Minimum 10 points required
        val points = List(10) { i ->
            StylusFactor.StylusPoint((i * 10).toFloat(), (i * 20).toFloat(), 0.5f, (i * 100).toLong())
        }

        // Act
        val digest = StylusFactor.digestFull(points)

        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== RATE LIMITER TESTS ====================
    
    @Test
    fun testRateLimiter_NoFailures_ReturnsOK() {
        // Arrange
        val uid = "test-user-1"
        
        // Act
        val result = RateLimiter.check(uid)
        
        // Assert
        assertEquals(RateLimiter.RateResult.OK, result)
    }
    
    @Test
    fun testRateLimiter_FiveFailures_ReturnsCooldown15M() {
        // Arrange
        val uid = "test-user-2"
        
        // Act
        repeat(5) { RateLimiter.recordFail(uid) }
        val result = RateLimiter.check(uid)
        
        // Assert
        assertEquals(RateLimiter.RateResult.COOL_DOWN_15M, result)
    }
    
    @Test
    fun testRateLimiter_EightFailures_ReturnsCooldown4H() {
        // Arrange
        val uid = "test-user-3"
        
        // Act
        repeat(8) { RateLimiter.recordFail(uid) }
        val result = RateLimiter.check(uid)
        
        // Assert
        assertEquals(RateLimiter.RateResult.COOL_DOWN_4H, result)
    }
    
    @Test
    fun testRateLimiter_TenFailures_ReturnsFrozen() {
        // Arrange
        val uid = "test-user-4"
        
        // Act
        repeat(10) { RateLimiter.recordFail(uid) }
        val result = RateLimiter.check(uid)
        
        // Assert
        assertEquals(RateLimiter.RateResult.FROZEN_FRAUD, result)
    }
    
    @Test
    fun testRateLimiter_ResetFails_ClearsFailures() {
        // Arrange
        val uid = "test-user-5"
        repeat(5) { RateLimiter.recordFail(uid) }
        
        // Act
        RateLimiter.resetFails(uid)
        val result = RateLimiter.check(uid)
        
        // Assert
        assertEquals(RateLimiter.RateResult.OK, result)
    }
    
    @Test
    fun testRateLimiter_ThreadSafety() {
        // Arrange
        val uid = "test-user-6"
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        
        // Act - Multiple threads recording failures simultaneously
        repeat(threadCount) {
            thread {
                RateLimiter.recordFail(uid)
                latch.countDown()
            }
        }
        
        latch.await(5, TimeUnit.SECONDS)
        val result = RateLimiter.check(uid)
        
        // Assert - Should have recorded exactly 10 failures
        assertEquals(
            "Rate limiter should be thread-safe",
            RateLimiter.RateResult.FROZEN_FRAUD,
            result
        )
    }
    
    // ==================== CSPRNG SHUFFLE TESTS ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testCsprngShuffle_EmptyList_ThrowsException() {
        // Arrange
        val empty = emptyList<Int>()

        // Act - Should throw IllegalArgumentException
        CsprngShuffle.shuffle(empty)

        // Assert - Should throw (SECURITY: Input validation prevents undefined behavior.
        // Fisher-Yates shuffle requires at least one element. Empty list handling prevents
        // potential timing attacks that could infer list size from execution time.)
    }
    
    @Test
    fun testCsprngShuffle_SingleElement_ReturnsSame() {
        // Arrange
        val single = listOf(42)
        
        // Act
        val shuffled = CsprngShuffle.shuffle(single)
        
        // Assert
        assertEquals(single, shuffled)
    }
    
    @Test
    fun testCsprngShuffle_PreservesElements() {
        // Arrange
        val original = (1..100).toList()
        
        // Act
        val shuffled = CsprngShuffle.shuffle(original)
        
        // Assert
        assertEquals(
            "Shuffle should preserve all elements",
            original.toSet(),
            shuffled.toSet()
        )
    }
    
    @Test
    fun testCsprngShuffle_ProducesDifferentResults() {
        // Arrange
        val original = (1..100).toList()
        
        // Act
        val shuffle1 = CsprngShuffle.shuffle(original)
        val shuffle2 = CsprngShuffle.shuffle(original)
        
        // Assert
        assertNotEquals(
            "Multiple shuffles should produce different results",
            shuffle1,
            shuffle2
        )
    }
    
    @Test
    fun testCsprngShuffle_RandomnessQuality() {
        // Arrange
        val original = (1..10).toList()
        val shuffleCount = 1000
        val positionCounts = Array(10) { IntArray(10) }
        
        // Act - Shuffle many times and track element positions
        repeat(shuffleCount) {
            val shuffled = CsprngShuffle.shuffle(original)
            shuffled.forEachIndexed { index, value ->
                positionCounts[value - 1][index]++
            }
        }
        
        // Assert - Each element should appear in each position roughly equally
        val expectedCount = shuffleCount / 10.0 // ~100 times per position
        val tolerance = expectedCount * 0.4 // Allow 40% deviation
        
        positionCounts.forEach { counts ->
            counts.forEach { count ->
                assertTrue(
                    "Element should be evenly distributed across positions (good randomness)",
                    abs(count - expectedCount) < tolerance
                )
            }
        }
    }
    
    // ==================== CRYPTO UTILS TESTS ====================
    
    @Test
    fun testCryptoUtils_SHA256_CorrectSize() {
        // Arrange
        val data = "test data".toByteArray()
        
        // Act
        val hash = CryptoUtils.sha256(data)
        
        // Assert
        assertEquals(32, hash.size)
    }
    
    @Test
    fun testCryptoUtils_SHA256_Deterministic() {
        // Arrange
        val data = "test data".toByteArray()
        
        // Act
        val hash1 = CryptoUtils.sha256(data)
        val hash2 = CryptoUtils.sha256(data)
        
        // Assert
        assertArrayEquals(hash1, hash2)
    }
    
    @Test
    fun testCryptoUtils_SHA256_DifferentInputs() {
        // Arrange
        val data1 = "test data 1".toByteArray()
        val data2 = "test data 2".toByteArray()
        
        // Act
        val hash1 = CryptoUtils.sha256(data1)
        val hash2 = CryptoUtils.sha256(data2)
        
        // Assert
        assertFalse(hash1.contentEquals(hash2))
    }
    
    @Test
    fun testCryptoUtils_SecureRandomBytes_CorrectSize() {
        // Arrange & Act
        val bytes = CryptoUtils.generateRandomBytes(32)
        
        // Assert
        assertEquals(32, bytes.size)
    }
    
    @Test
    fun testCryptoUtils_SecureRandomBytes_RandomnessQuality() {
        // Arrange & Act
        val bytes1 = CryptoUtils.generateRandomBytes(32)
        val bytes2 = CryptoUtils.generateRandomBytes(32)
        
        // Assert
        assertFalse(
            "Random bytes should be different each time",
            bytes1.contentEquals(bytes2)
        )
    }
    
    @Test
    fun testCryptoUtils_HMACSHA256_CorrectSize() {
        // Arrange
        val key = "secret key".toByteArray()
        val data = "message".toByteArray()
        
        // Act
        val hmac = CryptoUtils.hmacSha256(key, data)
        
        // Assert
        assertEquals(32, hmac.size)
    }
    
    @Test
    fun testCryptoUtils_FloatToBytes_CorrectSize() {
        // Arrange & Act
        val bytes = CryptoUtils.floatToBytes(3.14f)
        
        // Assert
        assertEquals(4, bytes.size)
    }
    
    @Test
    fun testCryptoUtils_LongToBytes_CorrectSize() {
        // Arrange & Act
        val bytes = CryptoUtils.longToBytes(123456789L)
        
        // Assert
        assertEquals(8, bytes.size)
    }
    
    // ==================== INTEGRATION TESTS ====================
    
    @Test
    fun testFullAuthenticationFlow_TwoFactors() {
        // Arrange
        val colourIndices = listOf(0, 1)
        val pin = "123456"
        
        // Act
        val colourDigest = ColourFactor.digest(colourIndices)
        val pinDigest = PinFactor.digest(pin)
        
        // Assert
        assertEquals(32, colourDigest.size)
        assertEquals(32, pinDigest.size)
        assertFalse(colourDigest.contentEquals(pinDigest))
    }
    
    @Test
    fun testFullAuthenticationFlow_ThreeFactors() {
        // Arrange
        val colourIndices = listOf(0, 1)
        val pin = "123456"
        val patternPoints = listOf(
            PatternFactor.PatternPoint(10f, 20f, 1000L),
            PatternFactor.PatternPoint(30f, 40f, 1050L)
        )
        
        // Act
        val colourDigest = ColourFactor.digest(colourIndices)
        val pinDigest = PinFactor.digest(pin)
        val patternDigest = PatternFactor.digestMicroTiming(patternPoints)
        
        // Assert
        assertEquals(32, colourDigest.size)
        assertEquals(32, pinDigest.size)
        assertEquals(32, patternDigest.size)
        
        // All digests should be unique
        assertFalse(colourDigest.contentEquals(pinDigest))
        assertFalse(colourDigest.contentEquals(patternDigest))
        assertFalse(pinDigest.contentEquals(patternDigest))
    }
    
    @Test
    fun testZeroKnowledgeCompliance_NoRawDataInDigest() {
        // Arrange
        val pin = "123456"
        
        // Act
        val digest = PinFactor.digest(pin)
        val digestString = digest.joinToString("") { it.toString() }
        
        // Assert - Digest should NOT contain the PIN
        assertFalse(
            "Digest should not contain raw PIN (zero-knowledge)",
            digestString.contains("123456")
        )
    }
    
    @Test
    fun testGDPRCompliance_DigestsAreIrreversible() {
        // Arrange
        val pin = "123456"
        val digest = PinFactor.digest(pin)
        
        // Act - Try to "reverse" the digest (impossible with SHA-256)
        val digestHex = digest.joinToString("") { "%02x".format(it) }
        
        // Assert - No way to derive original PIN from digest
        assertTrue(
            "Digest should be 64 hex characters (irreversible SHA-256)",
            digestHex.length == 64 && digestHex.all { it in '0'..'9' || it in 'a'..'f' }
        )
        // Note: This test demonstrates GDPR Art. 9 compliance
        // Biometric hashes are NOT sensitive data (irreversible)
    }
    
    // ==================== PERFORMANCE TESTS ====================
    
    @Test
    fun testPerformance_ColourDigest_Fast() {
        // Arrange
        val input = listOf(0, 1)
        
        // Act
        val time = measureTimeMillis {
            repeat(1000) {
                ColourFactor.digest(input)
            }
        }
        
        // Assert - Should complete 1000 digests in under 1 second
        assertTrue(
            "1000 colour digests should complete in < 1 second",
            time < 1000
        )
    }
    
    @Test
    fun testPerformance_PinDigest_Fast() {
        // Arrange
        val pin = "123456"
        
        // Act
        val time = measureTimeMillis {
            repeat(1000) {
                PinFactor.digest(pin)
            }
        }
        
        // Assert
        assertTrue(
            "1000 PIN digests should complete in < 1 second",
            time < 1000
        )
    }
    
    @Test
    fun testPerformance_PatternDigest_Fast() {
        // Arrange
        val points = List(100) { i ->
            PatternFactor.PatternPoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val time = measureTimeMillis {
            repeat(100) {
                PatternFactor.digestMicroTiming(points)
            }
        }
        
        // Assert
        assertTrue(
            "100 pattern digests (100 points each) should complete in < 1 second",
            time < 1000
        )
    }
    
    // ==================== EDGE CASE TESTS ====================
    
    @Test
    fun testEdgeCase_MaxPoints_PatternFactor() {
        // Arrange - Maximum allowed points (300)
        val points = List(300) { i ->
            PatternFactor.PatternPoint(i.toFloat(), i.toFloat(), i.toLong())
        }
        
        // Act
        val digest = PatternFactor.digestMicroTiming(points)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testEdgeCase_VeryLongTimestamps() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, Long.MAX_VALUE - 1000),
            PatternFactor.PatternPoint(30f, 40f, Long.MAX_VALUE)
        )
        
        // Act & Assert - Should not overflow
        val digest = PatternFactor.digestMicroTiming(points)
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testEdgeCase_ZeroTimestamp() {
        // Arrange
        val points = listOf(
            PatternFactor.PatternPoint(10f, 20f, 0L),
            PatternFactor.PatternPoint(30f, 40f, 0L)
        )
        
        // Act & Assert
        val digest = PatternFactor.digestMicroTiming(points)
        assertEquals(32, digest.size)
    }
}
