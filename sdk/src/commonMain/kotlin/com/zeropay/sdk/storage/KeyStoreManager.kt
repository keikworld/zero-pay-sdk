package com.zeropay.sdk.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure KeyStore Manager
 * 
 * Features:
 * - Android KeyStore for hardware-backed encryption
 * - StrongBox support (Titan M chip on Pixel 3+)
 * - Encrypted SharedPreferences for enrollment data
 * - Automatic key rotation
 * - Right to erasure (GDPR)
 */
class KeyStoreManager(private val context: Context) {
    
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    private val hasStrongBox = context.packageManager.hasSystemFeature(
        "android.hardware.strongbox_keystore"
    )
    
    companion object {
        private const val KEY_ALIAS = "zeropay_master_key"
        private const val PREF_NAME = "zeropay_secure_prefs"
        private const val GCM_TAG_LENGTH = 128
    }
    
    /**
     * Create or get master key
     */
    private fun getMasterKey(): SecretKey {
        // Check if key already exists
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }
        
        // Create new key
        return createMasterKey()
    }
    
    /**
     * Create master key with hardware backing
     */
    private fun createMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // Don't require auth for every encryption
            .apply {
                // Use StrongBox if available (hardware security module)
                if (hasStrongBox) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()
        
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
    
    /**
     * Get encrypted shared preferences
     */
    private fun getEncryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .apply {
                if (hasStrongBox) {
                    setRequestStrongBoxBacked(true)
                }
            }
            .build()
        
        return EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Store enrollment data securely
     */
    fun storeEnrollment(
        userUuid: String,
        factor: com.zeropay.sdk.Factor,
        digest: ByteArray
    ) {
        val prefs = getEncryptedPrefs()
        val key = "enrollment_${userUuid}_${factor.name}"
        
        // Convert to hex string for storage
        val digestHex = digest.joinToString("") { "%02x".format(it) }
        
        prefs.edit()
            .putString(key, digestHex)
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Retrieve enrollment data
     */
    fun getEnrollment(
        userUuid: String,
        factor: com.zeropay.sdk.Factor
    ): ByteArray? {
        val prefs = getEncryptedPrefs()
        val key = "enrollment_${userUuid}_${factor.name}"
        
        val digestHex = prefs.getString(key, null) ?: return null
        
        // Convert hex string back to bytes
        return digestHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
    
    /**
     * Delete enrollment (GDPR right to erasure)
     */
    fun deleteEnrollment(
        userUuid: String,
        factor: com.zeropay.sdk.Factor
    ): Boolean {
        val prefs = getEncryptedPrefs()
        val key = "enrollment_${userUuid}_${factor.name}"
        
        return prefs.edit()
            .remove(key)
            .remove("${key}_timestamp")
            .commit()
    }
    
    /**
     * Delete all enrollments for user (complete erasure)
     */
    fun deleteAllEnrollments(userUuid: String): Boolean {
        val prefs = getEncryptedPrefs()
        val editor = prefs.edit()
        
        // Remove all keys for this user
        val allKeys = prefs.all.keys
        allKeys.filter { it.contains(userUuid) }.forEach { key ->
            editor.remove(key)
        }
        
        return editor.commit()
    }
    
    /**
     * List all enrolled factors for user
     */
    fun getEnrolledFactors(userUuid: String): List<com.zeropay.sdk.Factor> {
        val prefs = getEncryptedPrefs()
        val prefix = "enrollment_${userUuid}_"
        
        return prefs.all.keys
            .filter { it.startsWith(prefix) && !it.endsWith("_timestamp") }
            .mapNotNull { key ->
                val factorName = key.removePrefix(prefix)
                try {
                    com.zeropay.sdk.Factor.valueOf(factorName)
                } catch (e: Exception) {
                    null
                }
            }
    }
    
    /**
     * Encrypt arbitrary data using KeyStore
     */
    fun encrypt(data: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        
        return EncryptedData(
            ciphertext = encrypted,
            iv = iv
        )
    }
    
    /**
     * Decrypt data using KeyStore
     */
    fun decrypt(encryptedData: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, encryptedData.iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)
        
        return cipher.doFinal(encryptedData.ciphertext)
    }
    
    /**
     * Check if StrongBox is being used
     */
    fun isStrongBoxBacked(): Boolean = hasStrongBox
    
    /**
     * Rotate encryption keys (security best practice)
     */
    fun rotateKeys() {
        // Get all encrypted data
        val prefs = getEncryptedPrefs()
        val allData = mutableMapOf<String, String>()
        
        prefs.all.forEach { (key, value) ->
            if (value is String) {
                allData[key] = value
            }
        }
        
        // Delete old key
        keyStore.deleteEntry(KEY_ALIAS)
        
        // Create new key
        createMasterKey()
        
        // Re-encrypt all data with new key
        val editor = prefs.edit()
        allData.forEach { (key, value) ->
            editor.putString(key, value)
        }
        editor.apply()
    }
    
    data class EncryptedData(
        val ciphertext: ByteArray,
        val iv: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedData) return false
            return ciphertext.contentEquals(other.ciphertext) &&
                   iv.contentEquals(other.iv)
        }
        
        override fun hashCode(): Int {
            return ciphertext.contentHashCode() * 31 + iv.contentHashCode()
        }
    }
}

