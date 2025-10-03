package com.zeropay.sdk.factors

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.Factor

object FactorCanvasFactory {

    @Composable
    fun CanvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        when (factor) {
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL -> PatternCanvas { points ->
                val digest = if (factor == Factor.PATTERN_MICRO) {
                    PatternFactor.digestMicroTiming(points)
                } else {
                    PatternFactor.digestNormalisedTiming(points)
                }
                onDone(digest)
            }
            Factor.MOUSE_DRAW -> MouseCanvas { points ->
                onDone(MouseFactor.digestMicroTiming(points))
            }
            Factor.STYLUS_DRAW -> StylusCanvas { points ->
                onDone(StylusFactor.digestFull(points))
            }
            Factor.COLOUR -> ColourCanvas(onDone)
            Factor.EMOJI -> EmojiCanvas(onDone)
            Factor.PIN -> PinCanvas(onDone)
            Factor.VOICE -> VoiceCanvas(onDone)
            Factor.NFC -> NfcCanvas(onDone)
            Factor.BALANCE -> BalanceCanvas(onDone)
            Factor.FACE -> FaceCanvas(onDone)
            Factor.WORDS -> WordsCanvas(onDone)
            Factor.IMAGE_TAP -> ImageTapCanvas(onDone)
        }
    }
}
