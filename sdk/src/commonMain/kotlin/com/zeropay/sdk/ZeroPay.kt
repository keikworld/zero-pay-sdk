package com.zeropay.sdk

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.errors.FactorNotAvailableException
import com.zeropay.sdk.factors.FactorRegistry
import com.zeropay.sdk.ui.*

/**
 * PRODUCTION-GRADE ZeroPay SDK - Main Entry Point
 * 
 * Features:
 * - Factor UI canvas factory
 * - Availability checking
 * - Error handling
 * - Lifecycle management
 * - Progress tracking
 * - State management hooks
 * 
 * Provides:
 * - Authentication factor UI components
 * - Zero-knowledge proof generation
 * - Factor validation
 * - User experience optimization
 */
object ZeroPay {
    
    private const val TAG = "ZeroPay"
    private const val VERSION = "1.0.0"
    
    /**
     * SDK initialization (optional, for configuration)
     */
    fun initialize(context: Context, config: Config = Config()) {
        Log.i(TAG, "ZeroPay SDK v$VERSION initialized")
        
        // Apply configuration
        this.config = config
        
        // Verify device capabilities
        val tier = FactorRegistry.getDeviceCapabilityTier(context)
        Log.d(TAG, "Device capability tier: $tier/5")
        
        // Pre-warm factor registry cache
        if (config.prewarmCache) {
            FactorRegistry.availableFactors(context)
        }
    }
    
    /**
     * Get composable canvas for a specific factor
     * 
     * @param factor Authentication factor type
     * @param onDone Callback with digest (32 bytes SHA-256)
     * @param onError Callback for errors (optional)
     * @param onProgress Progress callback 0.0-1.0 (optional)
     * @param modifier Compose modifier
     * 
     * Zero-knowledge: Canvas generates hash locally, never sends raw data
     * 
     * @throws FactorNotAvailableException if factor not available
     */
    @Composable
    fun canvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        onProgress: ((Float) -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        try {
            when (factor) {
                // Knowledge factors
                Factor.PIN -> PinCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.COLOUR -> ColourCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.EMOJI -> EmojiCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.WORDS -> WordsCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                // Behavioral factors
                Factor.PATTERN -> PatternCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.MOUSE -> MouseCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.STYLUS -> StylusCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.VOICE -> VoiceCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.IMAGE_TAP -> ImageTapCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                // Biometric factors
                Factor.FINGERPRINT -> FingerprintCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
                
                Factor.FACE -> FaceCanvas(
                    onDone = onDone,
                    onError = onError,
                    onProgress = onProgress,
                    modifier = modifier
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating canvas for factor: $factor", e)
            onError?.invoke(e) ?: throw e
        }
    }
    
    /**
     * Get human-readable factor display name
     */
    fun getFactorDisplayName(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "PIN Code"
            Factor.COLOUR -> "Color Selection"
            Factor.EMOJI -> "Emoji Selection"
            Factor.WORDS -> "Word Selection"
            Factor.PATTERN -> "Draw Pattern"
            Factor.MOUSE -> "Mouse Movement"
            Factor.STYLUS -> "Stylus Signature"
            Factor.VOICE -> "Voice Recognition"
            Factor.IMAGE_TAP -> "Image Tap Points"
            Factor.FINGERPRINT -> "Fingerprint"
            Factor.FACE -> "Face Recognition"
        }
    }
    
    /**
     * Get factor description (user-friendly explanation)
     */
    fun getFactorDescription(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "Enter your secret PIN code"
            Factor.COLOUR -> "Select colors in the correct sequence"
            Factor.EMOJI -> "Choose emojis in the right order"
            Factor.WORDS -> "Select words from a list"
            Factor.PATTERN -> "Draw your unique pattern"
            Factor.MOUSE -> "Move your mouse in a specific way"
            Factor.STYLUS -> "Sign with your stylus"
            Factor.VOICE -> "Speak your passphrase"
            Factor.IMAGE_TAP -> "Tap specific points on an image"
            Factor.FINGERPRINT -> "Use your fingerprint"
            Factor.FACE -> "Use face recognition"
        }
    }
    
