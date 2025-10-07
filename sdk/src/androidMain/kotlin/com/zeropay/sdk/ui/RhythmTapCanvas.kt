package com.zeropay.sdk.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.RhythmTapFactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rhythm Tap Canvas - Behavioral Biometric Authentication UI
 * 
 * User Experience:
 * - Tap 4-6 times anywhere on the screen
 * - Visual feedback shows tap locations and count
 * - Ripple animation on each tap
 * - Auto-submit after max taps or manual submit
 * - Can reset and try again
 * 
 * Security Features:
 * - Captures millisecond-level timing between taps
 * - Generates SHA-256 digest locally
 * - No raw timing data transmitted
 * - Validates tap sequence before submission
 * 
 * Accessibility:
 * - No fine motor skills required
 * - Large tap area (full screen)
 * - Clear visual feedback
 * - Informative error messages
 * 
 * GDPR Compliance:
 * - Only timing intervals captured (not coordinates)
 * - Irreversible hash generated
 * - No personal data stored
 * 
 * @param onDone Callback with 32-byte SHA-256 digest
 * @param modifier Compose modifier for layout
 * 
 * Version: 1.0.0
 * Last Updated: 2025-01-08
 * 
 * @author ZeroPay Security Team
 */
@Composable
fun RhythmTapCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Tap data storage
    var taps by remember { mutableStateOf<List<RhythmTapFactor.RhythmTap>>(emptyList()) }
    
    // Tap locations for visual feedback (not used in digest)
    var tapLocations by remember { mutableStateOf<List<Offset>>(emptyList()) }
    
    // UI state
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    
    // Animation state for ripples
    var rippleAnimations by remember { mutableStateOf<List<Pair<Offset, Float>>>(emptyList()) }
    
    // Coroutine scope for animations
    val scope = rememberCoroutineScope()
    
    // Min/max tap counts
    val minTaps = RhythmTapFactor.getMinTaps()
    val maxTaps = RhythmTapFactor.getMaxTaps()
    val currentTapCount = taps.size
    
    // ==================== HELPER FUNCTIONS ====================
    
    /**
     * Handle tap event
     */
    fun handleTap(offset: Offset) {
        if (isProcessing || showSuccess) return
        
        // Clear error on new tap
        errorMessage = null
        
        // Check if we've reached max taps
        if (currentTapCount >= maxTaps) {
            errorMessage = "Maximum $maxTaps taps reached. Submit or reset."
            return
        }
        
        // Record tap with timestamp
        val timestamp = System.currentTimeMillis()
        taps = taps + RhythmTapFactor.RhythmTap(timestamp)
        tapLocations = tapLocations + offset
        
        // Trigger ripple animation
        rippleAnimations = rippleAnimations + (offset to 0f)
        
        // Animate ripple
        scope.launch {
            // Remove this ripple after animation completes
            delay(500)
            rippleAnimations = rippleAnimations.drop(1)
        }
        
        // Auto-submit if we've reached max taps
        if (taps.size == maxTaps) {
            scope.launch {
                delay(300) // Brief delay for final tap feedback
                submitRhythm()
            }
        }
    }
    
    /**
     * Submit rhythm for authentication
     */
    fun submitRhythm() {
        if (isProcessing || showSuccess) return
        
        // Validate tap count
        if (currentTapCount < minTaps) {
            errorMessage = "Need at least $minTaps taps (current: $currentTapCount)"
            return
        }
        
        isProcessing = true
        errorMessage = null
        
        scope.launch {
            try {
                // Validate taps
                if (!RhythmTapFactor.isValidTaps(taps)) {
                    errorMessage = "Invalid rhythm pattern. Try varying your tap timing more."
                    isProcessing = false
                    return@launch
                }
                
                // Generate digest
                val digest = RhythmTapFactor.digest(taps)
                
                // Show success feedback
                showSuccess = true
                delay(500)
                
                // Call callback
                onDone(digest)
                
            } catch (e: Exception) {
                errorMessage = e.message ?: "Failed to process rhythm. Try again."
                isProcessing = false
            }
        }
    }
    
    /**
     * Reset rhythm input
     */
    fun resetRhythm() {
        taps = emptyList()
        tapLocations = emptyList()
        rippleAnimations = emptyList()
        errorMessage = null
        isProcessing = false
        showSuccess = false
    }
    
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
            text = "Tap Your Rhythm",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Tap $minTaps-$maxTaps times in your own rhythm",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // ==================== TAP COUNTER ====================
        
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Taps: ",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )
            
            Text(
                text = "$currentTapCount",
                color = when {
                    currentTapCount < minTaps -> Color.White
                    currentTapCount in minTaps until maxTaps -> Color.Green
                    else -> Color.Yellow
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = " / $maxTaps",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp
            )
        }
        
        // Progress indicator
        if (currentTapCount in minTaps until maxTaps) {
            Text(
                text = "Ready to submit!",
                color = Color.Green,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (currentTapCount < minTaps) {
            Text(
                text = "${minTaps - currentTapCount} more tap${if (minTaps - currentTapCount > 1) "s" else ""} needed",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== ERROR MESSAGE ====================
        
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // ==================== TAP AREA (CANVAS) ====================
        
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray.copy(alpha = 0.3f))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        handleTap(offset)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            // Canvas for drawing tap feedback
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw tap locations (persistent circles)
                tapLocations.forEachIndexed { index, location ->
                    // Outer circle (tap number)
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.6f),
                        radius = 40.dp.toPx(),
                        center = location,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    
                    // Inner filled circle
                    drawCircle(
                        color = Color.Green.copy(alpha = 0.3f),
                        radius = 35.dp.toPx(),
                        center = location
                    )
                }
                
                // Draw ripple animations
                rippleAnimations.forEach { (location, _) ->
                    // Animated ripple (expands outward)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = 50.dp.toPx(),
                        center = location,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            
            // Instruction overlay (when no taps yet)
            if (currentTapCount == 0 && !isProcessing) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎵",
                        fontSize = 64.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap anywhere to start",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Like tapping to a beat!",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Success overlay
            if (showSuccess) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "✓",
                        color = Color.Green,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Rhythm Captured!",
                        color = Color.Green,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Processing overlay
            if (isProcessing && !showSuccess) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "⏳",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Processing...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }
        
        // ==================== INTERVAL PREVIEW (OPTIONAL DEBUG) ====================
        
        if (currentTapCount >= 2 && !isProcessing && !showSuccess) {
            val intervals = taps.zipWithNext { a, b -> b.timestamp - a.timestamp }
            Text(
                text = "Intervals: ${intervals.joinToString(", ") { "${it}ms" }}",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== ACTION BUTTONS ====================
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Reset Button
            TextButton(
                onClick = { resetRhythm() },
                enabled = currentTapCount > 0 && !isProcessing && !showSuccess,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Reset",
                    color = if (currentTapCount > 0) Color.White else Color.Gray,
                    fontSize = 16.sp
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Submit Button
            Button(
                onClick = { submitRhythm() },
                enabled = currentTapCount >= minTaps && !isProcessing && !showSuccess,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentTapCount >= minTaps) Color.Green else Color.Gray,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = if (isProcessing) "Processing..." else "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== TIPS ====================
        
        Text(
            text = "💡 Tip: Vary your tap timing for better security",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
