package com.zeropay.sdk.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.zeropay.sdk.factors.ColourFactor

@Composable
fun ColourCanvas(onDone: (ByteArray) -> Unit, modifier: Modifier = Modifier) {
    // TODO: Implement color selection UI
    // For now, auto-submit
    LaunchedEffect(Unit) {
        val digest = ColourFactor.digest(listOf(0, 1, 2))
        onDone(digest)
    }
}
