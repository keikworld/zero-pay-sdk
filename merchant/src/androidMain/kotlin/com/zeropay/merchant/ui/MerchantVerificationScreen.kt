// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/MerchantVerificationScreen.kt

package com.zeropay.merchant.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.merchant.config.MerchantConfig
import com.zeropay.merchant.models.*
import com.zeropay.merchant.ui.verification.*
import com.zeropay.merchant.verification.VerificationManager
import com.zeropay.sdk.Factor
import com.zeropay.sdk.ZeroPay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Merchant Verification Screen - AUTHENTICATION ONLY VERSION
 *
 * Pure authentication orchestrator - NO payment processing.
 *
 * Responsibilities:
 * 1. UUID Input - User presents identity
 * 2. Factor Retrieval - Fetch enrolled factors from cache
 * 3. Factor Challenge - User completes authentication factors
 * 4. Digest Verification - Verify submitted factors
 * 5. Proof Generation - Generate ZK-SNARK proof
 * 6. Authentication Result - Return success/failure + proof
 * 7. HANDOFF - Pass auth token to merchant's payment system
 *
 * NOT Responsible For:
 * - Payment processing ❌
 * - Transaction management ❌
 * - Receipt generation ❌
 * - Money handling ❌
 *
 * Flow Stages:
 * - UUID_INPUT: User presents UUID
 * - FACTOR_RETRIEVAL: Fetch factors from cache
 * - FACTOR_CHALLENGE: User completes factors
 * - DIGEST_COMPARISON: Verify digests
 * - PROOF_GENERATION: Generate ZK-SNARK proof
 * - AUTHENTICATION_COMPLETE: Show result and handoff
 *
 * @param verificationManager Verification manager instance
 * @param merchantId Merchant identifier
 * @param onAuthenticationSuccess Callback with authentication result
 * @param onAuthenticationFailure Callback on auth failure
 * @param onCancel Callback for cancellation
 *
 * @version 2.0.0 (Simplified - Auth Only)
 * @date 2025-10-09
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MerchantVerificationScreen(
    verificationManager: VerificationManager,
    merchantId: String,
    onAuthenticationSuccess: (AuthenticationResult) -> Unit,
    onAuthenticationFailure: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // ==================== STATE MANAGEMENT ====================
    
    var currentStage by remember { mutableStateOf(VerificationStage.UUID_INPUT) }
    var session by remember { mutableStateOf<VerificationSession?>(null) }
    var currentFactorIndex by remember { mutableStateOf(0) }
    var remainingSeconds by remember { mutableStateOf(MerchantConfig.VERIFICATION_TIMEOUT_SECONDS) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var authenticationResult by remember { mutableStateOf<AuthenticationResult?>(null) }

    // ==================== HELPER FUNCTIONS (defined first) ====================

    fun getDeviceFingerprint(): String {
        // TODO: Generate device fingerprint
        return "DEVICE-${android.os.Build.MODEL}-${android.os.Build.ID}"
    }

    fun getIPAddress(): String {
        // TODO: Get actual IP address
        return "192.168.1.100"
    }

    fun generateAuthToken(result: VerificationResult.Success): String {
        // Generate authentication token for handoff
        // This token will be used by merchant's backend to process payment
        return buildString {
            append("AUTH:")
            append(result.userId)
            append(":")
            append(result.sessionId)
            append(":")
            append(System.currentTimeMillis())
            // TODO: Sign with merchant's private key for security
        }
    }

    fun handleAuthenticationFailure(reason: String) {
        session?.let { verificationManager.cancelSession(it.sessionId) }

        authenticationResult = AuthenticationResult(
            status = AuthenticationStatus.FAILED,
            userId = session?.userId ?: "unknown",
            sessionId = session?.sessionId ?: "unknown",
            verifiedFactors = emptyList(),
            zkProof = null,
            authToken = null,
            timestamp = System.currentTimeMillis(),
            expiresAt = 0,
            merchantId = merchantId,
            failureReason = reason
        )

        currentStage = VerificationStage.AUTHENTICATION_COMPLETE
    }

    fun handleTimeout() {
        errorMessage = "Authentication session expired"
        session?.let { verificationManager.cancelSession(it.sessionId) }
        handleAuthenticationFailure("Session timeout")
    }

    // ==================== HANDLERS ====================

    suspend fun generateAuthenticationResult(verificationResult: VerificationResult.Success) {
        try {
            // Generate authentication token/proof
            val authToken = generateAuthToken(verificationResult)

            authenticationResult = AuthenticationResult(
                status = AuthenticationStatus.SUCCESS,
                userId = verificationResult.userId,
                sessionId = verificationResult.sessionId,
                verifiedFactors = verificationResult.verifiedFactors,
                zkProof = verificationResult.zkProof,
                authToken = authToken,
                timestamp = verificationResult.timestamp,
                expiresAt = System.currentTimeMillis() + (5 * 60 * 1000), // 5 min validity
                merchantId = merchantId
            )

            currentStage = VerificationStage.AUTHENTICATION_COMPLETE
        } catch (e: Exception) {
            errorMessage = "Proof generation failed: ${e.message}"
            handleAuthenticationFailure(e.message ?: "Unknown error")
        }
    }

    suspend fun handleUUIDSubmit(userId: String) {
        isProcessing = true
        errorMessage = null
        currentStage = VerificationStage.FACTOR_RETRIEVAL
        
        try {
            // Create verification session
            val result = verificationManager.createSession(
                userId = userId,
                merchantId = merchantId,
                transactionAmount = 0.0, // Not needed for auth-only, but kept for API compatibility
                deviceFingerprint = getDeviceFingerprint(),
                ipAddress = getIPAddress()
            )
            
            if (result.isSuccess) {
                session = result.getOrNull()
                remainingSeconds = MerchantConfig.VERIFICATION_TIMEOUT_SECONDS
                currentStage = VerificationStage.FACTOR_CHALLENGE
                currentFactorIndex = 0
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "User not found or enrollment expired"
                currentStage = VerificationStage.UUID_INPUT
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            currentStage = VerificationStage.UUID_INPUT
        } finally {
            isProcessing = false
        }
    }
    
    suspend fun handleFactorSubmit(digest: ByteArray) {
        val currentSession = session ?: return
        val currentFactor = currentSession.requiredFactors.getOrNull(currentFactorIndex) ?: return
        
        isProcessing = true
        errorMessage = null
        
        try {
            val result = verificationManager.submitFactor(
                sessionId = currentSession.sessionId,
                factor = currentFactor,
                digest = digest
            )
            
            when (result) {
                is VerificationResult.Success -> {
                    // All factors verified successfully!
                    currentStage = VerificationStage.PROOF_GENERATION
                    generateAuthenticationResult(result)
                }
                
                is VerificationResult.PendingFactors -> {
                    // More factors needed
                    currentFactorIndex++
                    errorMessage = null
                }
                
                is VerificationResult.Failure -> {
                    errorMessage = result.message
                    
                    if (!result.canRetry) {
                        // Max attempts reached or session expired
                        handleAuthenticationFailure(result.message)
                    }
                    // else: user can retry current factor
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        } finally {
            isProcessing = false
        }
    }

    fun handleCancel() {
        session?.let { verificationManager.cancelSession(it.sessionId) }
        onCancel()
    }
    
    fun handleComplete() {
        authenticationResult?.let { result ->
            if (result.status == AuthenticationStatus.SUCCESS) {
                onAuthenticationSuccess(result)
            } else {
                onAuthenticationFailure(result.failureReason ?: "Authentication failed")
            }
        }
    }
    
    fun resetForNewAuthentication() {
        session = null
        currentStage = VerificationStage.UUID_INPUT
        currentFactorIndex = 0
        remainingSeconds = MerchantConfig.VERIFICATION_TIMEOUT_SECONDS
        errorMessage = null
        authenticationResult = null
        isProcessing = false
    }

    // ==================== TIMER ====================

    LaunchedEffect(session) {
        if (session != null) {
            while (remainingSeconds > 0 && session != null) {
                delay(1000)
                remainingSeconds = session?.getRemainingTimeSeconds()?.toInt() ?: 0
            }

            if (remainingSeconds <= 0 && session != null) {
                handleTimeout()
            }
        }
    }

    // ==================== UI ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        AnimatedContent(
            targetState = currentStage,
            transitionSpec = {
                fadeIn() + slideInHorizontally() with fadeOut() + slideOutHorizontally()
            }
        ) { stage ->
            when (stage) {
                VerificationStage.UUID_INPUT -> {
                    UUIDInputScreen(
                        merchantId = merchantId,
                        onSubmit = { userId ->
                            scope.launch {
                                handleUUIDSubmit(userId)
                            }
                        },
                        onCancel = { handleCancel() },
                        errorMessage = errorMessage,
                        isProcessing = isProcessing
                    )
                }
                
                VerificationStage.FACTOR_RETRIEVAL -> {
                    VerificationProgressScreen(
                        icon = "🔍",
                        message = "Retrieving authentication factors...",
                        remainingSeconds = remainingSeconds
                    )
                }
                
                VerificationStage.FACTOR_CHALLENGE -> {
                    session?.let { currentSession ->
                        val currentFactor = currentSession.requiredFactors.getOrNull(currentFactorIndex)
                        
                        if (currentFactor != null) {
                            FactorChallengeScreen(
                                factor = currentFactor,
                                factorNumber = currentFactorIndex + 1,
                                totalFactors = currentSession.requiredFactors.size,
                                remainingSeconds = remainingSeconds,
                                onSubmit = { digest ->
                                    scope.launch {
                                        handleFactorSubmit(digest)
                                    }
                                },
                                onTimeout = { handleTimeout() },
                                errorMessage = errorMessage
                            )
                        }
                    }
                }
                
                VerificationStage.DIGEST_COMPARISON -> {
                    VerificationProgressScreen(
                        icon = "🔐",
                        message = "Verifying authentication...",
                        remainingSeconds = remainingSeconds
                    )
                }
                
                VerificationStage.PROOF_GENERATION -> {
                    VerificationProgressScreen(
                        icon = "✨",
                        message = "Generating cryptographic proof...",
                        remainingSeconds = remainingSeconds
                    )
                }
                
                VerificationStage.AUTHENTICATION_COMPLETE -> {
                    authenticationResult?.let { result ->
                        AuthenticationResultScreen(
                            result = result,
                            onComplete = { handleComplete() },
                            onRetry = { resetForNewAuthentication() },
                            onCancel = { handleCancel() }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Verification stages (auth-only flow)
 */
enum class VerificationStage {
    UUID_INPUT,              // User presents UUID
    FACTOR_RETRIEVAL,        // Fetch factors from cache
    FACTOR_CHALLENGE,        // User completes factors
    DIGEST_COMPARISON,       // Verify digests
    PROOF_GENERATION,        // Generate ZK-SNARK proof
    AUTHENTICATION_COMPLETE  // Show result and handoff
}

/**
 * Authentication Result - What ZeroPay Returns
 * 
 * This is the handoff payload to merchant's backend.
 */
data class AuthenticationResult(
    val status: AuthenticationStatus,
    val userId: String,
    val sessionId: String,
    val verifiedFactors: List<Factor>,
    val zkProof: ByteArray?,
    val authToken: String?,
    val timestamp: Long,
    val expiresAt: Long,
    val merchantId: String,
    val failureReason: String? = null
)

enum class AuthenticationStatus {
    SUCCESS,
    FAILED
}

/**
 * Verification Progress Screen - Shows loading state
 */
@Composable
fun VerificationProgressScreen(
    icon: String,
    message: String,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = icon,
                fontSize = 72.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color(0xFF00BCD4)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Time remaining: ${remainingSeconds}s",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

/**
 * Factor Challenge Screen - User completes a factor
 */
@Composable
fun FactorChallengeScreen(
    factor: Factor,
    factorNumber: Int,
    totalFactors: Int,
    remainingSeconds: Int,
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    var isProcessing by remember { mutableStateOf(false) }

    // Check for timeout
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            onTimeout()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Progress header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1A1A1F)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Authentication Factor $factorNumber of $totalFactors",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = factor.name.replace("_", " "),
                        color = Color(0xFF00BCD4),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = factorNumber.toFloat() / totalFactors.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color(0xFF00BCD4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Time remaining: ${remainingSeconds}s",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }

            // Factor canvas
            ZeroPay.canvasForFactor(
                factor = factor,
                onDone = { digest ->
                    if (!isProcessing) {
                        isProcessing = true
                        onSubmit(digest)
                    }
                },
                modifier = Modifier.weight(1f)
            )

            // Error message (if any)
            errorMessage?.let { error ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFE53935)
                ) {
                    Text(
                        text = error,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
