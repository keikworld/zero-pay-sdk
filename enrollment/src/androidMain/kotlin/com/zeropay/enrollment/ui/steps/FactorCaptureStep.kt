// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/steps/FactorCaptureStep.kt

package com.zeropay.enrollment.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.ui.factors.*
import com.zeropay.sdk.Factor

/**
 * Factor Capture Step - PRODUCTION VERSION
 * 
 * Features:
 * - Sequential factor capture
 * - Progress tracking
 * - Re-capture option
 * - Canvas integration
 * - Error handling
 * 
 * @param selectedFactors Factors to capture
 * @param capturedFactors Already captured factor digests
 * @param onFactorCaptured Callback when factor is captured
 * @param onContinue Callback to proceed to next step
 * @param onBack Callback to go back
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun FactorCaptureStep(
    selectedFactors: List<Factor>,
    capturedFactors: Map<Factor, ByteArray>,
    onFactorCaptured: (Factor, ByteArray) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Current factor being captured
    var currentFactor by remember { mutableStateOf<Factor?>(null) }
    
    val allCaptured = selectedFactors.all { capturedFactors.containsKey(it) }
    
    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
    ) {
        if (currentFactor != null) {
            // Show factor canvas
            FactorCanvas(
                factor = currentFactor!!,
                onDone = { digest ->
                    onFactorCaptured(currentFactor!!, digest)
                    currentFactor = null
                },
                onCancel = {
                    currentFactor = null
                }
            )
        } else {
            // Show factor list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ========== HEADER ==========
                
                Text(
                    text = "Capture Your Factors",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // ========== PROGRESS ==========
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Progress: ${capturedFactors.size}/${selectedFactors.size}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        LinearProgressIndicator(
                            progress = capturedFactors.size.toFloat() / selectedFactors.size,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (allCaptured) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                        
                        if (allCaptured) {
                            Text(
                                text = "✓ All factors captured!",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
                
                // ========== FACTOR LIST ==========
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(selectedFactors) { factor ->
                        val isCaptured = capturedFactors.containsKey(factor)
                        
                        FactorCaptureCard(
                            factor = factor,
                            isCaptured = isCaptured,
                            onCapture = { currentFactor = factor },
                            onRecapture = { currentFactor = factor }
                        )
                    }
                }
                
                // ========== ACTION BUTTONS ==========
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("← Back")
                    }
                    
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        enabled = allCaptured,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text("Continue →")
                    }
                }
            }
        }
    }
}

/**
 * Factor Capture Card Component
 */
@Composable
private fun FactorCaptureCard(
    factor: Factor,
    isCaptured: Boolean,
    onCapture: () -> Unit,
    onRecapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCaptured) Color(0xFF1E3A1E) else Color(0xFF1A1A2E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = EnrollmentConfig.getFactorIcon(factor),
                    fontSize = 32.sp
                )
                
                Column {
                    Text(
                        text = EnrollmentConfig.getFactorDisplayName(factor),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isCaptured) {
                        Text(
                            text = "✓ Captured",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Not captured",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            if (isCaptured) {
                TextButton(onClick = onRecapture) {
                    Text("Re-capture", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                Button(
                    onClick = onCapture,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("Capture")
                }
            }
        }
    }
}

/**
 * Factor Canvas Router
 * 
 * Routes to appropriate canvas based on factor type
 */
@Composable
private fun FactorCanvas(
    factor: Factor,
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit
) {
    when (factor) {
        Factor.COLOUR -> ColourEnrollmentCanvas(onDone, onCancel)
        Factor.EMOJI -> EmojiEnrollmentCanvas(onDone, onCancel)
        Factor.WORDS -> WordsEnrollmentCanvas(onDone, onCancel)
        Factor.PIN -> PinEnrollmentCanvas(onDone, onCancel)
        
        // TODO: Add other factor canvases
        else -> {
            // Placeholder for factors not yet implemented
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Canvas for ${EnrollmentConfig.getFactorDisplayName(factor)} coming soon!",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Button(onClick = onCancel) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
