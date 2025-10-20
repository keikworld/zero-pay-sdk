package com.zeropay.sdk.security

import com.zeropay.sdk.crypto.ConstantTime
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * CryptoUtils Tests - COMPREHENSIVE VERSION
 *
 * Tests critical cryptographic functions:
 * - SHA-256 hashing
 * - PBKDF2 key derivation
 * - Constant-time comparison
 * - Random byte generation
 * - Security properties (determinism, collision resistance, entropy)
 *
 * Security-critical: These tests ensure cryptographic correctness
 */
class CryptoUtilsTest {

    // ==================== SHA-256 TESTS ====================

    @Test
    fun testSha256_ReturnsCorrectSize() {
        // Arrange
        val input = "test".toByteArray()

        // Act
        val hash = CryptoUtils.sha256(input)

        // Assert
        assertEquals("SHA-256 should produce 32 bytes", 32, hash.size)
    }

    @Test
    fun testSha256_Deterministic() {
        // Arrange
        val input = "hello world".toByteArray()

        // Act
        val hash1 = CryptoUtils.sha256(input)
        val hash2 = CryptoUtils.sha256(input)

        // Assert
        assertArrayEquals("Same input should produce same hash", hash1, hash2)
    }

    @Test
    fun testSha256_DifferentInputsProduceDifferentHashes() {
        // Arrange
        val input1 = "test1".toByteArray()
        val input2 = "test2".toByteArray()

        // Act
        val hash1 = CryptoUtils.sha256(input1)
        val hash2 = CryptoUtils.sha256(input2)

        // Assert
        assertFalse(
            "Different inputs should produce different hashes",
            hash1.contentEquals(hash2)
        )
    }

    @Test
    fun testSha256_EmptyInput() {
        // Arrange
        val input = ByteArray(0)

        // Act
        val hash = CryptoUtils.sha256(input)

        // Assert
        assertEquals(32, hash.size)
        assertFalse("Hash of empty input should not be all zeros", hash.all { it == 0.toByte() })
    }

    @Test
    fun testSha256_LargeInput() {
        // Arrange - 1MB input
        val input = ByteArray(1024 * 1024) { it.toByte() }

        // Act
        val hash = CryptoUtils.sha256(input)

        // Assert
        assertEquals(32, hash.size)
    }

    @Test
    fun testSha256_HighEntropy() {
        // Arrange
        val input = "ZeroPay Security Test".toByteArray()

        // Act
        val hash = CryptoUtils.sha256(input)

        // Assert - Check entropy
        val uniqueBytes = hash.toSet().size
        assertTrue("Hash should have high entropy (>=20 unique bytes)", uniqueBytes >= 20)
    }

    @Test
    fun testSha256_AvalancheEffect() {
        // Arrange - One bit difference
        val input1 = "test1".toByteArray()
        val input2 = "test2".toByteArray()

        // Act
        val hash1 = CryptoUtils.sha256(input1)
        val hash2 = CryptoUtils.sha256(input2)

        // Assert - Check avalanche (>40% bits different)
        var differentBits = 0
        for (i in hash1.indices) {
            val xor = hash1[i].toInt() xor hash2[i].toInt()
            differentBits += Integer.bitCount(xor and 0xFF)
        }

        val totalBits = hash1.size * 8
        val differencePercentage = (differentBits.toDouble() / totalBits) * 100

        assertTrue(
            "Avalanche effect: >40% bits should differ (actual: $differencePercentage%)",
            differencePercentage > 40.0
        )
    }

    // ==================== PBKDF2 TESTS ====================

    @Test
    fun testPbkdf2_ReturnsCorrectSize() {
        // Arrange
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertEquals("Key should be 32 bytes", 32, key.size)
    }

