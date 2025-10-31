// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt

package com.zeropay.merchant.models

import com.zeropay.merchant.config.MerchantConfig
import com.zeropay.sdk.Factor

/**
 * Verification Session - PRODUCTION VERSION
 * 
 * Tracks complete verification session state.
 * 
 * @property sessionId Unique session identifier
 * @property userId User UUID being verified
 * @property merchantId Merchant identifier
 * @property transactionAmount Transaction amount (USD)
 * @property requiredFactors Factors user must complete
 * @property completedFactors Factors user has completed
 * @property submittedDigests Digests submitted by user
 * @property currentStage Current verification stage
 * @property attemptCount Number of verification attempts
 * @property startTime Session start timestamp
 * @property expiresAt Session expiration timestamp
 * @property deviceFingerprint Device identifier
 * @property ipAddress Client IP address
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
data class VerificationSession(
    val sessionId: String,
    val userId: String,
    val merchantId: String,
    val transactionAmount: Double,
    val requiredFactors: List<Factor>,
    var completedFactors: MutableList<Factor> = mutableListOf(),
    var submittedDigests: MutableMap<Factor, ByteArray> = mutableMapOf(),
    var currentStage: MerchantConfig.VerificationStage = MerchantConfig.VerificationStage.UUID_INPUT,
    var attemptCount: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (MerchantConfig.VERIFICATION_TIMEOUT_SECONDS * 1000),
    val deviceFingerprint: String? = null,
    val ipAddress: String? = null
) {
    /**
     * Check if session is expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }
    
    /**
     * Check if all required factors completed
     */
    fun isComplete(): Boolean {
        return completedFactors.containsAll(requiredFactors)
    }
    
    /**
     * Get remaining time in seconds
     */
    fun getRemainingTimeSeconds(): Long {
        val remaining = (expiresAt - System.currentTimeMillis()) / 1000
        return remaining.coerceAtLeast(0)
    }
    
    /**
     * Check if max attempts reached
     */
    fun isMaxAttemptsReached(): Boolean {
        return attemptCount >= MerchantConfig.MAX_VERIFICATION_ATTEMPTS
    }
    
    /**
     * Add completed factor
     */
    fun addCompletedFactor(factor: Factor, digest: ByteArray) {
        if (!completedFactors.contains(factor)) {
            completedFactors.add(factor)
            submittedDigests[factor] = digest
        }
    }
}
