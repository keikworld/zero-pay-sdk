// Path: sdk/src/commonTest/kotlin/com/zeropay/sdk/security/DoubleLayerEncryptionTest.kt

package com.zeropay.sdk.security

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class DoubleLayerEncryptionTest {
    
    @Test
    fun testEnrollmentAndVerification() = runBlocking {
        // Setup
        val kmsProvider = LocalKMSProvider() // Testing KMS
        val encryption = DoubleLayerEncryption(kmsProvider)
        
        val uuid = "test-uuid-12345"
        val correctFactors = listOf("pattern123", "emoji🔥", "color#FF5733")
        val wrongFactors = listOf("wrong", "factors", "here")
        
        // Enroll
        val enrollResult = encryption.enroll(uuid, correctFactors)
        assertEquals(uuid, enrollResult.uuid)
        assertEquals(3, enrollResult.factorCount)
        
        // Verify with correct factors
        val correctVerify = encryption.verify(
            uuid,
            correctFactors,
            enrollResult.wrappedKey
        )
        assertTrue(correctVerify.success, "Correct factors should verify")
        
        // Verify with wrong factors
        val wrongVerify = encryption.verify(
            uuid,
            wrongFactors,
            enrollResult.wrappedKey
        )
        assertFalse(wrongVerify.success, "Wrong factors should not verify")
        
        println("✅ Double encryption test passed!")
    }
    
    @Test
    fun testCryptoUtils() {
        // Test SHA-256
        val data = "test data".toByteArray()
        val hash = CryptoUtils.sha256(data)
        assertEquals(32, hash.size, "SHA-256 should produce 32 bytes")
        
        // Test constant-time comparison
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 4)
        val c = byteArrayOf(1, 2, 3, 5)
        
        assertTrue(CryptoUtils.constantTimeEquals(a, b), "Equal arrays should match")
        assertFalse(CryptoUtils.constantTimeEquals(a, c), "Different arrays should not match")
        
        // Test hex conversion
        val hex = CryptoUtils.bytesToHex(hash)
        val bytes = CryptoUtils.hexToBytes(hex)
        assertTrue(hash.contentEquals(bytes), "Hex round-trip should work")
        
        println("✅ CryptoUtils test passed!")
    }
    
    @Test
    fun testKeyDerivation() {
        val uuid = "test-uuid"
        val factors = listOf("factor1", "factor2", "factor3")
        
        // Derive key
        val key1 = KeyDerivation.deriveKey(uuid, factors)
        assertEquals(32, key1.size, "Derived key should be 32 bytes")
        
        // Derive again (should be deterministic)
        val key2 = KeyDerivation.deriveKey(uuid, factors)
        assertTrue(key1.contentEquals(key2), "Key derivation should be deterministic")
        
        // Different factors should produce different key
        val key3 = KeyDerivation.deriveKey(uuid, listOf("different", "factors"))
        assertFalse(key1.contentEquals(key3), "Different factors should produce different key")
        
        // Validate key
        assertTrue(KeyDerivation.isValidKey(key1), "Valid key should pass validation")
        
        println("✅ KeyDerivation test passed!")
    }
}
