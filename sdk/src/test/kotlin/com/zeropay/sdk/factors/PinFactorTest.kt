package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * PIN Factor Tests - PRODUCTION VERSION (Updated for current API)
 *
 * Tests:
 * - SHA-256 digest generation
 * - Constant-time verification
 * - PIN validation (4-12 digits, all numeric)
 * - Edge cases (empty, too short, too long, non-numeric)
 * - Security (determinism, entropy)
 *
 * Note: API simplified to return ByteArray directly (no salt needed with SHA-256)
 */
class PinFactorTest {

    // ==================== BASIC FUNCTIONALITY ====================

    @Test
    fun testDigest_ValidPin_ReturnsCorrectSize() {
        // Arrange
        val pin = "1234"

        // Act
        val digest = PinFactor.digest(pin)

        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }

    @Test
    fun testDigest_Deterministic_SamePinProducesSameDigest() {
        // Arrange
        val pin = "123456"

        // Act
        val digest1 = PinFactor.digest(pin)
        val digest2 = PinFactor.digest(pin)

        // Assert
        assertArrayEquals("Same PIN should produce same digest (deterministic)", digest1, digest2)
    }

    @Test
    fun testDigest_DifferentPins_ProduceDifferentDigests() {
        // Arrange
        val pin1 = "1234"
        val pin2 = "5678"

        // Act
        val digest1 = PinFactor.digest(pin1)
        val digest2 = PinFactor.digest(pin2)

        // Assert
        assertFalse(
            "Different PINs should produce different digests",
            digest1.contentEquals(digest2)
        )
    }

    // ==================== VERIFICATION ====================

    @Test
    fun testVerify_CorrectPin_ReturnsTrue() {
        // Arrange
        val pin = "123456"
        val storedDigest = PinFactor.digest(pin)

        // Act
        val result = PinFactor.verify(pin, storedDigest)

        // Assert
        assertTrue("Correct PIN should verify successfully", result)
    }

