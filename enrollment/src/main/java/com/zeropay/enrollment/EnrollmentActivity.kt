// File: enrollment/src/main/java/com/zeropay/enrollment/EnrollmentActivity.kt
package com.zeropay.enrollment

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.zeropay.sdk.ZeroPay
import com.zeropay.sdk.Factor
import com.zeropay.sdk.crypto.CryptoUtils
import com.zeropay.sdk.storage.KeyStoreManager
import com.zeropay.sdk.network.SecureApiClient
import java.util.UUID

/**
 * Complete Enrollment Application
 * 
 * Flow:
 * 1. Generate UUID for user
 * 2. Select factors (min 2 for PSD3 SCA)
 * 3. Collect factor data via SDK canvases
 * 4. Hash each factor with SHA-256
 * 5. Encrypt digests locally
 * 6. Send to Redis cache with 24h TTL
 * 7. Return enrollment confirmation
 * 
 * Security:
 * - No raw data stored
 * - All digests SHA-256 (32 bytes)
 * - Encrypted at rest (KeyStore)
 * - HTTPS only for transmission
 * - Certificate pinning enforced
 */
class EnrollmentActivity : ComponentActivity() {
    
    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var apiClient: SecureApiClient
    private var userUuid by mutableStateOf<String?>(null)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SDK
        ZeroPay.initialize(this, ZeroPay.Config(
            enableDebugLogging = BuildConfig.DEBUG,
            prewarmCache = true
        ))
        
        // Initialize managers
        keyStoreManager = KeyStoreManager(this)
        apiClient = SecureApiClient("https://api.zeropay.com")
        
        // Generate or retrieve user UUID
        userUuid = getOrCreateUserUuid()
        
        setContent {
            EnrollmentScreen(
                userUuid = userUuid!!,
                keyStoreManager = keyStoreManager,
                apiClient = apiClient
            )
        }
    }
    
    private fun getOrCreateUserUuid(): String {
        val prefs = getSharedPreferences("zeropay_enrollment", MODE_PRIVATE)
        var uuid = prefs.getString("user_uuid", null)
        
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
            prefs.edit().putString("user_uuid", uuid).apply()
        }
        
        return uuid
    }
}
