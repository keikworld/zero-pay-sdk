package com.zeropay.sdk.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6
private const val CLICK_THROTTLE_MS = 100L
private const val AUTO_CLEAR_TIMEOUT_MS = 30000L

@Composable
fun PinCanvas(onDone: (ByteArray) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var targetLength by remember { mutableStateOf<Int?>(null) }
    var lastClickTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            "Enter Your PIN",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Step 1: Choose PIN length (4, 5, or 6 digits)
        if (targetLength == null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Choose your PIN length",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (length in MIN_PIN_LENGTH..MAX_PIN_LENGTH) {
                    Button(
                        onClick = { targetLength = length },
                        modifier = Modifier.width(100.dp)
                    ) {
                        Text("$length digits")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "Longer PIN = stronger security",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        } else {
            // Step 2: Enter PIN
            // Store targetLength in local variable for smart cast
            val length = targetLength ?: return@Column

            Text(
                "Enter $length digits",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // PIN display (dots)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(length) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < pin.length) Color.White
                                else Color.White.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Number pad
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (row in 0 until 3) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 1..3) {
                            val number = row * 3 + col
                            PinButton(
                                text = number.toString(),
                                enabled = pin.length < length && !isProcessing,
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                                        if (pin.length < length) {
                                            pin += number
                                        }
                                        lastClickTime = now
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Bottom row with 0
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Empty space
                    Box(modifier = Modifier.size(80.dp))
                    
                    PinButton(
                        text = "0",
                        enabled = pin.length < length && !isProcessing,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                                if (pin.length < length) {
                                    pin += "0"
                                }
                                lastClickTime = now
                            }
                        }
                    )
                    
                    // Backspace
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.DarkGray)
                            .clickable(enabled = pin.isNotEmpty() && !isProcessing) {
                                val now = System.currentTimeMillis()
                                if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                                    if (pin.isNotEmpty()) {
                                        pin = pin.dropLast(1)
                                    }
                                    lastClickTime = now
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "⌫",
                            color = if (pin.isNotEmpty() && !isProcessing) 
                                Color.White 
                            else 
                                Color.White.copy(alpha = 0.3f),
                            fontSize = 24.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pin.isNotEmpty() && !isProcessing) {
                    TextButton(onClick = { 
                        pin = "" 
                        lastClickTime = 0L
                    }) {
                        Text("Clear")
                    }
                }
                
                if (pin.isEmpty() && targetLength != null && !isProcessing) {
                    TextButton(onClick = { 
                        targetLength = null 
                    }) {
                        Text("Change Length")
                    }
                }
                
                if (pin.length == targetLength && !isProcessing) {
                    Button(
                        onClick = {
                            isProcessing = true
                            try {
                                val digest = PinFactor.digest(pin)
                                onDone(digest)
                                
                                // Security: Zero out PIN from memory
                                pin = ""
                                targetLength = null
                            } catch (e: Exception) {
                                // Log error without exposing PIN
                                println("PIN processing error: ${e.javaClass.simpleName}")
                                pin = ""
                            } finally {
                                isProcessing = false
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text(if (isProcessing) "Processing..." else "Confirm")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Your PIN is encrypted and never stored",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
    
    // Security: Auto-clear PIN after timeout
    LaunchedEffect(pin, targetLength) {
        val len = targetLength
        if (pin.isNotEmpty() && len != null) {
            delay(AUTO_CLEAR_TIMEOUT_MS)
            if (pin.length < len) {
                pin = ""
            }
        }
    }
}

@Composable
private fun PinButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.DarkGray else Color.DarkGray.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
