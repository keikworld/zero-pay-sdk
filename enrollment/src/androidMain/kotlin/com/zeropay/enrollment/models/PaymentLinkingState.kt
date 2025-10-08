// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/models/PaymentLinkingState.kt

package com.zeropay.enrollment.models

/**
 * Payment linking state for UI
 */
sealed class PaymentLinkingState {
    object Idle : PaymentLinkingState()
    object Linking : PaymentLinkingState()
    data class Success(val link: PaymentProviderLink) : PaymentLinkingState()
    data class Error(val message: String) : PaymentLinkingState()
}
