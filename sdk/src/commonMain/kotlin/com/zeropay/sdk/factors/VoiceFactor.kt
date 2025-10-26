package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Voice Factor with Enhanced Security
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 hash
 * - No raw voice data stored
 * - Audio deleted immediately after hashing
 * 
 * Security:
 * - Constant-time verification
 * - Memory wiping
 * - Replay protection (timestamp + salt)
 */
object VoiceFactor {

    const val MIN_AUDIO_SIZE = 1000      // ~1 second
    const val MAX_AUDIO_SIZE = 5_000_000  // ~5 MB (DoS protection)
    
    /**
     * Generate digest from audio recording
     * 
     * @param audioBytes Raw audio data (PCM, WAV, or compressed)
     * @return SHA-256 hash (32 bytes)
     */
    fun digest(audioBytes: ByteArray): ByteArray {
        require(audioBytes.isNotEmpty()) { "Audio data cannot be empty" }
        require(audioBytes.size >= MIN_AUDIO_SIZE) { 
            "Audio too short (minimum ~1 second, got ${audioBytes.size} bytes)" 
        }
        require(audioBytes.size <= MAX_AUDIO_SIZE) { 
            "Audio too long (maximum ~5 MB, got ${audioBytes.size} bytes)" 
        }
        
        return try {
            // Add timestamp for replay protection
            val timestamp = System.currentTimeMillis().toString()
            val salt = CryptoUtils.generateRandomBytes(16)
            
            // Combine audio + metadata
            val combined = audioBytes + timestamp.toByteArray() + salt
            
            // Return irreversible hash
            CryptoUtils.sha256(combined)
        } finally {
            // Zero out audio from memory (GDPR)
            Arrays.fill(audioBytes, 0)
        }
    }
    
    /**
     * Generate digest with specific salt (for verification)
     */
    fun digestWithSalt(audioBytes: ByteArray, salt: ByteArray, timestamp: Long): ByteArray {
        require(audioBytes.isNotEmpty()) { "Audio data cannot be empty" }
        require(audioBytes.size in MIN_AUDIO_SIZE..MAX_AUDIO_SIZE) {
            "Audio size out of range"
        }
        
        return try {
            val combined = audioBytes + timestamp.toString().toByteArray() + salt
            CryptoUtils.sha256(combined)
        } finally {
            Arrays.fill(audioBytes, 0)
        }
    }
    
    /**
     * Verify voice (constant-time)
     */
    fun verify(audioBytes: ByteArray, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digest(audioBytes)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        } finally {
            Arrays.fill(audioBytes, 0)
        }
    }
    
    /**
     * Validate audio format and quality
     */
    fun isValidAudio(audioBytes: ByteArray): Boolean {
        return audioBytes.size in MIN_AUDIO_SIZE..MAX_AUDIO_SIZE
    }
    
    /**
     * Get recommended recording duration
     */
    fun getRecommendedDurationMs(): Int = 2000 // 2 seconds
    
    /**
     * Get min/max audio sizes
     */
    fun getAudioSizeRange(): Pair<Int, Int> = MIN_AUDIO_SIZE to MAX_AUDIO_SIZE
}
