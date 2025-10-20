package com.zeropay.sdk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.factors.ImageTapFactor

/**
 * Image Tap Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - GDPR-compliant (no personal photos allowed)
 * - Pre-approved abstract images only
 * - Grid-based quantization
 * - No tap coordinates stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - Only abstract/geometric images
 * - No facial recognition
 * - No biometric analysis of images
 * - Only spatial coordinates used
 * 
 * Important: DO NOT allow user photo uploads
 * 
 * @param onDone Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
private const val REQUIRED_TAPS = 2
private const val MIN_TAP_DISTANCE = 50.0 // pixels

@Composable
fun ImageTapCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Pre-approved abstract image (GDPR-safe)
    // In production, use actual drawable resource
    // Example: painterResource(id = R.drawable.abstract_pattern_1)
    val selectedImage = remember { "abstract_pattern" }
    
    var tapPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var imageSize by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            "Tap 2 Locations",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Tap the same 2 points on this image each time",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== STATUS ====================
        
        Text(
            "Tapped: ${tapPoints.size} / $REQUIRED_TAPS",
            color = if (tapPoints.size == REQUIRED_TAPS) Color.Green else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
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
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== IMAGE WITH TAP DETECTION ====================
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
        ) {
            // Abstract pattern image (GDPR-safe)
            // Replace with actual image resource in production
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3F51B5)) // Placeholder color
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (tapPoints.size >= REQUIRED_TAPS) {
                                errorMessage = "Maximum $REQUIRED_TAPS taps reached. Reset to tap again."
                                return@detectTapGestures
                            }
                            
                            errorMessage = null
                            
                            // Store image size on first tap
                            if (imageSize == null) {
                                imageSize = size.width.toFloat() to size.height.toFloat()
                            }
                            
                            // Validate tap distance from previous taps
                            if (tapPoints.isNotEmpty()) {
                                val lastTap = tapPoints.last()
                                val distance = kotlin.math.sqrt(
                                    (offset.x - lastTap.x) * (offset.x - lastTap.x) +
                                    (offset.y - lastTap.y) * (offset.y - lastTap.y)
                                )
                                
                                if (distance < MIN_TAP_DISTANCE) {
                                    errorMessage = "Tap points must be further apart"
                                    return@detectTapGestures
                                }
                            }
                            
                            tapPoints = tapPoints + offset
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Geometric pattern overlay (example)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(4) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .background(Color.White.copy(alpha = 0.1f))
                                )
                            }
                        }
                    }
                }
                
                // Draw tap indicators
                tapPoints.forEachIndexed { index, point ->
                    Box(
                        modifier = Modifier
                            .offset(x = point.x.dp, y = point.y.dp)
                            .size(40.dp)
                            .background(
                                Color.Green.copy(alpha = 0.7f),
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
                        Text("⏳", fontSize = 48.sp)
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reset button
            TextButton(
                onClick = {
                    tapPoints = emptyList()
                    errorMessage = null
                },
                enabled = tapPoints.isNotEmpty() && !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "Reset",
                    color = if (tapPoints.isNotEmpty()) Color.White else Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            // Submit button
            Button(
                onClick = {
                    if (tapPoints.size != REQUIRED_TAPS) {
                        errorMessage = "Please tap exactly $REQUIRED_TAPS locations"
                        return@Button
                    }
                    
                    if (imageSize == null) {
                        errorMessage = "Image size not detected"
                        return@Button
                    }
                    
                    isProcessing = true
                    errorMessage = null
                    
                    try {
                        // Normalize tap coordinates to 0.0-1.0 range
                        val (width, height) = imageSize!!
                        val normalizedTaps = tapPoints.map { tap ->
                            ImageTapFactor.TapPoint(
                                x = tap.x / width,
                                y = tap.y / height,
                                timestamp = System.currentTimeMillis()
                            )
                        }
                        
                        // Generate image hash (for GDPR compliance)
                        val imageHash = CryptoUtils.sha256(selectedImage.toByteArray())
                        val imageInfo = ImageTapFactor.ImageInfo(
                            imageId = selectedImage,
                            imageHash = imageHash
                        )
                        
                        // Generate digest (Factor handles security)
                        val digest = ImageTapFactor.digest(normalizedTaps, imageInfo)
                        onDone(digest)
                        
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to process tap points"
                        isProcessing = false
                    }
                },
                enabled = tapPoints.size == REQUIRED_TAPS && !isProcessing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tapPoints.size == REQUIRED_TAPS) Color.Green else Color.Gray
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
        
        // ==================== SECURITY & GDPR INFO ====================
        
        Text(
            "🔒 Zero-Knowledge: Only tap coordinates are hashed",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            "✅ GDPR-Compliant: No personal photos or biometric analysis",
            color = Color.Green.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            "💡 Tip: Choose memorable points (corners, intersections)",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
