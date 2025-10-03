package com.zeropay.sdk.factors

import android.nfc.NfcAdapter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.crypto.CryptoUtils

/**
 * NFC Factor Implementation
 * 
 * Reads NFC tag UID as authentication factor
 * Security: Tag UID is unique and hard to duplicate
 */
object NfcFactor {
    
    fun digest(tagUid: ByteArray): ByteArray {
        require(tagUid.isNotEmpty()) { "NFC tag UID cannot be empty" }
        require(tagUid.size >= 4) { "NFC UID must be at least 4 bytes" }
        
        // Combine tag UID with timestamp for replay protection
        val timestamp = System.currentTimeMillis().toString().toByteArray()
        val combined = tagUid + timestamp
        
        return CryptoUtils.sha256(combined)
    }
}

@Composable
fun NfcCanvas(onDone: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var nfcStatus by remember { mutableStateOf("Waiting for NFC tag...") }
    var isReading by remember { mutableStateOf(true) }
    
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    
    LaunchedEffect(Unit) {
        if (nfcAdapter == null) {
            nfcStatus = "NFC not available on this device"
            isReading = false
        } else if (!nfcAdapter.isEnabled) {
            nfcStatus = "Please enable NFC in settings"
            isReading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "📱",
            fontSize = 72.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "NFC Authentication",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            nfcStatus,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp
        )
        
        if (isReading) {
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Tap your NFC tag",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Note: NFC reader implementation requires\nforeground dispatch setup in Activity",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}
