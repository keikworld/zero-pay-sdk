package com.zeropay.sdk.factors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColourCanvas(onSelected: (ByteArray) -> Unit) {
    val colours = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan)
    var selected by remember { mutableStateOf<List<Int>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tap 2 colours in order", color = Color.White)
        colours.forEachIndexed { idx, colour ->
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(colour)
                    .then(if (selected.contains(idx)) Modifier.border(3.dp, Color.White) else Modifier)
                    .clickable { selected = selected + idx }
            )
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