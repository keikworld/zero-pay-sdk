package com.zeropay.enrollment.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.zeropay.enrollment.EnrollmentManager
import com.zeropay.enrollment.models.EnrollmentResult
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.network.SecureApiClient
import com.zeropay.sdk.storage.KeyStoreManager
import kotlinx.coroutines.launch

/**
 * Enrollment Activity - Main UI for user enrollment
 */
class EnrollmentActivity : ComponentActivity() {
    
    private lateinit var enrollmentManager: EnrollmentManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize managers
        val keyStoreManager = KeyStoreManager(applicationContext)
        val apiClient = SecureApiClient()
        val redisCacheClient = RedisCacheClient(apiClient)
        enrollmentManager = EnrollmentManager(keyStoreManager, redisCacheClient)
        
        setContent {
            MaterialTheme {
                EnrollmentScreen(enrollmentManager)
            }
        }
    }
}

@Composable
fun EnrollmentScreen(enrollmentManager: EnrollmentManager) {
    var pin by remember { mutableStateOf("") }
    var patternInput by remember { mutableStateOf("") }
    var emojiInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔐 ZeroPay Enrollment",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // PIN Input
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 8) pin = it },
                label = { Text("PIN (4-8 digits)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Pattern Input (comma-separated)
            OutlinedTextField(
                value = patternInput,
                onValueChange = { patternInput = it },
                label = { Text("Pattern (e.g., 0,1,4,7)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            // Emoji Input (comma-separated)
            OutlinedTextField(
                value = emojiInput,
                onValueChange = { emojiInput = it },
                label = { Text("Emojis (e.g., 😀,🎉,🔥)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Enroll Button
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        val patternCoords = if (patternInput.isNotBlank()) {
                            patternInput.split(",").mapNotNull { it.trim().toIntOrNull() }
                        } else null
                        
                        val emojis = if (emojiInput.isNotBlank()) {
                            emojiInput.split(",").map { it.trim() }
                        } else null
                        
                        val enrollmentResult = enrollmentManager.enroll(
                            pinValue = pin.ifBlank { null },
                            patternCoordinates = patternCoords,
                            emojiSequence = emojis
                        )
                        
                        result = when (enrollmentResult) {
                            is EnrollmentResult.Success -> {
                                "✅ SUCCESS!\n\n" +
                                "UUID: ${enrollmentResult.user.uuid}\n" +
                                "Alias: ${enrollmentResult.user.alias}\n" +
                                "Factors: ${enrollmentResult.factorCount}\n" +
                                "Cache Key: ${enrollmentResult.cacheKey}"
                            }
                            is EnrollmentResult.Failure -> {
                                "❌ FAILED\n\n" +
                                "Error: ${enrollmentResult.error}\n" +
                                "Message: ${enrollmentResult.message}"
                            }
                        }
                        
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && (pin.isNotBlank() || patternInput.isNotBlank() || emojiInput.isNotBlank())
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Enroll Now")
                }
            }
            
            // Result Display
            if (result.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "📋 Instructions:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("• Provide at least 2 factors (PSD3 SCA)")
                    Text("• PIN: 4-8 digits")
                    Text("• Pattern: Grid positions 0-8 (e.g., 0,1,4,7)")
                    Text("• Emojis: 3-6 emojis from set")
                }
            }
        }
    }
}
