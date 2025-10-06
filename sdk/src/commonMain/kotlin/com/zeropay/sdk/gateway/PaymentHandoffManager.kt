package com.zeropay.sdk.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Payment Handoff Manager
 * 
 * Orchestrates post-authentication handoff to payment gateways.
 * 
 * Simplified flow:
 * 1. User authenticates → zkSNARK proof verified ✅
 * 2. Manager retrieves encrypted gateway token
 * 3. Sends authentication to gateway API
 * 4. Done. Gateway handles payment processing.
 * 
 * Zero-knowledge:
 * - Only sends: userUuid, proof hash, amount, merchant
 * - Don't track payment status
 * - Don't validate anything (gateway's job)
 * - Retry network failures only
 */
class PaymentHandoffManager(
    private val tokenStorage: GatewayTokenStorage
) {
    
    private val gatewayRegistry = ConcurrentHashMap<String, GatewayProvider>()
    
    /**
     * Register a gateway provider
     */
    fun registerGateway(gateway: GatewayProvider) {
        gatewayRegistry[gateway.gatewayId] = gateway
    }
    
    /**
     * Get gateway by ID
     */
    fun getGateway(gatewayId: String): GatewayProvider? {
        return gatewayRegistry[gatewayId]
    }
    
    /**
     * List all available gateways for a user
     * 
     * @param userUuid User identifier
     * @return List of gateways with linked tokens
     */
    suspend fun getAvailableGateways(userUuid: String): List<GatewayProvider> {
        return withContext(Dispatchers.IO) {
            gatewayRegistry.values.filter { gateway ->
                try {
                    gateway.isAvailable(userUuid)
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
    
    /**
     * Authenticate user to payment gateway
     * 
     * Called after zkSNARK verification succeeds.
     * Sends authentication proof to gateway.
     * Gateway handles the rest.
     * 
     * @param gatewayId Gateway identifier (e.g., "stripe")
     * @param request Authentication request
     * @return True if authentication handoff succeeded
     * @throws GatewayException if gateway not found or handoff fails
     */
    suspend fun authenticate(
        gatewayId: String,
        request: AuthRequest
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val gateway = gatewayRegistry[gatewayId]
                ?: throw GatewayException(
                    "Gateway not found: $gatewayId",
                    gatewayId = gatewayId
                )
            
            // Check if user has linked this gateway
            if (!gateway.isAvailable(request.userUuid)) {
                throw GatewayException(
                    "Gateway $gatewayId not linked for user ${request.userUuid}",
                    gatewayId = gatewayId
                )
            }
            
            // Authenticate with retry on network failures
            try {
                gateway.authenticate(request)
            } catch (e: Exception) {
                throw GatewayException(
                    "Authentication handoff failed: ${e.message}",
                    gatewayId = gatewayId,
                    cause = e
                )
            }
        }
    }
    
    /**
     * Authenticate with auto-selection
     * 
     * Uses first available gateway for the user.
     * 
     * @param request Authentication request
     * @return True if authentication handoff succeeded
     * @throws GatewayException if no gateways available
     */
    suspend fun authenticateAuto(request: AuthRequest): Boolean {
        return withContext(Dispatchers.IO) {
            val availableGateways = getAvailableGateways(request.userUuid)
            
            if (availableGateways.isEmpty()) {
                throw GatewayException(
                    "No payment gateways linked for user ${request.userUuid}",
                    gatewayId = "auto"
                )
            }
            
            // Use first available gateway
            val gateway = availableGateways.first()
            authenticate(gateway.gatewayId, request)
        }
    }
}
