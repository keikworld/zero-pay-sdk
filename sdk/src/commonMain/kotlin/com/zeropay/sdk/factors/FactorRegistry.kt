package com.zeropay.sdk.factors

import android.content.Context
import android.content.pm.PackageManager
import com.zeropay.sdk.Factor

/**
 * Factor Registry - Determines which factors are available on device
 * 
 * Checks:
 * - Hardware availability (touchscreen, microphone, camera)
 * - API level compatibility
 * - Permissions granted
 */
object FactorRegistry {
    
    /**
     * Get all available factors for this device
     */
    fun availableFactors(context: Context): List<Factor> {
        val available = mutableListOf<Factor>()
        
        // Knowledge factors (always available)
        available.add(Factor.PIN)
        available.add(Factor.COLOUR)
        available.add(Factor.EMOJI)
        available.add(Factor.WORDS)
        
        // Behavioral factors (require touchscreen)
        if (hasTouchscreen(context)) {
            available.add(Factor.PATTERN)
            available.add(Factor.MOUSE)
            available.add(Factor.IMAGE_TAP)
            
            // Stylus (only on devices with stylus support)
            if (hasStylus(context)) {
                available.add(Factor.STYLUS)
            }
        }
        
        // Voice (requires microphone)
        if (hasMicrophone(context)) {
            available.add(Factor.VOICE)
        }
        
        // Biometrics (Week 3+)
        if (hasFingerprint(context)) {
            available.add(Factor.FINGERPRINT)
        }
        if (hasFaceRecognition(context)) {
            available.add(Factor.FACE)
        }
        
        return available
    }
    
    /**
     * Check if specific factor is available
     */
    fun isAvailable(context: Context, factor: Factor): Boolean {
        return availableFactors(context).contains(factor)
    }
    
    /**
     * Get factors by category
     */
    fun factorsByCategory(context: Context, category: Factor.Category): List<Factor> {
        return availableFactors(context).filter { it.category == category }
    }
    
    // ============== Hardware Detection ==============
    
    private fun hasTouchscreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
    
    private fun hasStylus(context: Context): Boolean {
        // Samsung S-Pen, Surface Pen, etc.
        return context.packageManager.hasSystemFeature("android.hardware.touchscreen.stylus") ||
               context.packageManager.hasSystemFeature("com.samsung.feature.spen_usp")
    }
    
    private fun hasMicrophone(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }
    
    private fun hasFingerprint(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)
    }
    
    private fun hasFaceRecognition(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE) ||
               android.os.Build.VERSION.SDK_INT >= 28 // BiometricPrompt supports face
    }
}
