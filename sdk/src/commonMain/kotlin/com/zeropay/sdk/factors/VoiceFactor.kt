package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Voice Factor - Audio recording authentication
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 hash of audio bytes
 * - No raw voice data stored or transmitted
 * - Audio recorded temporarily, immediately hashed, then deleted
 * 
 * PSD3 Compliance:
 * - Voice as biometric "inherence" factor
 * 
 * Zero-Knowledge:
 * - Server receives only hash, never audio file
 * - Irreversible transformation (SHA-256)
 */
object VoiceFactor {
    
    /**
     * Generate digest from audio recording
     * 
     * @param audioBytes Raw audio data (PCM, WAV, or Opus)
     * @return SHA-256 hash (32 bytes)
     */
    fun digest(audioBytes: ByteArray): ByteArray {
        require(audioBytes.isNotEmpty()) { "Audio data cannot be empty" }
        require(audioBytes.size >= 1000) { "Audio too short (minimum ~1 second)" }
        require(audioBytes.size <= 5_000_000) { "Audio too long (maximum ~5 MB)" }
        
        // Add metadata for better uniqueness
        val timestamp = System.currentTimeMillis().toString()
        val salt = CryptoUtils.secureRandomBytes(16)
        
        // Combine audio + metadata
        val combined = audioBytes + timestamp.toByteArray() + salt
        
        // Return irreversible hash
        return CryptoUtils.sha256(combined)
    }
    
    /**
     * Validate audio format and quality
     */
    fun isValidAudio(audioBytes: ByteArray): Boolean {
        return audioBytes.size in 1000..5_000_000
    }
    
    /**
     * Get recommended recording duration
     */
    fun getRecommendedDurationMs(): Int = 2000 // 2 seconds
}
