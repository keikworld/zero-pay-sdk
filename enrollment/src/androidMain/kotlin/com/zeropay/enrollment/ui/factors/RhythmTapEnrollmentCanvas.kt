// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/RhythmTapEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.RhythmTapFactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rhythm Tap Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - User taps a rhythm pattern
 * - Behavioral biometric (rhythm unique to user)
 * - Visual metronome guide
 * - Practice mode
 * - Confirmation step
 * - Rhythm visualization
 * 
 * Security:
 * - Inter-tap intervals captured
 * - Rhythm consistency analysis
 * - Tempo variance unique to user
 * - Anti-forgery via timing patterns
 * 
 * Biometric Features:
 * - Inter-tap timing
 * - Rhythm consistency
 * - Tempo variations
 * - Acceleration patterns
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun RhythmTapEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    // ==================== STATE MANAGEMENT ====================
    
    var taps by remember { mutableStateOf<List<RhythmTap>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartTime by remember { mutableStateOf(0L) }
    
    var stage by remember { mutableStateOf(RhythmStage.INITIAL) }
    var initialTaps by remember { mutableStateOf<List<RhythmTap>>(emptyList()) }
    
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    var showMetronome by remember { mutableStateOf(false) }
    
    // Animation
    var tapAnimationTrigger by remember { mutableStateOf(0) }
    val scale by animateFloatAsState(
        targetValue = if (tapAnimationTrigger > 0) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    val minTaps = 5
    val maxTaps = 12
    val minDuration = 2000L // ms
    val maxDuration = 10000L // ms
    
    // ==================== HELPERS ====================
    
    fun startRecording() {
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        taps = emptyList()
        errorMessage = null
    }
    
    fun handleTap() {
        if (!isRecording) {
            startRecording()
        }
        
        val now = System.currentTimeMillis()
        val relativeTime = now - recordingStartTime
        
        if (taps.size < maxTaps) {
            taps = taps + RhythmTap(
                timestamp = relativeTime,
                order = taps.size + 1
            )
            
            // Trigger animation
            tapAnimationTrigger++
            scope.launch {
                delay(200)
                tapAnimationTrigger--
            }
        }
        
        if (taps.size >= maxTaps) {
            stopRecording()
        }
    }
    
    fun stopRecording() {
        isRecording = false
        handleRecordingComplete()
    }
    
    fun handleRecordingComplete() {
        // Validate taps
        if (taps.size < minTaps) {
            errorMessage = "Please tap at least $minTaps times"
            taps = emptyList()
            return
        }
        
        val duration = taps.lastOrNull()?.timestamp ?: 0L
        if (duration < minDuration) {
            errorMessage = "Rhythm too fast. Please take at least 2 seconds."
            taps = emptyList()
            return
        }
        
        if (duration > maxDuration) {
            errorMessage = "Rhythm too slow. Please complete within 10 seconds."
            taps = emptyList()
            return
        }
        
        when (stage) {
            RhythmStage.INITIAL -> {
                initialTaps = taps
                stage = RhythmStage.CONFIRM
                taps = emptyList()
            }
            RhythmStage.CONFIRM -> {
                handleSubmit()
            }
        }
    }
    
    suspend fun handleSubmit() {
        isProcessing = true
        errorMessage = null
        
        try {
            val result = RhythmTapFactor.processRhythmTaps(initialTaps, taps)
            
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                taps = emptyList()
                initialTaps = emptyList()
                onDone(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Rhythm verification failed"
                isProcessing = false
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
        }
    }
    
    fun clearTaps() {
        taps = emptyList()
        isRecording = false
        errorMessage = null
    }
    
    // ==================== METRONOME ====================
    
    LaunchedEffect(showMetronome) {
        if (showMetronome) {
            while (showMetronome) {
                tapAnimationTrigger++
                delay(600) // 100 BPM
                tapAnimationTrigger--
                delay(600)
            }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Text(
                text = "🥁 Rhythm Tap",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (stage) {
                    RhythmStage.INITIAL -> "Tap your unique rhythm (Attempt #$attemptCount)"
                    RhythmStage.CONFIRM -> "Confirm by tapping the same rhythm"
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
                                text = "📋 Instructions",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showInstructions = false }) {
                                Text("Hide", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Text(
                            text = "• Tap $minTaps-$maxTaps times in a unique rhythm",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Use a rhythm you can remember",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Your timing pattern is unique like a fingerprint",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                TextButton(onClick = { showInstructions = true }) {
                    Text("Show Instructions", color = Color.White.copy(alpha = 0.7f))
                }
            }
            
            // ========== TAP INFO ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Taps: ${taps.size}/$maxTaps",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    
                    if (isRecording) {
                        Text(
                            text = "🔴 Recording...",
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Metronome",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Switch(
                            checked = showMetronome,
                            onCheckedChange = { showMetronome = it },
                            enabled = !isRecording,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF4CAF50)
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== TAP BUTTON ==========
            
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) Color(0xFFFF6B6B) else Color(0xFF4CAF50)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { handleTap() },
                        enabled = !isProcessing
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "👆",
                        fontSize = 64.sp
                    )
                    Text(
                        text = if (isRecording) "TAP!" else "Start",
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // ========== RHYTHM VISUALIZATION ==========
            
            if (taps.isNotEmpty()) {
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
                            text = "Your Rhythm:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            taps.forEach { tap ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${tap.order}",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        // Show intervals
                        if (taps.size > 1) {
                            Text(
                                text = "Intervals: " + taps.zipWithNext { a, b ->
                                    "${b.timestamp - a.timestamp}ms"
                                }.joinToString(", "),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
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
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (taps.isNotEmpty() && !isRecording) {
                    OutlinedButton(
                        onClick = { clearTaps() },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Clear")
                    }
                    
                    Button(
                        onClick = { handleRecordingComplete() },
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
                            Text(if (stage == RhythmStage.INITIAL) "Continue →" else "✓ Confirm")
                        }
                    }
                }
            }
            
            if (!isRecording || taps.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        attemptCount++
                        stage = RhythmStage.INITIAL
                        clearTaps()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("🔄 Practice Again")
                }
            }
            
            TextButton(
                onClick = onCancel,
                enabled = !isProcessing && !isRecording
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Rhythm stage
 */
private enum class RhythmStage {
    INITIAL,
    CONFIRM
}

/**
 * Rhythm tap data
 */
data class RhythmTap(
    val timestamp: Long,
    val order: Int
)
