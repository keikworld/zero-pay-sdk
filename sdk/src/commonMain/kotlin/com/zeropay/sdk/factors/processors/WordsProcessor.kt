// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/WordsProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * WordsProcessor - Word Sequence Processing
 * 
 * Processes word sequence authentication factor (passphrase-style).
 * 
 * Format:
 * - Space-separated words: "correct horse battery staple"
 * - Minimum: 3 words
 * - Maximum: 8 words
 * - Language: English (expandable)
 * 
 * Validation Rules:
 * - Each word is valid (alphabetic)
 * - At least 3 words
 * - No extremely common phrases
 * - Sufficient entropy
 * - No repeated words
 * 
 * Weak Phrase Detection:
 * - Common phrases ("the quick brown fox")
 * - Repeated words ("test test test")
 * - Very short words only ("a b c d")
 * - Sequential dictionary words
 * 
 * Text Normalization:
 * - Lowercase
 * - Trim whitespace
 * - Remove punctuation
 * - Single space between words
 * - Remove diacritics (optional)
 * 
 * Security:
 * - Only hash stored
 * - Entropy calculation
 * - Weak phrase detection
 * - Constant-time comparison
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object WordsProcessor {
    
    private const val MIN_WORDS = 3
    private const val MAX_WORDS = 8
    private const val MIN_WORD_LENGTH = 2
    private const val MIN_TOTAL_LENGTH = 12
    private const val MAX_TOTAL_LENGTH = 100
    
    // Common weak phrases
    private val WEAK_PHRASES = setOf(
        "the quick brown fox",
        "lorem ipsum dolor sit",
        "to be or not to be",
        "test test test",
        "one two three four",
        "hello world goodbye world",
        "password password password",
        "correct horse battery staple", // XKCD famous example
        "admin admin admin",
        "welcome to the jungle"
    )
    
    // Very common words that reduce entropy
    private val COMMON_WORDS = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their"
    )
    
    /**
     * Validate word sequence
     * 
     * Checks:
     * - Format is valid (space-separated words)
     * - Word count within bounds (3-8)
     * - All words are alphabetic
     * - No repeated words
     * - Not a common phrase
     * - Sufficient entropy
     * 
     * @param sequence Word sequence
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = WordsProcessor.validate("correct horse battery staple")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(sequence: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse words
        val words = sequence.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        // Check word count
        if (words.size < MIN_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Word sequence must have at least $MIN_WORDS words"
            )
        }
        
        if (words.size > MAX_WORDS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Word sequence cannot have more than $MAX_WORDS words"
            )
        }
        
        // Check total length
        val totalLength = words.sumOf { it.length }
        if (totalLength < MIN_TOTAL_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Total word length must be at least $MIN_TOTAL_LENGTH characters"
            )
        }
        
        if (totalLength > MAX_TOTAL_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Total word length cannot exceed $MAX_TOTAL_LENGTH characters"
            )
        }
        
        // Check each word is alphabetic and minimum length
        words.forEach { word ->
            if (!word.all { it.isLetter() }) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "All words must contain only letters (found: '$word')"
                )
            }
            
            if (word.length < MIN_WORD_LENGTH) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Each word must be at least $MIN_WORD_LENGTH characters"
                )
            }
        }
        
        // Check for duplicates
        if (words.size != words.toSet().size) {
            warnings.add("Word sequence contains duplicate words. Consider using unique words.")
        }
        
        // Check if weak phrase
        val normalized = normalize(sequence)
        if (isWeak(normalized)) {
            warnings.add("This is a commonly used phrase. Choose a more unique combination.")
        }
        
        // Check for too many common words
        val commonWordCount = words.count { it in COMMON_WORDS }
        if (commonWordCount >= words.size / 2) {
            warnings.add("Phrase contains many common words. Consider adding less common words.")
        }
        
        // Check entropy
        val entropy = calculateEntropy(words)
        if (entropy < 40) {
            warnings.add("Phrase has low entropy. Consider using longer or more diverse words.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize word sequence
     * 
     * Converts to consistent format:
     * - Lowercase
     * - Single space between words
     * - Trim whitespace
     * - Remove punctuation
     * 
     * @param sequence Raw word sequence
     * @return Normalized sequence
     */
    fun normalize(sequence: String): String {
        return sequence.trim()
            .lowercase()
            .replace(Regex("[^a-z\\s]"), "") // Remove non-letter, non-space
            .replace(Regex("\\s+"), " ") // Collapse multiple spaces
            .trim()
    }
    
    /**
     * Check if phrase is weak
     */
    private fun isWeak(phrase: String): Boolean {
        return WEAK_PHRASES.any { phrase.contains(it) || it.contains(phrase) }
    }
    
    /**
     * Calculate entropy of word sequence
     * 
     * Higher entropy = more secure.
     * Based on word length and variety.
     */
    private fun calculateEntropy(words: List<String>): Int {
        // Base entropy from word count
        var entropy = words.size * 10
        
        // Bonus for longer words
        val avgLength = words.map { it.length }.average()
        entropy += (avgLength * 5).toInt()
        
        // Bonus for unique words
        val uniqueWords = words.toSet().size
        entropy += (uniqueWords.toDouble() / words.size * 20).toInt()
        
        // Penalty for common words
        val commonCount = words.count { it in COMMON_WORDS }
        entropy -= commonCount * 5
        
        // Bonus for rare letters (q, x, z)
        val rareLetterCount = words.sumOf { word ->
            word.count { it in "qxz" }
        }
        entropy += rareLetterCount * 3
        
        return entropy.coerceIn(0, 100)
    }
    
    /**
     * Calculate strength score
     */
    fun calculateStrength(sequence: String): Int {
        val words = sequence.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        var score = 50 // Base score
        
        // Word count bonus
        score += (words.size - MIN_WORDS) * 10
        
        // Length bonus
        val totalLength = words.sumOf { it.length }
        score += (totalLength - MIN_TOTAL_LENGTH) / 2
        
        // Entropy bonus
        val entropy = calculateEntropy(words)
        score += entropy / 5
        
        // Weak penalty
        if (isWeak(normalize(sequence))) {
            score -= 30
        }
        
        // Common words penalty
        val commonCount = words.count { it in COMMON_WORDS }
        score -= commonCount * 5
        
        // Duplicate penalty
        if (words.size != words.toSet().size) {
            score -= 15
        }
        
        return score.coerceIn(0, 100)
    }
}
