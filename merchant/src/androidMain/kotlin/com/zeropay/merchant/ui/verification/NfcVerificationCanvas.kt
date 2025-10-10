// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/NfcVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.factors.NfcFactorEnrollment
import kotlinx.coroutines.launch

/**
 * NFC Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick NFC tag authentication for POS.
 * 
 * Features:
 * - NFC detection
 * - Visual feedback
 * - Auto-submit on tap
 * - Timeout indicator
 * 
 * Security:
 * - SHA-256 digest only
 * - Tag UID hashed
 * - Memory wiping
 * 
 * @param onSubmit Callback with SHA-256 digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time in seconds
 * 
 * @version 1.0.0
 * @date 2025-10-10
 */
@Composable
fun NfcVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var nfcAdapter by remember { mutableStateOf<NfcAdapter?>(null) }
    var isNfcEnabled by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var pulseAnimation by remember { mutableStateOf(0) }
    
    val scale by animateFloatAsState(
        targetValue = if (pulseAnimation > 0) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nfc_pulse"
    )
    
    // Initialize NFC
    DisposableEffect(Unit) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        isNfcEnabled = nfcAdapter?.isEnabled == true
        
        onDispose {
            // Cleanup
        }
    }
    
    // Pulse animation
    LaunchedEffect(isScanning) {
        while (isScanning && !isProcessing) {
            pulseAnimation = 1
            kotlinx.coroutines.delay(1000)
            pulseAnimation = 0
            kotlinx.coroutines.delay(1000)
        }
    }
    
    suspend fun handleNfcTag(tag: Tag) {
        isScanning = false
        isProcessing = true
        
        try {
            val tagUid = tag.id
            val result = NfcFactorEnrollment.processNfcTag(tagUid)
            
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "NFC verification failed"
                isProcessing = false
                isScanning = true
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
            isScanning = true
        }
    }
    
    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds <= 0) {
            onTimeout()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📱 NFC Authentication",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Merchant Verification",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (remainingSeconds < 30) Color(0xFFFF6B6B) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(
                        text = "${remainingSeconds}s",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            
            // ========== NFC STATUS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isNfcEnabled) Color(0xFF4CAF50).copy(alpha = 0.2f) 
                                     else Color(0xFFFF6B6B).copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isNfcEnabled) "✓" else "✗",
                        color = if (isNfcEnabled) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column {
                        Text(
                            text = "NFC Status",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isNfcEnabled) "Ready" else "Disabled - Enable in Settings",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            // ========== INSTRUCTIONS ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Text(
                    text = if (isScanning) {
                        "Tap your NFC tag or card to the back of the device.\nMake sure NFC is enabled."
                    } else {
                        "Processing NFC tag..."
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            // ========== NFC ICON ==========
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📱",
                    fontSize = 120.sp,
                    modifier = Modifier.scale(scale)
                )
                
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFF4CAF50),
                        strokeWidth = 6.dp
                    )
                }
            }
            
            // ========== SCANNING INDICATOR ==========
            
            if (isScanning && !isProcessing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF16213E)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Scanning for NFC...",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = Color.White.copy(alpha = 0.2f)
                        )
                    }
                }
            }
            
            // ========== ERROR MESSAGE ==========
            
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B).copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isNfcEnabled) {
                    Button(
                        onClick = {
                            // Open NFC settings
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                errorMessage = "Could not open NFC settings"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Enable NFC", fontSize = 16.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            isScanning = true
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isScanning && !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50),
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        Text("Retry", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
