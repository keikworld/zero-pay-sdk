package com.zeropay.sdk.factors

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.Factor // Added import for Factor

object FactorCanvasFactory {

    @Composable
    fun CanvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        when (factor) {
            Factor.PATTERN_MICRO, Factor.PATTERN_NORMAL -> PatternCanvas { points -> // PatternCanvas needs to be defined
                val digest = if (factor == Factor.PATTERN_MICRO) {
                    PatternFactor.digestMicroTiming(points)
                } else {
                    PatternFactor.digestNormalisedTiming(points)
                }
                onDone(digest)
            }
            Factor.MOUSE_DRAW -> MouseCanvas { points -> // MouseCanvas needs to be defined
                onDone(MouseFactor.digestMicroTiming(points))
            }
            Factor.STYLUS_DRAW -> StylusCanvas { points -> // StylusCanvas needs to be defined
                onDone(StylusFactor.digestFull(points))
            }
            Factor.COLOUR -> ColourCanvas(onDone) // ColourCanvas needs to be defined
            Factor.EMOJI -> EmojiCanvas(onDone)   // EmojiCanvas needs to be defined
            Factor.PIN -> PinCanvas(onDone)       // PinCanvas needs to be defined
            Factor.VOICE -> VoiceCanvas(onDone)   // VoiceCanvas needs to be defined
            Factor.NFC -> NfcCanvas(onDone)       // NfcCanvas needs to be defined
            Factor.BALANCE -> BalanceCanvas(onDone) // BalanceCanvas needs to be defined
            Factor.FACE -> FaceCanvas(onDone)     // FaceCanvas needs to be defined
        }
    }
}