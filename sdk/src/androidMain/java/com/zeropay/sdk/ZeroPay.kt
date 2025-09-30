package com.zeropay.sdk

import com.zeropay.sdk.factors.FactorCanvasFactory
import com.zeropay.sdk.factors.FactorRegistry

object ZeroPay {

    fun availableFactors(context: android.content.Context) =
        FactorRegistry.availableFactors(context)

    // The FactorCanvasFactory.CanvasForFactor is a @Composable function.
    // A regular object function like ZeroPay.canvasForFactor cannot directly return or execute it
    // in a way that it becomes part of a Composable UI tree.
    // It should also be @Composable if it's intended to be used as a Composable.
    // Or, it simply acts as a passthrough to call the other Composable.
    // For now, I will keep the structure as you provided, but this might need adjustment
    // depending on how it's called.
    // If it's meant to be a Composable wrapper:
    // @Composable
    // fun canvasForFactor(factor: Factor, onDone: (ByteArray) -> Unit, modifier: Modifier = Modifier) {
    //     FactorCanvasFactory.CanvasForFactor(factor, onDone, modifier)
    // }
    // If it's just a direct call (and the caller handles composability):
    fun canvasForFactor(factor: Factor, onDone: (ByteArray) -> Unit) =
        FactorCanvasFactory.CanvasForFactor(factor, onDone)
}