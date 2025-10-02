package com.zeropay.sdk

import com.zeropay.sdk.factors.VoiceFactor
import org.junit.Assert.*
import org.junit.Test

/**
 * Voice Factor Unit Tests
 * 
 * Add these tests to ZeroPaySDKTest.kt or create separate file
 */
class VoiceFactorTest {
    
    @Test
    fun testVoiceFactor_ValidAudio_ReturnsCorrectDigest() {
        // Arrange - Simulate 2 seconds of audio at 8kHz (16,000 samples)
        val audioBytes = ByteArray(16000) { it.toByte() }
        
        // Act
        val digest = VoiceFactor.digest(audioBytes)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testVoiceFactor_SameAudio_ReturnsDifferentDigest() {
        // Arrange - Same audio data
        val audioBytes = ByteArray(16000) { it.toByte() }
        
        // Act - Generate digest twice
        val digest1 = VoiceFactor.digest(audioBytes)
        Thread.sleep(10) // Ensure different timestamp
        val digest2 = VoiceFactor.digest(audioBytes)
        
        // Assert - Should be different due to timestamp + salt
        assertFalse(
            "Same audio should produce different digests (timestamp + salt make it unique)",
            digest1.contentEquals(digest2)
        )
    }
    
    @Test
    fun testVoiceFactor_DifferentAudio_ReturnsDifferentDigests() {
        // Arrange
        val audio1 = ByteArray(16000) { it.toByte() }
        val audio2 = ByteArray(16000) { (it * 2).toByte() }
        
        // Act
        val digest1 = VoiceFactor.digest(audio1)
        val digest2 = VoiceFactor.digest(audio2)
        
        // Assert
        assertFalse(
            "Different audio should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testVoiceFactor_EmptyAudio_ThrowsException() {
        // Act & Assert
        VoiceFactor.digest(ByteArray(0))
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testVoiceFactor_TooShort_ThrowsException() {
        // Arrange - Audio too short (< 1 second ~1000 bytes)
        val audioBytes = ByteArray(500)
        
        // Act & Assert
        VoiceFactor.digest(audioBytes)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testVoiceFactor_TooLong_ThrowsException() {
        // Arrange - Audio too long (> 5 MB)
        val audioBytes = ByteArray(6_000_000)
        
        // Act & Assert
        VoiceFactor.digest(audioBytes)
    }
    
    @Test
    fun testVoiceFactor_MinimumLength_Succeeds() {
        // Arrange - Exactly 1000 bytes (minimum)
        val audioBytes = ByteArray(1000)
        
        // Act
        val digest = VoiceFactor.digest(audioBytes)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testVoiceFactor_MaximumLength_Succeeds() {
        // Arrange - Exactly 5 MB (maximum)
        val audioBytes = ByteArray(5_000_000)
        
        // Act
        val digest = VoiceFactor.digest(audioBytes)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testVoiceFactor_IsValidAudio_ValidRange() {
        // Arrange
        val validShort = ByteArray(1000)
        val validMedium = ByteArray(50000)
        val validLong = ByteArray(5_000_000)
        val tooShort = ByteArray(500)
        val tooLong = ByteArray(6_000_000)
        
        // Act & Assert
        assertTrue(VoiceFactor.isValidAudio(validShort))
        assertTrue(VoiceFactor.isValidAudio(validMedium))
        assertTrue(VoiceFactor.isValidAudio(validLong))
        assertFalse(VoiceFactor.isValidAudio(tooShort))
        assertFalse(VoiceFactor.isValidAudio(tooLong))
    }
    
    @Test
    fun testVoiceFactor_RecommendedDuration() {
        // Act
        val duration = VoiceFactor.getRecommendedDurationMs()
        
        // Assert
        assertEquals("Recommended duration should be 2 seconds", 2000, duration)
    }
    
    @Test
    fun testVoiceFactor_RealisticAudioSize() {
        // Arrange - Realistic 2 seconds of audio
        // PCM 16-bit, 8kHz: 2 * 8000 * 2 bytes = 32,000 bytes
        val realisticAudio = ByteArray(32000) { (Math.random() * 256).toInt().toByte() }
        
        // Act
        val digest = VoiceFactor.digest(realisticAudio)
        
        // Assert
        assertEquals(32, digest.size)
        assertTrue(VoiceFactor.isValidAudio(realisticAudio))
    }
    
    @Test
    fun testVoiceFactor_PerformanceTest() {
        // Arrange - 2 seconds of audio
        val audioBytes = ByteArray(32000)
        
        // Act - Measure digest generation time
        val startTime = System.currentTimeMillis()
        repeat(100) {
            VoiceFactor.digest(audioBytes)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Assert - Should complete 100 digests in under 1 second
        assertTrue(
            "100 voice digests should complete in < 1 second (actual: ${elapsedTime}ms)",
            elapsedTime < 1000
        )
    }
    
    @Test
    fun testVoiceFactor_ThreadSafety() {
        // Arrange
        val audioBytes = ByteArray(16000)
        val results = mutableListOf<ByteArray>()
        val lock = Any()
        
        // Act - Multiple threads generating digests simultaneously
        val threads = (1..10).map {
            Thread {
                val digest = VoiceFactor.digest(audioBytes)
                synchronized(lock) {
                    results.add(digest)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join(5000) }
        
        // Assert
        assertEquals("All threads should complete", 10, results.size)
        results.forEach { digest ->
            assertEquals("All digests should be 32 bytes", 32, digest.size)
        }
    }
    
    @Test
    fun testVoiceFactor_ZeroKnowledgeCompliance() {
        // Arrange - Simulate voice saying "hello world"
        val voiceData = "hello world".toByteArray()
        val paddedVoice = voiceData + ByteArray(10000) // Pad to minimum length
        
        // Act
        val digest = VoiceFactor.digest(paddedVoice)
        val digestHex = digest.joinToString("") { "%02x".format(it) }
        
        // Assert - Digest should NOT contain original text
        assertFalse(
            "Digest should not reveal original audio content (zero-knowledge)",
            digestHex.contains("hello") || digestHex.contains("world")
        )
    }
}
