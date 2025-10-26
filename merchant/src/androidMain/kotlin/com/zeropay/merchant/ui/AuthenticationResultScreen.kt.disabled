// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/AuthenticationResultScreen.kt

package com.zeropay.merchant.ui

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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Authentication Result Screen - THE HANDOFF
 * 
 * Shows authentication result and provides handoff token.
 * This is where ZeroPay's job ends.
 * 
 * On Success:
 * - Show verified factors
 * - Display auth token
 * - Provide handoff button
 * - Merchant's backend takes over
 * 
 * On Failure:
 * - Show failure reason
 * - Allow retry
 * 
 * @param result Authentication result
 * @param onComplete Callback to handoff to merchant backend
 * @param onRetry Callback to retry authentication
 * @param onCancel Callback for cancellation
 * 
 * @version 2.0.0
 * @date 2025-10-09
 */
@Composable
fun AuthenticationResultScreen(
    result: AuthenticationResult,
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSuccess = result.status == AuthenticationStatus.SUCCESS
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // ========== STATUS ICON ==========
            
            Text(
                text = if (isSuccess) "✅" else "❌",
                fontSize = 80.sp
            )
            
            Text(
                text = if (isSuccess) "Authentication Successful!" else "Authentication Failed",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            if (isSuccess) {
                Text(
                    text = "User identity verified",
                    color = Color(0xFF4CAF50),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = result.failureReason ?: "Unknown error",
                    color = Color(0xFFFF6B6B),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            // ========== AUTHENTICATION DETAILS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (isSuccess) "🎫 Authentication Ticket" else "⚠️ Failure Details",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Divider(color = Color.White.copy(alpha = 0.2f))
                    
                    // User ID
                    ResultRow(
                        label = "User ID",
                        value = result.userId.take(16) + "..."
                    )
                    
                    // Session ID
                    ResultRow(
                        label = "Session ID",
                        value = result.sessionId.take(16) + "..."
                    )
                    
                    if (isSuccess) {
                        // Verified Factors
                        ResultRow(
                            label = "Verified Factors",
                            value = result.verifiedFactors.joinToString(", ") { it.name }
                        )
                        
                        // Auth Token (The Handoff Key!)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Auth Token:",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                            
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = result.authToken ?: "N/A",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            
                            Text(
                                text = "⚠️ Pass this token to your payment backend",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                        
                        // ZK Proof (if generated)
                        if (result.zkProof != null) {
                            ResultRow(
                                label = "ZK-SNARK Proof",
                                value = "✓ Generated (${result.zkProof.size} bytes)"
                            )
                        }
                        
                        // Validity
                        val validityMinutes = ((result.expiresAt - result.timestamp) / 60000).toInt()
                        ResultRow(
                            label = "Valid For",
                            value = "$validityMinutes minutes"
                        )
                    }
                    
                    // Timestamp
                    ResultRow(
                        label = "Timestamp",
                        value = dateFormat.format(Date(result.timestamp))
                    )
                    
                    // Merchant
                    ResultRow(
                        label = "Merchant",
                        value = result.merchantId
                    )
                }
            }
            
            if (isSuccess) {
                // ========== HANDOFF INFO ==========
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🚀 Ready for Handoff",
                            color = Color(0xFF4CAF50),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ZeroPay has verified the user's identity. Pass the auth token to your payment backend to complete the transaction.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== ACTION BUTTONS ==========
            
            if (isSuccess) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("✓ Proceed to Payment", fontSize = 18.sp)
                }
                
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("New Authentication")
                }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("🔄 Try Again", fontSize = 18.sp)
                }
                
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            modifier = Modifier.weight(0.4f)
        )
        
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}
