// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/RhythmTapVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.RhythmTapFactor
import kotlinx.coroutines.launch

/**
 * Rhythm Tap Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick rhythm tap authentication for POS.
 * 
 * Features:
 * - Large tap area
 * - Visual feedback
 * - Tap counter
 * - Timeout indicator
 * - Automatic submission
 * 
 * Security:
 * - SHA-256 digest only
 * - Timing data hashed
 * - Memory wiping
 * 
 * @param onSubmit Callback with SHA-256 digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time in seconds
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
@Composable
fun RhythmTapVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var taps by remember { mutableStateOf<List<Long>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var tapAnimation by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val minTaps = 4
    val maxTaps = 6
    
    val scale by animateFloatAsState(
        targetValue = if (tapAnimation > 0) 1.3f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tap_scale"
    )
    
    LaunchedEffect(tapAnimation) {
        if (tapAnimation > 0) {
            kotlinx.coroutines.delay(200)
            tapAnimation = 0
        }
    }
    
    suspend fun handleSubmit() {
        if (taps.size < minTaps) {
            errorMessage = "Tap at least $minTaps times"
            return
        }
        
        isProcessing = true
        
        try {
            val result = RhythmTapFactor.processRhythmTaps(taps, taps)
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                taps = emptyList()
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid rhythm"
                isProcessing = false
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
        }
    }
    
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            onTimeout()
        }
    }
    
    // Auto-submit when max taps reached
    LaunchedEffect(taps.size) {
        if (taps.size >= maxTaps) {
            kotlinx.coroutines.delay(300)
            handleSubmit()
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
                        text = "🥁 Tap Rhythm",
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
            
            // ========== TAP COUNTER ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Taps: ${taps.size}/$maxTaps",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (taps.size > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(maxTaps) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(if (index < taps.size) 16.dp else 12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (index < taps.size) Color(0xFF4CAF50) 
                                            else Color.White.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
            
            // ========== INSTRUCTIONS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Text(
                    text = "Tap the circle below with your unique rhythm.\nYour timing pattern is your security.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // ========== TAP AREA ==========
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (taps.size < maxTaps && !isProcessing) {
                                    taps = taps + System.currentTimeMillis()
                                    tapAnimation++
                                    errorMessage = null
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "TAP",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // ========== ERROR MESSAGE ==========
            
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        taps = emptyList()
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = taps.isNotEmpty() && !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear", fontSize = 16.sp)
                }
                
                Button(
                    onClick = { scope.launch { handleSubmit() } },
                    modifier = Modifier.weight(1f),
                    enabled = taps.size >= minTaps && !isProcessing,
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
                        Text("Submit", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
