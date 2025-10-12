// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/models/Factor.kt

package com.zeropay.sdk.models

/**
 * Factor - Authentication Factor Model (Enhanced)
 * 
 * Represents a single authentication factor with validation rules.
 * 
 * Supported Factor Types:
 * - PATTERN: Draw pattern (min 3 strokes, max 9)
 * - EMOJI: Select emojis (min 3, max 8)
 * - COLOR: Select colors (min 2, max 6)
 * - VOICE: Voice phrase (min 3 words, max 10)
 * - PIN: Numeric PIN (exactly 6 digits)
 * - GESTURE: Gesture sequence (min 3, max 8)
 * - IMAGE_PATTERN: Image grid selection (min 4, max 16)
 * - RHYTHM: Tap rhythm pattern (min 3 taps, max 12)
 * - SHAPE: Shape drawing (min 3 points, max 20)
 * - SOUND: Sound pattern (min 2 sounds, max 8)
 * - LOCATION_HASH: Location coordinate hash (NOT raw location)
 * - CUSTOM: Custom factor type
 * 
 * PSD3 Categories:
 * - Knowledge: PIN, VOICE, PATTERN
 * - Possession: LOCATION_HASH
 * - Inherence: GESTURE, RHYTHM
 * 
 * Security:
 * - Raw data NEVER stored
 * - Only SHA-256 digests cached (24h TTL)
 * - Constant-time comparison
 * - Memory wiping after use
 * 
 * @version 2.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
enum class FactorType(
    val displayName: String,
    val category: FactorCategory,
    val minLength: Int,
    val maxLength: Int,
    val icon: String
) {
    PATTERN(
        displayName = "Pattern",
        category = FactorCategory.KNOWLEDGE,
        minLength = 3,
        maxLength = 9,
        icon = "🔢"
    ),
    
    EMOJI(
        displayName = "Emoji Sequence",
        category = FactorCategory.KNOWLEDGE,
        minLength = 3,
        maxLength = 8,
        icon = "😀"
    ),
    
    COLOR(
        displayName = "Color Sequence",
        category = FactorCategory.KNOWLEDGE,
        minLength = 2,
        maxLength = 6,
        icon = "🎨"
    ),
    
    VOICE(
        displayName = "Voice Phrase",
        category = FactorCategory.KNOWLEDGE,
        minLength = 3,
        maxLength = 10,
        icon = "🎤"
    ),
    
    PIN(
        displayName = "PIN Code",
        category = FactorCategory.KNOWLEDGE,
        minLength = 6,
        maxLength = 6,
        icon = "🔐"
    ),
    
    GESTURE(
        displayName = "Gesture",
        category = FactorCategory.INHERENCE,
        minLength = 3,
        maxLength = 8,
        icon = "👆"
    ),
    
    IMAGE_PATTERN(
        displayName = "Image Pattern",
        category = FactorCategory.KNOWLEDGE,
        minLength = 4,
        maxLength = 16,
        icon = "🖼️"
    ),
    
    RHYTHM(
        displayName = "Tap Rhythm",
        category = FactorCategory.INHERENCE,
        minLength = 3,
        maxLength = 12,
        icon = "🥁"
    ),
    
    SHAPE(
        displayName = "Shape Drawing",
        category = FactorCategory.INHERENCE,
        minLength = 3,
        maxLength = 20,
        icon = "✏️"
    ),
    
    SOUND(
        displayName = "Sound Pattern",
        category = FactorCategory.KNOWLEDGE,
        minLength = 2,
        maxLength = 8,
        icon = "🔊"
    ),
    
    LOCATION_HASH(
        displayName = "Location Hash",
        category = FactorCategory.POSSESSION,
        minLength = 32,
        maxLength = 32,
        icon = "📍"
    ),
    
    CUSTOM(
        displayName = "Custom Factor",
        category = FactorCategory.KNOWLEDGE,
        minLength = 1,
        maxLength = 100,
        icon = "⚙️"
    );
    
    /**
     * Check if value length is within acceptable range
     */
    fun isValidLength(value: String): Boolean {
        return value.length in minLength..maxLength
    }
    
    /**
     * Get validation error message
     */
    fun getValidationMessage(): String {
        return when {
            minLength == maxLength -> "Must be exactly $minLength characters"
            else -> "Must be between $minLength and $maxLength characters"
        }
    }
}

/**
 * FactorCategory - PSD3 SCA Categories
 * 
 * Strong Customer Authentication requires 2+ factors from 2+ categories.
 * 
 * Categories:
 * - KNOWLEDGE: Something you know (PIN, pattern, phrase)
 * - POSSESSION: Something you have (device, token, location)
 * - INHERENCE: Something you are (biometric, behavior)
 */
enum class FactorCategory {
    KNOWLEDGE,   // Something you know
    POSSESSION,  // Something you have
    INHERENCE    // Something you are (behavioral, NOT raw biometric)
}

/**
 * Factor - Complete factor data with metadata
 * 
 * @property type Factor type
 * @property value Factor value (will be hashed before storage)
 * @property displayName Optional custom display name
 * @property metadata Additional metadata
 */
