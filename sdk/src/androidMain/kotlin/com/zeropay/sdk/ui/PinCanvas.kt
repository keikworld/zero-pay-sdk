package com.zeropay.sdk.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.factors.PinFactor

@Composable
fun PinCanvas(onDone: (ByteArray) -> Unit, modifier: Modifier = Modifier) {
    var pin by remember { mutableStateOf("") }
    
    Column(modifier = modifier.padding(16.dp)) {
        Text("Enter PIN (4-12 digits)", style = MaterialTheme.typography.headlineSmall)
        
        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) pin = it },
            label = { Text("PIN") }
        )
        
        Button(
            onClick = {
                if (PinFactor.isValidPin(pin)) {
                    val digest = PinFactor.digest(pin)
                    onDone(digest.hash)
                }
            },
            enabled = PinFactor.isValidPin(pin)
        ) {
            Text("Submit")
        }
    }
}
