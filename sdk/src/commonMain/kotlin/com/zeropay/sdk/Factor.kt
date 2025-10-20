package com.zeropay.sdk

/**
 * Authentication Factor Types - PRODUCTION VERSION
 * 
 * PSD3 SCA Categories:
 * - Knowledge: Something you know (PIN, Password, Words, Colors, Emojis)
 * - Possession: Something you have (Device, Token, NFC)
 * - Inherence: Something you are (Biometrics, Behavioral patterns)
 * 
 * GDPR Compliance:
 * - All factor digests are irreversible SHA-256 hashes
 * - No raw biometric data stored or transmitted
 * - User explicit consent required for enrollment
 * 
 * Security:
 * - Each factor produces 32-byte SHA-256 digest
 * - Constant-time validation where applicable
 * - Rate limiting enforced server-side
 * - Zero-knowledge proof generation (Week 2)
 * 
 * Version: 1.0.0
 * Last Updated: 2025-01-08
 */
enum class Factor(
    val category: Category,
    val displayName: String,
    val description: String,
    val icon: String,
    val securityLevel: SecurityLevel,
    val convenienceLevel: ConvenienceLevel,
    val minLength: Int,
    val maxLength: Int,
    val requiresHardware: Boolean = false,
    val requiresPermission: String? = null
) {
    // ==================== KNOWLEDGE FACTORS ====================
    // Something you know - cognitive secrets
    
    /**
     * PIN Code (4-12 digits)
     * Security: Medium (brute-forceable)
     * Convenience: High (fast to enter)
     * PSD3: Knowledge factor
     */
    PIN(
        category = Category.KNOWLEDGE,
        displayName = "PIN Code",
        description = "Enter your secret PIN code (4-12 digits)",
        icon = "🔢",
        securityLevel = SecurityLevel.MEDIUM,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 4,
        maxLength = 12,
        requiresHardware = false
    ),
    
    /**
     * Color Sequence Selection
     * Security: Low-Medium (limited combinations)
     * Convenience: High (visual, fast)
     * PSD3: Knowledge factor
     */
    COLOUR(
        category = Category.KNOWLEDGE,
        displayName = "Color Sequence",
        description = "Select colors in the correct order",
        icon = "🎨",
        securityLevel = SecurityLevel.LOW_MEDIUM,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 3,
        maxLength = 6,
        requiresHardware = false
    ),
    
    /**
     * Emoji Sequence Selection
     * Security: Medium (more combinations than colors)
     * Convenience: High (fun, memorable)
     * PSD3: Knowledge factor
     */
    EMOJI(
        category = Category.KNOWLEDGE,
        displayName = "Emoji Sequence",
        description = "Choose emojis in the right order",
        icon = "😀",
        securityLevel = SecurityLevel.MEDIUM,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 3,
        maxLength = 8,
        requiresHardware = false
    ),
    
    /**
     * Word Selection (4 words from list)
     * Security: High (trillions of combinations)
     * Convenience: Medium (requires searching/selection)
     * PSD3: Knowledge factor
     */
    WORDS(
        category = Category.KNOWLEDGE,
        displayName = "Word Selection",
        description = "Select 4 memorable words from a list",
        icon = "📝",
        securityLevel = SecurityLevel.HIGH,
        convenienceLevel = ConvenienceLevel.MEDIUM,
        minLength = 3,
        maxLength = 10,
        requiresHardware = false
    ),
    
    // ==================== BEHAVIORAL FACTORS (Inherence) ====================
    // Something you are - biometric-like patterns
    
    /**
     * Pattern Drawing - Micro-Timing Analysis
     * Security: High (includes precise timing data)
     * Convenience: Medium (requires careful drawing)
     * PSD3: Inherence factor (behavioral biometric)
     * 
     * Analyzes:
     * - Draw pattern coordinates
     * - Velocity and acceleration
     * - Timing between points (microseconds)
     * - Pressure (if available)
     */
    PATTERN_MICRO(
        category = Category.INHERENCE,
        displayName = "Pattern (Micro-Timing)",
        description = "Draw pattern with precise timing analysis",
        icon = "✏️",
        securityLevel = SecurityLevel.HIGH,
        convenienceLevel = ConvenienceLevel.MEDIUM,
        minLength = 3,
        maxLength = 9,
        requiresHardware = false
    ),
    
    /**
     * Pattern Drawing - Normalized Timing
     * Security: Medium-High (speed-invariant)
     * Convenience: High (more forgiving)
     * PSD3: Inherence factor (behavioral biometric)
     * 
     * Analyzes:
     * - Draw pattern coordinates
     * - Normalized timing (speed-independent)
     * - Pattern shape and flow
     */
    PATTERN_NORMAL(
        category = Category.INHERENCE,
        displayName = "Pattern (Normalized)",
        description = "Draw pattern with normalized timing",
        icon = "✏️",
        securityLevel = SecurityLevel.MEDIUM_HIGH,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 3,
        maxLength = 9,
        requiresHardware = false
    ),
    
    /**
     * Mouse Movement Drawing
     * Security: Medium-High (unique movement patterns)
     * Convenience: Low (requires mouse/trackpad)
     * PSD3: Inherence factor (behavioral biometric)
     * 
     * Analyzes:
     * - Mouse movement trajectory
     * - Velocity and acceleration patterns
     * - Timing characteristics
     */
    MOUSE_DRAW(
        category = Category.INHERENCE,
        displayName = "Mouse Drawing",
        description = "Draw with mouse - unique movement pattern",
        icon = "🖱️",
        securityLevel = SecurityLevel.MEDIUM_HIGH,
        convenienceLevel = ConvenienceLevel.LOW,
        minLength = 3,
        maxLength = 20,
        requiresHardware = true // Mouse/trackpad
    ),
    
    /**
     * Stylus Drawing with Pressure
     * Security: Very High (pressure + movement biometric)
     * Convenience: Medium (requires stylus)
     * PSD3: Inherence factor (behavioral biometric)
     * 
     * Analyzes:
     * - Stylus trajectory
     * - Pressure patterns (unique to individual)
     * - Velocity and timing
     * - Tilt angle (if supported)
     */
    STYLUS_DRAW(
        category = Category.INHERENCE,
        displayName = "Stylus Signature",
        description = "Sign with stylus - pressure-sensitive",
        icon = "🖊️",
        securityLevel = SecurityLevel.VERY_HIGH,
        convenienceLevel = ConvenienceLevel.MEDIUM,
        minLength = 3,
        maxLength = 20,
        requiresHardware = true, // Stylus support
        requiresPermission = null
    ),
    
    /**
     * Voice Recording Authentication
     * Security: Very High (voice biometric)
     * Convenience: Medium (requires speaking)
     * PSD3: Inherence factor (biometric)
     * 
     * GDPR: Audio deleted immediately after hashing
     * Only SHA-256 hash stored, never raw audio
     */
    VOICE(
        category = Category.INHERENCE,
        displayName = "Voice Recognition",
        description = "Speak your passphrase",
        icon = "🎤",
        securityLevel = SecurityLevel.VERY_HIGH,
        convenienceLevel = ConvenienceLevel.MEDIUM,
        minLength = 3,
        maxLength = 10,
        requiresHardware = true, // Microphone
        requiresPermission = "android.permission.RECORD_AUDIO"
    ),
    
    /**
     * Image Tap Points
     * Security: Medium-High (spatial memory)
     * Convenience: High (visual, intuitive)
     * PSD3: Knowledge/Inherence hybrid
     * 
     * GDPR: Only pre-approved abstract images
     * No personal photos allowed
     */
    IMAGE_TAP(
        category = Category.INHERENCE,
        displayName = "Image Tap Points",
        description = "Tap specific points on an image",
        icon = "🖼️",
        securityLevel = SecurityLevel.MEDIUM_HIGH,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 4,
        maxLength = 16,
        requiresHardware = false
    ),
    
    /**
     * Balance/Tilt Pattern
     * Security: Medium (device movement pattern)
     * Convenience: Medium (requires steady holding)
     * PSD3: Inherence factor (behavioral)
     * 
     * Analyzes:
     * - Accelerometer data during balancing
     * - Unique hand tremor patterns
     * - Tilt characteristics
     */
    BALANCE(
        category = Category.INHERENCE,
        displayName = "Balance Pattern",
        description = "Hold device steady - balance authentication",
        icon = "⚖️",
        securityLevel = SecurityLevel.MEDIUM,
        convenienceLevel = ConvenienceLevel.MEDIUM,
        minLength = 10,
        maxLength = 100,
        requiresHardware = true // Accelerometer
    ),

    /**
     * Rhythm Tap Authentication
     * Security: Medium-High (timing variations create ~10^6 combinations)
     * Convenience: Very High (3-5s, intuitive, fun)
     * PSD3: Inherence factor (behavioral biometric)
     * 
     * GDPR Compliance:
     * - Only interval timing stored as irreversible hash
     * - No audio recording or precise biometric data
     * - User taps a simple rhythm (4-6 taps)
     * - Intervals hashed with nonce for replay protection
     * 
     * Security Features:
     * - High entropy from millisecond-level interval variations
     * - Behavioral biometric (hard to replicate exact timing)
     * - Constant-time verification
     * - Memory wiping after digest generation
     * 
     * Use Cases:
     * - Fun, intuitive authentication (like tapping to music)
     * - Low cognitive load (no memorization required)
     * - Accessible (no fine motor skills needed)
     * - Quick (3-5 second input)
     * 
     * Recommended Combinations:
     * - RHYTHM_TAP + PIN (high security)
     * - RHYTHM_TAP + EMOJI (balanced)
     * - RHYTHM_TAP + FINGERPRINT (very high security)
     */
    RHYTHM_TAP(
        category = Category.INHERENCE,
        displayName = "Rhythm Tap",
        description = "Tap a simple rhythm (4-6 taps)",
        icon = "🎵",
        securityLevel = SecurityLevel.MEDIUM_HIGH,
        convenienceLevel = ConvenienceLevel.VERY_HIGH,
        minLength = 3,
        maxLength = 12,
        requiresHardware = false, // Works on any touchscreen
        requiresPermission = null
    ),
    

    // ==================== POSSESSION FACTORS ====================
    // Something you have - physical tokens
    
    /**
     * NFC Tag Authentication
     * Security: High (physical token required)
     * Convenience: High (just tap)
     * PSD3: Possession factor
     * 
     * Uses NFC tag UID as authentication token
     */
    NFC(
        category = Category.POSSESSION,
        displayName = "NFC Tag",
        description = "Tap your NFC tag or card",
        icon = "📱",
        securityLevel = SecurityLevel.HIGH,
        convenienceLevel = ConvenienceLevel.HIGH,
        minLength = 4,
        maxLength = 32,
        requiresHardware = true, // NFC chip
        requiresPermission = "android.permission.NFC"
    ),
    
    // ==================== BIOMETRIC FACTORS ====================
    // Something you are - true biometrics (hardware-backed)
    
    /**
     * Fingerprint Biometric
     * Security: Very High (hardware biometric)
     * Convenience: Very High (instant)
     * PSD3: Inherence factor (biometric)
     * 
     * GDPR: Uses Android BiometricPrompt
     * Biometric data never leaves device
     * Only hash stored in secure enclave
     */
    FINGERPRINT(
        category = Category.INHERENCE,
        displayName = "Fingerprint",
        description = "Scan your fingerprint",
        icon = "👆",
        securityLevel = SecurityLevel.VERY_HIGH,
        convenienceLevel = ConvenienceLevel.VERY_HIGH,
        minLength = 1,
        maxLength = 1,
        requiresHardware = true, // Fingerprint sensor
        requiresPermission = "android.permission.USE_BIOMETRIC"
    ),
    
    /**
     * Face Recognition Biometric
     * Security: Very High (hardware biometric)
     * Convenience: Very High (instant)
     * PSD3: Inherence factor (biometric)
     * 
     * GDPR: Uses Android BiometricPrompt
     * Face data never leaves device
     * Only hash stored in secure enclave
     */
    FACE(
        category = Category.INHERENCE,
        displayName = "Face Recognition",
        description = "Scan your face",
        icon = "👤",
        securityLevel = SecurityLevel.VERY_HIGH,
        convenienceLevel = ConvenienceLevel.VERY_HIGH,
        minLength = 1,
        maxLength = 1,
        requiresHardware = true, // Face unlock hardware
        requiresPermission = "android.permission.USE_BIOMETRIC"
    );
    
    // ==================== ENUMS & DATA CLASSES ====================
    
    /**
     * PSD3 SCA Factor Categories
     */
    enum class Category(val displayName: String, val description: String) {
        /**
         * Knowledge Factor - Something you know
         * Examples: PIN, password, colors, emojis, words
         */
        KNOWLEDGE(
            displayName = "Something You Know",
            description = "Cognitive secrets like PINs, passwords, or patterns"
        ),
        
        /**
         * Possession Factor - Something you have
         * Examples: Device, NFC tag, hardware token, SMS OTP
         */
        POSSESSION(
            displayName = "Something You Have",
            description = "Physical devices or tokens in your possession"
        ),
        
        /**
         * Inherence Factor - Something you are
         * Examples: Fingerprint, face, voice, behavioral patterns
         */
        INHERENCE(
            displayName = "Something You Are",
            description = "Biometric or behavioral characteristics unique to you"
        )
    }
    
    /**
     * Security Level Rating (1-5 scale)
     */
    enum class SecurityLevel(val score: Int, val displayName: String) {
        LOW(1, "Low"),
        LOW_MEDIUM(2, "Low-Medium"),
        MEDIUM(3, "Medium"),
        MEDIUM_HIGH(4, "Medium-High"),
        HIGH(5, "High"),
        VERY_HIGH(6, "Very High")
    }
    
    /**
     * Convenience Level Rating (1-5 scale)
     */
    enum class ConvenienceLevel(val score: Int, val displayName: String) {
        VERY_LOW(1, "Very Low"),
        LOW(2, "Low"),
        MEDIUM(3, "Medium"),
        HIGH(4, "High"),
        VERY_HIGH(5, "Very High")
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Check if this factor is a biometric (hardware-backed)
     */
    fun isBiometric(): Boolean {
        return this == FINGERPRINT || this == FACE
    }
    
    /**
     * Check if this factor is behavioral (pattern-based)
     */
    fun isBehavioral(): Boolean {
        return this in setOf(
            PATTERN_MICRO, PATTERN_NORMAL,
            MOUSE_DRAW, STYLUS_DRAW,
            VOICE, BALANCE
        )
    }
    
    /**
     * Check if this factor is knowledge-based
     */
    fun isKnowledge(): Boolean {
        return category == Category.KNOWLEDGE
    }
    
    /**
     * Get combined security score (0-100)
     * Considers both security level and convenience
     */
    fun getCombinedScore(securityWeight: Float = 0.7f): Int {
        val securityScore = securityLevel.score * 100 / 6
        val convenienceScore = convenienceLevel.score * 100 / 5
        return (securityScore * securityWeight + convenienceScore * (1 - securityWeight)).toInt()
    }
    
    /**
     * Check if this factor is compatible with another for 2FA
     * PSD3 requires independent factors from different categories
     */
    fun isCompatibleWith(other: Factor): Boolean {
        // Same factor = not compatible
        if (this == other) return false

        // For PSD3 compliance, should be from different categories
        // But we allow same category if they're sufficiently different
        return when {
            // Different categories = always compatible
            this.category != other.category -> true

            // Same category behavioral factors = check if different types
            this.isBehavioral() && other.isBehavioral() -> {
                // Pattern + Voice = OK
                // Pattern + Mouse = OK
                // Mouse + Voice = OK
                // Pattern_Micro + Pattern_Normal = NOT OK (too similar)
                !(this == PATTERN_MICRO && other == PATTERN_NORMAL ||
                  this == PATTERN_NORMAL && other == PATTERN_MICRO)
            }

            // Same category knowledge factors = OK if different types
            this.isKnowledge() && other.isKnowledge() -> {
                // PIN + WORDS = OK (very different)
                // COLOUR + EMOJI = OK (different)
                true
            }

            // Different biometrics = OK
            this.isBiometric() && other.isBiometric() -> true

            else -> true
        }
    }

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

    /**
     * Validate value for this factor type
     */
    fun validate(value: String): FactorValidationResult {
        // Check length
        if (!isValidLength(value)) {
            return FactorValidationResult(
                isValid = false,
                errorMessage = getValidationMessage(),
                warnings = emptyList(),
                strength = 0
            )
        }

        // Type-specific validation
        val warnings = mutableListOf<String>()

        when (this) {
            PIN -> {
                if (!value.all { it.isDigit() }) {
                    return FactorValidationResult(
                        isValid = false,
                        errorMessage = "PIN must contain only digits",
                        warnings = emptyList(),
                        strength = 0
                    )
                }
                if (isWeakPin(value)) {
                    warnings.add("PIN appears to be weak (common pattern)")
                }
            }
            COLOUR -> {
                val colors = value.split(",")
                if (!colors.all { it.matches(Regex("#[0-9A-Fa-f]{6}")) }) {
                    return FactorValidationResult(
                        isValid = false,
                        errorMessage = "Colors must be in #RRGGBB format separated by commas",
                        warnings = emptyList(),
                        strength = 0
                    )
                }
            }
            else -> { /* Other type-specific validation in processors */ }
        }

        val strength = calculateStrength(value)

        return FactorValidationResult(
            isValid = true,
            errorMessage = null,
            warnings = warnings,
            strength = strength
        )
    }

    /**
     * Calculate security strength score (0-100)
     */
    private fun calculateStrength(value: String): Int {
        var score = 50 // Base score

        // Length bonus
        val lengthRatio = value.length.toDouble() / maxLength
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

        // Security level bonus
        score += securityLevel.score * 5

        // Weak pattern penalty
        if (isWeak(value)) score -= 30

        return score.coerceIn(0, 100)
    }

    /**
     * Check if this is a weak/common factor
     */
    fun isWeak(value: String): Boolean {
        return when (this) {
            PIN -> isWeakPin(value)
            PATTERN_MICRO, PATTERN_NORMAL -> isWeakPattern(value)
            VOICE, WORDS -> isWeakPhrase(value)
            else -> false
        }
    }

    private fun isWeakPin(pin: String): Boolean {
        val weakPins = setOf(
            "0000", "1111", "2222", "3333", "4444",
            "5555", "6666", "7777", "8888", "9999",
            "1234", "4321", "1212", "1122",
            "000000", "111111", "222222", "333333", "444444",
            "555555", "666666", "777777", "888888", "999999",
            "123456", "654321", "123123", "112233"
        )
        return pin in weakPins
    }

    private fun isWeakPattern(pattern: String): Boolean {
        if (pattern.length < 3) return true

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
            "welcome", "monkey", "dragon", "master",
            "hello", "test", "admin", "user"
        )
        return phrase.lowercase() in weakPhrases
    }
    
    companion object {
        /**
         * Get recommended factor combinations for 2FA/3FA
         * Based on PSD3 SCA requirements and security best practices
         */
        fun getRecommendedCombinations(
            factorCount: Int = 2,
            preferHighSecurity: Boolean = true
        ): List<List<Factor>> {
            require(factorCount in 2..4) { "Factor count must be 2-4" }
            
            // Predefined high-security combinations
            val highSecurityCombos = listOf(
                // 2-factor combinations
                listOf(FINGERPRINT, PIN),
                listOf(FACE, PIN),
                listOf(VOICE, PIN),
                listOf(PATTERN_MICRO, FINGERPRINT),
                listOf(WORDS, FACE),
                
                // 3-factor combinations
                listOf(FINGERPRINT, PIN, PATTERN_MICRO),
                listOf(FACE, WORDS, VOICE),
                listOf(NFC, PIN, FINGERPRINT)
            )
            
            // Balanced security/convenience combinations
            val balancedCombos = listOf(
                listOf(PIN, COLOUR),
                listOf(EMOJI, PATTERN_NORMAL),
                listOf(FACE, COLOUR),
                listOf(FINGERPRINT, EMOJI)
            )
            
            val combos = if (preferHighSecurity) highSecurityCombos else balancedCombos
            
            return combos.filter { it.size == factorCount }
        }
        
        /**
         * Get all factors by category
         */
        fun getByCategory(category: Category): List<Factor> {
            return values().filter { it.category == category }
        }
        
        /**
         * Get factors that don't require special hardware
         */
        fun getUniversalFactors(): List<Factor> {
            return values().filter { !it.requiresHardware }
        }
        
        /**
         * Get factors that require permissions
         */
        fun getFactorsRequiringPermissions(): Map<String, List<Factor>> {
            return values()
                .filter { it.requiresPermission != null }
                .groupBy { it.requiresPermission!! }
        }
    }
}

