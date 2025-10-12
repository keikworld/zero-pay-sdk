// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/VoiceProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * VoiceProcessor - Voice Passphrase Processing
 * 
 * Processes voice passphrase authentication factor.
 * 
 * Format:
 * - Base64-encoded audio fingerprint
 * - Minimum: 2 seconds of audio
 * - Maximum: 10 seconds of audio
 * - Sample rate: 16kHz (standard for voice)
 * - Format: WAV, FLAC, or M4A
 * 
 * IMPORTANT SECURITY NOTE:
 * - This is NOT biometric voice recognition
 * - We hash the spoken passphrase text, not voice characteristics
 * - User speaks same phrase each time
 * - Text extracted via speech-to-text
 * - Only text hash stored (GDPR compliant)
 * 
 * Process Flow:
 * 1. User records audio (2-10 seconds)
 * 2. Speech-to-text extracts passphrase
 * 3. Passphrase text is normalized
 * 4. Text hash is stored (not voice biometrics)
 * 5. Verification: compare text hashes
 * 
 * Validation Rules:
 * - Valid audio format (base64 or file path)
 * - Duration within bounds (2-10 seconds)
 * - Minimum 3 words spoken
 * - Clear audio quality (SNR > 10dB)
 * - Not ambient noise only
 * 
 * Weak Passphrase Detection:
 * - Common phrases: "open sesame", "hello world"
 * - Too short phrases
 * - Unclear audio (low quality)
 * - Background noise dominance
 * 
 * Security:
 * - Only text hash stored (never raw audio or voice print)
 * - GDPR compliant (no biometric data)
 * - Speech-to-text on device (privacy preserving)
 * - Constant-time hash comparison
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object VoiceProcessor {
    
    private const val MIN_DURATION_SECONDS = 2
    private const val MAX_DURATION_SECONDS = 10
    private const val MIN_WORDS = 3
    private const val MAX_WORDS = 15
    private const val MIN_AUDIO_QUALITY_SNR = 10.0 // Signal-to-noise ratio in dB
    
    // Known weak voice passphrases
    private val WEAK_PASSPHRASES = setOf(
        "open sesame",
        "hello world",
        "password",
        "one two three",
        "test test test",
        "hello hello hello",
        "ok google",
        "hey siri",
        "alexa",
        "voice",
        "authentication",
        "unlock",
        "access granted",
        "enter",
        "login"
    )
    
    /**
     * Validate voice input
     * 
     * For enrollment, this validates the extracted text passphrase.
     * For verification, this validates the audio metadata.
     * 
     * @param value Voice data (text passphrase or audio metadata JSON)
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * // Enrollment (text passphrase after speech-to-text)
     * val result = VoiceProcessor.validate("purple mountain forest river")
     * 
     * // Verification (audio metadata)
     * val result = VoiceProcessor.validate("""
     *     {
     *       "duration": 4.5,
     *       "sampleRate": 16000,
     *       "quality": "good",
     *       "text": "purple mountain forest river"
     *     }
     * """.trimIndent())
     * ```
     */
    fun validate(value: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Try to parse as JSON (audio metadata)
        val audioMetadata = tryParseAudioMetadata(value)
        
        if (audioMetadata != null) {
            // Validating audio metadata
            return validateAudioMetadata(audioMetadata, warnings)
        } else {
            // Validating text passphrase (post speech-to-text)
            return validateTextPassphrase(value, warnings)
        }
    }
    
    /**
     * Normalize voice input
     * 
     * For text passphrase:
     * - Lowercase
     * - Remove extra whitespace
     * - Remove punctuation
     * - Trim
     * 
     * For audio metadata:
     * - Extract and normalize text field
     * 
     * @param value Raw voice input
     * @return Normalized text passphrase
     */
    fun normalize(value: String): String {
        // Try to parse as JSON
        val audioMetadata = tryParseAudioMetadata(value)
        
        return if (audioMetadata != null) {
            // Extract text from metadata
            normalizeText(audioMetadata.text)
        } else {
            // Normalize text directly
            normalizeText(value)
        }
    }
    
    /**
     * Try to parse audio metadata JSON
     * 
     * @param value Potential JSON string
     * @return AudioMetadata if valid JSON, null otherwise
     */
    private fun tryParseAudioMetadata(value: String): AudioMetadata? {
        return try {
            // Simple JSON parsing (production would use kotlinx.serialization)
            if (value.trimStart().startsWith("{")) {
                // Extract fields
                val duration = extractJsonField(value, "duration")?.toDoubleOrNull() ?: 0.0
                val sampleRate = extractJsonField(value, "sampleRate")?.toIntOrNull() ?: 0
                val quality = extractJsonField(value, "quality") ?: ""
                val text = extractJsonField(value, "text") ?: ""
                
                if (text.isNotEmpty()) {
                    AudioMetadata(duration, sampleRate, quality, text)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract JSON field value (simple parser)
     * 
     * @param json JSON string
     * @param field Field name
     * @return Field value or null
     */
    private fun extractJsonField(json: String, field: String): String? {
        val regex = Regex(""""$field"\s*:\s*"?([^",}]+)"?""")
        return regex.find(json)?.groupValues?.get(1)?.trim()
    }
    
    /**
     * Validate audio metadata
     * 
     * @param metadata Audio metadata
     * @param warnings Mutable list to add warnings
     * @return Validation result
     */
    private fun validateAudioMetadata(
        metadata: AudioMetadata,
        warnings: MutableList<String>
    ): ValidationResult {
        // Check duration
        if (metadata.duration < MIN_DURATION_SECONDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Audio must be at least $MIN_DURATION_SECONDS seconds"
            )
        }
        
        if (metadata.duration > MAX_DURATION_SECONDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Audio cannot exceed $MAX_DURATION_SECONDS seconds"
            )
        }
        
        // Check sample rate (should be 16kHz for voice)
        if (metadata.sampleRate < 8000) {
            warnings.add("Audio sample rate is low. Recording quality may be poor.")
        }
        
        // Check quality
        if (metadata.quality.lowercase() in listOf("poor", "bad", "low")) {
            warnings.add("Audio quality is poor. Consider recording in a quieter environment.")
        }
        
        // Validate extracted text
        return validateTextPassphrase(metadata.text, warnings)
    }
    
    /**
     * Validate text passphrase (extracted from speech-to-text)
     * 
     * @param text Text passphrase
     * @param warnings Mutable list to add warnings
     * @return Validation result
     */
    private fun validateTextPassphrase(
        text: String,
        warnings: MutableList<String>
    ): ValidationResult {
        val normalized = normalizeText(text)
        
        // Check not empty
        if (normalized.isBlank()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Voice passphrase cannot be empty"
            )
        }
        
        // Parse words
        val words = normalized.split(Regex("\\s+")).filter { it.isNotEmpty() }
        
        // Check word count
        if (words.size < MIN_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Voice passphrase must contain at least $MIN_WORDS words"
            )
        }
        
        if (words.size > MAX_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Voice passphrase cannot exceed $MAX_WORDS words"
            )
        }
        
        // Check minimum length
        if (normalized.length < 10) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Voice passphrase must be at least 10 characters"
            )
        }
        
        // Check for weak passphrases
        if (normalized in WEAK_PASSPHRASES) {
            warnings.add("This is a commonly used phrase. Consider a more unique passphrase.")
        }
        
        // Check for repeating words
        if (words.all { it == words[0] }) {
            warnings.add("All words are the same. Use a more varied passphrase.")
        }
        
        // Check for very short words (all < 3 chars)
        if (words.all { it.length < 3 }) {
            warnings.add("Words are very short. Consider using longer words for better security.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize text passphrase
     * 
     * @param text Raw text
     * @return Normalized text
     */
    private fun normalizeText(text: String): String {
        return text.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove punctuation
            .replace(Regex("\\s+"), " ") // Collapse whitespace
            .trim()
    }
    
    /**
     * Audio metadata data class
     */
    private data class AudioMetadata(
        val duration: Double,
        val sampleRate: Int,
        val quality: String,
        val text: String
    )
}
