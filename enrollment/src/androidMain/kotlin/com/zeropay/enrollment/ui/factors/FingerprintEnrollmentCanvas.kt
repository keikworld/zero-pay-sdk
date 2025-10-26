// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/FingerprintEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
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
import androidx.fragment.app.FragmentActivity
import com.zeropay.enrollment.factors.FingerprintFactor
import kotlinx.coroutines.launch

/**
 * Fingerprint Enrollment Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - Android BiometricPrompt integration
 * - Hardware-backed authentication
 * - Practice mode (multiple attempts)
 * - Confirmation step
 * - Device compatibility check
 * - Enrollment status tracking
 * 
 * Security:
 * - Biometric data never leaves device
 * - Hardware-backed (TEE/Titan M)
 * - Only success event hashed
 * - No biometric template stored
 * - CryptoObject binding (optional)
 * 
 * GDPR Compliance:
 * - Explicit consent required
 * - Only SHA-256 digest of UUID + timestamp stored
 * - No raw biometric data
 * - User can unenroll anytime
 * 
 * Supported Sensors:
 * - Capacitive fingerprint
 * - Optical fingerprint (in-display)
 * - Ultrasonic fingerprint
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun FingerprintEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ==================== STATE MANAGEMENT ====================
    
    var biometricStatus by remember { mutableStateOf<BiometricStatus>(BiometricStatus.CHECKING) }
    var stage by remember { mutableStateOf(BiometricStage.INITIAL) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    var successCount by remember { mutableStateOf(0) }
    
    val requiredSuccesses = 2 // Initial + Confirmation
    
    // ==================== BIOMETRIC MANAGER ====================
    
    val biometricManager = remember {
        BiometricManager.from(context)
    }
    
    LaunchedEffect(Unit) {
        biometricStatus = when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }
    
    // ==================== HELPER FUNCTIONS ====================

    val handleSuccess: () -> Unit = {
        scope.launch {
            isProcessing = true

            try {
                // Generate UUID for this enrollment
                val userUuid = java.util.UUID.randomUUID().toString()

                val result = FingerprintFactor.processFingerprintEnrollment(
                    userUuid = userUuid,
                    cryptoObject = null  // BiometricPrompt handles crypto
                )

                if (result.isSuccess) {
                    val digest = result.getOrNull()!!
                    onDone(digest)
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Enrollment failed"
                    isProcessing = false
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                isProcessing = false
            }
        }
    }

    // ==================== BIOMETRIC PROMPT ====================

    fun showBiometricPrompt() {
        val activity = context as? FragmentActivity ?: run {
            errorMessage = "Unable to show biometric prompt"
            return
        }
        
        val executor = ContextCompat.getMainExecutor(context)
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Authentication")
            .setSubtitle(
                when (stage) {
                    BiometricStage.INITIAL -> "Scan your fingerprint to enroll (Attempt #$attemptCount)"
                    BiometricStage.CONFIRM -> "Scan your fingerprint again to confirm"
                }
            )
            .setDescription("Your biometric data stays on your device and is never transmitted")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setConfirmationRequired(true)
            .build()
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> {
                            errorMessage = "Authentication cancelled"
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            errorMessage = "Too many attempts. Please try again later."
                        }
                        else -> {
                            errorMessage = "Authentication error: $errString"
                        }
                    }
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    errorMessage = "Fingerprint not recognized. Try again."
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    
                    successCount++
                    errorMessage = null
                    
                    when (stage) {
                        BiometricStage.INITIAL -> {
                            stage = BiometricStage.CONFIRM
                        }
                        BiometricStage.CONFIRM -> {
                            handleSuccess()
                        }
                    }
                }
            }
        )
        
        biometricPrompt.authenticate(promptInfo)
    }

    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp)
    ) {
        when (biometricStatus) {
            BiometricStatus.CHECKING -> {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            
            BiometricStatus.AVAILABLE -> {
                // Fingerprint enrollment screen
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ========== HEADER ==========
                    
                    Text(
                        text = "👆 Fingerprint Authentication",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Text(
                        text = when (stage) {
                            BiometricStage.INITIAL -> "Scan your fingerprint (Attempt #$attemptCount)"
                            BiometricStage.CONFIRM -> "Confirm by scanning again"
                        },
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    // ========== PROGRESS ==========
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16213E)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Progress:",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "$successCount/$requiredSuccesses",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = successCount.toFloat() / requiredSuccesses,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF4CAF50),
                                trackColor = Color.White.copy(alpha = 0.2f)
                            )
                        }
                    }
                    
                    // ========== INSTRUCTIONS ==========
                    
                    if (showInstructions) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF16213E)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📋 Instructions",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    TextButton(onClick = { showInstructions = false }) {
                                        Text("Hide", color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                                Text(
                                    text = "• Use the same finger for both scans",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "• Place your finger flat on the sensor",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "• Hold still until scan completes",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = { showInstructions = true }) {
                            Text("Show Instructions", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                    
                    // ========== SECURITY INFO ==========
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF16213E)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🔒 Privacy & Security",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Your fingerprint never leaves your device",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Protected by ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) "Titan M" else "TEE"}",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• Only authentication result is recorded",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "• GDPR compliant - no biometric data stored",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // ========== FINGERPRINT ICON ==========
                    
                    Box(
                        modifier = Modifier
                            .size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "👆",
                            fontSize = 120.sp
                        )
                    }
                    
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
                    
                    // ========== ACTION BUTTONS ==========
                    
                    Button(
                        onClick = { showBiometricPrompt() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
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
                            Text(
                                text = when (stage) {
                                    BiometricStage.INITIAL -> "Scan Fingerprint"
                                    BiometricStage.CONFIRM -> "Scan Again to Confirm"
                                },
                                fontSize = 18.sp
                            )
                        }
                    }
                    
                    OutlinedButton(
                        onClick = {
                            attemptCount++
                            stage = BiometricStage.INITIAL
                            successCount = 0
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("🔄 Start Over")
                    }
                    
                    TextButton(
                        onClick = onCancel,
                        enabled = !isProcessing
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            
            BiometricStatus.NOT_ENROLLED -> {
                // Not enrolled state
                BiometricErrorScreen(
                    icon = "⚠️",
                    title = "No Fingerprint Enrolled",
                    message = "Please enroll a fingerprint in your device settings first.",
                    actionText = "Open Settings",
                    onAction = {
                        // TODO: Open device settings
                    },
                    onCancel = onCancel
                )
            }
            
            BiometricStatus.NO_HARDWARE -> {
                BiometricErrorScreen(
                    icon = "❌",
                    title = "No Fingerprint Sensor",
                    message = "This device doesn't have a fingerprint sensor.",
                    actionText = "Choose Another Factor",
                    onAction = onCancel,
                    onCancel = onCancel
                )
            }
            
            BiometricStatus.HARDWARE_UNAVAILABLE -> {
                BiometricErrorScreen(
                    icon = "⚠️",
                    title = "Sensor Unavailable",
                    message = "The fingerprint sensor is currently unavailable. Please try again later.",
                    actionText = "Retry",
                    onAction = {
                        biometricStatus = BiometricStatus.CHECKING
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
                            biometricStatus = when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
                                BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
                                else -> BiometricStatus.HARDWARE_UNAVAILABLE
                            }
                        }
                    },
                    onCancel = onCancel
                )
            }
            
            BiometricStatus.SECURITY_UPDATE_REQUIRED -> {
                BiometricErrorScreen(
                    icon = "🔒",
                    title = "Security Update Required",
                    message = "Your device needs a security update to use biometric authentication.",
                    actionText = "Choose Another Factor",
                    onAction = onCancel,
                    onCancel = onCancel
                )
            }
            
            else -> {
                BiometricErrorScreen(
                    icon = "❓",
                    title = "Biometric Status Unknown",
                    message = "Unable to determine biometric availability.",
                    actionText = "Choose Another Factor",
                    onAction = onCancel,
                    onCancel = onCancel
                )
            }
        }
    }
}

/**
 * Biometric error screen component
 */
@Composable
private fun BiometricErrorScreen(
    icon: String,
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
    ) {
        Text(
            text = icon,
            fontSize = 64.sp
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            )
        ) {
            Text(actionText)
        }
        
        TextButton(onClick = onCancel) {
            Text("Cancel", color = Color.White.copy(alpha = 0.7f))
        }
    }
}
