package com.zeropay.sdk.api

import com.zeropay.sdk.models.api.*
import com.zeropay.sdk.network.NetworkException
import com.zeropay.sdk.network.ZeroPayHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Blockchain API Client
 *
 * Handles blockchain wallet operations (Solana, Ethereum, etc.).
 *
 * Supported Operations:
 * - Link wallet to user UUID
 * - Unlink wallet
 * - Get linked wallets
 * - Check wallet balance
 * - Estimate transaction fees
 * - Verify transaction signatures
 *
 * Supported Networks:
 * - Solana (Mainnet, Devnet, Testnet)
 * - Ethereum (future)
 * - Polygon (future)
 * - Binance Smart Chain (future)
 *
 * Security:
 * - Wallet addresses never stored unencrypted
 * - Signature verification prevents impersonation
 * - Rate limiting on RPC calls
 * - Zero-knowledge: Server doesn't see private keys
 *
 * Usage:
 * ```kotlin
 * val client = BlockchainClient(httpClient, apiConfig)
 *
 * // Link Phantom wallet
 * val result = client.linkWallet(
 *     userUuid = uuid,
 *     walletAddress = "7x...abc",
 *     network = "solana",
 *     walletType = "phantom"
 * )
 *
 * // Get wallet balance
 * val balance = client.getWalletBalance(walletAddress, "solana")
 * ```
 *
 * @param httpClient HTTP client for network requests
 * @param config API configuration
 * @version 1.0.0
 */
