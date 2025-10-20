package com.zeropay.sdk.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.zeropay.sdk.biometrics.GoogleBiometricProvider

/**
 * Fingerprint Canvas - Fingerprint biometric authentication UI
 */
@Composable
fun FingerprintCanvas(onDone: (ByteArray) -> Unit, modifier: Modifier = Modifier) {
    // TODO: Implement fingerprint biometric UI using GoogleBiometricProvider
    // For now, auto-submit placeholder
    LaunchedEffect(Unit) {
        // In production, this would use BiometricPrompt with BIOMETRIC_STRONG
        val digest = ByteArray(32) { 0 } // Placeholder
        onDone(digest)
    }
}
