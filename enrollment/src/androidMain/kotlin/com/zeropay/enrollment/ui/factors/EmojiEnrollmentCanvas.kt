// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/EmojiEnrollmentCanvas.kt

package com.zeropay.enrollment.ui.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zeropay.enrollment.factors.EmojiFactor
import kotlinx.coroutines.launch

/**
 * Emoji Enrollment Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - CSPRNG shuffling on EVERY attempt (36 emojis → show 18, shuffled)
 * - Visual feedback for selections
 * - Practice mode (multiple attempts)
 * - Input validation (min 3, max 6 emojis)
 * - Error handling
 * 
 * Security:
 * - Dynamic positioning (CSPRNG shuffle per attempt)
 * - Large emoji pool (36 emojis, show 18 random)
 * - No emoji sequence stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 digest
 * - No raw emoji data retained
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * @param modifier Compose modifier
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun EmojiEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Full emoji set (36 emojis)
    val fullEmojiSet = remember {
        listOf(
            "😀", "😂", "😍", "😎", "🥳", "😇", "🤔", "😴", "😱", "🤯",
            "🐶", "🐱", "🐼", "🦁", "🐸", "🦄", "🐵", "🦊", "🐨", "🐯",
            "🍕", "🍔", "🍰", "🍎", "🍌", "🍇", "🍩", "🌮", "🍿", "🍦",
            "⚽", "🏀", "🎮", "🎸", "🎨", "📚", "🚗", "✈️", "🚀", "⛵",
            "🏠", "🌳", "❤️", "⭐", "🌈", "🔥", "💎", "🎁"
        )
    }
    
    // Shuffle trigger - change to re-shuffle emojis
    var shuffleTrigger by remember { mutableStateOf(0) }
    
    // Shuffled subset of emojis (CSPRNG) - show 18 out of 48
    val displayedEmojis = remember(shuffleTrigger) {
        CsprngShuffle.shuffleAndTake(fullEmojiSet, 18)
    }
    
    // Selected emojis (in order of selection)
    var selectedEmojis by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // UI states
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    
    // Constants
    val minEmojis = 3
    val maxEmojis = 6
    
    val scope = rememberCoroutineScope()
    
    // ==================== HELPERS ====================
    
    fun reshuffleEmojis() {
        shuffleTrigger++
        selectedEmojis = emptyList()
        errorMessage = null
    }
    
    fun handleEmojiSelection(emoji: String) {
        if (isProcessing) return
        
        if (selectedEmojis.contains(emoji)) {
            // Deselect
            selectedEmojis = selectedEmojis.filter { it != emoji }
        } else {
            // Select (if not at max)
            if (selectedEmojis.size < maxEmojis) {
                selectedEmojis = selectedEmojis + emoji
                errorMessage = null
            } else {
                errorMessage = "Maximum $maxEmojis emojis allowed"
            }
        }
    }
    
    fun handleSubmit() {
        if (selectedEmojis.size < minEmojis) {
            errorMessage = "Please select at least $minEmojis emojis"
            return
        }
        
        isProcessing = true
        errorMessage = null
        
        scope.launch {
            try {
                // Process emoji sequence
                val result = EmojiFactor.processEmojiSequence(selectedEmojis)
                
                if (result.isSuccess) {
                    val digest = result.getOrNull()!!
                    
                    // Clear selected emojis from memory (security)
                    selectedEmojis = emptyList()
                    
                    // Return digest
                    onDone(digest)
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "Invalid emoji sequence"
                    isProcessing = false
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                isProcessing = false
            }
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
                text = "😀 Emoji Pattern",
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
                            text = "• Select $minEmojis-$maxEmojis emojis in your preferred order",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Emojis shuffle each time for security",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Choose emojis you'll remember easily",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Tap again to deselect",
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
                        text = "Your Selection (${selectedEmojis.size}/$maxEmojis)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Display selected emojis in order
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedEmojis.isEmpty()) {
                            Text(
                                text = "No emojis selected yet",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            selectedEmojis.forEachIndexed { index, emoji ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = emoji,
                                        fontSize = 40.sp
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
            
            // ========== EMOJI GRID ==========
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayedEmojis) { _, emoji ->
                    val isSelected = selectedEmojis.contains(emoji)
                    val selectionOrder = if (isSelected) {
                        selectedEmojis.indexOf(emoji) + 1
                    } else null
                    
                    EmojiButton(
                        emoji = emoji,
                        isSelected = isSelected,
                        selectionOrder = selectionOrder,
                        onClick = { handleEmojiSelection(emoji) },
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
                        reshuffleEmojis()
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
                    onClick = { handleSubmit() },
                    modifier = Modifier.weight(1f),
                    enabled = selectedEmojis.size >= minEmojis && !isProcessing,
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
 * Emoji Button Component
 */
@Composable
private fun EmojiButton(
    emoji: String,
    isSelected: Boolean,
    selectionOrder: Int?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) Color(0xFF4CAF50) else Color(0xFF16213E)
            )
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 32.sp
            )
            if (isSelected && selectionOrder != null) {
                Text(
                    text = "$selectionOrder",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