class BlockchainClient(
    private val httpClient: ZeroPayHttpClient,
    private val config: ApiConfig
) {

    companion object {
        private const val TAG = "BlockchainClient"

        /**
         * Supported blockchain networks
         */
        val SUPPORTED_NETWORKS = listOf("solana", "ethereum", "polygon", "binance")

        /**
         * Supported wallet types
         */
        val SUPPORTED_WALLETS = listOf("phantom", "metamask", "coinbase", "trust", "other")
    }

    /**
     * Link wallet to user UUID
     *
     * Associates a blockchain wallet address with a user's ZeroPay account.
     *
     * Security:
     * - Signature verification ensures wallet ownership
     * - Server stores only hash of wallet address
     * - GDPR compliant: Can be unlinked at any time
     *
     * @param userUuid User UUID
     * @param walletAddress Blockchain wallet address
     * @param blockchainNetwork Network name ("solana", "ethereum", etc.)
     * @param walletType Wallet provider ("phantom", "metamask", etc.)
     * @param verificationSignature Optional signature proving wallet ownership
     * @return Link result with verification status
     * @throws IllegalArgumentException if validation fails
     * @throws NetworkException on network error
     */
    suspend fun linkWallet(
        userUuid: String,
        walletAddress: String,
        blockchainNetwork: String = "solana",
        walletType: String = "phantom",
        verificationSignature: String? = null
    ): Result<LinkWalletResponse> = withContext(Dispatchers.IO) {
        try {
            // Validate inputs
            validateUuid(userUuid)
            require(walletAddress.isNotBlank()) {
                "Wallet address cannot be blank"
            }
            require(blockchainNetwork.lowercase() in SUPPORTED_NETWORKS) {
                "Unsupported blockchain network: $blockchainNetwork. Supported: $SUPPORTED_NETWORKS"
            }
            require(walletType.lowercase() in SUPPORTED_WALLETS) {
                "Unsupported wallet type: $walletType. Supported: $SUPPORTED_WALLETS"
            }

            // Validate wallet address format (basic)
            when (blockchainNetwork.lowercase()) {
                "solana" -> {
                    require(walletAddress.length in 32..44) {
                        "Invalid Solana address length"
                    }
                }
                "ethereum", "polygon", "binance" -> {
                    require(walletAddress.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
                        "Invalid EVM address format"
                    }
                }
            }

            // Generate nonce
            val nonce = generateNonce()

            // Create request
            val request = LinkWalletRequest(
                user_uuid = userUuid,
                wallet_address = walletAddress,
                blockchain_network = blockchainNetwork.lowercase(),
                wallet_type = walletType.lowercase(),
                verification_signature = verificationSignature,
                nonce = nonce,
                timestamp = getCurrentTimestamp()
            )

            // Execute HTTP request
            val response = httpClient.post<ApiResponse<LinkWalletResponse>>(
                endpoint = ApiConfig.Endpoints.BLOCKCHAIN_WALLET_LINK,
                body = request
            )

            // Parse response
            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("User not found")
                    )
                }
                response.statusCode == 409 -> {
                    Result.failure(
                        IllegalArgumentException("Wallet already linked to another user")
                    )
                }
                response.isClientError -> {
                    val error = response.body?.error
                    Result.failure(
                        IllegalArgumentException(
                            error?.message ?: "Invalid request"
                        )
                    )
                }
                response.isServerError -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Server error"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.UnknownException("Unexpected response: ${response.statusCode}")
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Wallet linking failed", e))
        }
    }

    /**
     * Unlink wallet from user
     *
     * Removes wallet association (GDPR compliance).
     *
     * @param userUuid User UUID
     * @param walletAddress Wallet address to unlink
     * @param blockchainNetwork Network name
     * @return true if successful
     */
    suspend fun unlinkWallet(
        userUuid: String,
        walletAddress: String,
        blockchainNetwork: String = "solana"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            validateUuid(userUuid)
            require(walletAddress.isNotBlank()) { "Wallet address cannot be blank" }

            val response = httpClient.delete<ApiResponse<Unit>>(
                endpoint = "${ApiConfig.Endpoints.BLOCKCHAIN_WALLET_UNLINK}?user_uuid=$userUuid&wallet_address=$walletAddress&network=$blockchainNetwork"
            )

            when {
                response.isSuccessful -> Result.success(true)
                response.statusCode == 404 -> Result.success(true) // Already unlinked
                else -> Result.failure(
                    NetworkException.HttpException(
                        statusCode = response.statusCode,
                        message = "Failed to unlink wallet"
                    )
                )
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Wallet unlinking failed", e))
        }
    }

    /**
     * Get linked wallets for user
     *
     * Returns all wallets associated with user UUID.
     *
     * @param userUuid User UUID
     * @return List of linked wallets
     */
    suspend fun getLinkedWallets(
        userUuid: String
    ): Result<List<LinkWalletResponse>> = withContext(Dispatchers.IO) {
        try {
            validateUuid(userUuid)

            val response = httpClient.get<ApiResponse<List<LinkWalletResponse>>>(
                endpoint = "${ApiConfig.Endpoints.BLOCKCHAIN_WALLET_GET}/$userUuid"
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data ?: emptyList())
                }
                response.statusCode == 404 -> {
                    // No wallets found - return empty list
                    Result.success(emptyList())
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to get wallets"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Get wallets failed", e))
        }
    }

    /**
     * Get wallet balance
     *
     * Queries blockchain RPC for current wallet balance.
     *
     * @param walletAddress Wallet address
     * @param blockchainNetwork Network name
     * @return Balance information
     */
    suspend fun getWalletBalance(
        walletAddress: String,
        blockchainNetwork: String = "solana"
    ): Result<WalletBalanceResponse> = withContext(Dispatchers.IO) {
        try {
            require(walletAddress.isNotBlank()) { "Wallet address cannot be blank" }
            require(blockchainNetwork.lowercase() in SUPPORTED_NETWORKS) {
                "Unsupported network: $blockchainNetwork"
            }

            val response = httpClient.get<ApiResponse<WalletBalanceResponse>>(
                endpoint = "${ApiConfig.Endpoints.BLOCKCHAIN_BALANCE}/$walletAddress",
                queryParams = mapOf("network" to blockchainNetwork.lowercase())
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("Wallet not found on blockchain")
                    )
                }
                response.statusCode == 429 -> {
                    Result.failure(
                        NetworkException.RateLimitException(
                            message = "RPC rate limit exceeded"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to get balance"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Balance check failed", e))
        }
    }

    /**
     * Estimate transaction fee
     *
     * Calculates estimated gas/fee for a blockchain transaction.
     *
     * @param fromAddress Sender address
     * @param toAddress Recipient address
     * @param amount Transaction amount
     * @param blockchainNetwork Network name
     * @return Fee estimate
     */
    suspend fun estimateTransactionFee(
        fromAddress: String,
        toAddress: String,
        amount: Double,
        blockchainNetwork: String = "solana"
    ): Result<TransactionEstimateResponse> = withContext(Dispatchers.IO) {
        try {
            require(fromAddress.isNotBlank()) { "From address cannot be blank" }
            require(toAddress.isNotBlank()) { "To address cannot be blank" }
            require(amount > 0.0) { "Amount must be positive" }
            require(blockchainNetwork.lowercase() in SUPPORTED_NETWORKS) {
                "Unsupported network: $blockchainNetwork"
            }

            val request = TransactionEstimateRequest(
                from_address = fromAddress,
                to_address = toAddress,
                amount = amount,
                blockchain_network = blockchainNetwork.lowercase()
            )

            val response = httpClient.post<ApiResponse<TransactionEstimateResponse>>(
                endpoint = ApiConfig.Endpoints.BLOCKCHAIN_TX_ESTIMATE,
                body = request
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 400 -> {
                    Result.failure(
                        IllegalArgumentException("Invalid transaction parameters")
                    )
                }
                response.statusCode == 429 -> {
                    Result.failure(
                        NetworkException.RateLimitException(
                            message = "RPC rate limit exceeded"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to estimate fee"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Fee estimation failed", e))
        }
    }

    /**
     * Verify transaction
     *
     * Verifies that a blockchain transaction was successfully completed.
     *
     * Use Case: Confirm payment was received before releasing goods/services
     *
     * @param transactionSignature Transaction hash/signature
     * @param blockchainNetwork Network name
     * @param expectedAmount Optional: Verify transaction amount matches
     * @param expectedRecipient Optional: Verify recipient address matches
     * @return Verification result with transaction details
     */
    suspend fun verifyTransaction(
        transactionSignature: String,
        blockchainNetwork: String = "solana",
        expectedAmount: Double? = null,
        expectedRecipient: String? = null
    ): Result<TransactionVerificationResponse> = withContext(Dispatchers.IO) {
        try {
            require(transactionSignature.isNotBlank()) {
                "Transaction signature cannot be blank"
            }
            require(blockchainNetwork.lowercase() in SUPPORTED_NETWORKS) {
                "Unsupported network: $blockchainNetwork"
            }

            expectedAmount?.let {
                require(it > 0.0) { "Expected amount must be positive" }
            }

            val request = TransactionVerificationRequest(
                transaction_signature = transactionSignature,
                blockchain_network = blockchainNetwork.lowercase(),
                expected_amount = expectedAmount,
                expected_recipient = expectedRecipient
            )

            val response = httpClient.post<ApiResponse<TransactionVerificationResponse>>(
                endpoint = ApiConfig.Endpoints.BLOCKCHAIN_TX_VERIFY,
                body = request
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("Transaction not found on blockchain")
                    )
                }
                response.statusCode == 429 -> {
                    Result.failure(
                        NetworkException.RateLimitException(
                            message = "RPC rate limit exceeded"
                        )
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to verify transaction"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Transaction verification failed", e))
        }
    }

    /**
     * Get transaction status
     *
     * Quick check of transaction status without full verification.
     *
     * @param transactionSignature Transaction hash/signature
     * @param blockchainNetwork Network name
     * @return Transaction verification response
     */
    suspend fun getTransactionStatus(
        transactionSignature: String,
        blockchainNetwork: String = "solana"
    ): Result<TransactionVerificationResponse> = withContext(Dispatchers.IO) {
        try {
            require(transactionSignature.isNotBlank()) {
                "Transaction signature cannot be blank"
            }

            val response = httpClient.get<ApiResponse<TransactionVerificationResponse>>(
                endpoint = "${ApiConfig.Endpoints.BLOCKCHAIN_TX_STATUS}/$transactionSignature",
                queryParams = mapOf("network" to blockchainNetwork.lowercase())
            )

            when {
                response.isSuccessful && response.body?.success == true -> {
                    Result.success(response.body.data!!)
                }
                response.statusCode == 404 -> {
                    Result.failure(
                        IllegalArgumentException("Transaction not found")
                    )
                }
                else -> {
                    Result.failure(
                        NetworkException.HttpException(
                            statusCode = response.statusCode,
                            message = "Failed to get transaction status"
                        )
                    )
                }
            }

        } catch (e: NetworkException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(NetworkException.UnknownException("Status check failed", e))
        }
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    /**
     * Validate UUID format (RFC 4122, version 4)
     */
    private fun validateUuid(uuid: String) {
        require(uuid.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE))) {
            "Invalid UUID format: $uuid"
        }
    }

    /**
     * Generate secure nonce
     */
    private fun generateNonce(): String {
        val randomBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Get current timestamp
     */
    private fun getCurrentTimestamp(): String {
        return Instant.now().toString()
    }
}
