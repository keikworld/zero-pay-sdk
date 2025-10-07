package com.zeropay.sdk.factors

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.Factor
import com.zeropay.sdk.ui.RhythmTapCanvas

/**
 * Factor Canvas Factory - PRODUCTION VERSION
 * 
 * Routes each factor type to its corresponding UI implementation.
 * 
 * Architecture:
 * - Composable factory pattern
 * - Type-safe routing
 * - Consistent error handling
 * - Zero-knowledge digest generation
 * 
 * Security:
 * - All canvases generate SHA-256 digests locally
 * - No raw factor data transmitted
 * - GDPR-compliant (no PII stored)
 * 
 * Error Handling:
 * - Validates factor availability before rendering
 * - Graceful fallback for unsupported factors
 * - User-friendly error messages
 * 
 * Version: 1.0.0
 * Last Updated: 2025-01-08
 */
object FactorCanvasFactory {

    /**
     * Create Composable canvas for specific factor
     * 
     * @param factor The authentication factor to render
     * @param onDone Callback with 32-byte SHA-256 digest
     * @param modifier Compose modifier for layout
     * 
     * @throws IllegalArgumentException if factor is not supported
     * 
     * Zero-Knowledge: onDone receives only irreversible hash
     * GDPR: No biometric data leaves device
     */
    @Composable
    fun CanvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        // Route to appropriate canvas implementation
        when (factor) {
            // ==================== KNOWLEDGE FACTORS ====================
            
            /**
             * PIN Canvas - Numeric keypad input
             * Validates: 4-12 digits, no sequential patterns
             * Output: Argon2id hash (32 bytes)
             */
            Factor.PIN -> {
                PinCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            /**
             * Color Canvas - Visual color sequence
             * User selects 2-3 colors in order
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.COLOUR -> {
                ColourCanvas(
                    onSelected = onDone,
                    modifier = modifier
                )
            }
            
            /**
             * Emoji Canvas - Emoji sequence selection
             * User selects 4 emojis from shuffled grid
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.EMOJI -> {
                EmojiCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            /**
             * Words Canvas - Word list selection
             * User selects 4 words from 3000-word list
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.WORDS -> {
                WordsCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            // ==================== BEHAVIORAL FACTORS ====================
            
            /**
             * Pattern Canvas - Micro-Timing Mode
             * Includes precise timing data (microseconds)
             * Analyzes velocity, acceleration, timing
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.PATTERN_MICRO -> {
                PatternCanvas { points ->
                    val digest = PatternFactor.digestMicroTiming(points)
                    onDone(digest)
                }
            }
            
            /**
             * Pattern Canvas - Normalized Timing Mode
             * Speed-invariant analysis
             * More forgiving for tremors/disability
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.PATTERN_NORMAL -> {
                PatternCanvas { points ->
                    val digest = PatternFactor.digestNormalisedTiming(points)
                    onDone(digest)
                }
            }
            
            /**
             * Mouse Canvas - Mouse movement tracking
             * Captures trajectory, velocity, acceleration
             * Requires mouse/trackpad
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.MOUSE_DRAW -> {
                MouseCanvas { points ->
                    val digest = MouseFactor.digestMicroTiming(points)
                    onDone(digest)
                }
            }
            
            /**
             * Stylus Canvas - Pressure-sensitive drawing
             * Captures position, pressure, timing
             * Requires stylus (Samsung S-Pen, Apple Pencil, etc.)
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.STYLUS_DRAW -> {
                StylusCanvas { points ->
                    val digest = StylusFactor.digestFull(points)
                    onDone(digest)
                }
            }
            
            /**
             * Voice Canvas - Audio recording
             * Records 2-second voice sample
             * GDPR: Audio deleted immediately after hashing
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.VOICE -> {
                VoiceCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            /**
             * Image Tap Canvas - Spatial memory
             * User taps 2 points on pre-approved image
             * GDPR: Only abstract images, no personal photos
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.IMAGE_TAP -> {
                ImageTapCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }
            
            /**
             * Balance Canvas - Accelerometer pattern
             * User holds device steady
             * Captures unique hand tremor/balance pattern
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.BALANCE -> {
                BalanceCanvas(
                    onDone = onDone
                )
            }

            /**
             * Rhythm Tap Canvas - Behavioral biometric
             * User taps 4-6 times in a rhythm
             * Captures millisecond-level timing between taps
             * Output: SHA-256 hash (32 bytes)
             * 
             * Security: Timing variations create high entropy
             * UX: Fun, intuitive (like tapping to music)
             * Accessibility: No fine motor skills needed
             */
            Factor.RHYTHM_TAP -> {
                RhythmTapCanvas(
                    onDone = onDone,
                    modifier = modifier
                )
            }

            
            // ==================== POSSESSION FACTORS ====================
            
            /**
             * NFC Canvas - Near Field Communication
             * User taps NFC tag or card
             * Reads UID and generates hash
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.NFC -> {
                NfcCanvas(
                    onDone = onDone
                )
            }
            
            // ==================== BIOMETRIC FACTORS ====================
            
            /**
             * Face Canvas - Biometric face recognition
             * Uses Android BiometricPrompt API
             * GDPR: Biometric data never leaves device
             * Zero-Knowledge: Only hash transmitted
             * Output: SHA-256 hash (32 bytes)
             */
            Factor.FACE -> {
                FaceCanvas(
                    onDone = onDone
                )
            }
            
            /**
             * Fingerprint Canvas - Biometric fingerprint
             * Uses Android BiometricPrompt API
             * GDPR: Biometric data never leaves device
             * Zero-Knowledge: Only hash transmitted
             * Output: SHA-256 hash (32 bytes)
             * 
             * Note: BiometricPrompt handles both face and fingerprint,
             * this uses the same implementation as FACE
             */
            Factor.FINGERPRINT -> {
                // Android BiometricPrompt handles both face and fingerprint
                // The prompt will show whatever biometric is available
                FaceCanvas(
                    onDone = onDone
                )
            }
        }
    }
    
