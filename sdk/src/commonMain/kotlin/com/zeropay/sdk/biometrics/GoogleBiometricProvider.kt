package com.zeropay.sdk.biometrics

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.zeropay.sdk.security.CryptoUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google BiometricPrompt Provider
 * 
 * Uses Android BiometricPrompt API for face/fingerprint authentication
 * 
 * Security:
 * - Biometric data never leaves device
 * - Uses Android KeyStore for secure storage
 * - Hardware-backed authentication (Titan M, TEE)
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 hash of success event + salt
 * - No raw biometric templates stored
 * - User can unenroll anytime (right to erasure)
 * 
 * Week 3 Implementation Status: Structure ready, full implementation pending
 */
class GoogleBiometricProvider(
    private val context: Context
) : BiometricProvider {
    
    override val providerId = "google_biometric"
    
    private val biometricManager = BiometricManager.from(context)
    
    override suspend fun isAvailable(): Boolean {
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    override suspend fun isEnrolled(userUuid: String): Boolean {
        // TODO Week 3: Check if biometric is enrolled for this user
        // Check Android KeyStore for enrollment data
        return false // Placeholder
    }
    
    override suspend fun enroll(
        userUuid: String,
        onSuccess: (BiometricEnrollment) -> Unit,
        onFailure: (BiometricError) -> Unit
    ) {
        // Check if biometrics are available
        if (!isAvailable()) {
            onFailure(BiometricError.HardwareUnavailable)
            return
        }
        
        // Show biometric prompt for enrollment
        showBiometricPrompt(
            title = "Enroll Biometric",
            subtitle = "Scan your face or fingerprint",
            onSuccess = { cryptoObject ->
                // Generate enrollment hash
                val enrollmentData = generateEnrollmentHash(userUuid, cryptoObject)
                
                // Store in Android KeyStore (encrypted)
                storeEnrollment(userUuid, enrollmentData)
                
                // Return enrollment data
                val enrollment = BiometricEnrollment(
                    userUuid = userUuid,
                    providerId = providerId,
                    biometricHash = enrollmentData,
                    enrolledAt = System.currentTimeMillis(),
                    deviceId = getDeviceId(),
                    enclaveProtected = hasSecureEnclave()
                )
                
                onSuccess(enrollment)
            },
            onFailure = { error ->
                onFailure(error)
            }
        )
    }
    
    override suspend fun authenticate(
        userUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (BiometricError) -> Unit
    ) {
        // Check if enrolled
        if (!isEnrolled(userUuid)) {
            onFailure(BiometricError.NotEnrolled)
            return
        }
        
        // Show biometric prompt
        showBiometricPrompt(
            title = "Authenticate",
            subtitle = "Scan your face or fingerprint",
            onSuccess = { cryptoObject ->
                // Generate authentication hash (same as enrollment)
                val authHash = generateEnrollmentHash(userUuid, cryptoObject)
                
                // Verify against stored enrollment
                if (verifyEnrollment(userUuid, authHash)) {
                    // Return hash for zero-knowledge proof
                    onSuccess(authHash)
                } else {
                    onFailure(BiometricError.AuthenticationFailed)
                }
            },
            onFailure = { error ->
                onFailure(error)
            }
        )
    }
    
    override suspend fun unenroll(userUuid: String): Boolean {
        // TODO Week 3: Remove enrollment from KeyStore
        // GDPR: User right to erasure
        return try {
            removeEnrollment(userUuid)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getCapabilities(): BiometricCapabilities {
        val canAuthenticateFace = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
        
        return BiometricCapabilities(
            supportsFace = canAuthenticateFace,
            supportsFingerprint = canAuthenticateFace, // BiometricPrompt handles both
            supportsIris = false,
            supportsVoice = false,
            hasSecureEnclave = hasSecureEnclave(),
            supportsLiveness = true, // Android BiometricPrompt has liveness detection
            encryptionStrength = 256 // AES-256 in KeyStore
        )
    }
    
    // ============== Private Helper Methods ==============
    
    private suspend fun showBiometricPrompt(
        title: String,
        subtitle: String,
        onSuccess: (BiometricPrompt.CryptoObject?) -> Unit,
        onFailure: (BiometricError) -> Unit
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        
        val activity = context as? FragmentActivity ?: run {
            onFailure(BiometricError.Unknown("Context must be FragmentActivity"))
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        
        val executor = ContextCompat.getMainExecutor(context)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result.cryptoObject)
                continuation.resume(Unit)
            }
            
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                val error = when (errorCode) {
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_HW_NOT_PRESENT -> BiometricError.HardwareUnavailable
                    BiometricPrompt.ERROR_NO_BIOMETRICS -> BiometricError.NotEnrolled
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> BiometricError.UserCancelled
                    BiometricPrompt.ERROR_TIMEOUT -> BiometricError.Timeout
                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> BiometricError.Lockout
                    else -> BiometricError.Unknown(errString.toString())
                }
                onFailure(error)
                continuation.resume(Unit)
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Don't fail immediately - user can retry
            }
        }
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Generate enrollment hash from biometric authentication
     * 
     * Zero-Knowledge: Hash is irreversible, contains no biometric template
     * GDPR: Safe to store, not considered "sensitive data" under Art. 9
     * 
     * Hash components:
     * - User UUID
     * - Device ID
     * - Timestamp
     * - BiometricPrompt success event + salt
     */
    private fun generateEnrollmentHash(
        userUuid: String,
        cryptoObject: BiometricPrompt.CryptoObject?
    ): ByteArray {
        // Combine user UUID + device ID + timestamp + random salt
        val components = listOf(
            userUuid,
            getDeviceId(),
            System.currentTimeMillis().toString(),
            CryptoUtils.generateRandomBytes(16).joinToString("") { "%02x".format(it) }
        ).joinToString("|")
        
        // Hash with SHA-256 (irreversible)
        return CryptoUtils.sha256(components.toByteArray())
    }
    
    /**
     * Store enrollment in Android KeyStore
     * TODO Week 3: Implement KeyStore storage
     */
    private fun storeEnrollment(userUuid: String, enrollmentData: ByteArray) {
        // TODO Week 3: Use EncryptedSharedPreferences or KeyStore
        // For now, placeholder
    }
    
    /**
     * Verify enrollment against stored data
     * TODO Week 3: Implement verification
     */
    private fun verifyEnrollment(userUuid: String, authHash: ByteArray): Boolean {
        // TODO Week 3: Compare with stored enrollment hash
        // For now, placeholder
        return true
    }
    
    /**
     * Remove enrollment from storage
     * TODO Week 3: Implement removal
     */
    private fun removeEnrollment(userUuid: String) {
        // TODO Week 3: Delete from KeyStore
    }
    
    /**
     * Check if device has secure enclave (Titan M, TEE)
     */
    private fun hasSecureEnclave(): Boolean {
        // Check for StrongBox (Titan M chip on Pixel 3+)
        return context.packageManager.hasSystemFeature("android.hardware.strongbox_keystore")
    }
    
    /**
     * Get device identifier (hashed for privacy)
     */
    private fun getDeviceId(): String {
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        val hash = CryptoUtils.sha256(deviceId.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
}
