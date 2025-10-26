// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/UUIDInputScreen.kt

package com.zeropay.merchant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.merchant.config.MerchantConfig
import java.util.UUID

/**
 * UUID Input Screen - SIMPLIFIED VERSION
 * 
 * User presents UUID for authentication (no payment amount display).
 * 
 * @param merchantId Merchant identifier
 * @param onSubmit Callback with user UUID
 * @param onCancel Callback for cancellation
 * @param errorMessage Error message to display
 * @param isProcessing Processing state
 * 
 * @version 2.0.0
 * @date 2025-10-09
 */
@Composable
fun UUIDInputScreen(
    merchantId: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    errorMessage: String?,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    var manualUUID by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(MerchantConfig.UUIDInputMethod.QR_CODE) }
    
    fun validateAndSubmit(uuid: String) {
        val trimmed = uuid.trim().lowercase()
        
        // Validate UUID format
        try {
            UUID.fromString(trimmed)
            onSubmit(trimmed)
        } catch (e: Exception) {
            // Invalid UUID - will be shown as error by parent
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
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ========== HEADER ==========
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔐 ZeroPay Authentication",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Merchant: $merchantId",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
                
                Text(
                    text = "Device-Free Payment Authentication",
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            Divider(color = Color.White.copy(alpha = 0.2f))
            
            // ========== INPUT METHOD SELECTOR ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "How will the user present their UUID?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InputMethodButton(
                            icon = "📷",
                            label = "QR Code",
                            isSelected = selectedMethod == MerchantConfig.UUIDInputMethod.QR_CODE,
                            onClick = { selectedMethod = MerchantConfig.UUIDInputMethod.QR_CODE },
                            modifier = Modifier.weight(1f)
                        )
                        
                        InputMethodButton(
                            icon = "📡",
                            label = "NFC",
                            isSelected = selectedMethod == MerchantConfig.UUIDInputMethod.NFC,
                            onClick = { selectedMethod = MerchantConfig.UUIDInputMethod.NFC },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InputMethodButton(
                            icon = "⌨️",
                            label = "Manual",
                            isSelected = selectedMethod == MerchantConfig.UUIDInputMethod.MANUAL_ENTRY,
                            onClick = { selectedMethod = MerchantConfig.UUIDInputMethod.MANUAL_ENTRY },
                            modifier = Modifier.weight(1f)
                        )
                        
                        InputMethodButton(
                            icon = "📱",
                            label = "Bluetooth",
                            isSelected = selectedMethod == MerchantConfig.UUIDInputMethod.BLUETOOTH,
                            onClick = { selectedMethod = MerchantConfig.UUIDInputMethod.BLUETOOTH },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== INPUT CONTENT ==========
            
            when (selectedMethod) {
                MerchantConfig.UUIDInputMethod.QR_CODE -> {
                    QRCodeScannerPlaceholder(onScan = { validateAndSubmit(it) })
                }
                
                MerchantConfig.UUIDInputMethod.NFC -> {
                    NFCScannerPlaceholder(onScan = { validateAndSubmit(it) })
                }
                
                MerchantConfig.UUIDInputMethod.MANUAL_ENTRY -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = manualUUID,
                            onValueChange = { manualUUID = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Enter User UUID") },
                            placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = Color(0xFF4CAF50),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = { validateAndSubmit(manualUUID) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualUUID.isNotBlank() && !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Begin Authentication", fontSize = 18.sp)
                            }
                        }
                    }
                }
                
                MerchantConfig.UUIDInputMethod.BLUETOOTH -> {
                    BluetoothScannerPlaceholder(onScan = { validateAndSubmit(it) })
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // ========== ERROR MESSAGE ==========
            
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // ========== CANCEL BUTTON ==========
            
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun InputMethodButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF4CAF50) else Color(0xFF16213E)
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = icon, fontSize = 24.sp)
            Text(text = label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QRCodeScannerPlaceholder(onScan: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "📷", fontSize = 64.sp)
                Text(
                    text = "Scan customer's QR code",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NFCScannerPlaceholder(onScan: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "📡", fontSize = 64.sp)
                Text(
                    text = "Ready for NFC tap",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BluetoothScannerPlaceholder(onScan: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "Scanning for devices...",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }
    }
}
