package com.zeropay.sdk.security

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/**
 * Security Dialog Usage Examples
 *
 * Demonstrates how to integrate security dialogs into enrollment/verification flows.
 */
object SecurityDialogExamples {

    /**
     * Example: Enrollment with security check
     */
    @Composable
    fun EnrollmentWithSecurityCheck(
        onEnrollmentComplete: () -> Unit,
        onEnrollmentCanceled: () -> Unit
    ) {
        val context = LocalContext.current
        var securityDecision by remember { mutableStateOf<SecurityPolicy.SecurityDecision?>(null) }
        var showSecurityDialog by remember { mutableStateOf(false) }

        // Perform security check before enrollment
        LaunchedEffect(Unit) {
            val decision = SecurityPolicy.evaluateThreats(context)
            if (decision.action != SecurityPolicy.SecurityAction.ALLOW) {
                securityDecision = decision
                showSecurityDialog = true
            } else {
                // Proceed with enrollment
                onEnrollmentComplete()
            }
        }

        // Show security dialog if needed
        if (showSecurityDialog && securityDecision != null) {
            SecurityDialogs.SecurityAlertDialog(
                securityDecision = securityDecision!!,
                onDismiss = {
                    showSecurityDialog = false
                    if (securityDecision!!.action == SecurityPolicy.SecurityAction.WARN ||
                        securityDecision!!.action == SecurityPolicy.SecurityAction.DEGRADE) {
                        onEnrollmentComplete()
                    }
                },
                onRetry = {
                    // Re-run security check after user fixes issues
                    showSecurityDialog = false
                    // Trigger re-check (could use LaunchedEffect with key)
                },
                onClose = {
                    showSecurityDialog = false
                    onEnrollmentCanceled()
                }
            )
        }
    }

    /**
     * Example: Verification with security check
     */
    @Composable
    fun VerificationWithSecurityCheck(
        onVerificationProceed: () -> Unit,
        onVerificationCanceled: () -> Unit
    ) {
        val context = LocalContext.current
        var securityDecision by remember { mutableStateOf<SecurityPolicy.SecurityDecision?>(null) }
        var showSecurityDialog by remember { mutableStateOf(false) }

        // Perform security check before verification
        LaunchedEffect(Unit) {
            val decision = SecurityPolicy.evaluateThreats(context)
            if (decision.action != SecurityPolicy.SecurityAction.ALLOW) {
                securityDecision = decision
                showSecurityDialog = true
            } else {
                // Proceed with verification
                onVerificationProceed()
            }
        }

        // Show security dialog if needed
        if (showSecurityDialog && securityDecision != null) {
            SecurityDialogs.SecurityAlertDialog(
                securityDecision = securityDecision!!,
                onDismiss = {
                    showSecurityDialog = false
                    if (securityDecision!!.action == SecurityPolicy.SecurityAction.WARN ||
                        securityDecision!!.action == SecurityPolicy.SecurityAction.DEGRADE) {
                        onVerificationProceed()
                    }
                },
                onRetry = {
                    // Re-run security check
                    showSecurityDialog = false
                },
                onClose = {
                    showSecurityDialog = false
                    onVerificationCanceled()
                }
            )
        }
    }

    /**
     * Example: Manual security check (for testing/debugging)
     */
    suspend fun performManualSecurityCheck(context: Context): SecurityPolicy.SecurityDecision {
        return SecurityPolicy.evaluateThreats(context)
    }

    /**
     * Example: Check if device is secure enough (quick check)
     */
    fun isDeviceSecure(context: Context): Boolean {
        return SecurityPolicy.isDeviceSecure(context)
    }
}
