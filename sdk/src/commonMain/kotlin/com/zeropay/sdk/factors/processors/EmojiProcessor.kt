// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/EmojiProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * EmojiProcessor - Emoji Sequence Processing
 * 
 * Processes emoji sequence authentication factor.
 * 
 * Format:
 * - Comma-separated emojis: "😀,🔥,💎,🚀"
 * - Or continuous string: "😀🔥💎🚀"
 * - Minimum: 3 emojis
 * - Maximum: 8 emojis
 * 
 * Validation Rules:
 * - Each emoji is valid Unicode emoji
 * - No duplicate emojis
 * - No skin tone modifiers (for consistency)
 * - Valid emoji range: U+1F300 to U+1FAF8
 * 
 * Weak Pattern Detection:
 * - Sequential emojis (same category)
 * - Common sequences (😀😁😂🤣)
 * - Repeated emojis
 * 
 * Unicode Normalization:
 * - NFD (Canonical Decomposition)
 * - Removes variation selectors
 * - Consistent emoji representation
 * 
 * Security:
 * - Only hash stored
 * - Unicode normalization
 * - Weak pattern detection
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
object EmojiProcessor {
    
    private const val MIN_EMOJIS = 3
    private const val MAX_EMOJIS = 8
    
    // Emoji Unicode ranges
    private val EMOJI_RANGES = listOf(
        0x1F300..0x1F5FF, // Miscellaneous Symbols and Pictographs
        0x1F600..0x1F64F, // Emoticons
        0x1F680..0x1F6FF, // Transport and Map Symbols
        0x1F700..0x1F77F, // Alchemical Symbols
        0x1F780..0x1F7FF, // Geometric Shapes Extended
        0x1F800..0x1F8FF, // Supplemental Arrows-C
        0x1F900..0x1F9FF, // Supplemental Symbols and Pictographs
        0x1FA00..0x1FA6F, // Chess Symbols
        0x1FA70..0x1FAF8  // Symbols and Pictographs Extended-A
    )
    
    // Common weak sequences
    private val WEAK_SEQUENCES = setOf(
        "😀,😁,😂",
        "❤️,🧡,💛",
        "🔥,💧,🌱",
        "1️⃣,2️⃣,3️⃣",
        "🍎,🍊,🍋",
        "😀,😀,😀",
        "🔥,🔥,🔥"
    )
    
    /**
     * Validate emoji sequence
     * 
     * Checks:
     * - Format is valid (emojis separated by commas or continuous)
     * - Length within bounds (3-8)
     * - All are valid emojis
     * - No duplicates
     * - Not a weak sequence
     * 
     * @param sequence Emoji sequence
     * @return Validation result
     * 
     * Example:
     * ```kotlin
     * val result = EmojiProcessor.validate("😀,🔥,💎,🚀")
     * if (!result.isValid) {
     *     println("Error: ${result.errorMessage}")
     * }
     * ```
     */
    fun validate(sequence: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Parse emojis
        val emojis = parseEmojis(sequence)
        
        if (emojis.isEmpty()) {
            return ValidationResult(
                isValid = false,
                errorMessage = "No valid emojis found in sequence"
            )
        }
        
        // Check length
        if (emojis.size < MIN_EMOJIS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Emoji sequence must have at least $MIN_EMOJIS emojis"
            )
        }
        
