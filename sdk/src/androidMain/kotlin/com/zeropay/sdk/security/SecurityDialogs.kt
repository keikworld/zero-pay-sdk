package com.zeropay.sdk.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zeropay.sdk.security.AntiTampering.Severity
import com.zeropay.sdk.security.SecurityPolicy.SecurityAction

/**
 * Security UI Dialogs
 *
 * Composable dialogs for displaying security-related alerts and messages.
 * Follows Material Design 3 guidelines with appropriate severity colors.
 */
object SecurityDialogs {

    /**
     * Main security alert dialog - routes to appropriate sub-dialog based on action
     */
    @Composable
    fun SecurityAlertDialog(
        securityDecision: SecurityPolicy.SecurityDecision,
        onDismiss: () -> Unit = {},
        onRetry: () -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        when (securityDecision.action) {
            SecurityAction.BLOCK_PERMANENT -> {
                SecurityBlockedPermanentDialog(
                    securityDecision = securityDecision,
                    onClose = onClose
                )
            }

            SecurityAction.BLOCK_TEMPORARY -> {
                SecurityBlockedTemporaryDialog(
                    securityDecision = securityDecision,
                    onRetry = onRetry,
                    onClose = onClose
                )
            }

            SecurityAction.DEGRADE -> {
                SecurityDegradedDialog(
                    securityDecision = securityDecision,
                    onProceed = onDismiss,
                    onClose = onClose
                )
            }

            SecurityAction.WARN -> {
                SecurityWarningDialog(
                    securityDecision = securityDecision,
                    onProceed = onDismiss,
                    onClose = onClose
                )
            }

            SecurityAction.ALLOW -> {
                // No dialog needed
                onDismiss()
            }
        }
    }

    /**
     * Permanent block dialog - critical security issues
     */
    @Composable
    fun SecurityBlockedPermanentDialog(
        securityDecision: SecurityPolicy.SecurityDecision,
        onClose: () -> Unit
    ) {
        Dialog(
            onDismissRequest = { /* Cannot dismiss */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Error icon
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Blocked",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Security Violation Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Severity badge
                    SeverityBadge(severity = securityDecision.severity)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message
                    Text(
                        text = securityDecision.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Threats detected
                    if (securityDecision.threats.isNotEmpty()) {
                        ThreatsList(threats = securityDecision.threats)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Resolution instructions
                    if (securityDecision.resolutionInstructions.isNotEmpty()) {
                        ResolutionInstructions(
                            instructions = securityDecision.resolutionInstructions
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Close button
                    Button(
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    /**
     * Temporary block dialog - user can resolve and retry
     */
    @Composable
    fun SecurityBlockedTemporaryDialog(
        securityDecision: SecurityPolicy.SecurityDecision,
        onRetry: () -> Unit,
        onClose: () -> Unit
    ) {
        Dialog(
            onDismissRequest = { /* Cannot dismiss */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Warning icon
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Security Issue",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Security Issue Detected",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Severity badge
                    SeverityBadge(severity = securityDecision.severity)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message
                    Text(
                        text = securityDecision.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Threats detected
                    if (securityDecision.threats.isNotEmpty()) {
                        ThreatsList(threats = securityDecision.threats)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Resolution instructions
                    if (securityDecision.resolutionInstructions.isNotEmpty()) {
                        ResolutionInstructions(
                            instructions = securityDecision.resolutionInstructions
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }

    /**
     * Degraded mode dialog - allow with restrictions
     */
    @Composable
    fun SecurityDegradedDialog(
        securityDecision: SecurityPolicy.SecurityDecision,
        onProceed: () -> Unit,
        onClose: () -> Unit
    ) {
        Dialog(
            onDismissRequest = onClose
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3CD) // Warning yellow background
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Info icon
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Security Notice",
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFFFF9800) // Orange
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Security Notice",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100), // Dark orange
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Severity badge
                    SeverityBadge(severity = securityDecision.severity)

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message
                    Text(
                        text = securityDecision.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF5D4037) // Dark brown
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Degraded mode notice
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFFFE082),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "The merchant has been notified about these security concerns.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Threats detected
                    if (securityDecision.threats.isNotEmpty()) {
                        ThreatsList(threats = securityDecision.threats, isWarning = true)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClose,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = onProceed,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9800)
                            )
                        ) {
                            Text("Proceed Anyway")
                        }
                    }
                }
            }
        }
    }

    /**
     * Warning dialog - low-risk issues
     */
    @Composable
    fun SecurityWarningDialog(
        securityDecision: SecurityPolicy.SecurityDecision,
        onProceed: () -> Unit,
        onClose: () -> Unit
    ) {
        Dialog(
            onDismissRequest = onProceed
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Warning icon
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFFFFA000) // Amber
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Security Warning",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message
                    Text(
                        text = securityDecision.userMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Threats detected
                    if (securityDecision.threats.isNotEmpty()) {
                        ThreatsList(threats = securityDecision.threats, isWarning = true)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Proceed button
                    Button(
                        onClick = onProceed,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I Understand, Continue")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onClose) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    // ==================== HELPER COMPONENTS ====================

    /**
     * Severity badge component
     */
    @Composable
    private fun SeverityBadge(severity: Severity) {
        val (color, text) = when (severity) {
            Severity.CRITICAL -> Color(0xFFD32F2F) to "CRITICAL"
            Severity.HIGH -> Color(0xFFF57C00) to "HIGH"
            Severity.MEDIUM -> Color(0xFFFFA000) to "MEDIUM"
            Severity.LOW -> Color(0xFFFBC02D) to "LOW"
            Severity.NONE -> Color(0xFF4CAF50) to "NONE"
        }

        Surface(
            color = color,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }

    /**
     * Threats list component
     */
    @Composable
    private fun ThreatsList(
        threats: List<AntiTampering.Threat>,
        isWarning: Boolean = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (isWarning) Color(0xFFFFF9C4) else Color(0xFFFFCDD2),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "Detected Issues:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isWarning) Color(0xFF5D4037) else MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(8.dp))

                threats.forEach { threat ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "• ",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWarning) Color(0xFF5D4037) else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = threat.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isWarning) Color(0xFF5D4037) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    /**
     * Resolution instructions component
     */
    @Composable
    private fun ResolutionInstructions(
        instructions: List<String>
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How to Fix:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                instructions.forEachIndexed { index, instruction ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = instruction,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
