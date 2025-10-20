package com.zeropay.sdk.factors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.security.CryptoUtils
import kotlinx.coroutines.delay

/**
 * Balance Canvas - Composable UI for balance factor capture
 * Uses accelerometer to detect tilt/balance patterns
 */

@Composable
fun BalanceCanvas(onDone: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var balancePoints by remember { mutableStateOf<List<BalanceFactor.BalancePoint>>(emptyList()) }
    var isRecording by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(3) }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    
    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (isRecording && balancePoints.size < 50) {
                    balancePoints = balancePoints + BalanceFactor.BalancePoint(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
    }
    
    // Countdown timer
    LaunchedEffect(isRecording, countdown) {
        if (isRecording && countdown > 0) {
            delay(1000)
            countdown--
        } else if (isRecording && countdown == 0 && balancePoints.size >= 10) {
            // Stop recording
            isRecording = false
            sensorManager.unregisterListener(listener)
            
            // Generate digest
            val digest = BalanceFactor.digest(balancePoints)
            onDone(digest)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isRecording) {
            Text(
                "⚖️",
                fontSize = 72.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Balance Authentication",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Hold your device steady for 3 seconds",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = {
                balancePoints = emptyList()
                countdown = 3
                isRecording = true
                sensorManager.registerListener(
                    listener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }) {
                Text("Start")
            }
        } else {
            Text(
                countdown.toString(),
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Hold steady...",
                color = Color.White,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Samples: ${balancePoints.size} / 50",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
