package com.zeropay.sdk.factors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val MIN_STROKES = 3
private const val MAX_POINTS = 300 // DoS protection
private const val THROTTLE_MS = 20L // 50 Hz sampling rate

@Composable
fun PatternCanvas(onDone: (List<PatternFactor.PatternPoint>) -> Unit) {
    var paths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var points by remember { mutableStateOf<List<PatternFactor.PatternPoint>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var lastPointTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val strokeCount = paths.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Draw Your Pattern",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "Draw at least $MIN_STROKES strokes",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Strokes: $strokeCount / $MIN_STROKES",
            color = if (strokeCount >= MIN_STROKES) Color.Green else Color.White,
            fontSize = 16.sp
        )
        
        // Show point count for transparency (security by design)
        Text(
            "Points: ${points.size} / $MAX_POINTS",
            color = if (points.size >= MAX_POINTS) Color.Red else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Drawing Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (points.size >= MAX_POINTS) return@detectDragGestures
                                
                                currentPath = Path().apply {
                                    moveTo(offset.x, offset.y)
                                }
                                
                                val now = System.currentTimeMillis()
                                points = points + PatternFactor.PatternPoint(
                                    x = offset.x,
                                    y = offset.y,
                                    t = now
                                )
                                lastPointTime = now
                            },
                            onDrag = { change, _ ->
                                if (points.size >= MAX_POINTS) return@detectDragGestures
                                
                                val offset = change.position
                                currentPath.lineTo(offset.x, offset.y)
                                
                                // Throttle to THROTTLE_MS (50 Hz) to prevent flooding
                                val now = System.currentTimeMillis()
                                if (now - lastPointTime >= THROTTLE_MS) {
                                    points = points + PatternFactor.PatternPoint(
                                        x = offset.x,
                                        y = offset.y,
                                        t = now
                                    )
                                    lastPointTime = now
                                }
                            },
                            onDragEnd = {
                                // Finalize current stroke
                                paths = paths + currentPath
                                currentPath = Path()
                            }
                        )
                    }
            ) {
                // Draw completed strokes
                paths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(
                            width = 8f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                
                // Draw current stroke being drawn
                if (!currentPath.isEmpty) {
                    drawPath(
                        path = currentPath,
                        color = Color.White.copy(alpha = 0.7f),
                        style = Stroke(
                            width = 8f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            
            // Helper text overlay
            if (paths.isEmpty() && points.isEmpty()) {
                Text(
                    "Draw here",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            
            // Overflow warning
            if (points.size >= MAX_POINTS) {
                Text(
                    "Maximum points reached",
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (paths.isNotEmpty() && !isProcessing) {
                TextButton(onClick = {
                    paths = emptyList()
                    points = emptyList()
                    currentPath = Path()
                    lastPointTime = 0L
                }) {
                    Text("Clear")
                }
            }
            
            if (strokeCount >= MIN_STROKES && !isProcessing) {
                Button(
                    onClick = {
                        isProcessing = true
                        try {
                            // Security: Limit to MAX_POINTS even if somehow exceeded
                            val safePoints = if (points.size > MAX_POINTS) {
                                points.take(MAX_POINTS)
                            } else {
                                points
                            }
                            
                            // Pass points to digest generation
                            onDone(safePoints)
                            
                            // Clear sensitive data from memory
                            paths = emptyList()
                            points = emptyList()
                            currentPath = Path()
                        } catch (e: Exception) {
                            // Log error but don't expose details
                            println("Pattern processing error: ${e.javaClass.simpleName}")
                        } finally {
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(if (isProcessing) "Processing..." else "Confirm Pattern")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Security note
        Text(
            "Your pattern timing is unique to you",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
    
    // Auto-clear after successful submission
    LaunchedEffect(isProcessing) {
        if (!isProcessing && paths.isEmpty()) {
            delay(100)
            // Extra security: ensure no residual data
            points = emptyList()
            currentPath = Path()
        }
    }
}
