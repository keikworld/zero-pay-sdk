// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/MouseDrawVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import com.zeropay.sdk.factors.MouseDrawFactorEnrollment
import kotlinx.coroutines.launch

/**
 * Mouse Draw Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick mouse/trackpad drawing authentication for POS.
 * 
 * Features:
 * - Drawing canvas
 * - Path visualization
 * - Clear/submit options
 * - Timeout indicator
 * 
 * Security:
 * - SHA-256 digest only
 * - Timing data included
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
fun MouseDrawVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var points by remember { mutableStateOf<List<MouseDrawFactorEnrollment.MousePoint>>(emptyList()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isDrawing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val minPoints = 10
    
    suspend fun handleSubmit() {
        if (points.size < minPoints) {
            errorMessage = "Drawing too short. Draw more."
            return
        }
        
        isProcessing = true
        
        try {
            val result = MouseDrawFactorEnrollment.processMouseDrawing(points)
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                points = emptyList()
                currentPath = Path()
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid drawing"
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
                        text = "🖱️ Draw Signature",
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
            
            // ========== POINT COUNTER ==========
            
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
                        text = "Points: ${points.size}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isDrawing) {
                        Text(
                            text = "Drawing...",
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp
                        )
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
                    text = "Draw your signature or unique pattern using your mouse/trackpad.\nYour timing and stroke are captured for security.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // ========== DRAWING CANVAS ==========
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (!isProcessing) {
                                        isDrawing = true
                                        val point = MouseDrawFactorEnrollment.MousePoint(
                                            offset.x,
                                            offset.y,
                                            System.currentTimeMillis()
                                        )
                                        points = listOf(point)
                                        currentPath = Path().apply {
                                            moveTo(offset.x, offset.y)
                                        }
                                        errorMessage = null
                                    }
                                },
                                onDrag = { change, _ ->
                                    if (isDrawing && !isProcessing) {
                                        val offset = change.position
                                        val point = MouseDrawFactorEnrollment.MousePoint(
                                            offset.x,
                                            offset.y,
                                            System.currentTimeMillis()
                                        )
                                        points = points + point
                                        currentPath.lineTo(offset.x, offset.y)
                                    }
                                },
                                onDragEnd = {
                                    isDrawing = false
                                }
                            )
                        }
                ) {
                    // Draw the path
                    drawPath(
                        path = currentPath,
                        color = Color(0xFF4CAF50),
                        style = Stroke(width = 4.dp.toPx())
                    )
                    
                    // Draw start point
                    if (points.isNotEmpty()) {
                        drawCircle(
                            color = Color(0xFF2196F3),
                            radius = 8.dp.toPx(),
                            center = Offset(points.first().x, points.first().y)
                        )
                    }
                    
                    // Draw end point
                    if (points.size > 1) {
                        drawCircle(
                            color = Color(0xFFFF9800),
                            radius = 8.dp.toPx(),
                            center = Offset(points.last().x, points.last().y)
                        )
                    }
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
                        points = emptyList()
                        currentPath = Path()
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = points.isNotEmpty() && !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear", fontSize = 16.sp)
                }
                
                Button(
                    onClick = { scope.launch { handleSubmit() } },
                    modifier = Modifier.weight(1f),
                    enabled = points.size >= minPoints && !isProcessing,
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
