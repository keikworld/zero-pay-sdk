package com.zeropay.sdk.factors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.nfc.NfcAdapter
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.Factor
import com.zeropay.sdk.errors.FactorNotAvailableException
import com.zeropay.sdk.ui.*

/**
 * Factor Canvas Factory - PRODUCTION VERSION (REFACTORED)
 * 
 * Architecture:
 * - All Canvas UI components moved to sdk/ui/ package
 * - All Factor digest logic in sdk/factors/ (commonMain)
 * - Clean separation of concerns (UI vs Logic)
 * - Security features maintained at all levels
 * 
 * Security Features:
 * - Availability checks before rendering
 * - Permission validation
 * - Hardware capability detection
 * - DoS protection in all Canvas components
 * 
 * GDPR Compliance:
 * - User informed of data usage
 * - Zero-knowledge processing
 * - Privacy notices in all Canvas UIs
 * 
 * @author ZeroPay Security Team
 * @version 2.0.0 (Refactored)
 */
object FactorCanvasFactory {
    
    // ==================== MAIN CANVAS FACTORY ====================
    
    /**
     * Create Canvas UI for specified factor
     * 
     * Security:
     * - Validates factor availability before rendering
     * - Checks hardware requirements
     * - Verifies permissions
     * - Returns Canvas that produces SHA-256 digest
     * 
     * @param factor Factor type to create canvas for
     * @param context Android context (for hardware checks)
     * @param onDone Callback receiving SHA-256 digest (32 bytes)
     * @param modifier Compose modifier
     * 
     * @return Composable Canvas UI for the factor
     * @throws FactorNotAvailableException if factor not available on device
     */
    @Composable
    fun createCanvas(
        factor: Factor,
        context: Context,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Validate factor availability
        if (!FactorRegistry.isAvailable(context, factor)) {
            throw FactorNotAvailableException(
                factor = factor,
                reason = "Factor not available on this device"
            )
        }
        
        // Route to appropriate Canvas UI
        when (factor) {
            // ==================== KNOWLEDGE FACTORS ====================
            
            Factor.PIN -> {
                PinCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.COLOUR -> {
                ColourCanvas(
                    onSelected = onDone,
                    modifier = modifier
                )
            }
            
            Factor.EMOJI -> {
                EmojiCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.WORDS -> {
                WordsCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            // ==================== BEHAVIORAL FACTORS ====================
            
            Factor.PATTERN_MICRO -> {
                PatternCanvas(
                    onDone = { points ->
                        // Generate micro-timing digest
                        val digest = PatternFactor.digestMicroTiming(points)
                        onDone(digest)
                    },
                    modifier = modifier
                )
            }
            
            Factor.PATTERN_NORMAL -> {
                PatternCanvas(
                    onDone = { points ->
                        // Generate normalized timing digest
                        val digest = PatternFactor.digestNormalisedTiming(points)
                        onDone(digest)
                    },
                    modifier = modifier
                )
            }
            
            Factor.MOUSE_DRAW -> {
                MouseCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.STYLUS_DRAW -> {
                StylusCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.RHYTHM_TAP -> {
                RhythmTapCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.VOICE -> {
                VoiceCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.IMAGE_TAP -> {
                ImageTapCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            Factor.BALANCE -> {
                BalanceCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            // ==================== POSSESSION FACTORS ====================
            
            Factor.NFC -> {
                NfcCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            // ==================== BIOMETRIC FACTORS ====================
            
            Factor.FINGERPRINT -> {
                // Note: Fingerprint uses platform BiometricPrompt, not a Canvas
                throw FactorNotAvailableException(
                    factor = factor,
                    reason = "Fingerprint uses BiometricPrompt, not Canvas UI"
                )
            }
            
            Factor.FACE -> {
                // Note: Face uses platform BiometricPrompt, not a Canvas
                throw FactorNotAvailableException(
                    factor = factor,
                    reason = "Face uses BiometricPrompt, not Canvas UI"
                )
            }
        }
    }
    
    // ==================== VALIDATION ====================
    
    /**
     * Validation result for factor requirements
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )
    
    /**
     * Validate canvas requirements are met
     * 
     * Checks:
     * - Permissions granted
     * - Hardware available
     * - Factor enabled
     * 
     * @param factor Factor to validate
     * @param context Android context
     * @return ValidationResult with error message if invalid
     */
    fun validateRequirements(
        factor: Factor,
        context: Context
    ): ValidationResult {
        // Check if factor requires hardware
        if (factor.requiresHardware) {
            val available = FactorRegistry.checkAvailability(context, factor)
            if (!available.isAvailable) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = available.reason ?: "Hardware not available"
                )
            }
        }
        
        // Check permissions
        factor.requiresPermission?.let { permission ->
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Permission required: ${getPermissionDisplayName(permission)}"
                )
            }
        }
        
        // Additional factor-specific checks
        return when (factor) {
            Factor.NFC -> {
                val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
                when {
                    nfcAdapter == null -> ValidationResult(
                        isValid = false,
                        errorMessage = "NFC not available on this device"
                    )
                    !nfcAdapter.isEnabled -> ValidationResult(
                        isValid = false,
                        errorMessage = "Please enable NFC in settings"
                    )
                    else -> ValidationResult(isValid = true)
                }
            }
            
            Factor.BALANCE -> {
                val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                if (accelerometer == null) {
                    ValidationResult(
                        isValid = false,
                        errorMessage = "Accelerometer not available"
                    )
                } else {
                    ValidationResult(isValid = true)
                }
            }
            
            Factor.VOICE -> {
                if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)) {
                    ValidationResult(isValid = true)
                } else {
                    ValidationResult(
                        isValid = false,
                        errorMessage = "Microphone not available"
                    )
                }
            }
            
            Factor.FINGERPRINT, Factor.FACE -> {
                val biometricManager = BiometricManager.from(context)
                val canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                )
                
                when (canAuthenticate) {
                    BiometricManager.BIOMETRIC_SUCCESS -> ValidationResult(isValid = true)
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> ValidationResult(
                        isValid = false,
                        errorMessage = "Biometric hardware not available"
                    )
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> ValidationResult(
                        isValid = false,
                        errorMessage = "Biometric hardware unavailable"
                    )
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> ValidationResult(
                        isValid = false,
                        errorMessage = "No biometric enrolled. Please enroll in settings."
                    )
                    else -> ValidationResult(
                        isValid = false,
                        errorMessage = "Biometric authentication not available"
                    )
                }
            }
            
            else -> ValidationResult(isValid = true)
        }
    }
    
    // ==================== UI HELPER METHODS ====================
    
    /**
     * Get user-friendly instructions for factor
     * Displayed in Canvas UI
     * 
     * @param factor Factor to get instructions for
     * @return Instruction text
     */
    fun getInstructions(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "Enter your secret PIN code (4-12 digits)"
            Factor.COLOUR -> "Select colors in your chosen order"
            Factor.EMOJI -> "Choose emojis you'll remember easily"
            Factor.WORDS -> "Select 4 memorable words from the list"
            
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL -> 
                "Draw your unique pattern (at least 3 strokes)"
            Factor.MOUSE_DRAW -> "Draw a signature with your mouse or trackpad"
            Factor.STYLUS_DRAW -> "Draw with your stylus (pressure is part of security)"
            Factor.RHYTHM_TAP -> "Tap out your unique rhythm (4-6 taps)"
            Factor.VOICE -> "Speak clearly into the microphone"
            Factor.IMAGE_TAP -> "Tap the same 2 points on the image"
            Factor.BALANCE -> "Hold your device steady for 3 seconds"
            
            Factor.NFC -> "Tap your NFC tag or card to the back of your device"
            
            Factor.FACE -> "Position your face in front of the camera"
            Factor.FINGERPRINT -> "Place your finger on the sensor"
        }
    }
    
    /**
     * Get security tips for factor
     * Displayed during enrollment
     * 
     * @param factor Factor to get tips for
     * @return Security tip text
     */
    fun getSecurityTip(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "Choose a PIN that isn't your birthday or sequential numbers (1234)"
            Factor.COLOUR -> "Pick colors that have personal meaning but aren't obvious"
            Factor.EMOJI -> "Choose emojis you'll remember easily"
            Factor.WORDS -> "Select words that form a memorable story or phrase"
            Factor.RHYTHM_TAP -> "Vary your tap timing - don't tap evenly like a metronome"
            
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL -> 
                "Draw naturally - your unique drawing style is part of security"
            Factor.MOUSE_DRAW -> "Draw a simple signature - consistency matters more than complexity"
            Factor.STYLUS_DRAW -> "Your pressure pattern is unique - draw naturally"
            Factor.VOICE -> "Speak in your normal voice - don't try to disguise it"
            Factor.IMAGE_TAP -> "Choose points you'll easily remember"
            Factor.BALANCE -> "Hold the device the same way each time"
            
            Factor.NFC -> "Keep your NFC tag secure - treat it like a key"
            
            Factor.FACE, Factor.FINGERPRINT -> 
                "Your biometric data never leaves your device and cannot be recovered from the hash"
        }
    }
    
    /**
     * Get estimated completion time for factor
     * Used for UX planning and timeouts
     * 
     * @param factor Factor to estimate
     * @return Estimated time in milliseconds
     */
    fun getEstimatedCompletionTime(factor: Factor): Long {
        return when (factor) {
            Factor.PIN -> 5_000L              // 5 seconds
            Factor.COLOUR -> 3_000L           // 3 seconds
            Factor.EMOJI -> 5_000L            // 5 seconds
            Factor.WORDS -> 15_000L           // 15 seconds (searching)
            
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL -> 5_000L  // 5 seconds
            Factor.MOUSE_DRAW -> 8_000L       // 8 seconds
            Factor.STYLUS_DRAW -> 8_000L      // 8 seconds
            Factor.RHYTHM_TAP -> 5_000L       // 5 seconds
            Factor.VOICE -> 5_000L            // 5 seconds (includes recording)
            Factor.IMAGE_TAP -> 5_000L        // 5 seconds
            Factor.BALANCE -> 5_000L          // 5 seconds (includes recording)
            
            Factor.NFC -> 3_000L              // 3 seconds
            
            Factor.FINGERPRINT -> 2_000L      // 2 seconds
            Factor.FACE -> 2_000L             // 2 seconds
        }
    }
    
    /**
     * Get factor category display name
     * 
     * @param category Factor category
     * @return User-friendly category name
     */
    fun getCategoryDisplayName(category: Factor.Category): String {
        return when (category) {
            Factor.Category.KNOWLEDGE -> "Something You Know"
            Factor.Category.POSSESSION -> "Something You Have"
            Factor.Category.INHERENCE -> "Something You Are"
        }
    }
    
    /**
     * Get factor difficulty level for users
     * 
     * @param factor Factor to check
     * @return Difficulty (1=Very Easy, 5=Very Hard)
     */
    fun getDifficultyLevel(factor: Factor): Int {
        return when (factor) {
            Factor.PIN, Factor.COLOUR -> 1           // Very Easy
            Factor.EMOJI, Factor.NFC -> 2            // Easy
            Factor.PATTERN_NORMAL, Factor.FINGERPRINT, Factor.FACE -> 2  // Easy
            Factor.RHYTHM_TAP, Factor.IMAGE_TAP, Factor.BALANCE -> 3     // Medium
            Factor.WORDS -> 3                        // Medium (searching)
            Factor.PATTERN_MICRO, Factor.MOUSE_DRAW -> 4  // Hard
            Factor.STYLUS_DRAW, Factor.VOICE -> 4    // Hard
        }
    }
    
    /**
     * Check if factor requires practice/training
     * 
     * @param factor Factor to check
     * @return true if practice recommended
     */
    fun requiresPractice(factor: Factor): Boolean {
        return when (factor) {
            Factor.PATTERN_MICRO,
            Factor.MOUSE_DRAW,
            Factor.STYLUS_DRAW,
            Factor.RHYTHM_TAP,
            Factor.BALANCE -> true
            else -> false
        }
    }
    
    /**
     * Get recommended enrollment attempts
     * Multiple attempts improve accuracy for behavioral factors
     * 
     * @param factor Factor to check
     * @return Number of enrollment attempts recommended
     */
    fun getRecommendedEnrollmentAttempts(factor: Factor): Int {
        return when (factor) {
            Factor.PATTERN_MICRO,
            Factor.RHYTHM_TAP,
            Factor.BALANCE -> 3  // Behavioral biometrics need multiple samples
            
            Factor.VOICE -> 2    // Voice needs 2 samples for variation
            
            else -> 1            // Other factors need only 1 enrollment
        }
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Get display name for Android permission
     */
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECORD_AUDIO -> "Microphone"
            android.Manifest.permission.CAMERA -> "Camera"
            android.Manifest.permission.USE_BIOMETRIC -> "Biometric"
            android.Manifest.permission.USE_FINGERPRINT -> "Fingerprint"
            else -> permission.substringAfterLast(".")
        }
    }
}

/**
 * Extension function to check factor availability
 * Convenience method for quick checks
 */
fun Factor.isAvailableOn(context: Context): Boolean {
    return FactorRegistry.isAvailable(context, this)
}

/**
 * Extension function to get factor instructions
 * Convenience method for UI
 */
fun Factor.getInstructions(): String {
    return FactorCanvasFactory.getInstructions(this)
}

/**
 * Extension function to get factor security tip
 * Convenience method for UI
 */
fun Factor.getSecurityTip(): String {
    return FactorCanvasFactory.getSecurityTip(this)
}

/**
 * Extension function to get estimated completion time
 * Convenience method for UI
 */
fun Factor.getEstimatedTime(): Long {
    return FactorCanvasFactory.getEstimatedCompletionTime(this)
}

/**
 * Extension function to check if practice needed
 * Convenience method for UI
 */
fun Factor.needsPractice(): Boolean {
    return FactorCanvasFactory.requiresPractice(this)
}
