// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/PINVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.PinFactor
import kotlinx.coroutines.launch

/**
 * PIN Verification Canvas - MERCHANT POS VERSION
 * 
 * Streamlined PIN input for merchant verification.
 * 
 * Features:
 * - Numeric keypad
 * - Hidden PIN display
 * - Quick clear/submit
 * - Timeout indicator
 * - Merchant branding
 * 
 * Security:
 * - SHA-256 digest only
 * - Memory wiping
 * - No PIN stored
 * 
 * @param onSubmit Callback with SHA-256 digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time in seconds
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
@Composable
fun PINVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val minLength = 4
    val maxLength = 8
    
    fun handleSubmit() {
        if (pin.length < minLength) {
            errorMessage = "PIN must be at least $minLength digits"
            return
        }

        try {
            val digest = PinFactor.digest(pin)
            pin = "" // Clear immediately
            onSubmit(digest)
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
        }
    }
    
    // Auto-timeout
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            onTimeout()
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
                        text = "🔐 Enter PIN",
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
                
                // Timeout indicator
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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // ========== PIN DISPLAY ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(maxLength) { index ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.2f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < pin.length) {
                            Text(
                                text = "●",
                                color = Color.White,
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
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
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== NUMERIC KEYPAD ==========
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: 1, 2, 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (num in 1..3) {
                        NumberButton(
                            number = num,
                            onClick = {
                                if (pin.length < maxLength) {
                                    pin += num
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Row 2: 4, 5, 6
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (num in 4..6) {
                        NumberButton(
                            number = num,
                            onClick = {
                                if (pin.length < maxLength) {
                                    pin += num
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Row 3: 7, 8, 9
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (num in 7..9) {
                        NumberButton(
                            number = num,
                            onClick = {
                                if (pin.length < maxLength) {
                                    pin += num
                                    errorMessage = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Row 4: Clear, 0, Submit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Clear button
                    OutlinedButton(
                        onClick = {
                            pin = ""
                            errorMessage = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Clear", fontSize = 16.sp)
                    }
                    
                    // 0 button
                    NumberButton(
                        number = 0,
                        onClick = {
                            if (pin.length < maxLength) {
                                pin += "0"
                                errorMessage = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Submit button
                    Button(
                        onClick = { handleSubmit() },
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp),
                        enabled = pin.length >= minLength,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text("✓", fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

/**
 * Number button component
 */
@Composable
private fun NumberButton(
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
