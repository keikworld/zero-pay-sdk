package com.zeropay.sdk.factors

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.security.CryptoUtils

/**
 * Mouse Canvas - Composable UI for mouse draw factor capture
 * Captures mouse drawing patterns with timing analysis
 */

@Composable
fun MouseCanvas(onDone: (ByteArray) -> Unit) {
    var mousePoints by remember { mutableStateOf<List<MouseFactor.MousePoint>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var isDrawing by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Draw with Mouse",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Draw a pattern (3+ strokes)",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // Drawing canvas
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
                                if (mousePoints.size < 100) {
                                    currentPath.moveTo(offset.x, offset.y)
                                    mousePoints = mousePoints + MouseFactor.MousePoint(
                                        x = offset.x,
                                        y = offset.y,
                                        t = System.currentTimeMillis()
                                    )
                                    isDrawing = true
                                }
                            },
                            onDrag = { change, _ ->
                                if (mousePoints.size < 100) {
                                    val offset = change.position
                                    currentPath.lineTo(offset.x, offset.y)
                                    mousePoints = mousePoints + MouseFactor.MousePoint(
                                        x = offset.x,
                                        y = offset.y,
                                        t = System.currentTimeMillis()
                                    )
                                }
                            },
                            onDragEnd = {
                                isDrawing = false
                            }
                        )
                    }
            ) {
                if (!currentPath.isEmpty) {
                    drawPath(
                        path = currentPath,
                        color = Color.White,
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
            
            if (mousePoints.isEmpty()) {
                Text(
                    "Draw here",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mousePoints.isNotEmpty()) {
                Button(onClick = {
                    mousePoints = emptyList()
                    currentPath = Path()
                }) {
                    Text("Clear")
                }
            }
            
            if (mousePoints.size >= 3) {
                Button(onClick = {
                    val digest = MouseFactor.digestMicroTiming(mousePoints)
                    onDone(digest)
                }) {
                    Text("Confirm")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Points: ${mousePoints.size} / 100",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
