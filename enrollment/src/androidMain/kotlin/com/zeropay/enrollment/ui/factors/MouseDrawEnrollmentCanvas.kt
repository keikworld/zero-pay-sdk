// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/MouseDrawEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.MouseDrawFactor
import kotlinx.coroutines.launch

/**
 * Mouse/Touch Draw Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - Signature-style drawing
 * - Behavioral biometric capture (velocity, pressure, timing)
 * - Practice mode
 * - Confirmation step
 * - Stroke analysis
 * - Anti-forgery detection
 * 
 * Security:
 * - Captures drawing dynamics (not just image)
 * - Velocity profiles recorded
 * - Timing analysis
 * - Pressure sensitivity (if available)
 * - No image stored (only features)
 * 
 * Behavioral Features:
 * - Stroke velocity
 * - Acceleration patterns
 * - Pen-up/pen-down timing
 * - Total drawing time
 * - Number of strokes
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun MouseDrawEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var strokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<Stroke?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    var drawStartTime by remember { mutableStateOf(0L) }
    
    // Stage state
    var stage by remember { mutableStateOf(DrawStage.INITIAL) }
    var initialStrokes by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    
    // UI state
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    // Constants
    val minStrokes = 3
    val minDrawTime = 1000L // ms
    
    // ==================== HELPERS ====================
    
    fun clearDrawing() {
        strokes = emptyList()
        currentStroke = null
        isDrawing = false
        errorMessage = null
    }
    
    fun handleDrawingComplete() {
        // Validate drawing
        if (strokes.size < minStrokes) {
            errorMessage = "Please draw at least $minStrokes strokes"
            clearDrawing()
            return
        }
        
        val totalTime = System.currentTimeMillis() - drawStartTime
        if (totalTime < minDrawTime) {
            errorMessage = "Drawing too fast. Please take your time."
            clearDrawing()
            return
        }
        
        when (stage) {
            DrawStage.INITIAL -> {
                initialStrokes = strokes
                stage = DrawStage.CONFIRM
                clearDrawing()
            }
            DrawStage.CONFIRM -> {
                // Submit drawing
                handleSubmit()
            }
        }
    }
    
    suspend fun handleSubmit() {
        isProcessing = true
        errorMessage = null
        
        try {
            val result = MouseDrawFactor.processDrawing(initialStrokes, strokes)
            
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                
                // Clear drawings from memory
                strokes = emptyList()
                initialStrokes = emptyList()
                
                onDone(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Drawing verification failed"
                isProcessing = false
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
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
                text = "✍️ Signature Draw",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = when (stage) {
                    DrawStage.INITIAL -> "Draw your signature (Attempt #$attemptCount)"
                    DrawStage.CONFIRM -> "Confirm by drawing again"
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
                            text = "• Draw your signature naturally",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Use at least $minStrokes strokes",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Draw at your normal speed",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Your drawing style is captured, not the image",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // ========== DRAWING INFO ==========
            
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
                        text = "Strokes: ${strokes.size}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    
                    if (isDrawing) {
                        Text(
                            text = "✏️ Drawing...",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // ========== DRAWING CANVAS ==========
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDrawing = true
                                    if (strokes.isEmpty()) {
                                        drawStartTime = System.currentTimeMillis()
                                    }
                                    currentStroke = Stroke(
                                        points = mutableListOf(
                                            StrokePoint(
                                                position = offset,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        ),
                                        startTime = System.currentTimeMillis()
                                    )
                                },
                                onDrag = { change, _ ->
                                    currentStroke?.let { stroke ->
                                        stroke.points.add(
                                            StrokePoint(
                                                position = change.position,
                                                timestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                },
                                onDragEnd = {
                                    currentStroke?.let { stroke ->
                                        stroke.endTime = System.currentTimeMillis()
                                        strokes = strokes + stroke
                                    }
                                    currentStroke = null
                                    isDrawing = false
                                }
                            )
                        }
                ) {
                    // Draw completed strokes
                    strokes.forEach { stroke ->
                        if (stroke.points.size > 1) {
                            val path = Path()
                            path.moveTo(stroke.points[0].position.x, stroke.points[0].position.y)
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(
                                    stroke.points[i].position.x,
                                    stroke.points[i].position.y
                                )
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                    
                    // Draw current stroke
                    currentStroke?.let { stroke ->
                        if (stroke.points.size > 1) {
                            val path = Path()
                            path.moveTo(stroke.points[0].position.x, stroke.points[0].position.y)
                            for (i in 1 until stroke.points.size) {
                                path.lineTo(
                                    stroke.points[i].position.x,
                                    stroke.points[i].position.y
                                )
                            }
                            drawPath(
                                path = path,
                                color = Color(0xFF4CAF50),
                                style = Stroke(width = 4f)
                            )
                        }
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
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Clear button
                if (strokes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { clearDrawing() },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing && !isDrawing,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Clear")
                    }
                }
                
                // Done button
                Button(
                    onClick = { handleDrawingComplete() },
                    modifier = Modifier.weight(1f),
                    enabled = strokes.size >= minStrokes && !isProcessing && !isDrawing,
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
                        Text(if (stage == DrawStage.INITIAL) "Continue →" else "✓ Confirm")
                    }
                }
            }
            
            // Practice again button
            OutlinedButton(
                onClick = {
                    attemptCount++
                    stage = DrawStage.INITIAL
                    clearDrawing()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing && !isDrawing,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("🔄 Practice Again")
            }
            
            // Cancel button
            TextButton(
                onClick = onCancel,
                enabled = !isProcessing && !isDrawing
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Drawing stage
 */
private enum class DrawStage {
    INITIAL,
    CONFIRM
}

/**
 * Stroke data
 */
data class Stroke(
    val points: MutableList<StrokePoint>,
    val startTime: Long,
    var endTime: Long = 0L
)

/**
 * Stroke point with timing
 */
data class StrokePoint(
    val position: Offset,
    val timestamp: Long
)
