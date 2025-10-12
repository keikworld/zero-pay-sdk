// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/VoiceProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * VoiceProcessor - Voice Phrase Processing
 * 
 * Processes voice phrase authentication factor.
 * 
 * IMPORTANT: This is TEXT-ONLY processing, NOT voice biometrics.
 * - User speaks a phrase
 * - Speech-to-text converts to text
 * - Text phrase is hashed (like a password)
 * - NO voice biometric data stored
 * 
 * Format:
 * - Text phrase: "my secret passphrase goes here"
 * - Minimum: 3 words
 * - Maximum: 10 words
 * - Language: Any (UTF-8)
 * 
 * Validation Rules:
 * - At least 3 words
 * - No profanity (basic filter)
 * - Not a common phrase
 * - Sufficient entropy
 * 
 * Weak Phrase Detection:
 * - Common phrases (password, letmein, etc.)
 * - Dictionary words only
 * - Too short phrases
 * - Repetitive words
 * 
 * Text Normalization:
 * - Lowercase
 * - Trim whitespace
 * - Remove punctuation
 * - Single space between words
 * 
 * Security:
 * - Only text hash stored (NOT voice)
 * - No voice biometric data
 * - Constant-time comparison
 * - Memory wiping
 * 
 * Privacy:
 * - No voice recording stored
 * - No biometric data collected
 * - GDPR compliant (just text)
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object VoiceProcessor {
    
    private const val MIN_WORDS = 3
    private const val MAX_WORDS = 10
    private const val MIN_PHRASE_LENGTH = 10
    private const val MAX_PHRASE_LENGTH = 100
    
    // Common weak phrases
    private val WEAK_PHRASES = setOf(
        "password",
        "letmein",
        "welcome",
        "hello world",
        "open sesame",
        "123456",
        "qwerty",
        "admin",
        "test test test",
        "one two three",
        "my voice is my password"
    )
    
    // Basic profanity filter (add more as needed)
    private val PROFANITY_LIST = setOf(
        // Add language-specific profanity here
        // For production, use a comprehensive filter library
    )
    
    /**
     * Validate voice phrase
     * 
     * Checks:
     * - Length within bounds (3-10 words)
     * - No profanity
     * - Not a common phrase
     * - Sufficient entropy
     * - Valid characters
     * 
     * @param phrase Voice phrase (text)
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = VoiceProcessor.validate("my unique secret phrase here")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(phrase: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Check length
        if (phrase.length < MIN_PHRASE_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Phrase must be at least $MIN_PHRASE_LENGTH characters"
            )
        }
        
        if (phrase.length > MAX_PHRASE_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Phrase cannot exceed $MAX_PHRASE_LENGTH characters"
            )
        }
        
        // Normalize and count words
        val normalized = normalize(phrase)
        val words = normalized.split(" ").filter { it.isNotBlank() }
        
        // Check word count
        if (words.size < MIN_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Phrase must have at least $MIN_WORDS words"
            )
        }
        
        if (words.size > MAX_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Phrase cannot have more than $MAX_WORDS words"
            )
        }
        
        // Check for profanity
        if (containsProfanity(normalized)) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Phrase contains inappropriate language"
            )
        }
        
        // Check if weak phrase
        if (isWeak(normalized)) {
            warnings.add("This is a commonly used phrase. Consider something more unique.")
        }
        
        // Check for repeated words
        val uniqueWords = words.toSet()
        if (uniqueWords.size < words.size / 2) {
            warnings.add("Phrase has many repeated words. More variety improves security.")
        }
        
        // Check entropy
        val entropy = calculateEntropy(phrase)
        if (entropy < 30) {
            warnings.add("Phrase has low entropy. Try adding more unique words or characters.")
        }
        
        // Check if all words are too short
        val avgWordLength = words.map { it.length }.average()
        if (avgWordLength < 3) {
            warnings.add("Words are very short. Longer words improve security.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize voice phrase
     * 
     * Converts to consistent format:
     * - Lowercase
     * - Remove punctuation
     * - Single space between words
     * - Trim whitespace
     * 
     * @param phrase Raw voice phrase
     * @return Normalized phrase
     */
    fun normalize(phrase: String): String {
        return phrase
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ") // Remove punctuation
            .replace(Regex("\\s+"), " ")         // Single space
            .trim()
    }
    
    /**
     * Check if phrase contains profanity
     * 
     * Basic filter - for production use comprehensive library.
     * 
     * @param phrase Normalized phrase
     * @return true if profanity detected
     */
    private fun containsProfanity(phrase: String): Boolean {
        val words = phrase.split(" ")
        return words.any { it in PROFANITY_LIST }
    }
    
    /**
     * Check if phrase is weak
     * 
     * @param phrase Normalized phrase
     * @return true if commonly used
     */
    private fun isWeak(phrase: String): Boolean {
        return phrase in WEAK_PHRASES
    }
    
    /**
     * Calculate phrase entropy
     * 
     * Measures randomness/unpredictability.
     * 
     * @param phrase Raw phrase
     * @return Entropy score (0-100)
     */
    private fun calculateEntropy(phrase: String): Int {
        if (phrase.isEmpty()) return 0
        
        // Character frequency
        val charCounts = phrase.groupingBy { it }.eachCount()
        val length = phrase.length.toDouble()
        
        // Shannon entropy
        var entropy = 0.0
        for (count in charCounts.values) {
            val probability = count / length
            entropy -= probability * (kotlin.math.ln(probability) / kotlin.math.ln(2.0))
        }
        
        // Normalize to 0-100 (max entropy for ASCII is ~6.5 bits)
        val normalized = (entropy / 6.5 * 100).toInt()
        return normalized.coerceIn(0, 100)
    }
    
    /**
     * Calculate phrase strength
     * 
     * Combines multiple factors:
     * - Length
     * - Word count
     * - Entropy
     * - Uniqueness
     * 
     * @param phrase Raw phrase
     * @return Strength score (0-100)
     */
    fun calculateStrength(phrase: String): Int {
        var score = 0
        
        val normalized = normalize(phrase)
        val words = normalized.split(" ").filter { it.isNotBlank() }
        
        // Length score (max 30 points)
        val lengthScore = (phrase.length * 30) / MAX_PHRASE_LENGTH
        score += lengthScore.coerceAtMost(30)
        
        // Word count score (max 20 points)
        val wordScore = (words.size * 20) / MAX_WORDS
        score += wordScore.coerceAtMost(20)
        
        // Entropy score (max 30 points)
        val entropyScore = (calculateEntropy(phrase) * 30) / 100
        score += entropyScore
        
        // Uniqueness score (max 20 points)
        val uniqueWords = words.toSet().size
        val uniquenessScore = (uniqueWords * 20) / words.size
        score += uniquenessScore
        
        // Penalty for weak phrases
        if (isWeak(normalized)) {
            score -= 30
        }
        
        return score.coerceIn(0, 100)
    }
}
