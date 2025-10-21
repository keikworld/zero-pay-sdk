// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/steps/ConsentStep.kt

package com.zeropay.enrollment.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.consent.ConsentManager
import kotlinx.coroutines.launch

/**
 * Consent Step - GDPR Compliance
 * 
 * Features:
 * - All required consents displayed
 * - Individual consent toggles
 * - Full consent text with explanations
 * - Progress tracking
 * - Validation before proceeding
 * 
 * GDPR Articles Covered:
 * - Article 6: Lawfulness of processing
 * - Article 7: Conditions for consent
 * - Article 13: Information to be provided
 * - Article 17: Right to erasure
 * 
 * @param consentManager Consent manager instance
 * @param userId User UUID
 * @param consents Current consent states
 * @param onConsentChanged Callback when consent changes
 * @param onContinue Callback to proceed
 * @param onBack Callback to go back
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
@Composable
fun ConsentStep(
    consentManager: ConsentManager,
    userId: String,
    consents: Map<EnrollmentConfig.ConsentType, Boolean>,
    onConsentChanged: (EnrollmentConfig.ConsentType, Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    val allConsentsGranted = EnrollmentConfig.ConsentType.values().all { 
        consents[it] == true 
    }
    
    val grantedCount = consents.count { it.value }
    val totalCount = EnrollmentConfig.ConsentType.values().size
    
    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Text(
                text = "Consent & Privacy",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // ========== PROGRESS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Consents: $grantedCount/$totalCount",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    LinearProgressIndicator(
                        progress = grantedCount.toFloat() / totalCount,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (allConsentsGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    if (allConsentsGranted) {
                        Text(
                            text = "✓ All consents granted",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Please review and accept all consents to continue",
                            color = Color(0xFFFF9800),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // ========== GDPR INFO ==========
            
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
                        text = "🛡️ Your Data Rights (GDPR)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Right to access your data",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Right to erasure (delete your account anytime)",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Right to withdraw consent",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Data automatically deleted after 24 hours",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // ========== CONSENT LIST ==========
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(EnrollmentConfig.ConsentType.values().toList()) { consentType ->
                    ConsentCard(
                        consentType = consentType,
                        isGranted = consents[consentType] == true,
                        onConsentChanged = { granted ->
                            onConsentChanged(consentType, granted)
                            
                            // Store consent in ConsentManager
                            scope.launch {
                                if (granted) {
                                    consentManager.grantConsent(userId, consentType)
                                } else {
                                    consentManager.revokeConsent(userId, consentType)
                                }
                            }
                        }
                    )
                }
            }
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("← Back")
                }
                
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    enabled = allConsentsGranted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text("Continue →")
                }
            }
        }
    }
}

/**
 * Consent Card Component
 */
@Composable
private fun ConsentCard(
    consentType: EnrollmentConfig.ConsentType,
    isGranted: Boolean,
    onConsentChanged: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) Color(0xFF1E3A1E) else Color(0xFF1A1A2E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title and toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = consentType.name.replace("_", " "),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isGranted) {
                        Text(
                            text = "✓ Granted",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
                    }
                }
                
                Switch(
                    checked = isGranted,
                    onCheckedChange = onConsentChanged,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.Gray
                    )
                )
            }
            
            // Consent description
            Text(
                text = consentType.description,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
            
            // Additional details
            if (expanded) {
                Divider(color = Color.White.copy(alpha = 0.2f))
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (consentType) {
                        EnrollmentConfig.ConsentType.DATA_PROCESSING -> {
                            Text(
                                text = "What we do:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Generate SHA-256 hashes of your authentication factors\n" +
                                      "• Store only irreversible cryptographic digests\n" +
                                      "• Never store raw authentication data",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        
                        EnrollmentConfig.ConsentType.DATA_STORAGE -> {
                            Text(
                                text = "Storage details:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Encrypted storage in Redis cache\n" +
                                      "• 24-hour automatic expiration (TTL)\n" +
                                      "• No permanent storage of enrollment data",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        
                        EnrollmentConfig.ConsentType.PAYMENT_LINKING -> {
                            Text(
                                text = "Payment security:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Tokens encrypted with AES-256-GCM\n" +
                                      "• Encryption key derived from your factors\n" +
                                      "• Only you can decrypt with correct authentication",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        
                        EnrollmentConfig.ConsentType.BIOMETRIC_PROCESSING -> {
                            Text(
                                text = "Biometric handling:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Behavioral patterns only (no images/audio stored)\n" +
                                      "• Pattern analysis done locally on device\n" +
                                      "• Only timing/coordinate data hashed",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                        
                        EnrollmentConfig.ConsentType.TERMS_OF_SERVICE -> {
                            Text(
                                text = "Legal agreement:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• By enrolling, you agree to ZeroPay's Terms of Service\n" +
                                      "• Full terms available at zeropay.com/terms\n" +
                                      "• Privacy policy at zeropay.com/privacy",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        EnrollmentConfig.ConsentType.GDPR_COMPLIANCE -> {
                            Text(
                                text = "Your GDPR Rights:",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "• Right to access your data\n" +
                                      "• Right to erasure (delete all data)\n" +
                                      "• Right to data portability\n" +
                                      "• Right to withdraw consent anytime",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            
            // Expand/collapse button
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = if (expanded) "Show Less" else "Learn More",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
