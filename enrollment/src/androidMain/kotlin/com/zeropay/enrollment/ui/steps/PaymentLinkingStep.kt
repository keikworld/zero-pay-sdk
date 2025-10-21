// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/steps/PaymentLinkingStep.kt

package com.zeropay.enrollment.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.models.PaymentLinkingState
import com.zeropay.enrollment.models.PaymentProviderLink
import com.zeropay.enrollment.payment.PaymentProviderManager
import com.zeropay.sdk.Factor
import kotlinx.coroutines.launch

/**
 * Payment Linking Step - PRODUCTION VERSION
 * 
 * Features:
 * - OAuth flow for major providers (GooglePay, ApplePay, Stripe)
 * - Hashed reference for regional providers (PayU, Yappy, Nequi, Alipay, WeChat)
 * - Multiple provider support
 * - Link/Unlink functionality
 * - Error handling with retry
 * 
 * Security:
 * - OAuth tokens encrypted with AES-256-GCM
 * - Key derived from UUID + factors
 * - No plaintext tokens stored
 * - Rate limiting per provider
 * 
 * @param paymentProviderManager Payment provider manager
 * @param userId User UUID
 * @param factorDigests Factor digests for key derivation
 * @param linkedProviders Currently linked providers
 * @param onProviderLinked Callback when provider is linked
 * @param onProviderUnlinked Callback when provider is unlinked
 * @param onContinue Callback to proceed
 * @param onBack Callback to go back
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun PaymentLinkingStep(
    paymentProviderManager: PaymentProviderManager,
    userId: String,
    factorDigests: Map<Factor, ByteArray>,
    linkedProviders: List<PaymentProviderLink>,
    onProviderLinked: (PaymentProviderLink) -> Unit,
    onProviderUnlinked: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Available providers
    val availableProviders = remember {
        paymentProviderManager.getAvailableProviders()
    }
    
    // UI states
    var linkingState by remember { mutableStateOf<PaymentLinkingState>(PaymentLinkingState.Idle) }
    var selectedProvider by remember { mutableStateOf<EnrollmentConfig.PaymentProvider?>(null) }
    var showLinkDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val canContinue = linkedProviders.size >= EnrollmentConfig.MIN_PAYMENT_PROVIDERS
    
    // ==================== HELPERS ====================
    
    fun handleProviderClick(provider: EnrollmentConfig.PaymentProvider) {
        selectedProvider = provider
        showLinkDialog = true
    }
    
    suspend fun linkProvider(provider: EnrollmentConfig.PaymentProvider, linkData: Map<String, String>) {
        linkingState = PaymentLinkingState.Linking
        
        val result = paymentProviderManager.linkProvider(
            providerId = provider.id,
            uuid = userId,
            factorDigests = factorDigests,
            linkData = linkData
        )
        
        if (result.isSuccess) {
            val link = result.getOrNull()!!
            onProviderLinked(link)
            linkingState = PaymentLinkingState.Success(link)
            showLinkDialog = false
            selectedProvider = null
        } else {
            linkingState = PaymentLinkingState.Error(
                result.exceptionOrNull()?.message ?: "Failed to link provider"
            )
        }
    }
    
    suspend fun unlinkProvider(providerId: String) {
        val success = paymentProviderManager.unlinkProvider(providerId, userId)
        if (success) {
            onProviderUnlinked(providerId)
        }
    }
    
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
                text = "Link Payment Provider",
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
                        text = "Linked: ${linkedProviders.size}/${EnrollmentConfig.MAX_PAYMENT_PROVIDERS}",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    LinearProgressIndicator(
                        progress = (linkedProviders.size.toFloat() / EnrollmentConfig.MIN_PAYMENT_PROVIDERS).coerceAtMost(1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (canContinue) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    if (canContinue) {
                        Text(
                            text = "✓ Minimum requirement met",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Please link at least ${EnrollmentConfig.MIN_PAYMENT_PROVIDERS} payment provider",
                            color = Color(0xFFFF9800),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // ========== INFO CARD ==========
            
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
                        text = "🔒 Zero-Knowledge Security",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Payment tokens are encrypted with AES-256-GCM",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Encryption key derived from your UUID + factors",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• No raw payment credentials stored",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
            
            // ========== PROVIDER LIST ==========
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableProviders) { provider ->
                    val isLinked = linkedProviders.any { it.providerId == provider.id }
                    
                    PaymentProviderCard(
                        provider = provider,
                        isLinked = isLinked,
                        onLink = { handleProviderClick(provider) },
                        onUnlink = {
                            scope.launch {
                                unlinkProvider(provider.id)
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
                    enabled = canContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text("Continue →")
                }
            }
        }
        
        // ========== LINK DIALOG ==========
        
        if (showLinkDialog && selectedProvider != null) {
            PaymentLinkDialog(
                provider = selectedProvider!!,
                linkingState = linkingState,
                onLink = { linkData ->
                    scope.launch {
                        linkProvider(selectedProvider!!, linkData)
                    }
                },
                onDismiss = {
                    showLinkDialog = false
                    selectedProvider = null
                    linkingState = PaymentLinkingState.Idle
                }
            )
        }
    }
}

/**
 * Payment Provider Card Component
 */
