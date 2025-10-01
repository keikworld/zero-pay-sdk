package com.zeropay.sdk.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.CsprngShuffle

@Composable
fun EmojiCanvas(onDone: (ByteArray) -> Unit) {
    // Common emojis for authentication (shuffled each time for security)
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
    
    val emojis = remember { CsprngShuffle.shuffle(baseEmojis).take(12) }
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    val targetCount = 4 // User must select 4 emojis in sequence

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Select $targetCount emojis in order",
            color = Color.White,
            fontSize = 18.sp
        )
        
        Text(
            "${selected.size} / $targetCount selected",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Display emojis in a grid
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until 3) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 4) {
                        val idx = row * 4 + col
                        if (idx < emojis.size) {
                            val isSelected = selected.contains(idx)
                            val selectionOrder = if (isSelected) selected.indexOf(idx) + 1 else null
                            
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .background(Color.DarkGray)
                                    .then(
                                        if (isSelected)
                                            Modifier.border(3.dp, Color.White)
                                        else
                                            Modifier
                                    )
                                    .clickable(enabled = selected.size < targetCount) {
                                        if (!isSelected && selected.size < targetCount) {
                                            selected = selected + idx
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = emojis[idx],
                                        fontSize = 32.sp
                                    )
                                    if (selectionOrder != null) {
                                        Text(
                                            text = selectionOrder.toString(),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.7f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selected.isNotEmpty()) {
                TextButton(onClick = { selected = emptyList() }) {
                    Text("Reset")
                }
            }
            
            if (selected.size == targetCount) {
                Button(onClick = {
                    val digest = EmojiFactor.digest(selected)
                    onDone(digest)
                }) {
                    Text("Confirm")
                }
            }
        }
    }
}
