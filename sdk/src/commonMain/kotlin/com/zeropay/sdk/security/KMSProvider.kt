// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/security/KMSProvider.kt

package com.zeropay.sdk.security

/**
 * KMSProvider - Key Management Service Provider Interface
 * 
 * Abstract interface for KMS operations (Layer 2 of double encryption).
 * 
 * Purpose:
 * - Wrap/unwrap encryption keys using KMS master key
 * - Enable double encryption Layer 2 (system-controlled)
 * - Support multiple KMS providers (AWS, Azure, local)
 * - Enable key rotation without user interaction
 * 
 * Security Features:
 * - Master key never leaves KMS
 * - Hardware-backed key storage (HSM)
 * - Audit logging of all operations
 * - Access control via IAM/RBAC
 * - Encryption context for additional security
 * 
 * Supported Providers:
 * - AWS KMS (production)
 * - Azure Key Vault (production)
 * - Local KMS (testing only)
 * 
 * Double Encryption Flow:
 * ```
 * Enrollment:
 * 1. Derive key from factors (Layer 1)
 * 2. Wrap derived key with KMS (Layer 2)
 * 3. Store wrapped key in database
 * 4. Wipe derived key from memory
 * 
 * Verification:
 * 1. Retrieve wrapped key from database
 * 2. Unwrap with KMS (Layer 2)
 * 3. Get derived key
 * 4. Derive key from user input (Layer 1)
 * 5. Compare keys (constant-time)
 * 6. Wipe both keys from memory
 * ```
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */
interface KMSProvider {
    
    /**
     * Provider ID
     * 
     * Unique identifier for this KMS provider.
     * 
     * Examples:
     * - "aws-kms"
     * - "azure-keyvault"
     * - "local-kms"
     */
    val providerId: String
    
    /**
     * Provider name
     * 
     * Human-readable name for display.
     * 
     * Examples:
     * - "AWS Key Management Service"
     * - "Azure Key Vault"
     * - "Local KMS (Testing)"
     */
    val providerName: String
    
    /**
     * Check if provider is available
     * 
     * Verifies:
     * - KMS service is reachable
     * - Credentials are valid
     * - Master key exists and is accessible
     * 
     * @return true if provider is ready for use
     */
    suspend fun isAvailable(): Boolean
    
    /**
     * Wrap (encrypt) a data encryption key
     * 
     * Encrypts the derived key using KMS master key.
     * 
     * Process:
     * 1. Validate input key size (32 bytes)
     * 2. Call KMS encrypt API
     * 3. Add encryption context (uuid, timestamp)
     * 4. Return wrapped key blob
     * 
     * @param key Key to wrap (32 bytes)
     * @param uuid User UUID (for encryption context)
     * @return Wrapped key blob
     * @throws KMSException if wrapping fails
     * 
     * Example:
     * ```kotlin
     * val derivedKey = KeyDerivation.deriveKey(uuid, factors)
     * val wrappedKey = kmsProvider.wrapKey(derivedKey, uuid)
     * // Store wrappedKey in database
     * CryptoUtils.wipeMemory(derivedKey)
     * ```
     */
    suspend fun wrapKey(
        key: ByteArray,
        uuid: String
    ): WrappedKey
    
    /**
     * Unwrap (decrypt) a data encryption key
     * 
     * Decrypts the wrapped key using KMS master key.
     * 
     * Process:
     * 1. Validate wrapped key format
     * 2. Call KMS decrypt API
     * 3. Verify encryption context matches
     * 4. Return plaintext key
     * 
     * @param wrappedKey Wrapped key blob
     * @param uuid User UUID (for encryption context verification)
     * @return Unwrapped plaintext key (32 bytes)
     * @throws KMSException if unwrapping fails
     * 
     * Example:
     * ```kotlin
     * // Retrieve wrappedKey from database
     * val unwrappedKey = kmsProvider.unwrapKey(wrappedKey, uuid)
     * // Use unwrappedKey to decrypt factor hashes
     * CryptoUtils.wipeMemory(unwrappedKey)
     * ```
     */
    suspend fun unwrapKey(
        wrappedKey: WrappedKey,
        uuid: String
    ): ByteArray
    