    /**
     * Get factor icon emoji
     */
    fun getFactorIcon(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "🔢"
            Factor.COLOUR -> "🎨"
            Factor.EMOJI -> "😀"
            Factor.WORDS -> "📝"
            Factor.PATTERN -> "✏️"
            Factor.MOUSE -> "🖱️"
            Factor.STYLUS -> "🖊️"
            Factor.VOICE -> "🎤"
            Factor.IMAGE_TAP -> "🖼️"
            Factor.FINGERPRINT -> "👆"
            Factor.FACE -> "👤"
        }
    }
    
    /**
     * Get factor category display name
     */
    fun getCategoryDisplayName(category: Factor.Category): String {
        return when (category) {
            Factor.Category.KNOWLEDGE -> "Something You Know"
            Factor.Category.POSSESSION -> "Something You Have"
            Factor.Category.INHERENCE -> "Something You Are"
        }
    }
    
    /**
     * Get factor security level (1-5, higher is better)
     */
    fun getFactorSecurityLevel(factor: Factor): Int {
        return when (factor) {
            // Biometric (highest security)
            Factor.FINGERPRINT, Factor.FACE -> 5
            
            // Behavioral biometric (high security)
            Factor.VOICE, Factor.STYLUS -> 4
            
            // Complex knowledge + behavioral
            Factor.PATTERN, Factor.WORDS -> 3
            
            // Simple knowledge
            Factor.PIN, Factor.EMOJI, Factor.COLOUR -> 2
            
            // Basic behavioral
            Factor.MOUSE, Factor.IMAGE_TAP -> 2
        }
    }
    
    /**
     * Get factor convenience level (1-5, higher is more convenient)
     */
    fun getFactorConvenience(factor: Factor): Int {
        return when (factor) {
            // Fastest (biometric)
            Factor.FINGERPRINT, Factor.FACE -> 5
            
            // Very fast (simple input)
            Factor.PIN, Factor.COLOUR, Factor.EMOJI -> 4
            
            // Moderate (requires thought)
            Factor.WORDS, Factor.IMAGE_TAP -> 3
            
            // Slower (requires drawing/motion)
            Factor.PATTERN, Factor.MOUSE, Factor.STYLUS -> 2
            
            // Slowest (requires speaking)
            Factor.VOICE -> 1
        }
    }
    
    /**
     * Recommend factors for user based on device and preferences
     * 
     * @param context Android context
     * @param count Number of factors to recommend
     * @param preferSecurity Prefer security over convenience (default: true)
     * @return Recommended factors, sorted by score
     */
    fun recommendFactors(
        context: Context,
        count: Int = 2,
        preferSecurity: Boolean = true
    ): List<Factor> {
        val available = FactorRegistry.availableFactors(context)
        
        // Score each factor
        val scored = available.map { factor ->
            val security = getFactorSecurityLevel(factor)
            val convenience = getFactorConvenience(factor)
            
            val score = if (preferSecurity) {
                (security * 0.7 + convenience * 0.3).toInt()
            } else {
                (security * 0.3 + convenience * 0.7).toInt()
            }
            
            factor to score
        }
        
        // Sort by score (descending) and take top N
        return scored
            .sortedByDescending { it.second }
            .take(count)
            .map { it.first }
    }
    
    /**
     * Check if factor requires runtime permissions
     * 
     * @param context Android context
     * @param factor Factor to check
     * @return List of required permissions (empty if none)
     */
    fun getRequiredPermissions(context: Context, factor: Factor): List<String> {
        val availability = FactorRegistry.checkAvailability(context, factor)
        return availability.requiresPermission?.let { listOf(it) } ?: emptyList()
    }
    
    /**
     * Check if factor requires user setup
     * 
     * @param context Android context
     * @param factor Factor to check
     * @return true if setup required (e.g., enroll biometric)
     */
    fun requiresSetup(context: Context, factor: Factor): Boolean {
        val availability = FactorRegistry.checkAvailability(context, factor)
        return availability.requiresSetup
    }
    
    /**
     * Get SDK version
     */
    fun getVersion(): String = VERSION
    
    /**
     * Configuration
     */
    data class Config(
        val prewarmCache: Boolean = true,
        val enableTelemetry: Boolean = false,
        val enableDebugLogging: Boolean = false
    )
    
    private var config: Config = Config()
}
