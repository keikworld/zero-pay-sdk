package com.zeropay.sdk.config

/**
 * Security Configuration
 * 
 * Centralized configuration for all security parameters.
 * Thread-safe singleton with validation.
 */
object SecurityConfig {
    
    // Argon2 Parameters
    data class Argon2Config(
        val iterations: Int = 3,
        val memoryKB: Int = 65536,  // 64 MB
        val parallelism: Int = 4,
        val hashLength: Int = 32,
        val saltLength: Int = 16
    ) {
        fun validate(): Boolean {
            return iterations > 0 &&
                   memoryKB >= 8 &&
                   parallelism > 0 &&
                   hashLength in 16..64 &&
                   saltLength >= 8
        }
    }
    
    // Rate Limiting
    data class RateLimitConfig(
        val maxDailyAttempts: Int = 20,
        val cooldown15mThreshold: Int = 5,
        val cooldown4hThreshold: Int = 8,
        val frozenThreshold: Int = 10,
        val entryTTLHours: Int = 24,
        val maxEntries: Int = 10000
    ) {
        fun validate(): Boolean {
            return maxDailyAttempts > 0 &&
                   cooldown15mThreshold > 0 &&
                   cooldown4hThreshold > cooldown15mThreshold &&
                   frozenThreshold > cooldown4hThreshold &&
                   entryTTLHours > 0 &&
                   maxEntries > 0
        }
    }
    
    // Session Management
    data class SessionConfig(
        val sessionTTLMinutes: Int = 15,
        val maxActiveSessions: Int = 1000,
        val renewalThresholdMinutes: Int = 5
    ) {
        fun validate(): Boolean {
            return sessionTTLMinutes > 0 &&
                   maxActiveSessions > 0 &&
                   renewalThresholdMinutes > 0 &&
                   renewalThresholdMinutes < sessionTTLMinutes
        }
    }
    
    // Cryptography
    data class CryptoConfig(
        val hashAlgorithm: String = "SHA-256",
        val randomBytesLength: Int = 32,
        val saltLength: Int = 16,
        val hmacAlgorithm: String = "HmacSHA256",
        val aesKeySize: Int = 256
    ) {
        fun validate(): Boolean {
            return randomBytesLength >= 16 &&
                   saltLength >= 8 &&
                   aesKeySize in listOf(128, 192, 256)
        }
    }
    
    // Anti-Tampering
    data class TamperingConfig(
        val enableRootDetection: Boolean = true,
        val enableDebuggerDetection: Boolean = true,
        val enableEmulatorDetection: Boolean = false,
        val enableSafetyNet: Boolean = true,
        val blockOnTampering: Boolean = true
    )
    
    // Biometrics
    data class BiometricConfig(
        val enableFace: Boolean = true,
        val enableFingerprint: Boolean = true,
        val requireStrongBox: Boolean = false,
        val maxRetries: Int = 3,
        val timeoutSeconds: Int = 30
    ) {
        fun validate(): Boolean {
            return maxRetries > 0 &&
                   timeoutSeconds > 0
        }
    }
    
    // Pattern/Drawing
    data class PatternConfig(
        val minStrokes: Int = 3,
        val maxPoints: Int = 300,
        val throttleMs: Long = 20,
        val minTimingVariance: Float = 0.01f
    ) {
        fun validate(): Boolean {
            return minStrokes > 0 &&
                   maxPoints > minStrokes &&
                   throttleMs > 0 &&
                   minTimingVariance > 0
        }
    }
    
    // Voice Authentication
    data class VoiceConfig(
        val minDurationSeconds: Int = 2,
        val maxDurationSeconds: Int = 10,
        val sampleRate: Int = 8000,
        val enableLivenessDetection: Boolean = true
    ) {
        fun validate(): Boolean {
            return minDurationSeconds > 0 &&
                   maxDurationSeconds > minDurationSeconds &&
                   sampleRate in listOf(8000, 16000, 44100)
        }
    }
    
    // Current configuration (thread-safe)
    @Volatile var argon2 = Argon2Config()
        private set
    
    @Volatile var rateLimit = RateLimitConfig()
        private set
    
    @Volatile var session = SessionConfig()
        private set
    
