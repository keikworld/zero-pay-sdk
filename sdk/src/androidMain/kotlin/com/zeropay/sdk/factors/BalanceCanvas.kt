package com.zeropay.sdk.factors

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BalanceCanvas(onDone: (ByteArray) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Balance authentication coming soon",
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            // Placeholder: return empty digest for now
            onDone(ByteArray(32))
        }) {
            Text("Skip")
        }
    }
}
