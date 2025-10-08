package com.zeropay.enrollment.payment.providers

import android.content.Context
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.payment.PaymentProviderInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Apple Pay Provider - OAuth Implementation
 * 
 * Note: Apple Pay is iOS-only, but we include for completeness.
 * On Android, this will show "Not available on this platform"
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class ApplePayProvider(private val context: Context) : PaymentProviderInterface {
    
    override val providerId = "applepay"
    override val providerName = "Apple Pay"
    override val linkType = EnrollmentConfig.PaymentLinkType.OAUTH
    
    override suspend fun initiateOAuthFlow(context: Context): String = 
        withContext(Dispatchers.IO) {
            throw UnsupportedOperationException(
                "Apple Pay is only available on iOS devices"
            )
        }
    
    override fun generateHashedReference(uuid: String, identifier: String): String {
        throw UnsupportedOperationException("Apple Pay uses OAuth, not hashed reference")
    }
}
