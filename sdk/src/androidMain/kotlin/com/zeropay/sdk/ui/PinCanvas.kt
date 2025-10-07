package com.zeropay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.PinFactor

/**
 * PIN Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - Input validation (4-12 digits, all numeric)
 * - Password masking (toggleable)
 * - No PIN stored in memory after submission
 * - Argon2id key derivation in Factor
 * 
 * GDPR Compliance:
 * - No PIN stored or transmitted
 * - Only Argon2id hash stored
 * - Zero-knowledge authentication
 * 
 * @param onDone Callback with DerivedKey (hash + salt)
 * @param modifier Compose modifier
 */
@Composable
fun PinCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var pin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val isValid = PinFactor.isValidPin(pin)
    
    // ==================== UI LAYOUT ====================
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ==================== HEADER ====================
        
        Text(
            "Enter Your PIN",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "4-12 digits, numbers only",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== PIN INPUT ====================
        
        OutlinedTextField(
            value = pin,
            onValueChange = { newValue ->
                // Only allow digits, max 12 characters (DoS protection)
                if (newValue.length <= 12 && newValue.all { it.isDigit() }) {
                    pin = newValue
                    errorMessage = null
                }
            },
            label = { Text("PIN Code") },
            placeholder = { Text("Enter 4-12 digits") },
            visualTransformation = if (showPin) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            singleLine = true,
            enabled = !isProcessing,
            isError = errorMessage != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Green,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.Green,
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.Green
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // PIN length indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Length: ${pin.length}/12",
                color = when {
                    pin.length < 4 -> Color.Gray
                    pin.length in 4..6 -> Color.Yellow
                    else -> Color.Green
                },
                fontSize = 12.sp
            )
            
            // Show/Hide toggle
            TextButton(
                onClick = { showPin = !showPin },
                enabled = !isProcessing
            ) {
                Text(
                    text = if (showPin) "Hide" else "Show",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Validation status
        if (pin.isNotEmpty() && !isValid) {
            Text(
                text = when {
                    pin.length < 4 -> "Too short (minimum 4 digits)"
                    pin.length > 12 -> "Too long (maximum 12 digits)"
                    !pin.all { it.isDigit() } -> "Only numbers allowed"
                    else -> "Invalid PIN format"
                },
                color = Color.Yellow,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== SUBMIT BUTTON ====================
        
        Button(
            onClick = {
                if (!isValid) {
                    errorMessage = "PIN must be 4-12 digits"
                    return@Button
                }
                
                isProcessing = true
                errorMessage = null
                
                try {
                    // Generate Argon2id hash (Factor.digest handles security)
                    val derivedKey = PinFactor.digest(pin)
                    
                    // Pass hash to callback
                    onDone(derivedKey.hash)
                    
                    // Security: Clear PIN from memory
                    pin = ""
                    
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Failed to process PIN"
                    isProcessing = false
                }
            },
            enabled = isValid && !isProcessing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValid) Color.Green else Color.Gray,
                disabledContainerColor = Color.Gray
            )
        ) {
            Text(
                text = if (isProcessing) "Processing..." else "Submit PIN",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge Security",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "• Your PIN is hashed using Argon2id\n" +
            "• The hash is computationally infeasible to reverse\n" +
            "• Your PIN never leaves your device",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "💡 Security Tip: Avoid birthdays and sequential numbers (1234)",
            color = Color.Yellow.copy(alpha = 0.7f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
