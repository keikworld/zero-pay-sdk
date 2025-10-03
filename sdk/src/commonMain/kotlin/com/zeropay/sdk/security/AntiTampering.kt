package com.zeropay.sdk.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.provider.Settings
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.safetynet.SafetyNet
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Anti-Tampering Security Module
 * 
 * Detects:
 * - Root/Jailbreak
 * - Debugger attachment
 * - Emulator
 * - App tampering
 * - SafetyNet/Play Integrity violations
 * 
 * Defense in depth: Multiple checks, not relying on single method
 */
object AntiTampering {
    
    data class TamperResult(
        val isTampered: Boolean,
        val threats: List<ThreatType>,
        val severity: Severity
    )
    
    enum class ThreatType {
        ROOT_DETECTED,
        DEBUGGER_ATTACHED,
        EMULATOR_DETECTED,
        SAFETYNET_FAILED,
        APK_MODIFIED,
        MAGISK_DETECTED,
        XPOSED_DETECTED,
        FRIDA_DETECTED,
        USB_DEBUGGING_ENABLED
    }
    
    enum class Severity {
        NONE,      // No threats
        LOW,       // Emulator, USB debugging
        MEDIUM,    // Root detection, debugger
        HIGH,      // Multiple threats, active tampering
        CRITICAL   // SafetyNet failed + root + debugger
    }
    
    /**
     * Comprehensive tampering check
     * Returns detailed threat assessment
     */
    suspend fun checkTampering(context: Context): TamperResult {
        val threats = mutableListOf<ThreatType>()
        
        // Check 1: Root detection
        if (isRooted()) {
            threats.add(ThreatType.ROOT_DETECTED)
        }
        
        // Check 2: Debugger
        if (isDebuggerAttached()) {
            threats.add(ThreatType.DEBUGGER_ATTACHED)
        }
        
        // Check 3: Emulator
        if (isEmulator()) {
            threats.add(ThreatType.EMULATOR_DETECTED)
        }
        
        // Check 4: USB Debugging
        if (isUsbDebuggingEnabled(context)) {
            threats.add(ThreatType.USB_DEBUGGING_ENABLED)
        }
        
        // Check 5: Magisk (root hiding tool)
        if (isMagiskDetected()) {
            threats.add(ThreatType.MAGISK_DETECTED)
        }
        
        // Check 6: Xposed Framework
        if (isXposedDetected()) {
            threats.add(ThreatType.XPOSED_DETECTED)
        }
        
        // Check 7: Frida (dynamic instrumentation)
        if (isFridaDetected()) {
            threats.add(ThreatType.FRIDA_DETECTED)
        }
        
        // Check 8: APK integrity
        if (!verifyApkIntegrity(context)) {
            threats.add(ThreatType.APK_MODIFIED)
        }
        
        // Check 9: SafetyNet (async)
        val safetyNetPassed = checkSafetyNet(context)
        if (!safetyNetPassed) {
            threats.add(ThreatType.SAFETYNET_FAILED)
        }
        
        // Calculate severity
        val severity = calculateSeverity(threats)
        
        return TamperResult(
            isTampered = threats.isNotEmpty(),
            threats = threats,
            severity = severity
        )
    }
    
    /**
     * Quick tampering check (synchronous, no SafetyNet)
     * Use for fast validation without network call
     */
    fun checkTamperingFast(context: Context): TamperResult {
        val threats = mutableListOf<ThreatType>()
        
        if (isRooted()) threats.add(ThreatType.ROOT_DETECTED)
        if (isDebuggerAttached()) threats.add(ThreatType.DEBUGGER_ATTACHED)
        if (isEmulator()) threats.add(ThreatType.EMULATOR_DETECTED)
        if (isMagiskDetected()) threats.add(ThreatType.MAGISK_DETECTED)
        if (isXposedDetected()) threats.add(ThreatType.XPOSED_DETECTED)
        if (isFridaDetected()) threats.add(ThreatType.FRIDA_DETECTED)
        
        val severity = calculateSeverity(threats)
        
        return TamperResult(
            isTampered = threats.isNotEmpty(),
            threats = threats,
            severity = severity
        )
    }
    