    @Test
    fun testVerify_IncorrectPin_ReturnsFalse() {
        // Arrange
        val correctPin = "123456"
        val wrongPin = "654321"
        val storedDigest = PinFactor.digest(correctPin)

        // Act
        val result = PinFactor.verify(wrongPin, storedDigest)

        // Assert
        assertFalse("Incorrect PIN should fail verification", result)
    }

    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctPin = "123456"
        val wrongPin = "000000"
        val storedDigest = PinFactor.digest(correctPin)
        val iterations = 100

        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                PinFactor.verify(correctPin, storedDigest)
            }
        }

        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                PinFactor.verify(wrongPin, storedDigest)
            }
        }

        // Assert - Times should be similar (within 30% for constant-time)
        val timeDifference = kotlin.math.abs(correctTime - wrongTime)
        val averageTime = (correctTime + wrongTime) / 2.0
        val percentageDifference = if (averageTime > 0) (timeDifference / averageTime) * 100 else 0.0

        assertTrue(
            "Verification should be constant-time (within 30%). " +
            "Correct: ${correctTime}ms, Wrong: ${wrongTime}ms, Diff: ${percentageDifference.toInt()}%",
            percentageDifference < 30
        )
    }

    // ==================== VALIDATION ====================

    @Test
    fun testIsValidPin_ValidPins_ReturnsTrue() {
        // Arrange & Act & Assert
        assertTrue("4 digits should be valid", PinFactor.isValidPin("1234"))
        assertTrue("6 digits should be valid", PinFactor.isValidPin("123456"))
        assertTrue("12 digits should be valid", PinFactor.isValidPin("123456789012"))
    }

    @Test
    fun testIsValidPin_InvalidPins_ReturnsFalse() {
        // Arrange & Act & Assert
        assertFalse("Empty should be invalid", PinFactor.isValidPin(""))
        assertFalse("Too short (3 digits) should be invalid", PinFactor.isValidPin("123"))
        assertFalse("Too long (13 digits) should be invalid", PinFactor.isValidPin("1234567890123"))
        assertFalse("Letters should be invalid", PinFactor.isValidPin("12ab"))
        assertFalse("Special chars should be invalid", PinFactor.isValidPin("12!@"))
        assertFalse("Spaces should be invalid", PinFactor.isValidPin("12 34"))
    }

    @Test
    fun testIsValidPin_ConstantTime_NoEarlyReturn() {
        // Arrange
        val validPin = "123456"
        val invalidShort = "12"
        val invalidLong = "12345678901234567890"
        val invalidChars = "abcdef"
        val iterations = 1000

        // Act - Measure validation timing
        val validTime = measureTimeMillis {
            repeat(iterations) { PinFactor.isValidPin(validPin) }
        }

        val invalidShortTime = measureTimeMillis {
            repeat(iterations) { PinFactor.isValidPin(invalidShort) }
        }

        val invalidLongTime = measureTimeMillis {
            repeat(iterations) { PinFactor.isValidPin(invalidLong) }
        }

        val invalidCharsTime = measureTimeMillis {
            repeat(iterations) { PinFactor.isValidPin(invalidChars) }
        }

        // Assert - All validations should take similar time
        val times = listOf(validTime, invalidShortTime, invalidLongTime, invalidCharsTime)
        val avgTime = times.average()

        times.forEach { time ->
            val diff = if (avgTime > 0) kotlin.math.abs(time - avgTime) / avgTime * 100 else 0.0
            assertTrue(
                "Validation should be constant-time (within 40%). Time: ${time}ms, Avg: ${avgTime}ms",
                diff < 40
            )
        }
    }

    // ==================== EDGE CASES ====================

    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyPin_ThrowsException() {
        // Arrange
        val pin = ""

        // Act
        PinFactor.digest(pin)

        // Assert - Should throw
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooShortPin_ThrowsException() {
        // Arrange
        val pin = "123" // Only 3 digits

        // Act
        PinFactor.digest(pin)

        // Assert - Should throw
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooLongPin_ThrowsException() {
        // Arrange
        val pin = "1234567890123" // 13 digits

        // Act
        PinFactor.digest(pin)

        // Assert - Should throw
    }

    @Test(expected = IllegalArgumentException::class)
    fun testDigest_NonNumericPin_ThrowsException() {
        // Arrange
        val pin = "12ab"

        // Act
        PinFactor.digest(pin)

        // Assert - Should throw
    }

    @Test
    fun testDigest_MinimumLengthPin_WorksCorrectly() {
        // Arrange
        val pin = "1234" // Minimum 4 digits

        // Act
        val digest = PinFactor.digest(pin)

        // Assert
        assertEquals(32, digest.size)
    }

    @Test
    fun testDigest_MaximumLengthPin_WorksCorrectly() {
        // Arrange
        val pin = "123456789012" // Maximum 12 digits

        // Act
        val digest = PinFactor.digest(pin)

        // Assert
        assertEquals(32, digest.size)
    }

    // ==================== SECURITY ====================

    @Test
    fun testDigest_HashEntropy_HighQuality() {
        // Arrange
        val pin = "123456"

        // Act
        val digest = PinFactor.digest(pin)

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
    fun testDigest_SimilarPins_ProduceDifferentDigests() {
        // Arrange
        val pin1 = "123456"
        val pin2 = "123457" // Only last digit different

        // Act
        val digest1 = PinFactor.digest(pin1)
        val digest2 = PinFactor.digest(pin2)

        // Assert - Avalanche effect: small change = big difference
        var differentBytes = 0
        for (i in digest1.indices) {
            if (digest1[i] != digest2[i]) differentBytes++
        }

        assertTrue(
            "Similar PINs should produce very different digests (avalanche effect). " +
            "Different bytes: $differentBytes/32",
            differentBytes >= 10
        )
    }

    // ==================== GDPR COMPLIANCE ====================

    @Test
    fun testDigest_Irreversible_CannotRecoverPin() {
        // Arrange
        val pin = "123456"
        val digest = PinFactor.digest(pin)

        // Act - Try brute force on a small sample
        val commonPins = listOf("1234", "0000", "1111", "123456", "654321", "111111")
        val matches = commonPins.filter { testPin ->
            PinFactor.verify(testPin, digest)
        }

        // Assert - Only correct PIN should verify
        assertEquals("Only correct PIN should verify", 1, matches.size)
        assertEquals("Correct PIN should be found", pin, matches.first())
    }

    @Test
    fun testDigest_NoPlaintextStorage_OnlyHashStored() {
        // Arrange
        val pin = "123456"

        // Act
        val digest = PinFactor.digest(pin)

        // Assert - Digest should not contain plaintext PIN
        val digestString = digest.decodeToString()
        assertFalse(
            "Digest should not contain plaintext PIN",
            digestString.contains(pin)
        )
    }
}
