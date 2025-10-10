// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/ImageTapVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.ImageTapFactorEnrollment
import kotlinx.coroutines.launch

/**
 * Image Tap Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick image tap authentication for POS.
 * 
 * Features:
 * - Tap 2 points on image
 * - Visual markers
 * - Auto-submit
 * - Timeout indicator
 * 
 * Security:
 * - SHA-256 digest only
 * - Fuzzy matching
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
fun ImageTapVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var tapPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val requiredTaps = 2
    val imageId = "merchant_verification_image"
    
    suspend fun handleSubmit() {
        if (tapPoints.size != requiredTaps) {
            errorMessage = "Tap exactly $requiredTaps points"
            return
        }
        
        isProcessing = true
        
        try {
            val result = ImageTapFactorEnrollment.processImageTaps(imageId, tapPoints)
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                tapPoints = emptyList()
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid tap positions"
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
    
    // Auto-submit when required taps reached
    LaunchedEffect(tapPoints.size) {
        if (tapPoints.size >= requiredTaps) {
            kotlinx.coroutines.delay(300)
            handleSubmit()
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
                        text = "🖼️ Tap Image Points",
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
            
            // ========== TAP COUNTER ==========
            
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
                        text = "Taps: ${tapPoints.size}/$requiredTaps",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
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
                    text = "Tap the same 2 points on the image as you did during enrollment.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // ========== IMAGE WITH TAP DETECTION ==========
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFF1A1A2E))
            ) {
                // Placeholder for image - in production, use actual image resource
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2A3E))
                        .onSizeChanged { imageSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                if (tapPoints.size < requiredTaps && !isProcessing && imageSize.width > 0) {
                                    // Convert pixel coordinates to normalized (0-1) coordinates
                                    val normalizedX = offset.x / imageSize.width
                                    val normalizedY = offset.y / imageSize.height
                                    
                                    tapPoints = tapPoints + Pair(normalizedX, normalizedY)
                                    errorMessage = null
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🖼️\nAbstract Image\n(Tap to select points)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Draw tap markers
                    tapPoints.forEachIndexed { index, (x, y) ->
                        val pixelX = x * imageSize.width
                        val pixelY = y * imageSize.height
                        
                        Box(
                            modifier = Modifier
                                .offset(x = (pixelX - 20).dp, y = (pixelY - 20).dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
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
                        tapPoints = emptyList()
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = tapPoints.isNotEmpty() && !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear", fontSize = 16.sp)
                }
                
                Button(
                    onClick = { scope.launch { handleSubmit() } },
                    modifier = Modifier.weight(1f),
                    enabled = tapPoints.size == requiredTaps && !isProcessing,
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
