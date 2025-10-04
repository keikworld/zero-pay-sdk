package com.zeropay.sdk.factors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import com.zeropay.sdk.Factor
import java.util.concurrent.ConcurrentHashMap

/**
 * PRODUCTION-GRADE Factor Registry
 * 
 * Determines which authentication factors are available on device
 * 
 * Features:
 * - Hardware capability detection
 * - Runtime permission checking
 * - API level compatibility
 * - Device capability scoring
 * - Result caching for performance
 * - Thread-safe operations
 * 
 * Checks:
 * - Hardware availability (touchscreen, microphone, camera, sensors)
 * - Permissions granted (runtime permissions)
 * - API level compatibility
 * - Biometric enrollment status
 * - Device manufacturer capabilities (Samsung S-Pen, etc.)
 */
object FactorRegistry {
    
    private const val TAG = "FactorRegistry"
    
    // Cache for performance (thread-safe)
    private val availabilityCache = ConcurrentHashMap<Factor, FactorAvailability>()
    private var lastCacheUpdate = 0L
    private const val CACHE_TTL_MS = 5000L // 5 seconds
    
    data class FactorAvailability(
        val isAvailable: Boolean,
        val reason: String? = null,
        val requiresPermission: String? = null,
        val requiresSetup: Boolean = false,
        val apiLevel: Int = Build.VERSION_CODES.BASE,
        val score: Int = 0 // Capability score (0-100)
    )
    
    /**
     * Get all available factors for this device
     * 
     * @param context Android context
     * @param includeRequiringSetup Include factors that need user setup (default: true)
     * @return List of available factors, sorted by score (best first)
     */
    fun availableFactors(
        context: Context,
        includeRequiringSetup: Boolean = true
    ): List<Factor> {
        clearCacheIfStale()
        
        val available = mutableListOf<Factor>()
        
        Factor.values().forEach { factor ->
            val availability = checkAvailability(context, factor)
            
            if (availability.isAvailable) {
                if (includeRequiringSetup || !availability.requiresSetup) {
                    available.add(factor)
                }
            }
        }
        
        // Sort by capability score (descending)
        return available.sortedByDescending { 
            availabilityCache[it]?.score ?: 0 
        }
    }
    
    /**
     * Check if specific factor is available
     * 
     * @param context Android context
     * @param factor Factor to check
     * @return FactorAvailability with detailed information
     */
    fun checkAvailability(context: Context, factor: Factor): FactorAvailability {
        // Check cache first
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate < CACHE_TTL_MS) {
            availabilityCache[factor]?.let { return it }
        }
        
        val availability = when (factor) {
            // ========== KNOWLEDGE FACTORS ==========
            Factor.PIN -> checkPinAvailability(context)
            Factor.COLOUR -> checkColourAvailability(context)
            Factor.EMOJI -> checkEmojiAvailability(context)
            Factor.WORDS -> checkWordsAvailability(context)
            
            // ========== BEHAVIORAL FACTORS ==========
            Factor.PATTERN -> checkPatternAvailability(context)
            Factor.MOUSE -> checkMouseAvailability(context)
            Factor.STYLUS -> checkStylusAvailability(context)
            Factor.VOICE -> checkVoiceAvailability(context)
            Factor.IMAGE_TAP -> checkImageTapAvailability(context)
            
            // ========== BIOMETRIC FACTORS ==========
            Factor.FINGERPRINT -> checkFingerprintAvailability(context)
            Factor.FACE -> checkFaceAvailability(context)
        }
        
        // Cache result
        availabilityCache[factor] = availability
        lastCacheUpdate = now
        
