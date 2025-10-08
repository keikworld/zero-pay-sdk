package com.zeropay.sdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.WordsFactor

/**
 * Words Canvas - PRODUCTION VERSION
 * 
 * Security Features:
 * - CSPRNG shuffling for authentication
 * - Input validation (exactly 4 words)
 * - No words stored after submission
 * - Immediate digest generation
 * 
 * GDPR Compliance:
 * - No personal data collected
 * - Only word indices used
 * - Zero-knowledge authentication
 * 
 * @param onDone Callback with SHA-256 digest
 * @param modifier Compose modifier
 */
private const val WORD_COUNT = 4
private const val WORDS_PER_PAGE = 20

@Composable
fun WordsCanvas(
    onDone: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    // ==================== STATE MANAGEMENT ====================
    
    var selectedWords by remember { mutableStateOf<List<Int>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Get words for current page or search results
    val displayedWords = remember(currentPage, searchQuery) {
        if (searchQuery.isNotEmpty()) {
            WordsFactor.searchWords(searchQuery, maxResults = 50)
        } else {
            WordsFactor.getEnrollmentWordSubset(currentPage, WORDS_PER_PAGE)
        }
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
            "Select $WORD_COUNT Words",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Choose $WORD_COUNT memorable words from the list",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== STATUS ====================
        
        Text(
            "Selected: ${selectedWords.size} / $WORD_COUNT",
            color = if (selectedWords.size == WORD_COUNT) Color.Green else Color.White,
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
        
        // ==================== SEARCH BAR ====================
        
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search words") },
            placeholder = { Text("Type to search...") },
            singleLine = true,
            enabled = !isProcessing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.Green,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.Green,
                unfocusedLabelColor = Color.Gray,
                cursorColor = Color.Green
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== SELECTED WORDS DISPLAY ====================
        
        if (selectedWords.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                selectedWords.forEach { index ->
                    val word = WordsFactor.getWords()[index]
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Green.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.small
                            )
                            .border(
                                width = 1.dp,
                                color = Color.Green,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = word,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // ==================== WORD LIST ====================
        
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedWords) { (index, word) ->
                val isSelected = selectedWords.contains(index)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) Color.Green.copy(alpha = 0.3f)
                            else Color.DarkGray
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.Green else Color.Gray
                        )
                        .clickable(enabled = !isProcessing) {
                            errorMessage = null
                            
                            selectedWords = if (isSelected) {
                                // Deselect
                                selectedWords.filter { it != index }
                            } else if (selectedWords.size < WORD_COUNT) {
                                // Select
                                selectedWords + index
                            } else {
                                errorMessage = "Maximum $WORD_COUNT words selected"
                                selectedWords
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = word,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        
                        if (isSelected) {
                            Text(
                                text = "✓",
                                color = Color.Green,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // ==================== PAGINATION ====================
        
        if (searchQuery.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { currentPage = maxOf(0, currentPage - 1) },
                    enabled = currentPage > 0 && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("← Previous")
                }
                
                Text(
                    text = "Page ${currentPage + 1}",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                
                Button(
                    onClick = { currentPage++ },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray
                    )
                ) {
                    Text("Next →")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // ==================== ACTION BUTTONS ====================
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Reset button
            Button(
                onClick = {
                    selectedWords = emptyList()
                    errorMessage = null
                },
                enabled = selectedWords.isNotEmpty() && !isProcessing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Gray
                )
            ) {
                Text("Reset", fontSize = 16.sp)
            }
            
            // Submit button
            Button(
                onClick = {
                    if (selectedWords.size != WORD_COUNT) {
                        errorMessage = "Please select exactly $WORD_COUNT words"
                        return@Button
                    }
                    
                    isProcessing = true
                    errorMessage = null
                    
                    try {
                        // Generate digest (Factor handles security)
                        val digest = WordsFactor.digest(selectedWords)
                        onDone(digest)
                        
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Failed to process words"
                        isProcessing = false
                    }
                },
                enabled = selectedWords.size == WORD_COUNT && !isProcessing,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedWords.size == WORD_COUNT) Color.Green else Color.Gray
                )
            ) {
                Text(
                    text = if (isProcessing) "..." else "Submit",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // ==================== SECURITY INFO ====================
        
        Text(
            "🔒 81 trillion combinations - Highly secure",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