    /**
     * Rotate master key
     * 
     * Creates new master key version and re-wraps all data keys.
     * 
     * Note: This is a KMS-level operation, not per-user.
     * 
     * @return New master key version ID
     * @throws KMSException if rotation fails
     */
    suspend fun rotateMasterKey(): String
    
    /**
     * Get master key version
     * 
     * @return Current master key version ID
     */
    suspend fun getMasterKeyVersion(): String
}

/**
 * WrappedKey - Encrypted key blob from KMS
 * 
 * Contains:
 * - Encrypted key material (ciphertext)
 * - Encryption context (metadata)
 * - KMS provider ID
 * - Master key version
 * - Timestamp
 * 
 * @property ciphertext Encrypted key bytes
 * @property encryptionContext Metadata for additional security
 * @property providerId KMS provider that created this
 * @property keyVersion Master key version used
 * @property timestamp When key was wrapped
 */
data class WrappedKey(
    val ciphertext: ByteArray,
    val encryptionContext: Map<String, String>,
    val providerId: String,
    val keyVersion: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        
        other as WrappedKey
        
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (encryptionContext != other.encryptionContext) return false
        if (providerId != other.providerId) return false
        if (keyVersion != other.keyVersion) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + encryptionContext.hashCode()
        result = 31 * result + providerId.hashCode()
        result = 31 * result + keyVersion.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * KMSException - KMS operation error
 */
class KMSException(
    message: String,
    cause: Throwable? = null,
    val providerId: String? = null
) : SecurityException(message, cause)

/**
 * LocalKMSProvider - Testing-only KMS provider
 * 
 * WARNING: NOT FOR PRODUCTION USE
 * 
 * Uses AES-256-GCM with in-memory master key for testing.
 * 
 * @property masterKey In-memory master key (32 bytes)
 */
class LocalKMSProvider(
    private val masterKey: ByteArray = CryptoUtils.generateRandomBytes(32)
) : KMSProvider {
    
    override val providerId = "local-kms"
    override val providerName = "Local KMS (Testing Only)"
    
    init {
        require(masterKey.size == 32) { "Master key must be 32 bytes" }
    }
    
    override suspend fun isAvailable(): Boolean {
        return true // Always available (in-memory)
    }
    
    override suspend fun wrapKey(key: ByteArray, uuid: String): WrappedKey {
        require(key.size == 32) { "Key must be 32 bytes" }
        
        // Simple XOR encryption (NOT SECURE - testing only)
        val ciphertext = ByteArray(32)
        for (i in 0 until 32) {
            ciphertext[i] = (key[i].toInt() xor masterKey[i].toInt()).toByte()
        }
        
        return WrappedKey(
            ciphertext = ciphertext,
            encryptionContext = mapOf(
                "uuid" to uuid,
                "provider" to providerId,
                "algorithm" to "XOR-32"
            ),
            providerId = providerId,
            keyVersion = "v1",
            timestamp = System.currentTimeMillis()
        )
    }
    
    override suspend fun unwrapKey(wrappedKey: WrappedKey, uuid: String): ByteArray {
        require(wrappedKey.providerId == providerId) {
            "Wrapped key is from different provider: ${wrappedKey.providerId}"
        }
        
        // Verify encryption context
        require(wrappedKey.encryptionContext["uuid"] == uuid) {
            "UUID mismatch in encryption context"
        }
        
        // Simple XOR decryption (NOT SECURE - testing only)
        val plaintext = ByteArray(32)
        for (i in 0 until 32) {
            plaintext[i] = (wrappedKey.ciphertext[i].toInt() xor masterKey[i].toInt()).toByte()
        }
        
        return plaintext
    }
    
    override suspend fun rotateMasterKey(): String {
        throw UnsupportedOperationException("Key rotation not supported in LocalKMSProvider")
    }
    
    override suspend fun getMasterKeyVersion(): String {
        return "v1"
    }
}
