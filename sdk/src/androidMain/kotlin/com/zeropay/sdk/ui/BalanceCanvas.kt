package com.zeropay.sdk.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.BalanceFactor
import kotlinx.coroutines.delay

/**
 * Balance Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - DoS protection (MIN/MAX samples)
 * - Sensor sampling rate limited
 * - No accelerometer data stored after submission
 * - Immediate digest generation
 * - Memory cleared after processing
 * 
 * GDPR Compliance:
 * - Only accelerometer readings used (not personal data)
 * - Data quantized for consistency
 * - Irreversible SHA-256 transformation
 * 
 * Requirements:
 * - Accelerometer sensor
 * 
 * @param onDone Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
private const val RECORDING_DURATION_MS = 3000L // 3 seconds
private const val SAMPLE_INTERVAL_MS = 100L      // 10 Hz sampling

@Composable
fun BalanceCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val accelerometer = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    var balancePoints by remember { mutableStateOf<List<BalanceFactor.BalancePoint>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentAccel by remember { mutableStateOf(Triple(0f, 0f, 0f)) }
    var lastSampleTime by remember { mutableStateOf(0L) }
    
    // Check sensor availability
    val sensorAvailable = accelerometer != null
    
    // ==================== SENSOR LISTENER ====================
    
    DisposableEffect(isRecording) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null || !isRecording) return
                
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                currentAccel = Triple(x, y, z)
                
                // Throttle sampling to SAMPLE_INTERVAL_MS (security: prevents flooding)
                val now = System.currentTimeMillis()
                if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
                    // DoS protection: check max samples
                    if (balancePoints.size < BalanceFactor.getMaxSamples()) {
                        balancePoints = balancePoints + BalanceFactor.BalancePoint(
                            x = x,
                            y = y,
                            z = z,
                            timestamp = now
                        )
                        lastSampleTime = now
                    }
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Not needed for this implementation
            }
        }
        
        if (isRecording && accelerometer != null) {
            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    // ==================== RECORDING TIMER ====================
    
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                delay(100)
                val elapsed = System.currentTimeMillis() - startTime
                recordingProgress = (elapsed.toFloat() / RECORDING_DURATION_MS).coerceIn(0f, 1f)
                
                if (elapsed >= RECORDING_DURATION_MS) {
                    isRecording = false
                    recordingProgress = 1f
                }
            }
        }
    }

    // ==================== UI LAYOUT ====================
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ==================== SENSOR CHECK ====================
        
        if (!sensorAvailable) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("⚠️", fontSize = 64.sp)
                
                Text(
                    "Accelerometer Not Available",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    "This device does not have an accelerometer sensor. " +
                    "Balance authentication is not available.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            return
        }
        
        // ==================== HEADER ====================
        
        Text(
            "Balance Authentication",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Hold your device steady for ${RECORDING_DURATION_MS / 1000} seconds",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== BALANCE VISUALIZATION ====================
        
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    if (isRecording) Color.Green.copy(alpha = 0.3f)
                    else Color.DarkGray,
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isRecording) "📊" else "⚖️",
                    fontSize = 64.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isRecording) {
                    Text(
                        "${(recordingProgress * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else if (balancePoints.isNotEmpty()) {
                    Text(
                        "✅",
                        fontSize = 48.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress bar
        if (isRecording) {
            LinearProgressIndicator(
                progress = recordingProgress,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp),
                color = Color.Green,
                trackColor = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // ==================== REAL-TIME ACCELEROMETER DATA ====================
        
        if (isRecording) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Current Acceleration:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    "X: ${String.format("%.2f", currentAccel.first)} m/s²",
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
                Text(
                    "Y: ${String.format("%.2f", currentAccel.second)} m/s²",
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
                Text(
                    "Z: ${String.format("%.2f", currentAccel.third)} m/s²",
                    color = Color.Cyan,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Status text
        Text(
            text = when {
                isProcessing -> "Processing balance data..."
                isRecording -> "Recording... (${(recordingProgress * RECORDING_DURATION_MS / 1000).toInt()}s)"
                balancePoints.isNotEmpty() -> "Recording complete! ${balancePoints.size} samples collected"
                else -> "Tap the button and hold your device steady"
            },
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== ACTION BUTTONS ====================
        
        if (balancePoints.isEmpty()) {
            // Start button
            Button(
                onClick = {
                    isRecording = true
                    balancePoints = emptyList()
                    recordingProgress = 0f
                    errorMessage = null
                    lastSampleTime = 0L
                },
                enabled = !isRecording && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green
                )
            ) {
                Text(
                    text = if (isRecording) "Recording..." else "Start Recording",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            // Submit and Re-record buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        balancePoints = emptyList()
                        recordingProgress = 0f
                        errorMessage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Re-record", fontSize = 16.sp)
                }
                
                Button(
                    onClick = {
                        if (balancePoints.size < BalanceFactor.getMinSamples()) {
                            errorMessage = "Not enough samples (need at least ${BalanceFactor.getMinSamples()})"
                            return@Button
                        }
                        
                        isProcessing = true
                        errorMessage = null
                        
                        try {
                            // Validate balance pattern
                            if (!BalanceFactor.isValidPattern(balancePoints)) {
                                errorMessage = "Invalid balance pattern (not enough movement)"
                                isProcessing = false
                                return@Button
                            }
                            
                            // Generate digest (Factor handles security + memory wipe)
                            val digest = BalanceFactor.digest(balancePoints)
                            onDone(digest)
                            
                            // Security: Clear balance data from memory
                            balancePoints = emptyList()
                            
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to process balance data"
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green
                    )
                ) {
                    Text(
                        text = if (isProcessing) "..." else "Submit",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge Balance Authentication",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "• Your balance pattern is unique to how you hold the device\n" +
            "• Accelerometer data is hashed locally\n" +
            "• Raw sensor data is never stored\n" +
            "• Only irreversible hash is kept",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "💡 Hold the device the same way each time",
            color = Color.Yellow.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
    }
}