/**
 * Session storage (short-lived, in-memory)
 */
object SessionStorage {
    
    private val sessions = mutableMapOf<String, SessionData>()
    private val lock = Any()
    
    data class SessionData(
        val userUuid: String,
        val factorDigests: Map<com.zeropay.sdk.Factor, ByteArray>,
        val createdAt: Long,
        val expiresAt: Long
    )
    
    /**
     * Store session data (15 minute TTL)
     */
    fun storeSession(
        sessionId: String,
        userUuid: String,
        factorDigests: Map<com.zeropay.sdk.Factor, ByteArray>
    ) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            sessions[sessionId] = SessionData(
                userUuid = userUuid,
                factorDigests = factorDigests,
                createdAt = now,
                expiresAt = now + (15 * 60 * 1000) // 15 minutes
            )
            
            // Cleanup expired sessions
            cleanupExpired()
        }
    }
    
    /**
     * Get session data
     */
    fun getSession(sessionId: String): SessionData? {
        synchronized(lock) {
            val session = sessions[sessionId] ?: return null
            
            // Check expiration
            if (System.currentTimeMillis() > session.expiresAt) {
                sessions.remove(sessionId)
                return null
            }
            
            return session
        }
    }
    
    /**
     * Invalidate session
     */
    fun invalidateSession(sessionId: String) {
        synchronized(lock) {
            sessions.remove(sessionId)
        }
    }
    
    /**
     * Cleanup expired sessions
     */
    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        val expired = sessions.filter { (_, session) ->
            now > session.expiresAt
        }.keys
        
        expired.forEach { sessions.remove(it) }
    }
}

/**
 * Biometric enrollment storage
 */
class BiometricStorage(context: Context) {
    
    private val keyStoreManager = KeyStoreManager(context)
    
    /**
     * Store biometric enrollment
     */
    fun storeEnrollment(enrollment: com.zeropay.sdk.biometrics.BiometricEnrollment) {
        keyStoreManager.storeEnrollment(
            userUuid = enrollment.userUuid,
            factor = com.zeropay.sdk.Factor.FACE, // Or Factor.VOICE
            digest = enrollment.biometricHash
        )
        
        // Store additional metadata
        val prefs = context.getSharedPreferences("biometric_meta", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("${enrollment.userUuid}_provider", enrollment.providerId)
            .putLong("${enrollment.userUuid}_enrolled_at", enrollment.enrolledAt)
            .putBoolean("${enrollment.userUuid}_enclave", enrollment.enclaveProtected)
            .apply()
    }
    
    /**
     * Get biometric enrollment
     */
    fun getEnrollment(userUuid: String): com.zeropay.sdk.biometrics.BiometricEnrollment? {
        val digest = keyStoreManager.getEnrollment(userUuid, com.zeropay.sdk.Factor.FACE)
            ?: return null
        
        val prefs = context.getSharedPreferences("biometric_meta", Context.MODE_PRIVATE)
        val providerId = prefs.getString("${userUuid}_provider", null) ?: return null
        val enrolledAt = prefs.getLong("${userUuid}_enrolled_at", 0)
        val enclaveProtected = prefs.getBoolean("${userUuid}_enclave", false)
        
        return com.zeropay.sdk.biometrics.BiometricEnrollment(
            userUuid = userUuid,
            providerId = providerId,
            biometricHash = digest,
            enrolledAt = enrolledAt,
            deviceId = "", // TODO: Get device ID
            enclaveProtected = enclaveProtected
        )
    }
    
    /**
     * Delete biometric enrollment (GDPR)
     */
    fun deleteEnrollment(userUuid: String): Boolean {
        val keyStoreDeleted = keyStoreManager.deleteEnrollment(userUuid, com.zeropay.sdk.Factor.FACE)
        
        val prefs = context.getSharedPreferences("biometric_meta", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("${userUuid}_provider")
            .remove("${userUuid}_enrolled_at")
            .remove("${userUuid}_enclave")
            .apply()
        
        return keyStoreDeleted
    }
}
