// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/PinEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.CsprngShuffle
import kotlinx.coroutines.launch

/**
 * PIN Enrollment Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - CSPRNG shuffled keypad (0-9, shuffled positions)
 * - Visual feedback (dots for entered digits)
 * - Confirmation step (enter PIN twice)
 * - Input validation (4-8 digits)
 * - Weak PIN detection (1234, 0000, etc.)
 * 
 * Security:
 * - Shuffled keypad (prevents shoulder surfing)
 * - No plaintext PIN stored
 * - Immediate SHA-256 digest generation
 * - Memory wiping after use
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * @param modifier Compose modifier
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun PinEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Shuffled digit positions (CSPRNG)
    val shuffledDigits = remember {
        CsprngShuffle.shuffle((0..9).toList())
    }
    
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf(PinStage.INITIAL) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInstructions by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    val minLength = 4
    val maxLength = 8
    
    // ==================== HELPERS ====================
    
    fun handleDigit(digit: Int) {
        if (isProcessing) return
        
        when (stage) {
            PinStage.INITIAL -> {
                if (pin.length < maxLength) {
                    pin += digit.toString()
                    errorMessage = null
                }
            }
            PinStage.CONFIRM -> {
                if (confirmPin.length < maxLength) {
                    confirmPin += digit.toString()
                    errorMessage = null
                }
            }
        }
    }
    
    fun handleBackspace() {
        when (stage) {
            PinStage.INITIAL -> {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                    errorMessage = null
                }
            }
            PinStage.CONFIRM -> {
                if (confirmPin.isNotEmpty()) {
                    confirmPin = confirmPin.dropLast(1)
                    errorMessage = null
                }
            }
        }
    }

    fun handleSubmit() {
        isProcessing = true
        errorMessage = null

        scope.launch {
            try {
                // Use enrollment module's PinFactor for processing
                val result = com.zeropay.enrollment.factors.PinFactor.processPin(pin)

                if (result.isSuccess) {
                    val digest = result.getOrNull()!!

                    // Clear PINs from memory (security)
                    pin = ""
                    confirmPin = ""

                    // Return digest
                    onDone(digest)
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Invalid PIN"
                    isProcessing = false
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                isProcessing = false
            }
        }
    }

    fun handleContinue() {
        if (stage == PinStage.INITIAL) {
            // Validate PIN using enrollment module's PinFactor
            val validationResult = com.zeropay.enrollment.factors.PinFactor.processPin(pin)

            if (validationResult.isFailure) {
                errorMessage = validationResult.exceptionOrNull()?.message ?: "Invalid PIN"
                return
            }

            // Move to confirmation
            stage = PinStage.CONFIRM
            errorMessage = null
        } else {
            // Confirm PIN matches
            if (pin != confirmPin) {
                errorMessage = "PINs do not match. Try again."
                pin = ""
                confirmPin = ""
                stage = PinStage.INITIAL
                return
            }

            // Submit PIN
            handleSubmit()
        }
    }

    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ========== HEADER ==========
            
            Text(
                text = "🔢 PIN Code",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (stage) {
                    PinStage.INITIAL -> "Create your PIN ($minLength-$maxLength digits)"
                    PinStage.CONFIRM -> "Confirm your PIN"
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            // ========== INSTRUCTIONS ==========
            
            if (showInstructions) {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📋 Security Tips",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showInstructions = false }) {
                                Text("Hide", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Text(
                            text = "• Avoid obvious patterns (1234, 0000)",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Don't use birthdates or phone numbers",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Keypad is shuffled for security",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // ========== PIN DISPLAY ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentPin = when (stage) {
                        PinStage.INITIAL -> pin
                        PinStage.CONFIRM -> confirmPin
                    }
                    
                    repeat(maxLength) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .padding(horizontal = 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < currentPin.length) Color.White
                                    else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
            
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
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== KEYPAD ==========
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 3x3 grid of shuffled digits
                for (row in 0..2) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (col in 0..2) {
                            val index = row * 3 + col
                            val digit = shuffledDigits[index]
                            DigitButton(
                                digit = digit,
                                onClick = { handleDigit(digit) },
                                enabled = !isProcessing
                            )
                        }
                    }
                }
                
                // Bottom row: Clear, 0, Backspace
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        text = "Clear",
                        onClick = {
                            when (stage) {
                                PinStage.INITIAL -> pin = ""
                                PinStage.CONFIRM -> confirmPin = ""
                            }
                        },
                        enabled = !isProcessing
                    )
                    
                    DigitButton(
                        digit = shuffledDigits[9],
                        onClick = { handleDigit(shuffledDigits[9]) },
                        enabled = !isProcessing
                    )
                    
                    ActionButton(
                        text = "⌫",
                        onClick = { handleBackspace() },
                        enabled = !isProcessing
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ========== ACTION BUTTONS ==========
            
            Button(
                onClick = { handleContinue() },
                modifier = Modifier.fillMaxWidth(),
                enabled = when (stage) {
                    PinStage.INITIAL -> pin.length >= minLength && !isProcessing
                    PinStage.CONFIRM -> confirmPin.length >= minLength && !isProcessing
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    disabledContainerColor = Color.Gray
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = when (stage) {
                            PinStage.INITIAL -> "Continue"
                            PinStage.CONFIRM -> "✓ Confirm PIN"
                        }
                    )
                }
            }
            
            TextButton(
                onClick = onCancel,
                enabled = !isProcessing
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Digit Button Component
 */
@Composable
private fun DigitButton(
    digit: Int,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFF16213E))
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Action Button Component
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16213E))
            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * PIN enrollment stages
 */
private enum class PinStage {
    INITIAL,  // Enter PIN
    CONFIRM   // Confirm PIN
}
