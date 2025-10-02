package com.zeropay.sdk.factors

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.fragment.app.FragmentActivity
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Face Canvas - Biometric authentication using Android BiometricPrompt
 * 
 * GDPR Compliance:
 * - No raw biometric data stored or transmitted
 * - Only SHA-256 hash of authentication event + salt
 * - Biometric data never leaves device
 * 
 * PSD3 Compliance:
 * - Biometric authentication as "inherence" factor
 * - Strong Customer Authentication (SCA)
 * 
 * Zero-Knowledge:
 * - Returns boolean success + 32-byte hash
 * - Server never sees biometric template
 */
@Composable
fun FaceCanvas(onDone: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<FaceState>(FaceState.Checking) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Check if biometric hardware is available
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                state = FaceState.Ready
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                state = FaceState.Error
                errorMessage = "No biometric hardware available"
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                state = FaceState.Error
                errorMessage = "Biometric hardware unavailable"
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                state = FaceState.Error
                errorMessage = "No biometrics enrolled.\nPlease enroll face or fingerprint in device settings."
            }
            else -> {
                state = FaceState.Error
                errorMessage = "Biometric authentication not available"
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            FaceState.Checking -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Checking biometric availability...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            
            FaceState.Ready -> {
                Text(
                    "👤",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Biometric Authentication",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Scan your face or fingerprint",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        state = FaceState.Authenticating
                        authenticateBiometric(
                            context = context,
                            onSuccess = { hash ->
                                state = FaceState.Success
                                onDone(hash)
                            },
                            onError = { error ->
                                state = FaceState.Ready
                                errorMessage = error
                            }
                        )
                    }
                ) {
                    Text("Authenticate")
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage!!,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Your biometric data never leaves your device",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            FaceState.Authenticating -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Authenticating...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            
            FaceState.Success -> {
                Text(
                    "✓",
                    color = Color.Green,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Authentication Successful",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            FaceState.Error -> {
                Text(
                    "⚠",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Biometric Not Available",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    errorMessage ?: "Unknown error",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private enum class FaceState {
    Checking,
    Ready,
    Authenticating,
    Success,
    Error
}

/**
 * Authenticate using BiometricPrompt
 * 
 * Zero-Knowledge: Only returns hash, never raw biometric
 * GDPR: Biometric data processed entirely on-device
 */
private fun authenticateBiometric(
    context: android.content.Context,
    onSuccess: (ByteArray) -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? FragmentActivity ?: run {
        onError("Context must be FragmentActivity")
        return
    }
    
    val executor = ContextCompat.getMainExecutor(context)
    
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            
            // Generate hash from authentication event
            // GDPR: Hash is irreversible, not considered sensitive data
            val hash = generateBiometricHash(
                deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ),
                timestamp = System.currentTimeMillis()
            )
            
            onSuccess(hash)
        }
        
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            
            val message = when (errorCode) {
                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                BiometricPrompt.ERROR_HW_NOT_PRESENT -> "Biometric hardware not available"
                BiometricPrompt.ERROR_NO_BIOMETRICS -> "No biometrics enrolled"
                BiometricPrompt.ERROR_USER_CANCELED -> "Authentication canceled"
                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "Authentication canceled"
                BiometricPrompt.ERROR_TIMEOUT -> "Authentication timeout"
                BiometricPrompt.ERROR_LOCKOUT -> "Too many attempts. Try again later."
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "Biometric authentication locked"
                else -> errString.toString()
            }
            
            onError(message)
        }
        
        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            // Don't call onError here - user can retry
        }
    }
    
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Authentication")
        .setSubtitle("Scan your face or fingerprint")
        .setDescription("Your biometric data stays on your device")
        .setNegativeButtonText("Cancel")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    
    val biometricPrompt = BiometricPrompt(activity, executor, callback)
    biometricPrompt.authenticate(promptInfo)
}

/**
 * Generate hash from biometric authentication event
 * 
 * Zero-Knowledge: Hash contains no biometric data
 * Components: Device ID + Timestamp + Random salt
 */
private fun generateBiometricHash(deviceId: String, timestamp: Long): ByteArray {
    // Combine device ID, timestamp, and random salt
    val components = listOf(
        deviceId,
        timestamp.toString(),
        CryptoUtils.secureRandomBytes(16).joinToString("") { "%02x".format(it) }
    ).joinToString("|")
    
    // Return SHA-256 hash (irreversible, GDPR-compliant)
    return CryptoUtils.sha256(components.toByteArray())
}