    @Test
    fun testPbkdf2_Deterministic() {
        // Arrange
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key1 = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)
        val key2 = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertArrayEquals("Same password+salt should produce same key", key1, key2)
    }

    @Test
    fun testPbkdf2_DifferentPasswordsProduceDifferentKeys() {
        // Arrange
        val password1 = "password1".toByteArray()
        val password2 = "password2".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key1 = CryptoUtils.pbkdf2(password1, salt, iterations = 1000, keyLengthBytes = 32)
        val key2 = CryptoUtils.pbkdf2(password2, salt, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertFalse("Different passwords should produce different keys", key1.contentEquals(key2))
    }

    @Test
    fun testPbkdf2_DifferentSaltsProduceDifferentKeys() {
        // Arrange
        val password = "password".toByteArray()
        val salt1 = "salt1".toByteArray()
        val salt2 = "salt2".toByteArray()

        // Act
        val key1 = CryptoUtils.pbkdf2(password, salt1, iterations = 1000, keyLengthBytes = 32)
        val key2 = CryptoUtils.pbkdf2(password, salt2, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertFalse("Different salts should produce different keys", key1.contentEquals(key2))
    }

    @Test
    fun testPbkdf2_HighIterationCount() {
        // Arrange
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key = CryptoUtils.pbkdf2(password, salt, iterations = 100_000, keyLengthBytes = 32)

        // Assert
        assertEquals(32, key.size)
        assertFalse("Key should not be all zeros", key.all { it == 0.toByte() })
    }

    @Test
    fun testPbkdf2_IterationCountAffectsOutput() {
        // Arrange
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key1000 = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)
        val key2000 = CryptoUtils.pbkdf2(password, salt, iterations = 2000, keyLengthBytes = 32)

        // Assert
        assertFalse(
            "Different iteration counts should produce different keys",
            key1000.contentEquals(key2000)
        )
    }

    // ==================== CONSTANT-TIME COMPARISON TESTS ====================

    @Test
    fun testConstantTimeEquals_IdenticalArrays_ReturnsTrue() {
        // Arrange
        val array1 = byteArrayOf(1, 2, 3, 4, 5)
        val array2 = byteArrayOf(1, 2, 3, 4, 5)

        // Act
        val result = ConstantTime.equals(array1, array2)

        // Assert
        assertTrue("Identical arrays should be equal", result)
    }

    @Test
    fun testConstantTimeEquals_DifferentArrays_ReturnsFalse() {
        // Arrange
        val array1 = byteArrayOf(1, 2, 3, 4, 5)
        val array2 = byteArrayOf(1, 2, 3, 4, 6)

        // Act
        val result = ConstantTime.equals(array1, array2)

        // Assert
        assertFalse("Different arrays should not be equal", result)
    }

    @Test
    fun testConstantTimeEquals_DifferentLengths_ReturnsFalse() {
        // Arrange
        val array1 = byteArrayOf(1, 2, 3)
        val array2 = byteArrayOf(1, 2, 3, 4)

        // Act
        val result = ConstantTime.equals(array1, array2)

        // Assert
        assertFalse("Arrays of different lengths should not be equal", result)
    }

    @Test
    fun testConstantTimeEquals_EmptyArrays_ReturnsTrue() {
        // Arrange
        val array1 = ByteArray(0)
        val array2 = ByteArray(0)

        // Act
        val result = ConstantTime.equals(array1, array2)

        // Assert
        assertTrue("Empty arrays should be equal", result)
    }

    @Test
    fun testConstantTimeEquals_SingleByteDifference() {
        // Arrange
        val array1 = ByteArray(32) { 0x42 }
        val array2 = ByteArray(32) { 0x42 }
        array2[15] = 0x43 // Change one byte in the middle

        // Act
        val result = ConstantTime.equals(array1, array2)

        // Assert
        assertFalse("Single byte difference should be detected", result)
    }

    // ==================== RANDOM BYTE GENERATION TESTS ====================

    @Test
    fun testGenerateRandomBytes_ReturnsCorrectSize() {
        // Act
        val bytes = CryptoUtils.generateRandomBytes(32)

        // Assert
        assertEquals("Should generate 32 bytes", 32, bytes.size)
    }

    @Test
    fun testGenerateRandomBytes_NonZero() {
        // Act
        val bytes = CryptoUtils.generateRandomBytes(32)

        // Assert
        assertFalse("Random bytes should not be all zeros", bytes.all { it == 0.toByte() })
    }

    @Test
    fun testGenerateRandomBytes_Unique() {
        // Act
        val bytes1 = CryptoUtils.generateRandomBytes(32)
        val bytes2 = CryptoUtils.generateRandomBytes(32)

        // Assert
        assertFalse(
            "Two random byte arrays should be different",
            bytes1.contentEquals(bytes2)
        )
    }

    @Test
    fun testGenerateRandomBytes_HighEntropy() {
        // Act
        val bytes = CryptoUtils.generateRandomBytes(256)

        // Assert
        val uniqueBytes = bytes.toSet().size
        assertTrue(
            "Random bytes should have high entropy (>100 unique bytes in 256)",
            uniqueBytes > 100
        )
    }

    @Test
    fun testGenerateRandomBytes_LargeSize() {
        // Act
        val bytes = CryptoUtils.generateRandomBytes(1024)

        // Assert
        assertEquals(1024, bytes.size)
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    fun testFullCryptoFlow_HashThenDeriveKey() {
        // Arrange
        val input = "user_pin_1234".toByteArray()

        // Act - Hash first, then use as password for PBKDF2
        val hash = CryptoUtils.sha256(input)
        val salt = CryptoUtils.generateRandomBytes(16)
        val key = CryptoUtils.pbkdf2(hash, salt, iterations = 10000, keyLengthBytes = 32)

        // Assert
        assertEquals(32, hash.size)
        assertEquals(16, salt.size)
        assertEquals(32, key.size)

        // Verify they're all different
        assertFalse(hash.contentEquals(key))
        assertFalse(salt.contentEquals(key))
    }

    @Test
    fun testConstantTimeComparison_WithHashedValues() {
        // Arrange
        val password = "correct_password".toByteArray()
        val salt = "salt".toByteArray()
        val correctKey = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)

        // Act - Attempt authentication
        val attemptPassword = "correct_password".toByteArray()
        val attemptKey = CryptoUtils.pbkdf2(attemptPassword, salt, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertTrue(
            "Correct password should verify",
            ConstantTime.equals(correctKey, attemptKey)
        )
    }

    @Test
    fun testConstantTimeComparison_WithWrongPassword() {
        // Arrange
        val password = "correct_password".toByteArray()
        val salt = "salt".toByteArray()
        val correctKey = CryptoUtils.pbkdf2(password, salt, iterations = 1000, keyLengthBytes = 32)

        // Act - Wrong password
        val wrongPassword = "wrong_password".toByteArray()
        val wrongKey = CryptoUtils.pbkdf2(wrongPassword, salt, iterations = 1000, keyLengthBytes = 32)

        // Assert
        assertFalse(
            "Wrong password should fail",
            ConstantTime.equals(correctKey, wrongKey)
        )
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testSha256_UnicodeInput() {
        // Arrange
        val input = "Hello 世界 🔐".toByteArray(Charsets.UTF_8)

        // Act
        val hash = CryptoUtils.sha256(input)

        // Assert
        assertEquals(32, hash.size)
    }

    @Test
    fun testPbkdf2_MinimalIterations() {
        // Arrange
        val password = "password".toByteArray()
        val salt = "salt".toByteArray()

        // Act
        val key = CryptoUtils.pbkdf2(password, salt, iterations = 1, keyLengthBytes = 32)

        // Assert
        assertEquals(32, key.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGenerateRandomBytes_ZeroSize_ThrowsException() {
        // Act
        CryptoUtils.generateRandomBytes(0)

        // Assert - Should throw
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGenerateRandomBytes_NegativeSize_ThrowsException() {
        // Act
        CryptoUtils.generateRandomBytes(-1)

        // Assert - Should throw
    }
}