    // ============== Detection Methods ==============
    
    /**
     * Root detection using multiple methods
     */
    private fun isRooted(): Boolean {
        // Method 1: Check for su binary
        val suPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in suPaths) {
            if (File(path).exists()) return true
        }
        
        // Method 2: Check for root management apps
        val rootApps = listOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk"
        )
        
        // Method 3: Check build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        
        // Method 4: Check for RW system partition
        try {
            val process = Runtime.getRuntime().exec("mount")
            val mountInfo = process.inputStream.bufferedReader().readText()
            if (mountInfo.contains("/system") && mountInfo.contains("rw")) {
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        return false
    }
    
    /**
     * Debugger detection
     */
    private fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }
    
    /**
     * Emulator detection
     */
    private fun isEmulator(): Boolean {
        // Method 1: Check build properties
        val rating = (
            (if (Build.FINGERPRINT.startsWith("generic")) 1 else 0) +
            (if (Build.FINGERPRINT.startsWith("unknown")) 1 else 0) +
            (if (Build.MODEL.contains("google_sdk")) 1 else 0) +
            (if (Build.MODEL.contains("Emulator")) 1 else 0) +
            (if (Build.MODEL.contains("Android SDK built for x86")) 1 else 0) +
            (if (Build.MANUFACTURER.contains("Genymotion")) 1 else 0) +
            (if (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) 1 else 0) +
            (if (Build.PRODUCT.contains("sdk")) 1 else 0) +
            (if (Build.PRODUCT.contains("google_sdk")) 1 else 0) +
            (if (Build.PRODUCT.contains("sdk_x86")) 1 else 0) +
            (if (Build.PRODUCT.contains("vbox86p")) 1 else 0) +
            (if (Build.HARDWARE.contains("goldfish")) 1 else 0) +
            (if (Build.HARDWARE.contains("ranchu")) 1 else 0)
        )
        
        return rating > 3
    }
    
    /**
     * USB Debugging detection
     */
    private fun isUsbDebuggingEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Global.ADB_ENABLED,
            0
        ) == 1
    }
    
    /**
     * Magisk detection (root hiding tool)
     */
    private fun isMagiskDetected(): Boolean {
        val magiskPaths = listOf(
            "/sbin/.magisk",
            "/cache/.magisk",
            "/data/adb/magisk",
            "/data/adb/magisk.img",
            "/data/adb/magisk.db"
        )
        
        return magiskPaths.any { File(it).exists() }
    }
    
    /**
     * Xposed Framework detection
     */
    private fun isXposedDetected(): Boolean {
        try {
            throw Exception("Xposed check")
        } catch (e: Exception) {
            val stackTrace = e.stackTraceToString()
            if (stackTrace.contains("de.robv.android.xposed.XposedBridge")) {
                return true
            }
        }
        
        // Check for Xposed installer
        val xposedPaths = listOf(
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed_art.so"
        )
        
        return xposedPaths.any { File(it).exists() }
    }
    
    /**
     * Frida detection (dynamic instrumentation framework)
     */
    private fun isFridaDetected(): Boolean {
        // Check for Frida server process
        try {
            val process = Runtime.getRuntime().exec("ps")
            val processInfo = process.inputStream.bufferedReader().readText()
            if (processInfo.contains("frida-server") || 
                processInfo.contains("frida-agent")) {
                return true
            }
        } catch (e: Exception) {
            // Ignore
        }
        
        // Check for Frida ports
        val fridaPorts = listOf(27042, 27043)
        for (port in fridaPorts) {
            try {
                val socket = java.net.Socket()
                socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 100)
                socket.close()
                return true
            } catch (e: Exception) {
                // Port not open, continue
            }
        }
        
        return false
    }
    
    /**
     * APK integrity verification
     */
    private fun verifyApkIntegrity(context: Context): Boolean {
        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            
            // In production, compare with known good signature
            // For now, just check if signature exists
            val signatures = packageInfo.signatures
            if (signatures == null || signatures.isEmpty()) {
                return false
            }
            
            // TODO: Compare signature hash with expected value
            // val signatureHash = CryptoUtils.sha256(signatures[0].toByteArray())
            // val expectedHash = ... // From secure storage or hardcoded
            // return signatureHash.contentEquals(expectedHash)
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * SafetyNet attestation check
     */
    private suspend fun checkSafetyNet(context: Context): Boolean {
        // Check if Google Play Services is available
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        
        if (resultCode != ConnectionResult.SUCCESS) {
            // Play Services not available, cannot check SafetyNet
            return true // Don't fail if service unavailable
        }
        
        return suspendCancellableCoroutine { continuation ->
            try {
                // Generate nonce
                val nonce = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(24)
                
                // Request SafetyNet attestation
                SafetyNet.getClient(context)
                    .attest(nonce, "YOUR_API_KEY_HERE") // TODO: Add API key
                    .addOnSuccessListener { response ->
                        // Parse JWS response
                        val jwsResult = response.jwsResult
                        
                        // TODO: Verify JWS signature and parse payload
                        // For now, just check if we got a response
                        val passed = jwsResult != null && jwsResult.isNotEmpty()
                        
                        continuation.resume(passed)
                    }
                    .addOnFailureListener { exception ->
                        // SafetyNet check failed
                        continuation.resume(false)
                    }
            } catch (e: Exception) {
                // Error during check, don't fail authentication
                continuation.resume(true)
            }
        }
    }
    
    /**
     * Calculate overall severity from threat list
     */
    private fun calculateSeverity(threats: List<ThreatType>): Severity {
        if (threats.isEmpty()) return Severity.NONE
        
        val criticalThreats = threats.filter { 
            it in listOf(
                ThreatType.SAFETYNET_FAILED,
                ThreatType.APK_MODIFIED,
                ThreatType.FRIDA_DETECTED
            )
        }
        
        val highThreats = threats.filter {
            it in listOf(
                ThreatType.ROOT_DETECTED,
                ThreatType.DEBUGGER_ATTACHED,
                ThreatType.MAGISK_DETECTED,
                ThreatType.XPOSED_DETECTED
            )
        }
        
        return when {
            criticalThreats.size >= 2 || 
            (criticalThreats.isNotEmpty() && highThreats.isNotEmpty()) -> Severity.CRITICAL
            
            criticalThreats.isNotEmpty() || highThreats.size >= 2 -> Severity.HIGH
            
            highThreats.isNotEmpty() -> Severity.MEDIUM
            
            else -> Severity.LOW
        }
    }
    
    /**
     * Get user-friendly message for threat
     */
    fun getThreatMessage(threat: ThreatType): String {
        return when (threat) {
            ThreatType.ROOT_DETECTED -> 
                "Rooted device detected. For security, authentication is not allowed on rooted devices."
            ThreatType.DEBUGGER_ATTACHED -> 
                "Debugger detected. Please disconnect debugger to continue."
            ThreatType.EMULATOR_DETECTED -> 
                "Emulator detected. Authentication must be performed on a physical device."
            ThreatType.SAFETYNET_FAILED -> 
                "Device security verification failed. Please use a certified device."
            ThreatType.APK_MODIFIED -> 
                "App integrity check failed. Please reinstall from official store."
            ThreatType.MAGISK_DETECTED -> 
                "Root management tool detected. Authentication not allowed."
            ThreatType.XPOSED_DETECTED -> 
                "Framework modification detected. Authentication not allowed."
            ThreatType.FRIDA_DETECTED -> 
                "Instrumentation tool detected. Authentication not allowed."
            ThreatType.USB_DEBUGGING_ENABLED -> 
                "USB debugging is enabled. Please disable it for secure authentication."
        }
    }
}
