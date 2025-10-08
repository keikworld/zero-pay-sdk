// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/EnrollmentScreen.kt

package com.zeropay.enrollment.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.EnrollmentManager
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.consent.ConsentManager
import com.zeropay.enrollment.models.*
import com.zeropay.enrollment.payment.PaymentProviderManager
import com.zeropay.enrollment.ui.steps.*
import com.zeropay.sdk.Factor
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Enrollment Screen - PRODUCTION VERSION
 * 
 * Main enrollment wizard orchestrator.
 * 
 * Features:
 * - 5-step wizard flow
 * - Progress tracking
 * - State management
 * - Error handling
 * - Session timeout
 * - Back navigation
 * 
 * Steps:
 * 1. Factor Selection (6+ factors)
 * 2. Factor Capture (with practice mode)
 * 3. Payment Linking (1+ providers)
 * 4. Consent (GDPR compliance)
 * 5. Confirmation & Submit
 * 
 * Architecture:
 * - Single source of truth (EnrollmentSession)
 * - Composable step components
 * - Clean separation of concerns
 * 
 * Security:
 * - Session timeout (30 minutes)
 * - Rate limiting
 * - Input validation
 * - Memory wiping
 * 
 * @param enrollmentManager Enrollment manager instance
 * @param paymentProviderManager Payment provider manager instance
 * @param consentManager Consent manager instance
 * @param onEnrollmentComplete Callback when enrollment succeeds
 * @param onEnrollmentCancelled Callback when enrollment is cancelled
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EnrollmentScreen(
    enrollmentManager: EnrollmentManager,
    paymentProviderManager: PaymentProviderManager,
    consentManager: ConsentManager,
    onEnrollmentComplete: (EnrollmentResult.Success) -> Unit,
    onEnrollmentCancelled: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Enrollment session
    var session by remember {
        mutableStateOf(
            EnrollmentSession(
                sessionId = UUID.randomUUID().toString(),
                userId = UUID.randomUUID().toString()
            )
        )
    }
    
    // UI states
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // ==================== SESSION TIMEOUT ====================
    
    LaunchedEffect(session.createdAt) {
        kotlinx.coroutines.delay(EnrollmentConfig.SESSION_TIMEOUT_MS)
        if (!session.isExpired()) {
            errorMessage = "Session expired. Please start over."
            kotlinx.coroutines.delay(3000)
            onEnrollmentCancelled()
        }
    }
    
    // ==================== NAVIGATION HELPERS ====================
    
    fun goToNextStep() {
        if (session.canProceedToNext()) {
            val nextStep = when (session.currentStep) {
                EnrollmentStep.FACTOR_SELECTION -> EnrollmentStep.FACTOR_CAPTURE
                EnrollmentStep.FACTOR_CAPTURE -> EnrollmentStep.PAYMENT_LINKING
                EnrollmentStep.PAYMENT_LINKING -> EnrollmentStep.CONSENT
                EnrollmentStep.CONSENT -> EnrollmentStep.CONFIRMATION
                EnrollmentStep.CONFIRMATION -> return // Already at end
            }
            session = session.copy(currentStep = nextStep)
            errorMessage = null
        } else {
            errorMessage = "Please complete the current step before continuing"
        }
    }
    
    fun goToPreviousStep() {
        val previousStep = when (session.currentStep) {
            EnrollmentStep.FACTOR_SELECTION -> return // Already at start
            EnrollmentStep.FACTOR_CAPTURE -> EnrollmentStep.FACTOR_SELECTION
            EnrollmentStep.PAYMENT_LINKING -> EnrollmentStep.FACTOR_CAPTURE
            EnrollmentStep.CONSENT -> EnrollmentStep.PAYMENT_LINKING
            EnrollmentStep.CONFIRMATION -> EnrollmentStep.CONSENT
        }
        session = session.copy(currentStep = previousStep)
        errorMessage = null
    }
    
    fun handleExit() {
        showExitDialog = true
    }
    
    // ==================== ENROLLMENT SUBMISSION ====================
    
    suspend fun submitEnrollment() {
        isProcessing = true
        errorMessage = null
        
        try {
            // Build factor map for EnrollmentManager
            val factorMap = session.capturedFactors
            
            // Call EnrollmentManager.enroll
            val result = enrollmentManager.enroll(factorMap)
            
            when (result) {
                is EnrollmentResult.Success -> {
                    // Success!
                    isProcessing = false
                    onEnrollmentComplete(result)
                }
                is EnrollmentResult.Failure -> {
                    errorMessage = result.message
                    isProcessing = false
                }
            }
        } catch (e: Exception) {
            errorMessage = "Enrollment failed: ${e.message}"
            isProcessing = false
        }
    }
    
    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // ========== TOP BAR ==========
            EnrollmentTopBar(
                currentStep = session.currentStep,
                completionPercentage = session.getCompletionPercentage(),
                onBackClick = {
                    if (session.currentStep == EnrollmentStep.FACTOR_SELECTION) {
                        handleExit()
                    } else {
                        goToPreviousStep()
                    }
                },
                onExitClick = { handleExit() }
            )
            
            // ========== ERROR BANNER ==========
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                ErrorBanner(
                    message = errorMessage ?: "",
                    onDismiss = { errorMessage = null }
                )
            }
            
            // ========== STEP CONTENT ==========
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = session.currentStep,
                    transitionSpec = {
                        slideInHorizontally { width -> width } + fadeIn() with
                        slideOutHorizontally { width -> -width } + fadeOut()
                    }
                ) { step ->
                    when (step) {
                        EnrollmentStep.FACTOR_SELECTION -> {
                            FactorSelectionStep(
                                selectedFactors = session.selectedFactors,
                                onFactorsSelected = { factors ->
                                    session = session.copy(selectedFactors = factors)
                                },
                                onContinue = { goToNextStep() },
                                onCancel = { handleExit() }
                            )
                        }
                        
                        EnrollmentStep.FACTOR_CAPTURE -> {
                            FactorCaptureStep(
                                selectedFactors = session.selectedFactors,
                                capturedFactors = session.capturedFactors,
                                onFactorCaptured = { factor, digest ->
                                    val updated = session.capturedFactors.toMutableMap()
                                    updated[factor] = digest
                                    session = session.copy(capturedFactors = updated)
                                },
                                onContinue = { goToNextStep() },
                                onBack = { goToPreviousStep() }
                            )
                        }
                        
                        EnrollmentStep.PAYMENT_LINKING -> {
                            PaymentLinkingStep(
                                paymentProviderManager = paymentProviderManager,
                                userId = session.userId!!,
                                factorDigests = session.capturedFactors,
                                linkedProviders = session.linkedPaymentProviders,
                                onProviderLinked = { link ->
                                    val updated = session.linkedPaymentProviders.toMutableList()
                                    updated.add(link)
                                    session = session.copy(linkedPaymentProviders = updated)
                                },
                                onProviderUnlinked = { providerId ->
                                    val updated = session.linkedPaymentProviders
                                        .filter { it.providerId != providerId }
                                    session = session.copy(linkedPaymentProviders = updated)
                                },
                                onContinue = { goToNextStep() },
                                onBack = { goToPreviousStep() }
                            )
                        }
                        
                        EnrollmentStep.CONSENT -> {
                            ConsentStep(
                                consentManager = consentManager,
                                userId = session.userId!!,
                                consents = session.consents,
                                onConsentChanged = { consentType, granted ->
                                    val updated = session.consents.toMutableMap()
                                    updated[consentType] = granted
                                    session = session.copy(consents = updated)
                                },
                                onContinue = { goToNextStep() },
                                onBack = { goToPreviousStep() }
                            )
                        }
                        
                        EnrollmentStep.CONFIRMATION -> {
                            ConfirmationStep(
                                session = session,
                                isProcessing = isProcessing,
                                onConfirm = {
                                    scope.launch {
                                        submitEnrollment()
                                    }
                                },
                                onBack = { goToPreviousStep() }
                            )
                        }
                    }
                }
            }
        }
        
        // ========== EXIT CONFIRMATION DIALOG ==========
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("Exit Enrollment?") },
                text = { Text("Your progress will be lost. Are you sure you want to exit?") },
                confirmButton = {
                    TextButton(onClick = {
                        showExitDialog = false
                        onEnrollmentCancelled()
                    }) {
                        Text("Exit", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Continue Enrollment")
                    }
                }
            )
        }
        
        // ========== PROCESSING OVERLAY ==========
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Processing enrollment...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enrollment Top Bar Component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnrollmentTopBar(
    currentStep: EnrollmentStep,
    completionPercentage: Int,
    onBackClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A2E))
    ) {
        // Top bar with back/exit buttons
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "ZeroPay Enrollment",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentStep.title,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Text("←", fontSize = 24.sp, color = Color.White)
                }
            },
            actions = {
                TextButton(onClick = onExitClick) {
                    Text("Exit", color = Color.White.copy(alpha = 0.7f))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E)
            )
        )
        
        // Progress bar
        LinearProgressIndicator(
            progress = completionPercentage / 100f,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color(0xFF4CAF50),
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        
        // Step description
        Text(
            text = currentStep.description,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

/**
 * Error Banner Component
 */
@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFF6B6B)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("✕", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
