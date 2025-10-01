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
import androidx.compose.ui.unit.dp

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Tap 2 colours in order",
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
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
                    .clickable(enabled = selected.size < 2) {
                        if (!isSelected && selected.size < 2) {
                            selected = selected + idx
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (selectionOrder != null) {
                    Text(
                        text = selectionOrder.toString(),
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(8.dp)
                    )
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
            
            if (selected.size == 2) {
                Button(onClick = {
                    val digest = ColourFactor.digest(selected)
                    onSelected(digest)
                }) {
                    Text("Confirm")
                }
            }
        }
    }
}
