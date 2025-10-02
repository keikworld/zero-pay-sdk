package com.zeropay.sdk.factors

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException

/**
 * Voice Canvas - Audio recording for voice authentication
 * 
 * GDPR Compliance:
 * - Audio file deleted immediately after hashing
 * - Only hash transmitted, never raw audio
 * - User explicit consent via microphone permission
 * 
 * PSD3 Compliance:
 * - Voice biometric as inherence factor
 * 
 * Zero-Knowledge:
 * - SHA-256 hash only, audio cannot be reconstructed
 */
@Composable
fun VoiceCanvas(onDone: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<VoiceState>(VoiceState.CheckingPermission) }
    var recordingTime by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    
    val requiredDuration = 2 // 2 seconds
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        state = if (isGranted) {
            VoiceState.Ready
        } else {
            errorMessage = "Microphone permission required"
            VoiceState.Error
        }
    }
    
    // Check permission on first composition
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            state = VoiceState.Ready
        } else {
            // Request permission
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    // Recording timer
    LaunchedEffect(state) {
        if (state == VoiceState.Recording) {
            while (recordingTime < requiredDuration) {
                delay(100)
                recordingTime += 1
                if (recordingTime >= requiredDuration * 10) {
                    // Stop recording after required duration
                    try {
                        recorder?.stop()
                        recorder?.release()
                        recorder = null
                        
                        state = VoiceState.Processing
                        
                        // Read audio file and generate hash
                        audioFile?.let { file ->
                            if (file.exists()) {
                                val audioBytes = file.readBytes()
                                val hash = VoiceFactor.digest(audioBytes)
                                
                                // GDPR: Delete audio file immediately
                                file.delete()
                                audioFile = null
                                
                                onDone(hash)
                            } else {
                                errorMessage = "Audio file not found"
                                state = VoiceState.Error
                            }
                        }
                    } catch (e: Exception) {
                        errorMessage = "Recording failed: ${e.message}"
                        state = VoiceState.Error
                        recorder?.release()
                        recorder = null
                        audioFile?.delete()
                    }
                }
            }
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
        when (state) {
            VoiceState.CheckingPermission -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Checking microphone permission...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            
            VoiceState.Ready -> {
                Text(
                    "🎤",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Voice Authentication",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Record $requiredDuration seconds of audio",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        try {
                            // Create temporary audio file
                            val tempFile = File.createTempFile("voice_", ".3gp", context.cacheDir)
                            audioFile = tempFile
                            
                            // Start recording
                            recorder = MediaRecorder().apply {
                                setAudioSource(MediaRecorder.AudioSource.MIC)
                                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                                setOutputFile(tempFile.absolutePath)
                                prepare()
                                start()
                            }
                            
                            recordingTime = 0
                            state = VoiceState.Recording
                        } catch (e: IOException) {
                            errorMessage = "Failed to start recording: ${e.message}"
                            state = VoiceState.Error
                        }
                    }
                ) {
                    Text("Start Recording")
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        errorMessage!!,
                        color = Color.Red,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Your voice recording is immediately deleted after processing",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            VoiceState.Recording -> {
                Text(
                    "🔴",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Recording...",
                    color = Color.Red,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "${recordingTime / 10.0} / $requiredDuration seconds",
                    color = Color.White,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(
                    progress = (recordingTime / (requiredDuration * 10f)),
                    color = Color.Red
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        try {
                            recorder?.stop()
                            recorder?.release()
                            recorder = null
                            audioFile?.delete()
                            audioFile = null
                        } catch (e: Exception) {
                            // Ignore
                        }
                        state = VoiceState.Ready
                        recordingTime = 0
                    }
                ) {
                    Text("Cancel", color = Color.White)
                }
            }
            
            VoiceState.Processing -> {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Processing audio...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
            
            VoiceState.Error -> {
                Text(
                    "⚠",
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Voice Authentication Failed",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    errorMessage ?: "Unknown error",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { 
                    errorMessage = null
                    state = VoiceState.Ready 
                }) {
                    Text("Try Again")
                }
            }
        }
    }
}

private enum class VoiceState {
    CheckingPermission,
    Ready,
    Recording,
    Processing,
    Error
}
