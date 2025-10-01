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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinCanvas(onDone: (ByteArray) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val targetLength = 6 // 6-digit PIN

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
        
        Text(
            "Enter $targetLength digits",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // PIN display (dots)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(targetLength) { index ->
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
                            onClick = {
                                if (pin.length < targetLength) {
                                    pin += number
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
                    onClick = {
                        if (pin.length < targetLength) {
                            pin += "0"
                        }
                    }
                )
                
                // Backspace
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable(enabled = pin.isNotEmpty()) {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⌫",
                        color = if (pin.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f),
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
            if (pin.isNotEmpty()) {
                TextButton(onClick = { pin = "" }) {
                    Text("Clear")
                }
            }
            
            if (pin.length == targetLength) {
                Button(
                    onClick = {
                        val digest = PinFactor.digest(pin)
                        onDone(digest)
                    }
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

@Composable
private fun PinButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.DarkGray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}


        require(pin.isNotEmpty()) { "PIN cannot be empty" }
        require(pin.all { it.isDigit() }) { "PIN must contain only digits" }
        val bytes = pin.encodeToByteArray()
        return com.zeropay.sdk.crypto.CryptoUtils.sha256(bytes)
    }
}
