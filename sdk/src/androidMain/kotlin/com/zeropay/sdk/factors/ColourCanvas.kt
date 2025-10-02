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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_COLORS = 2
private const val MAX_COLORS = 3

@Composable
fun ColourCanvas(onSelected: (ByteArray) -> Unit) {
    val colours = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan
    )
    
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }
    var targetCount by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Select Your Colors",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // Step 1: Choose how many colors (2 or 3)
        if (targetCount == null) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "How many colors do you want to select?",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { targetCount = 2 },
                    modifier = Modifier.width(120.dp)
                ) {
                    Text("2 Colors")
                }
                
                Button(
                    onClick = { targetCount = 3 },
                    modifier = Modifier.width(120.dp)
                ) {
                    Text("3 Colors")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                "More colors = stronger security",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        } else {
            // Step 2: Select colors
            Text(
                "Tap $targetCount colors in order",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp
            )
            
            Text(
                "${selected.size} / $targetCount selected",
                color = if (selected.size == targetCount) Color.Green else Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Display colors
            colours.forEachIndexed { idx, colour ->
                val isSelected = selected.contains(idx)
                val selectionOrder = if (isSelected) selected.indexOf(idx) + 1 else null
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(colour)
                        .then(
                            if (isSelected)
                                Modifier.border(4.dp, Color.White)
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
                    if (selectionOrder != null) {
                        Text(
                            text = selectionOrder.toString(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(10.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { 
                        selected = emptyList()
                    }) {
                        Text("Reset")
                    }
                }
                
                if (selected.isEmpty() && targetCount != null) {
                    TextButton(onClick = { 
                        targetCount = null 
                    }) {
                        Text("Change Count")
                    }
                }
                
                if (selected.size == targetCount) {
                    Button(onClick = {
                        val digest = ColourFactor.digest(selected)
                        onSelected(digest)
                    }) {
                        Text("Confirm")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Your color sequence is stored securely",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}
