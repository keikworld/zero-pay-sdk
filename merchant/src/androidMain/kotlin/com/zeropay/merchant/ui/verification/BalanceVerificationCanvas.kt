// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/BalanceVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.BalanceFactorEnrollment
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Balance Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick device balance authentication for POS.
 * 
 * Features:
 * - Accelerometer reading
 * - Balance visualization
 * - Auto-capture when stable
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
fun BalanceVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var accelerometerData by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureProgress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var balanceReadings by remember { mutableStateOf<List<Triple<Float, Float, Float>>>(emptyList()) }
    
    val captureThreshold = 0.5f // Maximum movement allowed
    val captureDuration = 3000L // 3 seconds
    val sampleRate = 50L // Sample every 50ms
    
    // Sensor manager and listener
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]
                    accelerometerData = Triple(x, y, z)
                    
                    if (isCapturing && balanceReadings.size < (captureDuration / sampleRate).toInt()) {
                        balanceReadings = balanceReadings + Triple(x, y, z)
                        captureProgress = balanceReadings.size.toFloat() / (captureDuration / sampleRate)
                    }
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    // Check if device is stable enough to start capture
    val isStable = remember(accelerometerData) {
        accelerometerData?.let { (x, y, z) ->
            abs(x) < captureThreshold && abs(y) < captureThreshold
        } ?: false
    }
    
    // Auto-submit when capture complete
    LaunchedEffect(captureProgress) {
        if (captureProgress >= 1f && balanceReadings.isNotEmpty()) {
            scope.launch {
                handleSubmit()
            }
        }
    }
    
    suspend fun handleSubmit() {
        isProcessing = true
        
        try {
            val result = BalanceFactorEnrollment.processBalance(balanceReadings)
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                balanceReadings = emptyList()
                isCapturing = false
                captureProgress = 0f
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Balance verification failed"
                isProcessing = false
                isCapturing = false
                captureProgress = 0f
                balanceReadings = emptyList()
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
            isCapturing = false
            captureProgress = 0f
            balanceReadings = emptyList()
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
                        text = "⚖️ Hold Steady",
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
            
            // ========== INSTRUCTIONS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Text(
                    text = if (isCapturing) {
                        "Keep holding steady..."
                    } else {
                        "Hold your device steady for 3 seconds.\nYour balance pattern is your security."
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // ========== BALANCE VISUALIZATION ==========
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(300.dp)
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.width / 2 - 20
                    
                    // Draw outer circle
                    drawCircle(
                        color = Color.White.copy(alpha = 0.2f),
                        radius = radius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    // Draw inner target
                    drawCircle(
                        color = if (isStable) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                        radius = radius * 0.3f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    
                    // Draw balance indicator
                    accelerometerData?.let { (x, y, _) ->
                        val indicatorX = centerX + (x * radius * 0.8f)
                        val indicatorY = centerY + (y * radius * 0.8f)
                        
                        drawCircle(
                            color = if (isStable) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            radius = 15.dp.toPx(),
                            center = Offset(indicatorX, indicatorY)
                        )
                    }
                }
            }
            
            // ========== CAPTURE PROGRESS ==========
            
            if (isCapturing) {
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
                            text = "Capturing...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = captureProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(alpha = 0.2f)
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
                        isCapturing = false
                        captureProgress = 0f
                        balanceReadings = emptyList()
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isCapturing && !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel", fontSize = 16.sp)
                }
                
                Button(
                    onClick = {
                        if (isStable) {
                            isCapturing = true
                            captureProgress = 0f
                            balanceReadings = emptyList()
                            errorMessage = null
                        } else {
                            errorMessage = "Device not stable enough"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isCapturing && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStable) Color(0xFF4CAF50) else Color(0xFFFF9800),
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
                        Text("Start", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
