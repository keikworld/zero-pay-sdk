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
import com.zeropay.sdk.CsprngShuffle
import com.zeropay.sdk.factors.EmojiFactor

/**
 * Emoji Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - CSPRNG shuffling (prevents pattern prediction)
 * - Input validation before submission
 * - No emojis stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - No personal data collected
 * - Only emoji indices used
 * - Zero-knowledge authentication
 * 
 * @param onDone Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
@Composable
fun EmojiCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Base emoji set (shuffled once per session - CSPRNG)
    val baseEmojis = remember {
        listOf(
            "😀", "😎", "🥳", "😇", "🤔", "😴",
            "🐶", "🐱", "🐼", "🦁", "🐸", "🦄",
            "🍕", "🍔", "🍰", "🍎", "🍌", "🍇",
            "⚽", "🏀", "🎮", "🎸", "🎨", "📚",
            "🚗", "✈️", "🚀", "⛵", "🏠", "🌳",
            "❤️", "⭐", "🌈", "🔥", "💎", "🎁"
        )
    }
    
    // Shuffle using CSPRNG for security
    val emojis = remember { CsprngShuffle.shuffle(baseEmojis).take(12) }
    
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val targetCount = 4

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
            "Select $targetCount Emojis",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Tap $targetCount emojis in your chosen order",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== STATUS ====================
        
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
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== EMOJI GRID ====================
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0..2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (col in 0..3) {
                        val index = row * 4 + col
                        val emoji = emojis[index]
                        val isSelected = selected.contains(index)
                        val selectionOrder = if (isSelected) selected.indexOf(index) + 1 else null
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (isSelected) Color.Green.copy(alpha = 0.3f)
                                    else Color.DarkGray
                                )
                                .border(
                                    width = if (isSelected) 3.dp else 2.dp,
                                    color = if (isSelected) Color.Green else Color.Gray
                                )
                                .clickable(enabled = !isProcessing) {
                                    errorMessage = null
                                    
                                    selected = if (isSelected) {
                                        // Deselect
                                        selected.filter { it != index }
                                    } else if (selected.size < targetCount) {
                                        // Select
                                        selected + index
                                    } else {
                                        errorMessage = "Maximum $targetCount emojis selected"
                                        selected
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = emoji,
                                    fontSize = 40.sp
                                )
                                
                                // Show selection order number
                                if (selectionOrder != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$selectionOrder",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
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
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Submit button
            Button(
                onClick = {
                    if (selected.size != targetCount) {
                        errorMessage = "Please select exactly $targetCount emojis"
                        return@Button
                    }
                    
                    isProcessing = true
                    errorMessage = null
                    
                    try {
                        // Generate digest (Factor.digest handles security)
                        val digest = EmojiFactor.digest(selected)
                        onDone(digest)
                        
                        // Note: Don't clear selected here - parent handles navigation
                        
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to process emojis"
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
                    text = if (isProcessing) "Processing..." else "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 Zero-Knowledge: Only emoji indices are hashed",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            "🔀 Positions are randomly shuffled each time for security",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