@Composable
private fun PaymentProviderCard(
    provider: EnrollmentConfig.PaymentProvider,
    isLinked: Boolean,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isLinked) {
                    Modifier.border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLinked) Color(0xFF1E3A1E) else Color(0xFF1A1A2E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = provider.displayName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                when (provider.linkType) {
                                    EnrollmentConfig.PaymentLinkType.OAUTH -> Color(0xFF2196F3)
                                    EnrollmentConfig.PaymentLinkType.HASHED_REF -> Color(0xFF9C27B0)
                                    // TODO: Add NFC support
                                    // EnrollmentConfig.PaymentLinkType.NFC -> Color(0xFFFF9800)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = provider.linkType.name,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    if (isLinked) {
                        Text(
                            text = "✓ Linked",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            if (isLinked) {
                TextButton(onClick = onUnlink) {
                    Text("Unlink", color = Color.Red.copy(alpha = 0.8f))
                }
            } else {
                Button(
                    onClick = onLink,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Link")
                }
            }
        }
    }
}

/**
 * Payment Link Dialog
 */
@Composable
private fun PaymentLinkDialog(
    provider: EnrollmentConfig.PaymentProvider,
    linkingState: PaymentLinkingState,
    onLink: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link ${provider.displayName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (provider.linkType) {
                    EnrollmentConfig.PaymentLinkType.OAUTH -> {
                        Text(
                            text = "You'll be redirected to ${provider.displayName} to authorize the connection.",
                            fontSize = 14.sp
                        )
                        
                        if (linkingState is PaymentLinkingState.Linking) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Connecting to ${provider.displayName}...",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    
                    EnrollmentConfig.PaymentLinkType.HASHED_REF -> {
                        Text(
                            text = "Enter your ${provider.displayName} account details:",
                            fontSize = 14.sp
                        )
                        
                        when (provider) {
                            EnrollmentConfig.PaymentProvider.YAPPY,
                            EnrollmentConfig.PaymentProvider.NEQUI -> {
                                OutlinedTextField(
                                    value = phone,
                                    onValueChange = { phone = it },
                                    label = { Text("Phone Number") },
                                    placeholder = { Text("+507xxxxxxxx") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                            
                            else -> {
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address") },
                                    placeholder = { Text("your@email.com") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                        
                        if (linkingState is PaymentLinkingState.Linking) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // TODO: Add NFC support
                    // EnrollmentConfig.PaymentLinkType.NFC -> {
                    //     Text(
                    //         text = "NFC linking coming soon!",
                    //         fontSize = 14.sp
                    //     )
                    // }
                }
                
                if (linkingState is PaymentLinkingState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF6B6B)
                        )
                    ) {
                        Text(
                            text = linkingState.message,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val linkData = when (provider.linkType) {
                        EnrollmentConfig.PaymentLinkType.OAUTH -> emptyMap()
                        EnrollmentConfig.PaymentLinkType.HASHED_REF -> {
                            when (provider) {
                                EnrollmentConfig.PaymentProvider.YAPPY,
                                EnrollmentConfig.PaymentProvider.NEQUI -> 
                                    mapOf("phone" to phone)
                                else ->
                                    mapOf("email" to email)
                            }
                        }
                        // TODO: Add NFC support
                        // EnrollmentConfig.PaymentLinkType.NFC -> emptyMap()
                    }
                    onLink(linkData)
                },
                enabled = when (provider.linkType) {
                    EnrollmentConfig.PaymentLinkType.OAUTH -> 
                        linkingState !is PaymentLinkingState.Linking
                    EnrollmentConfig.PaymentLinkType.HASHED_REF -> {
                        when (provider) {
                            EnrollmentConfig.PaymentProvider.YAPPY,
                            EnrollmentConfig.PaymentProvider.NEQUI -> 
                                phone.isNotBlank() && linkingState !is PaymentLinkingState.Linking
                            else ->
                                email.contains("@") && linkingState !is PaymentLinkingState.Linking
                        }
                    }
                    // TODO: Add NFC support
                    // EnrollmentConfig.PaymentLinkType.NFC -> false
                }
            ) {
                Text("Link")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = linkingState !is PaymentLinkingState.Linking
            ) {
                Text("Cancel")
            }
        }
    )
}