    /**
     * Check if factor has canvas implementation
     * 
     * @param factor Factor to check
     * @return true if canvas is implemented
     */
    fun hasImplementation(factor: Factor): Boolean {
        // All factors in the enum have implementations
        return true
    }
    
    /**
     * Get estimated time to complete factor (milliseconds)
     * Used for UX planning and timeout settings
     * 
     * @param factor Factor to estimate
     * @return Estimated completion time in milliseconds
     */
    fun getEstimatedCompletionTime(factor: Factor): Long {
        return when (factor) {
            // Fast factors (< 10 seconds)
            Factor.PIN -> 8_000L
            Factor.COLOUR -> 5_000L
            Factor.EMOJI -> 6_000L
            Factor.FINGERPRINT -> 3_000L
            Factor.FACE -> 3_000L
            Factor.NFC -> 2_000L
            
            // Medium factors (10-30 seconds)
            Factor.PATTERN_MICRO -> 15_000L
            Factor.PATTERN_NORMAL -> 12_000L
            Factor.IMAGE_TAP -> 10_000L
            Factor.BALANCE -> 8_000L
            
            // Slower factors (30-60 seconds)
            Factor.WORDS -> 45_000L
            Factor.MOUSE_DRAW -> 20_000L
            Factor.STYLUS_DRAW -> 25_000L
            Factor.VOICE -> 10_000L
        }
    }
    
    /**
     * Get user-friendly instructions for factor
     * 
     * @param factor Factor to get instructions for
     * @return Instruction text for UI
     */
    fun getInstructions(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "Enter your secret PIN code (4-12 digits)"
            Factor.COLOUR -> "Tap colors in the sequence you chose during enrollment"
            Factor.EMOJI -> "Select your 4 emojis in the correct order"
            Factor.WORDS -> "Find and tap your 4 words from the grid"
            
            Factor.PATTERN_MICRO -> "Draw your pattern carefully - timing matters"
            Factor.PATTERN_NORMAL -> "Draw your pattern at any speed"
            Factor.MOUSE_DRAW -> "Draw your signature with the mouse"
            Factor.STYLUS_DRAW -> "Sign with your stylus - pressure matters"
            Factor.VOICE -> "Speak your passphrase clearly into the microphone"
            Factor.IMAGE_TAP -> "Tap the same 2 points on the image"
            Factor.BALANCE -> "Hold your device steady for 3 seconds"
            
            Factor.NFC -> "Tap your NFC tag or card to the back of your device"
            
            Factor.FACE -> "Position your face in front of the camera"
            Factor.FINGERPRINT -> "Place your finger on the sensor"
        }
    }
    
    /**
     * Get security tips for factor
     * Displayed to user during enrollment
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
     * Validate canvas requirements are met
     * Checks permissions, hardware availability, etc.
     * 
     * @param factor Factor to validate
     * @param context Android context
     * @return Validation result with error message if failed
     */
    fun validateRequirements(
        factor: Factor,
        context: android.content.Context
    ): ValidationResult {
        // Check if factor requires hardware
        if (factor.requiresHardware) {
            val available = FactorRegistry.checkAvailability(context, factor)
            if (!available.isAvailable) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = available.reason ?: "Required hardware not available"
                )
            }
        }
        
        // Check if factor requires permission
        factor.requiresPermission?.let { permission ->
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (!granted) {
                return ValidationResult(
                    isValid = false,
                    errorMessage = "Permission required: ${getPermissionDisplayName(permission)}",
                    requiredPermission = permission
                )
            }
        }
        
        return ValidationResult(isValid = true)
    }
    
    /**
     * Get user-friendly permission name
     */
    private fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            "android.permission.RECORD_AUDIO" -> "Microphone access"
            "android.permission.USE_BIOMETRIC" -> "Biometric authentication"
            "android.permission.NFC" -> "NFC access"
            else -> permission.substringAfterLast('.')
        }
    }
    
    /**
     * Validation result data class
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val requiredPermission: String? = null
    )
}
