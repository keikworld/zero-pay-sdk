// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt

package com.zeropay.merchant.models

import com.zeropay.merchant.config.MerchantConfig

/**
 * Verification Result - PRODUCTION VERSION
 * 
 * Result of user verification attempt.
 */
sealed class VerificationResult {
    
    data class Success(
        val sessionId: String,
        val userId: String,
        val merchantId: String,
        val verifiedFactors: List<com.zeropay.sdk.Factor>,
        val zkProof: ByteArray?,
        val timestamp: Long = System.currentTimeMillis(),
        val transactionId: String? = null
    ) : VerificationResult()
    
    data class Failure(
        val sessionId: String,
        val error: MerchantConfig.VerificationError,
        val message: String,
        val attemptNumber: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val canRetry: Boolean = true
    ) : VerificationResult()
    
    data class PendingFactors(
        val sessionId: String,
        val remainingFactors: List<com.zeropay.sdk.Factor>,
        val completedFactors: List<com.zeropay.sdk.Factor>
    ) : VerificationResult()
}
