package com.zeropay.sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.factors.FactorCanvasFactory
import com.zeropay.sdk.factors.FactorRegistry

object ZeroPay {

    fun availableFactors(context: android.content.Context) =
        FactorRegistry.availableFactors(context)

    @Composable
    fun canvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        FactorCanvasFactory.CanvasForFactor(factor, onDone, modifier)
    }
}
