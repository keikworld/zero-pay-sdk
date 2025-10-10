package com.zeropay.enrollment.payment

import android.content.Context
import android.util.Log
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.models.PaymentProviderLink
import com.zeropay.enrollment.payment.providers.*
import com.zeropay.sdk.Factor
import com.zeropay.sdk.gateway.GatewayTokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Payment Provider Manager - PRODUCTION VERSION
 * 
 * Orchestrates payment provider enrollment and linking.
 * 
 * Features:
 * - OAuth 2.0 flow handling
 * - Hashed reference generation
 * - Token encryption & storage
 * - Provider availability checking
 * - Thread-safe operations
 * 
 * Architecture:
 * - Pluggable providers (easy to add new ones)
 * - Reuses SDK GatewayTokenStorage
 * - Zero-knowledge (tokens encrypted)
 * 
 * Security:
 * - AES-256-GCM encryption
 * - Key derived from UUID + factors
 * - Rate limiting per provider
 * - Input validation
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class PaymentProviderManager(
    private val context: Context,
    private val tokenStorage: GatewayTokenStorage
) {
    
    companion object {
        private const val TAG = "PaymentProviderManager"
    }
    
    // Registry of payment providers
    private val providers = ConcurrentHashMap<String, PaymentProviderInterface>()
    
    init {
        // Register all providers
        registerProvider(GooglePayProvider(context))
        registerProvider(ApplePayProvider(context))
        registerProvider(StripeProvider(context))
        registerProvider(PayUProvider())
        registerProvider(YappyProvider())
        registerProvider(NequiProvider())
        registerProvider(TilopayProvider())
        registerProvider(AlipayProvider())
        registerProvider(WeChatPayProvider())
        registerProvider(AdyenProvider(context))
        registerProvider(MercadoPagoProvider(context))
        registerProvider(WorldpayProvider())
        registerProvider(AuthorizeNetProvider())
    }
    
    /**
     * Register a payment provider
     */
    fun registerProvider(provider: PaymentProviderInterface) {
        providers[provider.providerId] = provider
        Log.d(TAG, "Registered provider: ${provider.providerName}")
    }
    
    /**
     * Get all available providers
     * 
     * @return List of provider IDs and names
     */
    fun getAvailableProviders(): List<EnrollmentConfig.PaymentProvider> {
        return EnrollmentConfig.PaymentProvider.values().filter { provider ->
            providers.containsKey(provider.id)
        }
    }
    
    /**
     * Check if provider is available
     * 
     * @param providerId Provider ID
     * @return true if provider exists and is supported
     */
    fun isProviderAvailable(providerId: String): Boolean {
        return providers.containsKey(providerId)
    }
    
    /**
     * Link payment provider during enrollment
     * 
     * Flow:
     * 1. Validate provider exists
     * 2. Execute OAuth or hashed reference flow
     * 3. Encrypt token with UUID + factors
     * 4. Store in Redis via GatewayTokenStorage
     * 5. Return PaymentProviderLink
     * 
     * @param providerId Provider ID
     * @param uuid User UUID
     * @param factorDigests Factor digests for key derivation
     * @param linkData Provider-specific data (email, phone, etc.)
     * @return PaymentProviderLink or null on failure
     */
    suspend fun linkProvider(
        providerId: String,
        uuid: String,
        factorDigests: Map<Factor, ByteArray>,
        linkData: Map<String, String> = emptyMap()
    ): Result<PaymentProviderLink> = withContext(Dispatchers.IO) {
        try {
            // Validate provider exists
            val provider = providers[providerId]
                ?: return@withContext Result.failure(
                    Exception("Provider not found: $providerId")
                )
            
            Log.d(TAG, "Linking provider: ${provider.providerName}")
            
            // Execute provider-specific linking
            val token = when (provider.linkType) {
                EnrollmentConfig.PaymentLinkType.OAUTH -> {
                    // OAuth flow
                    provider.initiateOAuthFlow(context)
                }
                
                EnrollmentConfig.PaymentLinkType.HASHED_REF -> {
                    // Hashed reference (email/phone)
                    val email = linkData["email"]
                        ?: return@withContext Result.failure(
                            Exception("Email required for ${provider.providerName}")
                        )
                    provider.generateHashedReference(uuid, email)
                }
                
                EnrollmentConfig.PaymentLinkType.NFC -> {
                    // NFC (future implementation)
                    return@withContext Result.failure(
                        Exception("NFC linking not yet implemented")
                    )
                }
            }
            
            // Derive encryption key from UUID + factors
            val encryptionKey = PaymentTokenEncryption.deriveEncryptionKey(
                uuid, factorDigests
            )
            
            // Encrypt token
            val encryptedToken = PaymentTokenEncryption.encryptToken(token, encryptionKey)
            
            // Store in GatewayTokenStorage (reuse SDK infrastructure)
            val stored = tokenStorage.storeToken(
                userUuid = uuid,
                gatewayId = providerId,
                token = encryptedToken.toBase64()
            )
            
            // Wipe encryption key from memory
            PaymentTokenEncryption.wipeKey(encryptionKey)
            
            if (!stored) {
                return@withContext Result.failure(
                    Exception("Failed to store token for ${provider.providerName}")
                )
            }
            
            // Create link record
            val link = PaymentProviderLink(
                providerId = providerId,
                providerName = provider.providerName,
                linkType = provider.linkType,
                encryptedToken = encryptedToken.ciphertext,
                hashedReference = if (provider.linkType == EnrollmentConfig.PaymentLinkType.HASHED_REF) {
                    token
                } else null,
                linkedAt = System.currentTimeMillis()
            )
            
            Log.i(TAG, "Successfully linked provider: ${provider.providerName}")
            Result.success(link)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to link provider: $providerId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Unlink payment provider
     * 
     * @param providerId Provider ID
     * @param uuid User UUID
     * @return true if successfully unlinked
     */
    suspend fun unlinkProvider(
        providerId: String,
        uuid: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val deleted = tokenStorage.deleteToken(uuid, providerId)
            if (deleted) {
                Log.i(TAG, "Unlinked provider: $providerId")
            }
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unlink provider: $providerId", e)
            false
        }
    }
    
    /**
     * Get linked providers for user
     * 
     * @param uuid User UUID
     * @return List of linked provider IDs
     */
    suspend fun getLinkedProviders(uuid: String): List<String> = withContext(Dispatchers.IO) {
        // Query all providers and check which have tokens
        providers.keys.filter { providerId ->
            try {
                tokenStorage.getToken(uuid, providerId) != null
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * Payment Provider Interface
 * 
 * All payment providers must implement this interface
 */
interface PaymentProviderInterface {
    val providerId: String
    val providerName: String
    val linkType: EnrollmentConfig.PaymentLinkType
    
    /**
     * Initiate OAuth 2.0 flow (for OAuth providers)
     * 
     * @param context Android context
     * @return OAuth token
     * @throws Exception if OAuth fails
     */
    suspend fun initiateOAuthFlow(context: Context): String
    
    /**
     * Generate hashed reference (for hashed ref providers)
     * 
     * @param uuid User UUID
     * @param identifier Email or phone number
     * @return Hashed reference string
     */
    fun generateHashedReference(uuid: String, identifier: String): String
}
