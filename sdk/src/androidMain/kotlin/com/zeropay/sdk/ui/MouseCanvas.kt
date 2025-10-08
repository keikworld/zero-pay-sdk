package com.zeropay.sdk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.MouseFactor

/**
 * Mouse Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - DoS protection (MAX_POINTS = 300)
 * - Input throttling (50 Hz)
 * - No mouse data stored after submission
 * - Immediate digest generation
 * - Memory cleared on reset
 * 
 * GDPR Compliance:
 * - Only coordinates and timing used
 * - No raw mouse data stored
 * - Irreversible SHA-256 transformation
 * 
 * @param onDone Callback with list of mouse points
 * @param modifier Compose modifier
 */
private const val MIN_POINTS = 10
private const val MAX_POINTS = 300  // DoS protection
private const val THROTTLE_MS = 20L // 50 Hz sampling

@Composable
fun MouseCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var path by remember { mutableStateOf(Path()) }
    var points by remember { mutableStateOf<List<MouseFactor.MousePoint>>(emptyList()) }
    var lastPointTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isDrawing by remember { mutableStateOf(false) }

    // ==================== UI LAYOUT ====================
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ==================== HEADER ====================
        
        Text(
            "Draw Mouse Signature",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Draw a unique signature with your mouse or trackpad",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== STATUS ====================
        
        Text(
            "Points: ${points.size} / $MAX_POINTS",
            color = when {
                points.size < MIN_POINTS -> Color.Gray
                points.size >= MAX_POINTS -> Color.Red
                points.size > MAX_POINTS * 0.8 -> Color.Yellow
                else -> Color.Green
            },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        if (points.size >= MAX_POINTS) {
            Text(
                "⚠️ Maximum points reached",
                color = Color.Red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        if (points.size > 0 && points.size < MIN_POINTS) {
            Text(
                "Need at least $MIN_POINTS points (currently ${points.size})",
                color = Color.Yellow,
                fontSize = 12.sp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== DRAWING CANVAS ====================
        
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
                                // DoS protection
                                if (points.size >= MAX_POINTS) {
                                    errorMessage = "Maximum points reached. Please submit or reset."
                                    return@detectDragGestures
                                }
                                
                                errorMessage = null
                                isDrawing = true
                                
                                path = Path().apply {
                                    moveTo(offset.x, offset.y)
                                }
                                
                                val now = System.currentTimeMillis()
                                points = points + MouseFactor.MousePoint(
                                    x = offset.x,
                                    y = offset.y,
                                    t = now
                                )
                                lastPointTime = now
                            },
                            onDrag = { change, _ ->
                                // DoS protection
                                if (points.size >= MAX_POINTS) return@detectDragGestures
                                
                                val offset = change.position
                                path.lineTo(offset.x, offset.y)
                                
                                // Throttle to THROTTLE_MS (security)
                                val now = System.currentTimeMillis()
                                if (now - lastPointTime >= THROTTLE_MS) {
                                    points = points + MouseFactor.MousePoint(
                                        x = offset.x,
                                        y = offset.y,
                                        t = now
                                    )
                                    lastPointTime = now
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                            }
                        )
                    }
            ) {
                // Draw path
                drawPath(
                    path = path,
                    color = Color.Cyan,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            
            // Processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Generating secure digest...",
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== ACTION BUTTONS ====================
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Clear button
            TextButton(
                onClick = {
                    path = Path()
                    points = emptyList()
                    errorMessage = null
                },
                enabled = points.isNotEmpty() && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Clear",
                    color = if (points.isNotEmpty()) Color.White else Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Submit button
            Button(
                onClick = {
                    if (points.size < MIN_POINTS) {
                        errorMessage = "Need at least $MIN_POINTS points"
                        return@Button
                    }
                    
                    isProcessing = true
                    errorMessage = null
                    
                    try {
                        // Generate digest (Factor handles security)
                        val digest = MouseFactor.digestMicroTiming(points)
                        onDone(digest)
                        
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to process mouse data"
                        isProcessing = false
                    }
                },
                enabled = points.size >= MIN_POINTS && !isProcessing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (points.size >= MIN_POINTS) Color.Green else Color.Gray
                )
            ) {
                Text(
                    text = if (isProcessing) "Processing..." else "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge: Your mouse pattern is hashed locally",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            "💡 Tip: Draw naturally - your movement pattern is unique",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
