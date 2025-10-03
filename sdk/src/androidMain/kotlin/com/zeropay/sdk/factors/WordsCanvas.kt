package com.zeropay.sdk.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Words Canvas - 4-word authentication UI
 * 
 * Displays 12 words in a grid (4x3)
 * User must tap their 4 selected words in any order
 * Word positions are dynamically shuffled for each authentication
 */
@Composable
fun WordsCanvas(
    onDone: (ByteArray) -> Unit,
    enrolledWords: List<Int>? = null // If null, this is enrollment; otherwise authentication
) {
    var selectedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var displayedWords by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(enrolledWords == null) }
    
    // Initialize displayed words
    LaunchedEffect(enrolledWords) {
        displayedWords = if (enrolledWords != null) {
            // Authentication mode: show 12 words (4 enrolled + 8 decoys)
            WordsFactor.getAuthenticationWords(enrolledWords)
        } else {
            // Enrollment mode: show first 100 words
            WordsFactor.getEnrollmentWordSubset(page = 0, pageSize = 100)
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
            if (enrolledWords == null) "Select 4 Words" else "Tap Your 4 Words",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        Text(
            "${selectedIndices.size} / 4 selected",
            color = if (selectedIndices.size == 4) Color.Green else Color.White,
            fontSize = 16.sp
        )
        
        // Search bar (enrollment only)
        if (showSearch && enrolledWords == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    displayedWords = if (query.isBlank()) {
                        WordsFactor.getEnrollmentWordSubset(page = 0, pageSize = 100)
                    } else {
                        WordsFactor.searchWords(query)
                    }
                },
                label = { Text("Search words") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Word grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (enrolledWords == null) 3 else 4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(displayedWords.size) { displayIndex ->
                val (originalIndex, word) = displayedWords[displayIndex]
                val isSelected = selectedIndices.contains(
                    if (enrolledWords == null) originalIndex else displayIndex
                )
                val selectionOrder = if (isSelected) {
                    selectedIndices.indexOf(
                        if (enrolledWords == null) originalIndex else displayIndex
                    ) + 1
                } else null
                
                Box(
                    modifier = Modifier
                        .aspectRatio(1.5f)
                        .background(if (isSelected) Color.Blue else Color.DarkGray)
                        .then(
                            if (isSelected)
                                Modifier.border(2.dp, Color.White)
                            else
                                Modifier
                        )
                        .clickable(enabled = selectedIndices.size < 4 || isSelected) {
                            if (isSelected) {
                                // Deselect
                                selectedIndices = selectedIndices.filter { 
                                    it != (if (enrolledWords == null) originalIndex else displayIndex)
                                }
                            } else if (selectedIndices.size < 4) {
                                // Select
                                selectedIndices = selectedIndices + 
                                    (if (enrolledWords == null) originalIndex else displayIndex)
                            }
                        },
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
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        
                        if (selectionOrder != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✓",
                                color = Color.Green,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedIndices.isNotEmpty()) {
                TextButton(onClick = { 
                    selectedIndices = emptyList()
                }) {
                    Text("Reset")
                }
            }
            
            if (selectedIndices.size == 4) {
                Button(onClick = {
                    // Generate digest
                    val digest = if (enrolledWords == null) {
                        // Enrollment: use selected word indices
                        WordsFactor.digest(selectedIndices)
                    } else {
                        // Authentication: map display indices to original word indices
                        val selectedWordIndices = selectedIndices.map { displayIndex ->
                            displayedWords[displayIndex].first
                        }
                        WordsFactor.digest(selectedWordIndices)
                    }
                    onDone(digest)
                }) {
                    Text("Confirm")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            if (enrolledWords == null)
                "Choose 4 memorable words - you'll need to find them later"
            else
                "Tap the 4 words you selected during enrollment",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}
