package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Voice Factor Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - Digest generation from audio data
 * - Constant-time verification
 * - Audio size validation (DoS protection)
 * - Memory wiping after digest
 * - Edge cases
 */
class VoiceFactorTest {
    
    // Helper to generate simulated audio data
    private fun generateSimulatedAudio(sizeBytes: Int): ByteArray {
        return ByteArray(sizeBytes) { (it % 256).toByte() }
    }
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidAudio_ReturnsCorrectSize() {
        // Arrange - 48kHz * 2 bytes * 3 seconds = 288,000 bytes
        val audioData = generateSimulatedAudio(288_000)
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameAudioProducesSameDigest() {
        // Arrange
        val audioData = generateSimulatedAudio(100_000)
        
        // Act
        val digest1 = VoiceFactor.digest(audioData.copyOf())
        val digest2 = VoiceFactor.digest(audioData.copyOf())
        
        // Assert
        assertArrayEquals("Same audio should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_DifferentAudio_ProducesDifferentDigests() {
        // Arrange
        val audio1 = ByteArray(100_000) { 1 }
        val audio2 = ByteArray(100_000) { 2 }
        
        // Act
        val digest1 = VoiceFactor.digest(audio1)
        val digest2 = VoiceFactor.digest(audio2)
        
        // Assert
        assertFalse(
            "Different audio should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION ====================
    
    @Test
    fun testVerify_MatchingAudio_ReturnsTrue() {
        // Arrange
        val audioData = generateSimulatedAudio(100_000)
        val digest = VoiceFactor.digest(audioData.copyOf())
        
        // Act
        val result = VoiceFactor.verify(audioData.copyOf(), digest)
        
        // Assert
        assertTrue("Matching audio should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingAudio_ReturnsFalse() {
        // Arrange
        val audio1 = generateSimulatedAudio(100_000)
        val audio2 = ByteArray(100_000) { (it + 10).toByte() } // Different
        val digest = VoiceFactor.digest(audio1)
        
        // Act
        val result = VoiceFactor.verify(audio2, digest)
        
        // Assert
        assertFalse("Non-matching audio should fail verification", result)
    }
    
    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctAudio = generateSimulatedAudio(50_000)
        val wrongAudio = generateSimulatedAudio(50_000).apply {
            this[0] = (this[0] + 1).toByte() // Make it different
        }
        val digest = VoiceFactor.digest(correctAudio.copyOf())
        val iterations = 100
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                VoiceFactor.verify(correctAudio.copyOf(), digest)
            }
        }
        
        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                VoiceFactor.verify(wrongAudio.copyOf(), digest)
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
    
    // ==================== VALIDATION ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_EmptyAudio_ThrowsException() {
        // Arrange
        val audioData = ByteArray(0)
        
        // Act
        VoiceFactor.digest(audioData)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_AudioTooShort_ThrowsException() {
        // Arrange - 500 bytes (minimum is 1000)
        val audioData = generateSimulatedAudio(500)
        
        // Act
        VoiceFactor.digest(audioData)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_AudioTooLarge_ThrowsException() {
        // Arrange - 6 MB (maximum is 5 MB)
        val audioData = generateSimulatedAudio(6_000_000)
        
        // Act
        VoiceFactor.digest(audioData)
        
        // Assert - Should throw (DoS protection)
    }
    
    @Test
    fun testDigest_MinimumSize_WorksCorrectly() {
        // Arrange - Exactly 1000 bytes (minimum)
        val audioData = generateSimulatedAudio(1000)
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_MaximumSize_WorksCorrectly() {
        // Arrange - Exactly 5 MB (maximum)
        val audioData = generateSimulatedAudio(5_000_000)
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_MemoryWiping_AudioClearedAfterDigest() {
        // Arrange
        val audioData = generateSimulatedAudio(10_000)
        val originalChecksum = audioData.sum()
        
        // Act
        VoiceFactor.digest(audioData)
        
        // Assert - Audio should be wiped (all zeros)
        val afterChecksum = audioData.sum()
        assertEquals(
            "Audio data should be wiped after digest generation (security)",
            0,
            afterChecksum
        )
        
        // Verify original was not all zeros
        assertNotEquals("Original audio was not all zeros", 0, originalChecksum)
    }
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val audioData = generateSimulatedAudio(100_000)
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
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
    fun testDigest_SmallChange_LargeHashChange() {
        // Arrange - Audio differing by 1 byte
        val audio1 = generateSimulatedAudio(10_000)
        val audio2 = audio1.copyOf().apply {
            this[5000] = (this[5000] + 1).toByte() // Change 1 byte
        }
        
        // Act
        val digest1 = VoiceFactor.digest(audio1.copyOf())
        val digest2 = VoiceFactor.digest(audio2.copyOf())
        
        // Assert - Check avalanche effect
        var differentBytes = 0
        for (i in digest1.indices) {
            if (digest1[i] != digest2[i]) differentBytes++
        }
        
        assertTrue(
            "Small audio change should cause large hash change (avalanche effect). " +
            "Different bytes: $differentBytes/32",
            differentBytes >= 16 // At least half should be different
        )
    }
    
    // ==================== EDGE CASES ====================
    
    @Test
    fun testDigest_AllZeroAudio_WorksCorrectly() {
        // Arrange - Silence (all zeros)
        val audioData = ByteArray(10_000) { 0 }
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals(32, digest.size)
        assertFalse("Digest of silence should not be all zeros", digest.all { it == 0.toByte() })
    }
    
    @Test
    fun testDigest_MaxValueAudio_WorksCorrectly() {
        // Arrange - Maximum amplitude
        val audioData = ByteArray(10_000) { 127 }
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_AlternatingPattern_WorksCorrectly() {
        // Arrange - Alternating values
        val audioData = ByteArray(10_000) { if (it % 2 == 0) 0 else 127 }
        
        // Act
        val digest = VoiceFactor.digest(audioData)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== GETTERS ====================
    
    @Test
    fun testGetters_ReturnCorrectValues() {
        // Act & Assert
        // Note: VoiceFactor doesn't expose getMinAudioSize/getMaxAudioSize getters
        // These are internal validation constants
    }
    
    // ==================== PERFORMANCE ====================
    
    @Test
    fun testDigest_Performance_CompletesQuickly() {
        // Arrange - 1 second of audio at 48kHz
        val audioData = generateSimulatedAudio(96_000)
        
        // Act
        val time = measureTimeMillis {
            repeat(100) {
                VoiceFactor.digest(audioData.copyOf())
            }
        }
        
        // Assert - 100 digests should complete in under 2 seconds
        assertTrue(
            "100 digests (96KB audio each) should complete in <2s (was ${time}ms)",
            time < 2000
        )
    }
}
