// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/EnrollmentActivity.kt

package com.zeropay.enrollment.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.zeropay.enrollment.EnrollmentManager
import com.zeropay.enrollment.consent.ConsentManager
import com.zeropay.enrollment.models.EnrollmentResult
import com.zeropay.enrollment.payment.PaymentProviderManager
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.gateway.GatewayTokenStorage
import com.zeropay.sdk.network.SecureApiClient
import com.zeropay.sdk.storage.KeyStoreManager

/**
 * Enrollment Activity - PRODUCTION VERSION
 * 
 * Main entry point for user enrollment.
 * 
 * Features:
 * - Complete 5-step enrollment wizard
 * - Success/failure handling
 * - Navigation management
 * - Dependency injection
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
class EnrollmentActivity : ComponentActivity() {
    
    private lateinit var enrollmentManager: EnrollmentManager
    private lateinit var paymentProviderManager: PaymentProviderManager
    private lateinit var consentManager: ConsentManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize dependencies
        initializeDependencies()
        
        setContent {
            MaterialTheme {
                EnrollmentApp(
                    enrollmentManager = enrollmentManager,
                    paymentProviderManager = paymentProviderManager,
                    consentManager = consentManager,
                    onEnrollmentComplete = { result ->
                        handleEnrollmentSuccess(result)
                    },
                    onEnrollmentCancelled = {
                        handleEnrollmentCancelled()
                    }
                )
            }
        }
    }
    
    /**
     * Initialize all dependencies
     */
    private fun initializeDependencies() {
        // KeyStore for encrypted storage
        val keyStoreManager = KeyStoreManager(applicationContext)
        
        // API client for backend communication
        val apiClient = SecureApiClient()
        
        // Redis cache client
        val redisCacheClient = RedisCacheClient(apiClient)
        
        // Gateway token storage
        val gatewayTokenStorage = GatewayTokenStorage()
        
        // Enrollment manager
        enrollmentManager = EnrollmentManager(
            keyStoreManager = keyStoreManager,
            redisCacheClient = redisCacheClient
        )
        
        // Payment provider manager
        paymentProviderManager = PaymentProviderManager(
            context = applicationContext,
            tokenStorage = gatewayTokenStorage
        )
        
        // Consent manager
        consentManager = ConsentManager(applicationContext)
    }
    
    /**
     * Handle successful enrollment
     */
    private fun handleEnrollmentSuccess(result: EnrollmentResult.Success) {
        // Show success message
        Toast.makeText(
            this,
            "✓ Enrollment successful!\nUUID: ${result.user.uuid}",
            Toast.LENGTH_LONG
        ).show()
        
        // TODO: Navigate to success screen or main app
        finish()
    }
    
    /**
     * Handle enrollment cancellation
     */
    private fun handleEnrollmentCancelled() {
        Toast.makeText(
            this,
            "Enrollment cancelled",
            Toast.LENGTH_SHORT
        ).show()
        
        finish()
    }
}

/**
 * Enrollment App Composable
 */
@Composable
private fun EnrollmentApp(
    enrollmentManager: EnrollmentManager,
    paymentProviderManager: PaymentProviderManager,
    consentManager: ConsentManager,
    onEnrollmentComplete: (EnrollmentResult.Success) -> Unit,
    onEnrollmentCancelled: () -> Unit
) {
    EnrollmentScreen(
        enrollmentManager = enrollmentManager,
        paymentProviderManager = paymentProviderManager,
        consentManager = consentManager,
        onEnrollmentComplete = onEnrollmentComplete,
        onEnrollmentCancelled = onEnrollmentCancelled
    )
}
