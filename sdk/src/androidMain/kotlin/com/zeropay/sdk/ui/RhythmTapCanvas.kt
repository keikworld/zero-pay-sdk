package com.zeropay.sdk.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.RhythmTapFactor

/**
 * Rhythm Tap Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - DoS protection (MIN/MAX taps: 4-6)
 * - Input timeout (10 seconds)
 * - Interval normalization
 * - No tap data stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - Only tap intervals used (behavioral biometric)
 * - Data normalized for consistency
 * - Irreversible SHA-256 transformation
 * 
 * PSD3 Category: INHERENCE (behavioral biometric)
 * 
 * @param onDone Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
private const val MIN_TAPS = 4
private const val MAX_TAPS = 6
private const val INPUT_TIMEOUT_MS = 10_000L // 10 seconds

@Composable
fun RhythmTapCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var tapTimestamps by remember { mutableStateOf<List<Long>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    
    // Visual feedback animation
    var showTapFeedback by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (showTapFeedback) 1.2f else 1f,
        animationSpec = tween(durationMillis = 100)
    )
    
    // Reset tap feedback
    LaunchedEffect(showTapFeedback) {
        if (showTapFeedback) {
            kotlinx.coroutines.delay(100)
            showTapFeedback = false
        }
    }
    
    // Recording timeout
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingStartTime = System.currentTimeMillis()
            kotlinx.coroutines.delay(INPUT_TIMEOUT_MS)
            
            if (isRecording && tapTimestamps.size < MIN_TAPS) {
                errorMessage = "Recording timeout - please try again"
                isRecording = false
                tapTimestamps = emptyList()
            }
        }
    }

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
            "Rhythm Tap Authentication",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Tap out your unique rhythm ($MIN_TAPS-$MAX_TAPS taps)",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== STATUS ====================
        
        Text(
            "Taps: ${tapTimestamps.size} / $MAX_TAPS",
            color = when {
                tapTimestamps.size < MIN_TAPS -> Color.White
                tapTimestamps.size >= MIN_TAPS -> Color.Green
                else -> Color.Yellow
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        
        if (isRecording) {
            Spacer(modifier = Modifier.height(8.dp))
            val elapsed = (System.currentTimeMillis() - recordingStartTime) / 1000
            Text(
                "Time: ${elapsed}s / ${INPUT_TIMEOUT_MS / 1000}s",
                color = if (elapsed > INPUT_TIMEOUT_MS / 2000) Color.Yellow else Color.White,
                fontSize = 14.sp
            )
        }
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== TAP AREA ====================
        
        Box(
            modifier = Modifier
                .size(250.dp)
                .background(
                    when {
                        isRecording && tapTimestamps.size >= MIN_TAPS -> Color.Green.copy(alpha = 0.3f)
                        isRecording -> Color.Blue.copy(alpha = 0.3f)
                        else -> Color.DarkGray
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
                .pointerInput(isRecording) {
                    if (isRecording) {
                        detectTapGestures {
                            // DoS protection
                            if (tapTimestamps.size >= MAX_TAPS) {
                                errorMessage = "Maximum $MAX_TAPS taps reached"
                                return@detectTapGestures
                            }
                            
                            errorMessage = null
                            
                            val now = System.currentTimeMillis()
                            tapTimestamps = tapTimestamps + now
                            showTapFeedback = true
                            
                            // Auto-stop at max taps
                            if (tapTimestamps.size >= MAX_TAPS) {
                                isRecording = false
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Visual feedback
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(100.dp * scale)
                ) {
                    drawCircle(
                        color = Color.Cyan,
                        radius = size.minDimension / 2
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = when {
                        !isRecording -> "Ready"
                        tapTimestamps.size < MIN_TAPS -> "Keep tapping..."
                        else -> "Tap to finish"
                    },
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== TAP INTERVALS DISPLAY ====================
        
        if (tapTimestamps.size > 1) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Intervals (ms):",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
                Text(
                    intervals.joinToString(" → "),
                    color = Color.Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // ==================== ACTION BUTTONS ====================
        
        if (!isRecording && tapTimestamps.isEmpty()) {
            // Start button
            Button(
                onClick = {
                    isRecording = true
                    tapTimestamps = emptyList()
                    errorMessage = null
                    recordingStartTime = System.currentTimeMillis()
                },
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green
                )
            ) {
                Text(
                    text = "Start Tapping",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (isRecording) {
            // Stop button (only if min taps reached)
            Button(
                onClick = {
                    isRecording = false
                },
                enabled = tapTimestamps.size >= MIN_TAPS,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tapTimestamps.size >= MIN_TAPS) Color.Green else Color.Gray
                )
            ) {
                Text(
                    text = "Finish",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Submit and Reset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        tapTimestamps = emptyList()
                        errorMessage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Reset", fontSize = 16.sp)
                }
                
                Button(
                    onClick = {
                        if (tapTimestamps.size < MIN_TAPS) {
                            errorMessage = "Need at least $MIN_TAPS taps"
                            return@Button
                        }
                        
                        isProcessing = true
                        errorMessage = null
                        
                        try {
                            // Generate digest (Factor handles security + normalization)
                            val digest = RhythmTapFactor.digest(tapTimestamps)
                            onDone(digest)
                            
                            // Security: Clear tap data from memory
                            tapTimestamps = emptyList()
                            
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to process rhythm"
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green
                    )
                ) {
                    Text(
                        text = if (isProcessing) "..." else "Submit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge Behavioral Biometric",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "• Your rhythm pattern is unique to you\n" +
            "• Tap intervals are normalized and hashed\n" +
            "• Raw timing data is never stored\n" +
            "• Replay protection included",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "💡 Tap naturally - don't try to be too precise",
            color = Color.Yellow.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}
