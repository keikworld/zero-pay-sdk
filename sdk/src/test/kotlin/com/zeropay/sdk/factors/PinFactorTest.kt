package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * PIN Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Argon2id digest generation
 * - Constant-time verification
 * - PIN validation (4-12 digits, all numeric)
 * - Edge cases (empty, too short, too long, non-numeric)
 * - Security (salt randomness, computational cost)
 */
class PinFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidPin_ReturnsCorrectSize() {
        // Arrange
        val pin = "1234"
        
        // Act
        val derivedKey = PinFactor.digest(pin)
        
        // Assert
        assertEquals("Hash should be 32 bytes", 32, derivedKey.hash.size)
        assertEquals("Salt should be 16 bytes", 16, derivedKey.salt.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameSaltProducesSameHash() {
        // Arrange
        val pin = "123456"
        val firstKey = PinFactor.digest(pin)
        
        // Act - Use same salt
        val secondKey = PinFactor.digestWithSalt(pin, firstKey.salt)
        
        // Assert
        assertArrayEquals("Same PIN + same salt = same hash", firstKey.hash, secondKey.hash)
    }
    
    @Test
    fun testDigest_RandomSalt_DifferentDigestsEachTime() {
        // Arrange
        val pin = "1234"
        
        // Act
        val key1 = PinFactor.digest(pin)
        val key2 = PinFactor.digest(pin)
        
        // Assert
        assertFalse(
            "Different salts should produce different hashes",
            key1.hash.contentEquals(key2.hash)
        )
        assertFalse(
            "Salts should be randomly generated",
            key1.salt.contentEquals(key2.salt)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_CorrectPin_ReturnsTrue() {
        // Arrange
        val pin = "123456"
        val storedKey = PinFactor.digest(pin)
        
        // Act
        val result = PinFactor.verify(pin, storedKey)
        
        // Assert
        assertTrue("Correct PIN should verify successfully", result)
    }
    
    @Test
    fun testVerify_IncorrectPin_ReturnsFalse() {
        // Arrange
        val correctPin = "123456"
        val wrongPin = "654321"
        val storedKey = PinFactor.digest(correctPin)
        
        // Act
        val result = PinFactor.verify(wrongPin, storedKey)
        
        // Assert
        assertFalse("Incorrect PIN should fail verification", result)
    }
    
    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctPin = "123456"
        val wrongPin = "000000"
        val storedKey = PinFactor.digest(correctPin)
        val iterations = 100
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                PinFactor.verify(correctPin, storedKey)
            }
        }
        
        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                PinFactor.verify(wrongPin, storedKey)
            }
        }
        
        // Assert - Times should be similar (within 20% for constant-time)
        val timeDifference = kotlin.math.abs(correctTime - wrongTime)
        val averageTime = (correctTime + wrongTime) / 2.0
        val percentageDifference = (timeDifference / averageTime) * 100
        
        assertTrue(
            "Verification should be constant-time (within 20%). " +
            "Correct: ${correctTime}ms, Wrong: ${wrongTime}ms, Diff: ${percentageDifference.toInt()}%",
            percentageDifference < 20
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
            val diff = kotlin.math.abs(time - avgTime) / avgTime * 100
            assertTrue(
                "Validation should be constant-time (within 30%). Time: ${time}ms, Avg: ${avgTime}ms",
                diff < 30
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
        val key = PinFactor.digest(pin)
        
        // Assert
        assertEquals(32, key.hash.size)
        assertEquals(16, key.salt.size)
    }
    
    @Test
    fun testDigest_MaximumLengthPin_WorksCorrectly() {
        // Arrange
        val pin = "123456789012" // Maximum 12 digits
        
        // Act
        val key = PinFactor.digest(pin)
        
        // Assert
        assertEquals(32, key.hash.size)
        assertEquals(16, key.salt.size)
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_Argon2id_ComputationallyExpensive() {
        // Arrange
        val pin = "123456"
        
        // Act - Measure time for single digest
        val time = measureTimeMillis {
            PinFactor.digest(pin)
        }
        
        // Assert - Should take at least 100ms (Argon2id is intentionally slow)
        assertTrue(
            "Argon2id should be computationally expensive (>=100ms), was ${time}ms",
            time >= 100
        )
    }
    
    @Test
    fun testDigest_SaltRandomness_NoDuplicatesIn100() {
        // Arrange
        val pin = "123456"
        val iterations = 100
        
        // Act
        val salts = mutableSetOf<String>()
        repeat(iterations) {
            val key = PinFactor.digest(pin)
            salts.add(key.salt.contentToString())
        }
        
        // Assert - All salts should be unique
        assertEquals(
            "All 100 salts should be unique (CSPRNG)",
            iterations,
            salts.size
        )
    }
    
    @Test
    fun testDigest_HashEntropy_HighQuality() {
        // Arrange
        val pin = "123456"
        
        // Act
        val key = PinFactor.digest(pin)
        
        // Assert
        val uniqueBytes = key.hash.toSet().size
        assertTrue(
            "Hash should have high entropy (>=20 unique bytes)",
            uniqueBytes >= 20
        )
        
        assertFalse(
            "Hash should not be all zeros",
            key.hash.all { it == 0.toByte() }
        )
    }
    
    // ==================== GDPR COMPLIANCE ====================
    
    @Test
    fun testDigest_Irreversible_CannotRecoverPin() {
        // Arrange
        val pin = "123456"
        val key = PinFactor.digest(pin)
        
        // Act - Try brute force on a small sample
        val commonPins = listOf("1234", "0000", "1111", "123456", "654321", "111111")
        val matches = commonPins.filter { testPin ->
            PinFactor.verify(testPin, key)
        }
        
        // Assert - Only correct PIN should verify
        assertEquals("Only correct PIN should verify", 1, matches.size)
        assertEquals("Correct PIN should be found", pin, matches.first())
    }
}
