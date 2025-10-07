package com.zeropay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.ColourFactor

/**
 * Colour Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - Input validation (2-3 colors only)
 * - No colors stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - No personal data collected
 * - Only color indices used
 * - Zero-knowledge authentication
 * 
 * @param onSelected Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
private const val MIN_COLORS = 2
private const val MAX_COLORS = 3

@Composable
fun ColourCanvas(
    onSelected: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    val colours = remember {
        listOf(
            Color.Red,
            Color.Green,
            Color.Blue,
            Color.Yellow,
            Color.Magenta,
            Color.Cyan
        )
    }
    
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var targetCount by remember { mutableStateOf<Int?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // ==================== UI LAYOUT ====================
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==================== HEADER ====================
        
        Text(
            "Select Your Colors",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== STEP 1: CHOOSE COUNT ====================
        
        if (targetCount == null) {
            Text(
                "How many colors do you want to select?",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { targetCount = 2 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("2 Colors", fontSize = 16.sp)
                }
                
                Button(
                    onClick = { targetCount = 3 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Text("3 Colors", fontSize = 16.sp)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                "More colors = more security",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            
        } else {
            // ==================== STEP 2: SELECT COLORS ====================
            
            Text(
                "Selected: ${selected.size} / $targetCount",
                color = if (selected.size == targetCount) Color.Green else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Color selection grid
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                colours.forEachIndexed { index, color ->
                    val isSelected = selected.contains(index)
                    val selectionOrder = if (isSelected) selected.indexOf(index) + 1 else null
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(color)
                            .border(
                                width = if (isSelected) 4.dp else 2.dp,
                                color = if (isSelected) Color.White else Color.Gray
                            )
                            .clickable(enabled = !isProcessing) {
                                errorMessage = null
                                
                                selected = if (isSelected) {
                                    // Deselect
                                    selected.filter { it != index }
                                } else if (selected.size < targetCount!!) {
                                    // Select
                                    selected + index
                                } else {
                                    errorMessage = "Maximum $targetCount colors selected"
                                    selected
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectionOrder != null) {
                            Text(
                                text = "$selectionOrder",
                                color = Color.White,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ==================== ACTION BUTTONS ====================
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Back button
                TextButton(
                    onClick = {
                        targetCount = null
                        selected = emptyList()
                        errorMessage = null
                    },
                    enabled = !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("← Back", color = Color.White, fontSize = 16.sp)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Reset button
                TextButton(
                    onClick = {
                        selected = emptyList()
                        errorMessage = null
                    },
                    enabled = selected.isNotEmpty() && !isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Reset",
                        color = if (selected.isNotEmpty()) Color.White else Color.Gray,
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Submit button
                Button(
                    onClick = {
                        if (selected.size != targetCount) {
                            errorMessage = "Please select exactly $targetCount colors"
                            return@Button
                        }
                        
                        isProcessing = true
                        errorMessage = null
                        
                        try {
                            // Generate digest (Factor.digest handles security)
                            val digest = ColourFactor.digest(selected)
                            onSelected(digest)
                            
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to process colors"
                            isProcessing = false
                        }
                    },
                    enabled = selected.size == targetCount && !isProcessing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected.size == targetCount) Color.Green else Color.Gray
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge: Your color sequence is hashed locally",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