    @Volatile var crypto = CryptoConfig()
        private set
    
    @Volatile var tampering = TamperingConfig()
        private set
    
    @Volatile var biometric = BiometricConfig()
        private set
    
    @Volatile var pattern = PatternConfig()
        private set
    
    @Volatile var voice = VoiceConfig()
        private set
    
    /**
     * Update configuration (thread-safe)
     */
    @Synchronized
    fun updateConfig(builder: ConfigBuilder.() -> Unit) {
        val configBuilder = ConfigBuilder()
        configBuilder.builder()
        configBuilder.validate()
        configBuilder.apply()
    }
    
    /**
     * Load configuration from properties
     */
    @Synchronized
    fun loadFromProperties(properties: Map<String, String>) {
        updateConfig {
            properties["argon2.iterations"]?.toIntOrNull()?.let {
                argon2 = argon2.copy(iterations = it)
            }
            properties["argon2.memoryKB"]?.toIntOrNull()?.let {
                argon2 = argon2.copy(memoryKB = it)
            }
            properties["rateLimit.maxDailyAttempts"]?.toIntOrNull()?.let {
                rateLimit = rateLimit.copy(maxDailyAttempts = it)
            }
            properties["session.ttlMinutes"]?.toIntOrNull()?.let {
                session = session.copy(sessionTTLMinutes = it)
            }
        }
    }
    
    /**
     * Validate all configuration
     */
    fun validateAll(): Boolean {
        return argon2.validate() &&
               rateLimit.validate() &&
               session.validate() &&
               crypto.validate() &&
               biometric.validate() &&
               pattern.validate() &&
               voice.validate()
    }
    
    /**
     * Reset to defaults
     */
    @Synchronized
    fun resetToDefaults() {
        argon2 = Argon2Config()
        rateLimit = RateLimitConfig()
        session = SessionConfig()
        crypto = CryptoConfig()
        tampering = TamperingConfig()
        biometric = BiometricConfig()
        pattern = PatternConfig()
        voice = VoiceConfig()
    }
    
    /**
     * Configuration builder
     */
    class ConfigBuilder internal constructor() {
        internal var argon2: Argon2Config = SecurityConfig.argon2
        internal var rateLimit: RateLimitConfig = SecurityConfig.rateLimit
        internal var session: SessionConfig = SecurityConfig.session
        internal var crypto: CryptoConfig = SecurityConfig.crypto
        internal var tampering: TamperingConfig = SecurityConfig.tampering
        internal var biometric: BiometricConfig = SecurityConfig.biometric
        internal var pattern: PatternConfig = SecurityConfig.pattern
        internal var voice: VoiceConfig = SecurityConfig.voice
        
        fun argon2(config: Argon2Config) {
            this.argon2 = config
        }
        
        fun rateLimit(config: RateLimitConfig) {
            this.rateLimit = config
        }
        
        fun session(config: SessionConfig) {
            this.session = config
        }
        
        fun crypto(config: CryptoConfig) {
            this.crypto = config
        }
        
        fun tampering(config: TamperingConfig) {
            this.tampering = config
        }
        
        fun biometric(config: BiometricConfig) {
            this.biometric = config
        }
        
        fun pattern(config: PatternConfig) {
            this.pattern = config
        }
        
        fun voice(config: VoiceConfig) {
            this.voice = config
        }
        
        internal fun validate() {
            require(argon2.validate()) { "Invalid Argon2 config" }
            require(rateLimit.validate()) { "Invalid rate limit config" }
            require(session.validate()) { "Invalid session config" }
            require(crypto.validate()) { "Invalid crypto config" }
            require(biometric.validate()) { "Invalid biometric config" }
            require(pattern.validate()) { "Invalid pattern config" }
            require(voice.validate()) { "Invalid voice config" }
        }
        
        internal fun apply() {
            SecurityConfig.argon2 = this.argon2
            SecurityConfig.rateLimit = this.rateLimit
            SecurityConfig.session = this.session
            SecurityConfig.crypto = this.crypto
            SecurityConfig.tampering = this.tampering
            SecurityConfig.biometric = this.biometric
            SecurityConfig.pattern = this.pattern
            SecurityConfig.voice = this.voice
        }
    }
}
