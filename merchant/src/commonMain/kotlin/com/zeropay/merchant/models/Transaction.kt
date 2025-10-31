// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt

package com.zeropay.merchant.models

/**
 * Transaction - PRODUCTION VERSION
 * 
 * Represents a payment transaction.
 * 
 * @property transactionId Unique transaction identifier
 * @property sessionId Associated verification session
 * @property userId User UUID
 * @property merchantId Merchant identifier
 * @property amount Transaction amount
 * @property currency Transaction currency (default USD)
 * @property gateway Payment gateway used
 * @property status Transaction status
 * @property timestamp Transaction timestamp
 * @property receiptUrl Receipt URL (if generated)
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
data class Transaction(
    val transactionId: String,
    val sessionId: String,
    val userId: String,
    val merchantId: String,
    val amount: Double,
    val currency: String = "USD",
    val gateway: String,
    var status: TransactionStatus = TransactionStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis(),
    var receiptUrl: String? = null,
    var gatewayTransactionId: String? = null,
    var errorMessage: String? = null
)

enum class TransactionStatus {
    PENDING,
    AUTHORIZED,
    COMPLETED,
    FAILED,
    CANCELLED,
    REFUNDED
}