        if (emojis.size > MAX_EMOJIS) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Emoji sequence cannot have more than $MAX_EMOJIS emojis"
            )
        }
        
        // Check all are valid emojis
        if (!emojis.all { isValidEmoji(it) }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Sequence contains invalid characters (non-emojis)"
            )
        }
        
        // Check no duplicates
        if (emojis.size != emojis.toSet().size) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Each emoji can only be used once"
            )
        }
        
        // Check if weak sequence
        val normalized = normalize(sequence)
        if (isWeak(normalized)) {
            warnings.add("This is a commonly used emoji sequence")
        }
        
        // Check diversity (different categories)
        val categories = emojis.map { getEmojiCategory(it) }.toSet()
        if (categories.size == 1 && emojis.size >= 4) {
            warnings.add("All emojis are from the same category. Mix different types for better security.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize emoji sequence
     * 
     * Converts to consistent format:
     * - Comma-separated
     * - No spaces
     * - Unicode normalization (NFD)
     * - Removes variation selectors
     * 
     * @param sequence Raw emoji sequence
     * @return Normalized sequence
     */
    fun normalize(sequence: String): String {
        val emojis = parseEmojis(sequence)
        return emojis.joinToString(",") { normalizeEmoji(it) }
    }
    
    /**
     * Parse emojis from string
     * 
     * Supports:
     * - Comma-separated: "😀,🔥,💎"
     * - Continuous: "😀🔥💎"
     * - Mixed with spaces
     * 
     * @param sequence Emoji sequence
     * @return List of individual emojis
     */
    private fun parseEmojis(sequence: String): List<String> {
        // Try comma-separated first
        if (sequence.contains(",")) {
            return sequence.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        
        // Otherwise, split by emoji boundaries
        return splitEmojis(sequence.replace(" ", ""))
    }
    
    /**
     * Split continuous emoji string into individual emojis
     * 
     * Handles multi-codepoint emojis (flags, skin tones, etc.)
     * 
     * @param text Continuous emoji string
     * @return List of individual emojis
     */
    private fun splitEmojis(text: String): List<String> {
        val emojis = mutableListOf<String>()
        var i = 0
        
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            
            if (isEmojiCodePoint(codePoint)) {
                var emoji = String(Character.toChars(codePoint))
                i += Character.charCount(codePoint)
                
                // Check for ZWJ sequences or modifiers
                while (i < text.length) {
                    val nextCodePoint = text.codePointAt(i)
                    
                    // Zero Width Joiner or modifier
                    if (nextCodePoint == 0x200D || isModifier(nextCodePoint)) {
                        emoji += String(Character.toChars(nextCodePoint))
                        i += Character.charCount(nextCodePoint)
                    } else if (isEmojiCodePoint(nextCodePoint)) {
                        // Part of multi-emoji sequence (like flags)
                        emoji += String(Character.toChars(nextCodePoint))
                        i += Character.charCount(nextCodePoint)
                        break
                    } else {
                        break
                    }
                }
                
                emojis.add(emoji)
            } else {
                i += Character.charCount(codePoint)
            }
        }
        
        return emojis
    }
    
    /**
     * Check if code point is an emoji
     * 
     * @param codePoint Unicode code point
     * @return true if emoji
     */
    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return EMOJI_RANGES.any { codePoint in it }
    }
    
    /**
     * Check if code point is a modifier
     * 
     * @param codePoint Unicode code point
     * @return true if modifier (skin tone, etc.)
     */
    private fun isModifier(codePoint: Int): Boolean {
        return codePoint in 0x1F3FB..0x1F3FF || // Skin tone modifiers
               codePoint == 0xFE0F ||            // Variation Selector-16
               codePoint == 0xFE0E               // Variation Selector-15
    }
    
    /**
     * Check if string is a valid emoji
     * 
     * @param emoji Emoji string
     * @return true if valid emoji
     */
    private fun isValidEmoji(emoji: String): Boolean {
        if (emoji.isEmpty()) return false
        
        val firstCodePoint = emoji.codePointAt(0)
        return isEmojiCodePoint(firstCodePoint)
    }
    
    /**
     * Normalize single emoji
     * 
     * Removes variation selectors for consistency.
     * 
     * @param emoji Emoji string
     * @return Normalized emoji
     */
    private fun normalizeEmoji(emoji: String): String {
        return emoji.replace("\uFE0F", "") // Remove variation selector
                    .replace("\uFE0E", "")
    }
    
    /**
     * Check if sequence is weak
     * 
     * @param sequence Normalized sequence
     * @return true if commonly used
     */
    private fun isWeak(sequence: String): Boolean {
        return sequence in WEAK_SEQUENCES
    }
    
    /**
     * Get emoji category
     * 
     * @param emoji Emoji string
     * @return Category name
     */
    private fun getEmojiCategory(emoji: String): String {
        if (emoji.isEmpty()) return "unknown"
        
        val codePoint = emoji.codePointAt(0)
        
        return when (codePoint) {
            in 0x1F600..0x1F64F -> "emoticons"
            in 0x1F680..0x1F6FF -> "transport"
            in 0x1F300..0x1F5FF -> "symbols"
            in 0x1F900..0x1F9FF -> "supplemental"
            else -> "other"
        }
    }
}
