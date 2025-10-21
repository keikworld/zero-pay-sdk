// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/ColourEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.CsprngShuffle
import com.zeropay.enrollment.factors.ColourFactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Colour Enrollment Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - CSPRNG shuffling on EVERY attempt (prevents memorization by observers)
 * - Visual feedback for selections
 * - Practice mode (multiple attempts)
 * - Input validation (min 3, max 5 colors)
 * - Weak pattern detection
 * - Error handling
 * 
 * Security:
 * - Dynamic positioning (CSPRNG shuffle per attempt)
 * - No color sequence stored in memory after submission
 * - Immediate digest generation
 * - Memory wiping
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 digest
 * - No raw color data retained
 * - User can change selection
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * @param modifier Compose modifier
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun ColourEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    // ==================== STATE MANAGEMENT ====================

    // Shuffle trigger - change to re-shuffle colors
    var shuffleTrigger by remember { mutableStateOf(0) }
    
    // Shuffled color indices (CSPRNG)
    val shuffledIndices = remember(shuffleTrigger) {
        CsprngShuffle.shuffle(ColourFactor.COLOUR_SET.indices.toList())
    }
    
    // Selected color indices (original indices, not shuffled positions)
    var selectedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    
    // UI states
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    
    // Constants
    val minColors = 3
    val maxColors = 5
    
    // ==================== HELPERS ====================
    
    fun reshuffleColors() {
        shuffleTrigger++
        selectedIndices = emptyList()
        errorMessage = null
    }
    
    fun handleColorSelection(shuffledPosition: Int) {
        if (isProcessing) return
        
        // Get original color index from shuffled position
        val originalIndex = shuffledIndices[shuffledPosition]
        
        if (selectedIndices.contains(originalIndex)) {
            // Deselect
            selectedIndices = selectedIndices.filter { it != originalIndex }
        } else {
            // Select (if not at max)
            if (selectedIndices.size < maxColors) {
                selectedIndices = selectedIndices + originalIndex
                errorMessage = null
            } else {
                errorMessage = "Maximum $maxColors colors allowed"
            }
        }
    }
    
    suspend fun handleSubmit() {
        if (selectedIndices.size < minColors) {
            errorMessage = "Please select at least $minColors colors"
            return
        }
        
        isProcessing = true
        errorMessage = null
        
        try {
            // Process color sequence
            val result = ColourFactor.processColourSequence(selectedIndices)
            
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                
                // Clear selected indices from memory (security)
                selectedIndices = emptyList()
                
                // Return digest
                onDone(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid color sequence"
                isProcessing = false
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
        }
    }
    
    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Text(
                text = "🎨 Color Sequence",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Attempt #$attemptCount",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            
            // ========== INSTRUCTIONS ==========
            
            if (showInstructions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16213E)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📋 Instructions",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { showInstructions = false }) {
                                Text("Hide", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                        Text(
                            text = "• Select $minColors-$maxColors colors in your preferred order",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Colors shuffle each time for security",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Tap again to deselect",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Avoid obvious patterns (all same, sequential)",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                TextButton(onClick = { showInstructions = true }) {
                    Text("Show Instructions", color = Color.White.copy(alpha = 0.7f))
                }
            }
            
            // ========== SELECTION DISPLAY ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Your Selection (${selectedIndices.size}/$maxColors)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Display selected colors in order
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedIndices.isEmpty()) {
                            Text(
                                text = "No colors selected yet",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            selectedIndices.forEachIndexed { index, colorIndex ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Color(
                                                    android.graphics.Color.parseColor(
                                                        ColourFactor.getColourCode(colorIndex)
                                                    )
                                                )
                                            )
                                            .border(
                                                2.dp,
                                                Color.White,
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // ========== COLOR GRID ==========
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(shuffledIndices) { shuffledPosition, originalIndex ->
                    val isSelected = selectedIndices.contains(originalIndex)
                    val selectionOrder = if (isSelected) {
                        selectedIndices.indexOf(originalIndex) + 1
                    } else null
                    
                    ColorButton(
                        colorIndex = originalIndex,
                        isSelected = isSelected,
                        selectionOrder = selectionOrder,
                        onClick = { handleColorSelection(shuffledPosition) },
                        enabled = !isProcessing
                    )
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
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Practice Again Button
                OutlinedButton(
                    onClick = {
                        attemptCount++
                        reshuffleColors()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("🔄 Practice Again")
                }
                
                // Submit Button
                Button(
                    onClick = {
                        scope.launch {
                            handleSubmit()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedIndices.size >= minColors && !isProcessing,
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
                        Text("✓ Confirm")
                    }
                }
            }
            
            // Cancel Button
            TextButton(
                onClick = onCancel,
                enabled = !isProcessing
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/**
 * Color Button Component
 */
@Composable
private fun ColorButton(
    colorIndex: Int,
    isSelected: Boolean,
    selectionOrder: Int?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val colorCode = ColourFactor.getColourCode(colorIndex)
    val colorName = ColourFactor.getColourName(colorIndex)
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                Color(android.graphics.Color.parseColor(colorCode))
            )
            .border(
                width = if (isSelected) 4.dp else 2.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected && selectionOrder != null) {
            // Show selection order number
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$selectionOrder",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
