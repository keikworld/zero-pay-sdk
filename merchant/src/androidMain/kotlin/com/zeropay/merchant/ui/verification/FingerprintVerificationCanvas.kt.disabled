// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/FingerprintVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zeropay.sdk.factors.FingerprintFactor
import kotlinx.coroutines.launch

/**
 * Fingerprint Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick fingerprint authentication for POS.
 * 
 * Features:
 * - BiometricPrompt integration
 * - Auto-submit on success
 * - Timeout indicator
 * - Error feedback
 * 
 * Security:
 * - Hardware-backed authentication
 * - No biometric data stored
 * - SHA-256 digest only
 * 
 * @param onSubmit Callback with SHA-256 digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time in seconds
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
@Composable
fun FingerprintVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var promptShown by remember { mutableStateOf(false) }
    
    val biometricManager = remember {
        BiometricManager.from(context)
    }
    
    fun showBiometricPrompt() {
        val activity = context as? FragmentActivity ?: run {
            errorMessage = "Unable to show biometric prompt"
            return
        }
        
        promptShown = true
        val executor = ContextCompat.getMainExecutor(context)
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Verification")
            .setSubtitle("Merchant authentication")
            .setDescription("Scan your fingerprint to complete payment")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    promptShown = false
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            errorMessage = "Cancelled"
                        }
                        else -> {
                            errorMessage = "Error: $errString"
                        }
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    errorMessage = "Not recognized. Try again."
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    promptShown = false
                    
                    scope.launch {
                        isProcessing = true
                        
                        try {
                            val digestResult = BiometricFactor.processBiometricEnrollment(
                                biometricType = "fingerprint",
                                deviceInfo = emptyMap()
                            )
                            
                            if (digestResult.isSuccess) {
                                val digest = digestResult.getOrNull()!!
                                onSubmit(digest)
                            } else {
                                errorMessage = "Verification failed"
                                isProcessing = false
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            isProcessing = false
                        }
                    }
                }
            }
        )
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            onTimeout()
        }
    }
    
    // Auto-show prompt on mount
    LaunchedEffect(Unit) {
        if (!promptShown && !isProcessing) {
            showBiometricPrompt()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "👆 Fingerprint",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Merchant Verification",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (remainingSeconds < 30) Color(0xFFFF6B6B) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = "${remainingSeconds}s",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== FINGERPRINT ICON ==========
            
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👆",
                    fontSize = 120.sp
                )
            }
            
            Text(
                text = if (promptShown) "Scanning..." else "Ready to scan",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== ERROR MESSAGE ==========
            
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ========== ACTION BUTTON ==========
            
            Button(
                onClick = { showBiometricPrompt() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing && !promptShown,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Scan Fingerprint", fontSize = 18.sp)
                }
            }
        }
    }
}