/**
 * FactorDigest - Hashed factor for storage/comparison
 *
 * @property factor Factor type
 * @property digest SHA-256 digest (32 bytes)
 * @property timestamp When digest was created
 * @property metadata Additional metadata
 */
data class FactorDigest(
    val factor: Factor,
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

        if (factor != other.factor) return false
        if (!digest.contentEquals(other.digest)) return false
        if (timestamp != other.timestamp) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = factor.hashCode()
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
 * - Minimum 2 factors (configurable)
 * - From at least 2 different categories
 *
 * @property factors List of factors
 * @property minFactors Minimum number of factors (default: 2)
 */
data class FactorSet(
    val factors: List<Factor>,
    val minFactors: Int = 2
) {
    init {
        require(factors.size >= minFactors) {
            "Minimum $minFactors factors required (PSD3 SCA)"
        }

        val categories = factors.map { it.category }.toSet()
        require(categories.size >= 2) {
            "Factors must be from at least 2 different categories (PSD3 SCA)"
        }
    }

    /**
     * Get factor by type
     */
    fun getByType(factor: Factor): Factor? {
        return factors.find { it == factor }
    }

    /**
     * Get factors by category
     */
    fun getByCategory(category: Factor.Category): List<Factor> {
        return factors.filter { it.category == category }
    }

    /**
     * Check if set contains weak factors (based on validation)
     */
    fun hasWeakFactors(values: Map<Factor, String>): Boolean {
        return factors.any { factor ->
            val value = values[factor] ?: return@any false
            factor.isWeak(value)
        }
    }

    /**
     * Get overall strength score
     */
    fun getOverallStrength(values: Map<Factor, String>): Int {
        if (factors.isEmpty()) return 0
        val scores = factors.mapNotNull { factor ->
            val value = values[factor] ?: return@mapNotNull null
            factor.validate(value).strength
        }
        return if (scores.isEmpty()) 0 else scores.sum() / scores.size
    }

    /**
     * Get category distribution
     */
    fun getCategoryDistribution(): Map<Factor.Category, Int> {
        return factors.groupBy { it.category }
            .mapValues { it.value.size }
    }

    /**
     * Validate all factors in the set
     */
    fun validateAll(values: Map<Factor, String>): Map<Factor, FactorValidationResult> {
        return factors.associateWith { factor ->
            val value = values[factor]
            if (value == null) {
                FactorValidationResult(
                    isValid = false,
                    errorMessage = "Value missing for factor ${factor.displayName}",
                    warnings = emptyList(),
                    strength = 0
                )
            } else {
                factor.validate(value)
            }
        }
    }
}

/**
 * FactorInput - User input for factor processing
 *
 * Represents a factor value provided by the user during enrollment/verification.
 *
 * @property type The factor type (from Factor enum)
 * @property value The raw value provided by the user
 * @property metadata Optional metadata (e.g., device info, timestamp)
 */
data class FactorInput(
    val type: Factor,
    val value: String,
    val metadata: Map<String, String> = emptyMap()
)

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
