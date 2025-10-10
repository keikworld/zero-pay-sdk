package com.zeropay.merchant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.crypto.CryptoUtils
import com.zeropay.sdk.gateway.*
import com.zeropay.sdk.gateway.impl.*
import kotlinx.coroutines.launch

/**
 * Merchant Payment Activity
 * 
 * Flow:
 * 1. User authenticates (zkSNARK proof generated ✅)
 * 2. Hash the proof (SHA-256)
 * 3. Hand off to gateway
 * 4. Done. Gateway handles payment.
 */
class PaymentActivity : ComponentActivity() {
    
    private lateinit var handoffManager: PaymentHandoffManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize handoff manager with ALL gateways
    val tokenStorage = GatewayTokenStorage()
    handoffManager = PaymentHandoffManager(tokenStorage).apply {
        // OAuth Gateways
        registerGateway(GooglePayGateway(tokenStorage))
        registerGateway(ApplePayGateway(tokenStorage))
        registerGateway(StripeGateway(tokenStorage))
        
        // Hashed Reference Gateways
        registerGateway(PayUGateway(tokenStorage))
        registerGateway(YappyGateway(tokenStorage))
        registerGateway(NequiGateway(tokenStorage))
        registerGateway(TilopayGateway(tokenStorage)) 
        registerGateway(AlipayGateway(tokenStorage))
        registerGateway(WeChatPayGateway(tokenStorage))
    }
    
    setContent {
        PaymentScreen(
            handoffManager = handoffManager,
            userUuid = "user_abc123",
            proof = ByteArray(80 * 1024), // zkSNARK proof (80 KB)
            amount = 5000L, // $50.00
            currency = "USD",
            merchantId = "merchant_xyz"
        )
    }
}

@Composable
fun PaymentScreen(
    handoffManager: PaymentHandoffManager,
    userUuid: String,
    proof: ByteArray,
    amount: Long,
    currency: String,
    merchantId: String
) {
    var selectedGateway by remember { mutableStateOf<String?>(null) }
    var availableGateways by remember { mutableStateOf<List<GatewayProvider>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Load available gateways
    LaunchedEffect(Unit) {
        availableGateways = handoffManager.getAvailableGateways(userUuid)
        if (availableGateways.size == 1) {
            selectedGateway = availableGateways[0].gatewayId
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Complete Payment",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Amount: ${formatAmount(amount, currency)}")
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Gateway selection
        availableGateways.forEach { gateway ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedGateway == gateway.gatewayId,
                    onClick = { selectedGateway = gateway.gatewayId }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = gateway.displayName,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Authenticate button
        Button(
            onClick = {
                scope.launch {
                    isProcessing = true
                    
                    // Hash the zkSNARK proof
                    val proofHash = CryptoUtils.sha256(proof)
                    
                    val request = AuthRequest(
                        userUuid = userUuid,
                        proofHash = proofHash,
                        amount = amount,
                        currency = currency,
                        merchantId = merchantId,
                        sessionId = "session_${System.currentTimeMillis()}"
                    )
                    
                    try {
                        val success = if (selectedGateway != null) {
                            handoffManager.authenticate(selectedGateway!!, request)
                        } else {
                            handoffManager.authenticateAuto(request)
                        }
                        
                        resultMessage = if (success) {
                            "Authentication sent to gateway ✅"
                        } else {
                            "Authentication failed"
                        }
                    } catch (e: Exception) {
                        resultMessage = "Error: ${e.message}"
                    } finally {
                        isProcessing = false
                    }
                }
            },
            enabled = !isProcessing && selectedGateway != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Authenticate & Pay")
            }
        }
        
        // Result
        resultMessage?.let { message ->
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

private fun formatAmount(amount: Long, currency: String): String {
    val major = amount / 100
    val minor = amount % 100
    return "$currency $major.${minor.toString().padStart(2, '0')}"
}
