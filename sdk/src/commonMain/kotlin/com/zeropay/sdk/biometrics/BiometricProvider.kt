package com.zeropay.sdk.biometrics

/**
 * Biometric Provider Interface
 * 
 * Pluggable architecture for different biometric providers:
 * - Google BiometricPrompt (Android)
 * - AWS Rekognition (Cloud)
 * - Samsung Knox (Hardware TEE/Enclave)
 * - Apple Face ID / Touch ID (iOS)
 * - Custom providers
 * 
 * GDPR Compliance:
 * - Art. 9: Biometric hashes are NOT sensitive data (irreversible)
 * - Only SHA-256 hashes stored/transmitted, never raw biometric templates
 * - User explicit consent required for enrollment
 * 
 * PSD3 Compliance:
 * - Biometrics as "inherence" factor for Strong Customer Authentication (SCA)
 * - Independent from knowledge (PIN) and possession (NFC) factors
 * 
 * Zero-Knowledge:
 * - Provider returns boolean success + hash
 * - Server never sees raw biometric data
 * - Client-side verification with secure enclave
 */
interface BiometricProvider {
    
    /**
     * Provider identifier
     */
    val providerId: String
    
    /**
     * Check if biometric hardware is available on this device
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Check if user has enrolled biometrics
     */
    suspend fun isEnrolled(userUuid: String): Boolean
    
    /**
     * Enroll biometric for user
     * 
     * GDPR: User must explicitly consent
     * Flow:
     * 1. Show consent dialog
     * 2. Capture biometric (face/finger)
     * 3. Generate hash in secure enclave
     * 4. Link hash to userUuid
     * 5. Store encrypted in device KeyStore/Knox
     * 
     * @param userUuid User identifier (not PII)
     * @param onSuccess Callback with enrollment data (hash only)
     * @param onFailure Callback with error message
     */
    suspend fun enroll(
        userUuid: String,
        onSuccess: (BiometricEnrollment) -> Unit,
        onFailure: (BiometricError) -> Unit
    )
    
    /**
     * Authenticate using enrolled biometric
     * 
     * Zero-Knowledge: Returns hash + boolean, never raw template
     * 
     * @param userUuid User identifier
     * @param onSuccess Callback with biometric hash (32 bytes)
     * @param onFailure Callback with error
     */
    suspend fun authenticate(
        userUuid: String,
        onSuccess: (ByteArray) -> Unit,
        onFailure: (BiometricError) -> Unit
    )
    
    /**
     * Unenroll biometric (GDPR right to erasure)
     * 
     * @param userUuid User identifier
     */
    suspend fun unenroll(userUuid: String): Boolean
    
    /**
     * Get provider capabilities
     */
    fun getCapabilities(): BiometricCapabilities
}

/**
 * Biometric enrollment data
 * GDPR-compliant: No raw biometric data, only irreversible hash
 */
data class BiometricEnrollment(
    val userUuid: String,              // User identifier (not PII)
    val providerId: String,            // Which provider (google/aws/knox)
    val biometricHash: ByteArray,      // SHA-256 hash (irreversible, 32 bytes)
    val enrolledAt: Long,              // Timestamp (Unix milliseconds)
    val deviceId: String,              // Device identifier (hashed)
    val enclaveProtected: Boolean      // Stored in secure enclave (Knox/TEE)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BiometricEnrollment) return false
        return userUuid == other.userUuid &&
               providerId == other.providerId &&
               biometricHash.contentEquals(other.biometricHash)
    }
    
    override fun hashCode(): Int {
        var result = userUuid.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + biometricHash.contentHashCode()
        return result
    }
}

/**
 * Biometric error types
 */
sealed class BiometricError(val message: String) {
    object HardwareUnavailable : BiometricError("Biometric hardware not available")
    object NotEnrolled : BiometricError("No biometric enrolled for this user")
    object AuthenticationFailed : BiometricError("Biometric authentication failed")
    object UserCancelled : BiometricError("User cancelled biometric prompt")
    object Timeout : BiometricError("Biometric authentication timeout")
    object Lockout : BiometricError("Too many failed attempts - locked out")
    object EnclaveError : BiometricError("Secure enclave error")
    data class Unknown(val errorMessage: String) : BiometricError(errorMessage)
}

/**
 * Provider capabilities
 */
data class BiometricCapabilities(
    val supportsFace: Boolean,         // Face recognition
    val supportsFingerprint: Boolean,  // Fingerprint
    val supportsIris: Boolean,         // Iris scan
    val supportsVoice: Boolean,        // Voice recognition
    val hasSecureEnclave: Boolean,     // Hardware TEE/Enclave (Knox, Titan M)
    val supportsLiveness: Boolean,     // Liveness detection (anti-spoofing)
    val encryptionStrength: Int        // Bits (256, 384, etc.)
)

/**
 * Biometric types
 */
enum class BiometricType {
    FACE,
    FINGERPRINT,
    IRIS,
    VOICE
}
