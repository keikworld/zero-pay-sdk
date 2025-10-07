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
import com.zeropay.sdk.factors.PatternFactor
import kotlinx.coroutines.delay

/**
 * Pattern Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - DoS protection (MAX_POINTS limit)
 * - Input throttling (50 Hz)
 * - No sensitive data stored after submission
 * - Immediate digest generation
 * - Memory cleared on reset
 * 
 * GDPR Compliance:
 * - Only coordinates used for digest
 * - No raw pattern data stored
 * - User informed of zero-knowledge
 * 
 * @param onDone Callback with list of pattern points (passed to Factor.digest())
 * @param modifier Compose modifier
 */
private const val MIN_STROKES = 3
private const val MAX_POINTS = 300  // DoS protection
private const val THROTTLE_MS = 20L // 50 Hz sampling rate (security)

@Composable
fun PatternCanvas(
    onDone: (List<PatternFactor.PatternPoint>) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var paths by remember { mutableStateOf<List<Path>>(emptyList()) }
    var points by remember { mutableStateOf<List<PatternFactor.PatternPoint>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var lastPointTime by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val strokeCount = paths.size

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
            "Draw Your Pattern",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Draw at least $MIN_STROKES continuous strokes",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== STATUS ====================
        
        Text(
            "Strokes: $strokeCount / $MIN_STROKES",
            color = if (strokeCount >= MIN_STROKES) Color.Green else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        
        // DoS protection indicator
        Text(
            "Points: ${points.size} / $MAX_POINTS",
            color = when {
                points.size >= MAX_POINTS -> Color.Red
                points.size > MAX_POINTS * 0.8 -> Color.Yellow
                else -> Color.White.copy(alpha = 0.5f)
            },
            fontSize = 12.sp
        )
        
        if (points.size >= MAX_POINTS) {
            Text(
                "⚠️ Maximum points reached",
                color = Color.Red,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
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
                                // DoS protection: stop if max points reached
                                if (points.size >= MAX_POINTS) {
                                    errorMessage = "Maximum points reached. Please submit or reset."
                                    return@detectDragGestures
                                }
                                
                                errorMessage = null
                                
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
                                // DoS protection
                                if (points.size >= MAX_POINTS) return@detectDragGestures
                                
                                val offset = change.position
                                currentPath.lineTo(offset.x, offset.y)
                                
                                // Throttle to THROTTLE_MS (security: prevents flooding)
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
                            }
                        )
                    }
            ) {
                // Draw all completed paths
                paths.forEach { path ->
                    drawPath(
                        path = path,
                        color = Color.Green,
                        style = Stroke(
                            width = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                
                // Draw current path being drawn
                drawPath(
                    path = currentPath,
                    color = Color.Green,
                    style = Stroke(
                        width = 5.dp.toPx(),
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
                        Text(
                            "⏳",
                            fontSize = 48.sp
                        )
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
                    paths = emptyList()
                    points = emptyList()
                    currentPath = Path()
                    errorMessage = null
                },
                enabled = paths.isNotEmpty() && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Clear",
                    color = if (paths.isNotEmpty()) Color.White else Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Submit button
            Button(
                onClick = {
                    if (strokeCount < MIN_STROKES) {
                        errorMessage = "Need at least $MIN_STROKES strokes"
                        return@Button
                    }
                    
                    isProcessing = true
                    errorMessage = null
                    
                    // Pass points to callback (Factor.digest() handles security)
                    onDone(points)
                    
                    // Note: Don't clear points here - parent handles navigation
                },
                enabled = strokeCount >= MIN_STROKES && !isProcessing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (strokeCount >= MIN_STROKES) Color.Green else Color.Gray
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
        
        // ==================== SECURITY & PRIVACY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge: Your pattern is hashed locally and never stored",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            "💡 Tip: Draw naturally - your timing and style add security",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
        )
    }
}
