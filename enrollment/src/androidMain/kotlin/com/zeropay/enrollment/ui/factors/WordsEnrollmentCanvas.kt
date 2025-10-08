// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/WordsEnrollmentCanvas.kt

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
import com.zeropay.sdk.crypto.CryptoUtils
import kotlinx.coroutines.launch

/**
 * Words Enrollment Canvas - PRODUCTION VERSION
 * 
 * Features:
 * - CSPRNG shuffling on EVERY attempt (100 words → show 20, shuffled)
 * - Visual feedback for selections
 * - Practice mode (multiple attempts)
 * - Input validation (min 3, max 6 words)
 * - Error handling
 * 
 * Security:
 * - Dynamic positioning (CSPRNG shuffle per attempt)
 * - Large word pool (100 words, show 20 random)
 * - No word sequence stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 digest
 * - No raw word data retained
 * 
 * @param onDone Callback with SHA-256 digest
 * @param onCancel Callback for cancellation
 * @param modifier Compose modifier
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun WordsEnrollmentCanvas(
    onDone: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    // Full word set (100 common, memorable words)
    val fullWordSet = remember {
        listOf(
            // Animals (10)
            "cat", "dog", "lion", "tiger", "bear", "wolf", "fox", "eagle", "shark", "whale",
            // Nature (10)
            "sun", "moon", "star", "ocean", "mountain", "river", "forest", "desert", "lake", "sky",
            // Colors (10)
            "red", "blue", "green", "yellow", "purple", "orange", "black", "white", "pink", "gold",
            // Objects (10)
            "book", "phone", "car", "house", "tree", "door", "window", "chair", "table", "lamp",
            // Food (10)
            "pizza", "burger", "apple", "banana", "coffee", "bread", "cheese", "chicken", "rice", "pasta",
            // Actions (10)
            "run", "jump", "swim", "fly", "dance", "sing", "read", "write", "play", "sleep",
            // Weather (10)
            "rain", "snow", "wind", "storm", "cloud", "thunder", "lightning", "sunshine", "fog", "hail",
            // Body (10)
            "hand", "foot", "eye", "ear", "nose", "mouth", "head", "heart", "brain", "arm",
            // Places (10)
            "home", "school", "work", "park", "beach", "city", "town", "village", "island", "farm",
            // Abstract (10)
            "love", "hope", "dream", "power", "magic", "peace", "joy", "trust", "courage", "freedom"
        )
    }
    
    // Shuffle trigger - change to re-shuffle words
    var shuffleTrigger by remember { mutableStateOf(0) }
    
    // Shuffled subset of words (CSPRNG) - show 20 out of 100
    val displayedWords = remember(shuffleTrigger) {
        CsprngShuffle.shuffleAndTake(fullWordSet, 20)
    }
    
    // Selected words (in order of selection)
    var selectedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // UI states
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptCount by remember { mutableStateOf(1) }
    var showInstructions by remember { mutableStateOf(true) }
    
    // Constants
    val minWords = 3
    val maxWords = 6
    
    val scope = rememberCoroutineScope()
    
    // ==================== HELPERS ====================
    
    fun reshuffleWords() {
        shuffleTrigger++
        selectedWords = emptyList()
        errorMessage = null
    }
    
    fun handleWordSelection(word: String) {
        if (isProcessing) return
        
        if (selectedWords.contains(word)) {
            // Deselect
            selectedWords = selectedWords.filter { it != word }
        } else {
            // Select (if not at max)
            if (selectedWords.size < maxWords) {
                selectedWords = selectedWords + word
                errorMessage = null
            } else {
                errorMessage = "Maximum $maxWords words allowed"
            }
        }
    }
    
    fun handleSubmit() {
        if (selectedWords.size < minWords) {
            errorMessage = "Please select at least $minWords words"
            return
        }
        
        isProcessing = true
        errorMessage = null
        
        scope.launch {
            try {
                // Create word sequence string (comma-separated)
                val wordSequence = selectedWords.joinToString(",")
                
                // Generate SHA-256 digest
                val digest = CryptoUtils.sha256(wordSequence.toByteArray(Charsets.UTF_8))
                
                // Clear selected words from memory (security)
                selectedWords = emptyList()
                
                // Return digest
                onDone(digest)
                
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
                text = "🔡 Word Selection",
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
                            text = "• Select $minWords-$maxWords words in your preferred order",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Words shuffle each time for security",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Choose words that form a memorable story",
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
                        text = "Your Selection (${selectedWords.size}/$maxWords)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Display selected words in order
                    if (selectedWords.isEmpty()) {
                        Text(
                            text = "No words selected yet",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = selectedWords.mapIndexed { index, word -> 
                                "${index + 1}. $word" 
                            }.joinToString(" → "),
                            color = Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            
            // ========== WORD GRID ==========
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(displayedWords) { _, word ->
                    val isSelected = selectedWords.contains(word)
                    val selectionOrder = if (isSelected) {
                        selectedWords.indexOf(word) + 1
                    } else null
                    
                    WordButton(
                        word = word,
                        isSelected = isSelected,
                        selectionOrder = selectionOrder,
                        onClick = { handleWordSelection(word) },
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
                        reshuffleWords()
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
                    enabled = selectedWords.size >= minWords && !isProcessing,
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
 * Word Button Component
 */
@Composable
private fun WordButton(
    word: String,
    isSelected: Boolean,
    selectionOrder: Int?,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .height(56.dp)
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
                text = word,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected && selectionOrder != null) {
                Text(
                    text = "#$selectionOrder",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
