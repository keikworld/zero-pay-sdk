package com.zeropay.sdk.factors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Image Tap Canvas - Tap 2 locations on an image
 * 
 * GDPR-COMPLIANT IMPLEMENTATION:
 * - Only uses pre-approved abstract images
 * - No user photo uploads allowed
 * - No facial recognition or biometric analysis
 * - Only spatial coordinates are used
 * 
 * Security Note:
 * This implementation is designed to be GDPR-compliant by avoiding
 * any personal data or biometric templates. Only abstract patterns
 * and geometric images are allowed.
 */
@Composable
fun ImageTapCanvas(
    onDone: (ByteArray) -> Unit,
    enrolledImageId: String? = null // If null, this is enrollment; otherwise authentication
) {
    var selectedImage by remember { mutableStateOf<ImageTapFactor.ImageInfo?>(null) }
    var tapPoints by remember { mutableStateOf<List<ImageTapFactor.TapPoint>>(emptyList()) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var showImageSelector by remember { mutableStateOf(enrolledImageId == null) }
    
    // Load enrolled image if in authentication mode
    LaunchedEffect(enrolledImageId) {
        if (enrolledImageId != null) {
            selectedImage = ImageTapFactor.getApprovedImages()
                .find { it.imageId == enrolledImageId }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (enrolledImageId == null) "Select Image & Tap 2 Spots" else "Tap Your 2 Spots",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "${tapPoints.size} / 2 taps",
            color = if (tapPoints.size == 2) Color.Green else Color.White,
            fontSize = 16.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Image selector (enrollment only)
        if (showImageSelector && enrolledImageId == null) {
            Text(
                "Choose an abstract image:",
                color = Color.White,
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImageTapFactor.getApprovedImages().forEach { imageInfo ->
                    Button(
                        onClick = {
                            selectedImage = imageInfo
                            showImageSelector = false
                            tapPoints = emptyList()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(imageInfo.imageId.replace("_", " ").capitalize())
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "⚠️ GDPR Notice: Only abstract images allowed.\nNo personal photos permitted.",
                color = Color.Yellow,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
        
        // Image tap area
        if (selectedImage != null && !showImageSelector) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .onSizeChanged { size ->
                        imageSize = size
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (tapPoints.size < 2 && imageSize.width > 0 && imageSize.height > 0) {
                                // Normalize coordinates to 0.0-1.0
                                val normalizedX = offset.x / imageSize.width
                                val normalizedY = offset.y / imageSize.height
                                
                                val tapPoint = ImageTapFactor.TapPoint(
                                    x = normalizedX,
                                    y = normalizedY,
                                    timestamp = System.currentTimeMillis()
                                )
                                
                                tapPoints = tapPoints + tapPoint
                            }
                        }
                    }
            ) {
                // Display placeholder image (in production, load actual image)
                // Using ColorPainter as placeholder for abstract pattern
                Image(
                    painter = ColorPainter(getImageColor(selectedImage!!.imageId)),
                    contentDescription = "Authentication image",
                    modifier = Modifier.fillMaxSize()
                )
                
                // Overlay tap indicators
                tapPoints.forEachIndexed { index, point ->
                    Box(
                        modifier = Modifier
                            .offset(
                                x = (point.x * imageSize.width).dp,
                                y = (point.y * imageSize.height).dp
                            )
                            .size(40.dp)
                            .background(Color.Red.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.CircleShape),
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
                
                // Instructions overlay
                if (tapPoints.isEmpty()) {
                    Text(
                        "Tap on the image",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tapPoints.isNotEmpty()) {
                    TextButton(onClick = { 
                        tapPoints = emptyList()
                    }) {
                        Text("Reset Taps")
                    }
                }
                
                if (enrolledImageId == null && selectedImage != null) {
                    TextButton(onClick = { 
                        showImageSelector = true
                        tapPoints = emptyList()
                    }) {
                        Text("Change Image")
                    }
                }
                
                if (tapPoints.size == 2 && selectedImage != null) {
                    Button(onClick = {
                        val digest = ImageTapFactor.digest(tapPoints, selectedImage!!)
                        onDone(digest)
                    }) {
                        Text("Confirm")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Tap 2 memorable spots on the image",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Get color for image placeholder
 * In production, load actual image assets
 */
private fun getImageColor(imageId: String): Color {
    return when {
        imageId.contains("pattern") -> Color(0xFF4A148C)
        imageId.contains("landscape") -> Color(0xFF1B5E20)
        imageId.contains("geometric") -> Color(0xFF0D47A1)
        imageId.contains("abstract") -> Color(0xFFB71C1C)
        imageId.contains("gradient") -> Color(0xFFFF6F00)
        else -> Color.DarkGray
    }
}
