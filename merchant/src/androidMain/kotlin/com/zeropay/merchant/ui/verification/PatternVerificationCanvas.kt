// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/PatternVerificationCanvas.kt

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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.PatternFactor
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pattern Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick pattern authentication for POS.
 * 
 * @param factorType PATTERN_NORMAL or PATTERN_MICRO
 * @param onSubmit Callback with digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
@Composable
fun PatternVerificationCanvas(
    factorType: com.zeropay.sdk.Factor,
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var selectedDots by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var currentPosition by remember { mutableStateOf<Offset?>(null) }
    var isDrawing by remember { mutableStateOf(false) }
    
    var dotTimings by remember { mutableStateOf<List<PatternDotTiming>>(emptyList()) }
    var drawStartTime by remember { mutableStateOf(0L) }
    var lastDotTime by remember { mutableStateOf(0L) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    val gridSize = 3
    val minDots = 4
    val dotPositions = remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    fun findNearestDot(position: Offset): Int? {
        if (dotPositions.value.isEmpty()) return null
        
        val threshold = 80f
        var nearestDot = -1
        var minDistance = Float.MAX_VALUE
        
        dotPositions.value.forEachIndexed { index, dotPos ->
            val distance = sqrt(
                (position.x - dotPos.x).pow(2) + (position.y - dotPos.y).pow(2)
            )
            
            if (distance < threshold && distance < minDistance && !selectedDots.contains(index)) {
                minDistance = distance
                nearestDot = index
            }
        }
        
        return if (nearestDot != -1) nearestDot else null
    }
    
    fun handleSubmit() {
        if (selectedDots.size < minDots) {
            errorMessage = "Pattern must connect at least $minDots dots"
            return
        }
        
        isProcessing = true
        
        try {
            // Convert to PatternPoints
            val patternPoints = selectedDots.mapIndexed { index, dotIndex ->
                PatternFactor.PatternPoint(
                    x = (dotIndex % 3).toFloat(),
                    y = (dotIndex / 3).toFloat(),
                    t = if (index < dotTimings.size) dotTimings[index].timestamp else System.currentTimeMillis()
                )
            }

            val digest = when (factorType) {
                com.zeropay.sdk.Factor.PATTERN_NORMAL -> {
                    PatternFactor.digestNormalisedTiming(patternPoints)
                }
                com.zeropay.sdk.Factor.PATTERN_MICRO -> {
                    PatternFactor.digestMicroTiming(patternPoints)
                }
                else -> throw IllegalArgumentException("Invalid factor type")
            }

            selectedDots = emptyList()
            dotTimings = emptyList()
            onSubmit(digest)
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
                        text = "🔐 Draw Pattern",
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
            
            // ========== PATTERN INFO ==========
            
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
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dots: ${selectedDots.size}",
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
            
            // ========== PATTERN GRID ==========
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp)
            ) {
                val canvasSize = minOf(maxWidth.value, maxHeight.value).dp
                val dotSpacing = canvasSize / 4
                
                LaunchedEffect(canvasSize) {
                    val positions = mutableListOf<Offset>()
                    for (row in 0 until gridSize) {
                        for (col in 0 until gridSize) {
                            positions.add(
                                Offset(
                                    x = (dotSpacing.value * (col + 1)),
                                    y = (dotSpacing.value * (row + 1))
                                )
                            )
                        }
                    }
                    dotPositions.value = positions
                }
                
                Canvas(
                    modifier = Modifier
                        .size(canvasSize)
                        .align(Alignment.Center)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDrawing = true
                                    drawStartTime = System.currentTimeMillis()
                                    lastDotTime = drawStartTime
                                    currentPosition = offset
                                    
                                    findNearestDot(offset)?.let { dotIndex ->
                                        selectedDots = listOf(dotIndex)
                                        currentPath = listOf(dotPositions.value[dotIndex])
                                        
                                        if (factorType == com.zeropay.sdk.Factor.PATTERN_MICRO) {
                                            dotTimings = listOf(
                                                PatternDotTiming(
                                                    dotIndex = dotIndex,
                                                    timestamp = System.currentTimeMillis() - drawStartTime,
                                                    dwellTime = 0L
                                                )
                                            )
                                        }
                                    }
                                },
                                onDrag = { change, _ ->
                                    currentPosition = change.position
                                    
                                    findNearestDot(change.position)?.let { dotIndex ->
                                        if (!selectedDots.contains(dotIndex)) {
                                            val now = System.currentTimeMillis()
                                            val interDotTime = now - lastDotTime
                                            
                                            selectedDots = selectedDots + dotIndex
                                            currentPath = currentPath + dotPositions.value[dotIndex]
                                            
                                            if (factorType == com.zeropay.sdk.Factor.PATTERN_MICRO) {
                                                dotTimings = dotTimings + PatternDotTiming(
                                                    dotIndex = dotIndex,
                                                    timestamp = now - drawStartTime,
                                                    dwellTime = interDotTime
                                                )
                                            }
                                            
                                            lastDotTime = now
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDrawing = false
                                    currentPosition = null
                                    scope.launch {
                                        handleSubmit()
                                    }
                                }
                            )
                        }
                ) {
                    // Draw connections
                    if (currentPath.size > 1) {
                        val path = Path()
                        path.moveTo(currentPath[0].x, currentPath[0].y)
                        for (i in 1 until currentPath.size) {
                            path.lineTo(currentPath[i].x, currentPath[i].y)
                        }
                        
                        if (isDrawing && currentPosition != null) {
                            path.lineTo(currentPosition!!.x, currentPosition!!.y)
                        }
                        
                        drawPath(
                            path = path,
                            color = Color(0xFF4CAF50),
                            style = Stroke(width = 8f)
                        )
                    }
                    
                    // Draw dots
                    dotPositions.value.forEachIndexed { index, position ->
                        val isSelected = selectedDots.contains(index)
                        val dotColor = if (isSelected) Color(0xFF4CAF50) else Color.White
                        val dotRadius = if (isSelected) 28f else 20f
                        
                        drawCircle(
                            color = dotColor.copy(alpha = 0.3f),
                            radius = 40f,
                            center = position
                        )
                        
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = position
                        )
                        
                        if (isSelected) {
                            val order = selectedDots.indexOf(index) + 1
                            drawIntoCanvas { canvas ->
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.WHITE
                                    textSize = 32f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isFakeBoldText = true
                                }
                                canvas.nativeCanvas.drawText(
                                    order.toString(),
                                    position.x,
                                    position.y + 12f,
                                    paint
                                )
                            }
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
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // ========== CLEAR BUTTON ==========
            
            if (selectedDots.isNotEmpty() && !isDrawing) {
                OutlinedButton(
                    onClick = {
                        selectedDots = emptyList()
                        currentPath = emptyList()
                        dotTimings = emptyList()
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear")
                }
            }
        }
        
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

private data class PatternDotTiming(
    val dotIndex: Int,
    val timestamp: Long,
    val dwellTime: Long
)