        return availability
    }
    
    /**
     * Check if factor is available (simple boolean)
     */
    fun isAvailable(context: Context, factor: Factor): Boolean {
        return checkAvailability(context, factor).isAvailable
    }
    
    /**
     * Get factors by category
     */
    fun factorsByCategory(
        context: Context,
        category: Factor.Category,
        includeRequiringSetup: Boolean = true
    ): List<Factor> {
        return availableFactors(context, includeRequiringSetup)
            .filter { it.category == category }
    }
    
    /**
     * Get best N factors (highest capability score)
     */
    fun getBestFactors(context: Context, count: Int): List<Factor> {
        return availableFactors(context)
            .sortedByDescending { availabilityCache[it]?.score ?: 0 }
            .take(count)
    }
    
    /**
     * Get factors requiring user setup
     */
    fun getFactorsRequiringSetup(context: Context): List<Factor> {
        return Factor.values().filter { factor ->
            val availability = checkAvailability(context, factor)
            availability.isAvailable && availability.requiresSetup
        }
    }
    
    /**
     * Clear availability cache (force re-check)
     */
    fun clearCache() {
        availabilityCache.clear()
        lastCacheUpdate = 0L
    }
    
    // ============================================================
    // FACTOR-SPECIFIC AVAILABILITY CHECKS
    // ============================================================
    
    // ========== KNOWLEDGE FACTORS (Always Available) ==========
    
    private fun checkPinAvailability(context: Context): FactorAvailability {
        return FactorAvailability(
            isAvailable = true,
            score = 70, // Medium security, high convenience
            apiLevel = Build.VERSION_CODES.BASE
        )
    }
    
    private fun checkColourAvailability(context: Context): FactorAvailability {
        return FactorAvailability(
            isAvailable = true,
            score = 60, // Lower security, but fast
            apiLevel = Build.VERSION_CODES.BASE
        )
    }
    
    private fun checkEmojiAvailability(context: Context): FactorAvailability {
        return FactorAvailability(
            isAvailable = true,
            score = 65,
            apiLevel = Build.VERSION_CODES.BASE
        )
    }
    
    private fun checkWordsAvailability(context: Context): FactorAvailability {
        return FactorAvailability(
            isAvailable = true,
            score = 75, // Higher security (more combinations)
            apiLevel = Build.VERSION_CODES.BASE
        )
    }
    
    // ========== BEHAVIORAL FACTORS ==========
    
    private fun checkPatternAvailability(context: Context): FactorAvailability {
        val hasTouchscreen = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TOUCHSCREEN
        )
        
        return if (hasTouchscreen) {
            FactorAvailability(
                isAvailable = true,
                score = 80, // Good security + biometric component
                apiLevel = Build.VERSION_CODES.BASE
            )
        } else {
            FactorAvailability(
                isAvailable = false,
                reason = "Touchscreen required",
                score = 0
            )
        }
    }
    
    private fun checkMouseAvailability(context: Context): FactorAvailability {
        // Mouse factors are less common on mobile
        return FactorAvailability(
            isAvailable = true,
            score = 50, // Lower score on mobile
            apiLevel = Build.VERSION_CODES.BASE
        )
    }
    
    private fun checkStylusAvailability(context: Context): FactorAvailability {
        val pm = context.packageManager
        val hasStylus = pm.hasSystemFeature("android.hardware.touchscreen.stylus") ||
                        pm.hasSystemFeature("com.samsung.feature.spen_usp") ||
                        Build.MANUFACTURER.equals("Samsung", ignoreCase = true)
        
        return if (hasStylus) {
            FactorAvailability(
                isAvailable = true,
                score = 85, // High security (pressure + motion biometrics)
                apiLevel = Build.VERSION_CODES.BASE
            )
        } else {
            FactorAvailability(
                isAvailable = false,
                reason = "Stylus not detected",
                score = 0
            )
        }
    }
    
    private fun checkVoiceAvailability(context: Context): FactorAvailability {
        val pm = context.packageManager
        val hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        return when {
            !hasMicrophone -> FactorAvailability(
                isAvailable = false,
                reason = "Microphone not available",
                score = 0
            )
            !hasPermission -> FactorAvailability(
                isAvailable = true,
                requiresPermission = Manifest.permission.RECORD_AUDIO,
                requiresSetup = true,
                score = 90,
                apiLevel = Build.VERSION_CODES.BASE
            )
            else -> FactorAvailability(
                isAvailable = true,
                score = 90, // High security (biometric)
                apiLevel = Build.VERSION_CODES.BASE
            )
        }
    }
    
    private fun checkImageTapAvailability(context: Context): FactorAvailability {
        val hasTouchscreen = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_TOUCHSCREEN
        )
        
        return if (hasTouchscreen) {
            FactorAvailability(
                isAvailable = true,
                score = 70,
                apiLevel = Build.VERSION_CODES.BASE
            )
        } else {
            FactorAvailability(
                isAvailable = false,
                reason = "Touchscreen required",
                score = 0
            )
        }
    }
    
    // ========== BIOMETRIC FACTORS ==========
    
    private fun checkFingerprintAvailability(context: Context): FactorAvailability {
        val biometricManager = BiometricManager.from(context)
        
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> FactorAvailability(
                isAvailable = true,
                score = 95, // Very high security
                apiLevel = Build.VERSION_CODES.M // API 23+
            )
            
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> FactorAvailability(
                isAvailable = false,
                reason = "No fingerprint hardware",
                score = 0
            )
            
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> FactorAvailability(
                isAvailable = false,
                reason = "Fingerprint hardware unavailable",
                score = 0
            )
            
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> FactorAvailability(
                isAvailable = true,
                requiresSetup = true,
                reason = "No fingerprints enrolled",
                score = 95,
                apiLevel = Build.VERSION_CODES.M
            )
            
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> FactorAvailability(
                isAvailable = false,
                reason = "Security update required",
                score = 0
            )
            
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> FactorAvailability(
                isAvailable = false,
                reason = "Biometric authentication not supported",
                score = 0
            )
            
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> FactorAvailability(
                isAvailable = false,
                reason = "Biometric status unknown",
                score = 0
            )
            
            else -> FactorAvailability(
                isAvailable = false,
                reason = "Unknown biometric error",
                score = 0
            )
        }
    }
    
    private fun checkFaceAvailability(context: Context): FactorAvailability {
        // Face ID requires Android 10+ (API 29) for strong biometric
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return FactorAvailability(
                isAvailable = false,
                reason = "Requires Android 10+",
                score = 0,
                apiLevel = Build.VERSION_CODES.Q
            )
        }
        
        val pm = context.packageManager
        val hasFaceHardware = pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
                              pm.hasSystemFeature("android.hardware.biometrics.face")
        
        if (!hasFaceHardware) {
            return FactorAvailability(
                isAvailable = false,
                reason = "No face recognition hardware",
                score = 0
            )
        }
        
        val biometricManager = BiometricManager.from(context)
        
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> FactorAvailability(
                isAvailable = true,
                score = 95, // Very high security
                apiLevel = Build.VERSION_CODES.Q
            )
            
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> FactorAvailability(
                isAvailable = true,
                requiresSetup = true,
                reason = "Face not enrolled",
                score = 95,
                apiLevel = Build.VERSION_CODES.Q
            )
            
            else -> FactorAvailability(
                isAvailable = false,
                reason = "Face recognition unavailable",
                score = 0
            )
        }
    }
    
    // ========== HELPER METHODS ==========
    
    private fun clearCacheIfStale() {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate > CACHE_TTL_MS) {
            clearCache()
        }
    }
    
    /**
     * Check if device has specific sensor
     */
    private fun hasSensor(context: Context, sensorType: Int): Boolean {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sensorManager.getDefaultSensor(sensorType) != null
    }
    
    /**
     * Get device capability tier (1-5, higher is better)
     */
    fun getDeviceCapabilityTier(context: Context): Int {
        val availableCount = availableFactors(context).size
        return when {
            availableCount >= 10 -> 5 // Flagship device
            availableCount >= 8 -> 4  // High-end device
            availableCount >= 6 -> 3  // Mid-range device
            availableCount >= 4 -> 2  // Budget device
            else -> 1                 // Basic device
        }
    }
}