data class Factor(
    val type: FactorType,
    val value: String,
    val displayName: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        // Validate value length
        require(type.isValidLength(value)) {
            "${type.displayName} ${type.getValidationMessage()}"
        }
        
        // Additional validation per type
        when (type) {
            FactorType.PIN -> {
                require(value.all { it.isDigit() }) {
                    "PIN must contain only digits"
                }
            }
            FactorType.COLOR -> {
                require(value.split(",").all { it.matches(Regex("#[0-9A-Fa-f]{6}")) }) {
                    "Colors must be in #RRGGBB format separated by commas"
                }
            }
            FactorType.LOCATION_HASH -> {
                require(value.length == 32 && value.all { it.isLetterOrDigit() }) {
                    "Location hash must be 32 character hex string"
                }
            }
            else -> { /* Type-specific validation in processors */ }
        }
    }
    
    /**
     * Get effective display name
     */
    fun getDisplayName(): String {
        return displayName ?: type.displayName
    }
    
    /**
     * Check if this is a weak/common factor
     */
    fun isWeak(): Boolean {
        return when (type) {
            FactorType.PIN -> isWeakPin(value)
            FactorType.PATTERN -> isWeakPattern(value)
            FactorType.VOICE -> isWeakPhrase(value)
            else -> false
        }
    }
    
    /**
     * Get security strength score (0-100)
     */
    fun getStrength(): Int {
        var score = 50 // Base score
        
        // Length bonus
        val lengthRatio = value.length.toDouble() / type.maxLength
        score += (lengthRatio * 20).toInt()
        
        // Complexity bonus
        val hasUpperCase = value.any { it.isUpperCase() }
        val hasLowerCase = value.any { it.isLowerCase() }
        val hasDigits = value.any { it.isDigit() }
        val hasSpecial = value.any { !it.isLetterOrDigit() }
        
        if (hasUpperCase) score += 5
        if (hasLowerCase) score += 5
        if (hasDigits) score += 5
        if (hasSpecial) score += 10
        
        // Weak pattern penalty
        if (isWeak()) score -= 30
        
        return score.coerceIn(0, 100)
    }
    
    private fun isWeakPin(pin: String): Boolean {
        val weakPins = setOf(
            "000000", "111111", "222222", "333333", "444444",
            "555555", "666666", "777777", "888888", "999999",
            "123456", "654321", "123123", "112233"
        )
        return pin in weakPins
    }
    
    private fun isWeakPattern(pattern: String): Boolean {
        // Check for sequential patterns
        val isSequential = pattern.zipWithNext().all { (a, b) -> 
            b.code - a.code == 1 
        }
        
        // Check for repeating patterns
        val isRepeating = pattern.all { it == pattern[0] }
        
        return isSequential || isRepeating
    }
    
    private fun isWeakPhrase(phrase: String): Boolean {
        val weakPhrases = setOf(
            "password", "123456", "qwerty", "letmein",
            "welcome", "monkey", "dragon", "master"
        )
        return phrase.lowercase() in weakPhrases
    }
}

/**
 * FactorDigest - Hashed factor for storage/comparison
 * 
 * @property type Factor type
 * @property digest SHA-256 digest (32 bytes)
 * @property timestamp When digest was created
 * @property metadata Additional metadata
 */
data class FactorDigest(
    val type: FactorType,
    val digest: ByteArray,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(digest.size == 32) { "Digest must be 32 bytes (SHA-256)" }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as FactorDigest
        
        if (type != other.type) return false
        if (!digest.contentEquals(other.digest)) return false
        if (timestamp != other.timestamp) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + digest.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * FactorValidationResult - Validation result
 * 
 * @property isValid Whether factor is valid
 * @property errorMessage Error message if invalid
 * @property warnings Warning messages (non-blocking)
 * @property strength Security strength score (0-100)
 */
data class FactorValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val strength: Int = 0
) {
    /**
     * Check if factor has warnings
     */
    fun hasWarnings(): Boolean = warnings.isNotEmpty()
    
    /**
     * Check if factor is strong (strength >= 70)
     */
    fun isStrong(): Boolean = strength >= 70
    
    /**
     * Check if factor is weak (strength < 40)
     */
    fun isWeak(): Boolean = strength < 40
}

/**
 * FactorSet - Collection of factors with validation
 * 
 * Ensures PSD3 SCA compliance:
 * - Minimum 2 factors
 * - From at least 2 different categories
 * 
 * @property factors List of factors
 */
data class FactorSet(
    val factors: List<Factor>
) {
    init {
        require(factors.size >= 2) {
            "Minimum 2 factors required (PSD3 SCA)"
        }
        
        val categories = factors.map { it.type.category }.toSet()
        require(categories.size >= 2) {
            "Factors must be from at least 2 different categories (PSD3 SCA)"
        }
    }
    
    /**
     * Get factor by type
     */
    fun getByType(type: FactorType): Factor? {
        return factors.find { it.type == type }
    }
    
    /**
     * Get factors by category
     */
    fun getByCategory(category: FactorCategory): List<Factor> {
        return factors.filter { it.type.category == category }
    }
    
    /**
     * Check if set contains weak factors
     */
    fun hasWeakFactors(): Boolean {
        return factors.any { it.isWeak() }
    }
    
    /**
     * Get overall strength score
     */
    fun getOverallStrength(): Int {
        if (factors.isEmpty()) return 0
        return factors.sumOf { it.getStrength() } / factors.size
    }
    
    /**
     * Get category distribution
     */
    fun getCategoryDistribution(): Map<FactorCategory, Int> {
        return factors.groupBy { it.type.category }
            .mapValues { it.value.size }
    }
}

/**
 * FactorException - Factor-related errors
 */
sealed class FactorException(message: String, cause: Throwable? = null) : 
    Exception(message, cause) {
    
    class ValidationException(message: String) : FactorException(message)
    class WeakFactorException(message: String) : FactorException(message)
    class InsufficientFactorsException(message: String) : FactorException(message)
    class InvalidCategoryDistributionException(message: String) : FactorException(message)
}
