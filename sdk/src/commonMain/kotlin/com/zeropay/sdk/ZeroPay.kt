package com.zeropay.sdk

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zeropay.sdk.ui.*

/**
 * ZeroPay SDK - Main Entry Point
 * 
 * Provides:
 * - Factor UI canvases
 * - Authentication flow coordination
 * - Zero-knowledge proof generation
 */
object ZeroPay {
    
    /**
     * Get composable canvas for a specific factor
     * 
     * @param factor Authentication factor type
     * @param onDone Callback with digest (32 bytes SHA-256)
     * @param modifier Compose modifier
     * 
     * Zero-knowledge: Canvas generates hash locally, never sends raw data
     */
    @Composable
    fun canvasForFactor(
        factor: Factor,
        onDone: (ByteArray) -> Unit,
        modifier: Modifier = Modifier
    ) {
        when (factor) {
            Factor.PIN -> PinCanvas(onDone = onDone, modifier = modifier)
            Factor.COLOUR -> ColourCanvas(onDone = onDone, modifier = modifier)
            Factor.EMOJI -> EmojiCanvas(onDone = onDone, modifier = modifier)
            Factor.WORDS -> WordsCanvas(onDone = onDone, modifier = modifier)
            Factor.PATTERN -> PatternCanvas(onDone = onDone, modifier = modifier)
            Factor.MOUSE -> MouseCanvas(onDone = onDone, modifier = modifier)
            Factor.STYLUS -> StylusCanvas(onDone = onDone, modifier = modifier)
            Factor.VOICE -> VoiceCanvas(onDone = onDone, modifier = modifier)
            Factor.IMAGE_TAP -> ImageTapCanvas(onDone = onDone, modifier = modifier)
            Factor.FINGERPRINT -> FingerprintCanvas(onDone = onDone, modifier = modifier)
            Factor.FACE -> FaceCanvas(onDone = onDone, modifier = modifier)
        }
    }
    
    /**
     * Get human-readable factor name
     */
    fun getFactorDisplayName(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "PIN Code"
            Factor.COLOUR -> "Color Selection"
            Factor.EMOJI -> "Emoji Selection"
            Factor.WORDS -> "Word Selection"
            Factor.PATTERN -> "Draw Pattern"
            Factor.MOUSE -> "Mouse Movement"
            Factor.STYLUS -> "Stylus Signature"
            Factor.VOICE -> "Voice Recognition"
            Factor.IMAGE_TAP -> "Image Tap Points"
            Factor.FINGERPRINT -> "Fingerprint"
            Factor.FACE -> "Face Recognition"
        }
    }
    
    /**
     * Get factor icon emoji
     */
    fun getFactorIcon(factor: Factor): String {
        return when (factor) {
            Factor.PIN -> "🔢"
            Factor.COLOUR -> "🎨"
            Factor.EMOJI -> "😀"
            Factor.WORDS -> "📝"
            Factor.PATTERN -> "✏️"
            Factor.MOUSE -> "🖱️"
            Factor.STYLUS -> "🖊️"
            Factor.VOICE -> "🎤"
            Factor.IMAGE_TAP -> "🖼️"
            Factor.FINGERPRINT -> "👆"
            Factor.FACE -> "👤"
        }
    }
}
