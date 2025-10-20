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
 * Stylus Canvas - Composable UI for stylus draw factor capture
 * Captures stylus drawing with pressure data
 */

@Composable
fun StylusCanvas(onDone: (ByteArray) -> Unit) {
    var stylusPoints by remember { mutableStateOf<List<StylusFactor.StylusPoint>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var lastPressure by remember { mutableStateOf(0f) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Draw with Stylus",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Use stylus to draw (pressure-sensitive)",
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
                                if (stylusPoints.size < 150) {
                                    currentPath.moveTo(offset.x, offset.y)
                                    // Note: pressure detection requires MotionEvent API
                                    // This is simplified - real implementation needs
                                    // androidx.compose.ui.input.pointer.PointerInputChange
                                    lastPressure = 0.5f // Default pressure
                                    stylusPoints = stylusPoints + StylusFactor.StylusPoint(
                                        x = offset.x,
                                        y = offset.y,
                                        pressure = lastPressure,
                                        t = System.currentTimeMillis()
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                if (stylusPoints.size < 150) {
                                    val offset = change.position
                                    currentPath.lineTo(offset.x, offset.y)
                                    // Simulate pressure variation
                                    lastPressure = (lastPressure + 0.1f).coerceIn(0.3f, 1.0f)
                                    stylusPoints = stylusPoints + StylusFactor.StylusPoint(
                                        x = offset.x,
                                        y = offset.y,
                                        pressure = lastPressure,
                                        t = System.currentTimeMillis()
                                    )
                                }
                            },
                            onDragEnd = {}
                        )
                    }
            ) {
                if (!currentPath.isEmpty) {
                    // Vary stroke width based on pressure
                    drawPath(
                        path = currentPath,
                        color = Color.Cyan,
                        style = Stroke(
                            width = 2f + (lastPressure * 6f),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
            
            if (stylusPoints.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Draw with stylus",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 18.sp
                    )
                    Text(
                        "Pressure: ${(lastPressure * 100).toInt()}%",
                        color = Color.Cyan.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (stylusPoints.isNotEmpty()) {
                Button(onClick = {
                    stylusPoints = emptyList()
                    currentPath = Path()
                }) {
                    Text("Clear")
                }
            }
            
            if (stylusPoints.size >= 5) {
                Button(onClick = {
                    val digest = StylusFactor.digestFull(stylusPoints)
                    onDone(digest)
                }) {
                    Text("Confirm")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Points: ${stylusPoints.size} / 150 | Avg Pressure: ${if (stylusPoints.isNotEmpty()) (stylusPoints.map { it.pressure }.average() * 100).toInt() else 0}%",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
