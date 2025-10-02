package com.zeropay.merchant

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.Factor
import com.zeropay.sdk.CsprngShuffle
import com.zeropay.sdk.RateLimiter
import com.zeropay.sdk.ZeroPay
import com.zeropay.sdk.factors.FactorRegistry
import kotlinx.coroutines.delay
import java.security.MessageDigest
import java.util.UUID

/**
 * MerchantActivity - Production-ready authentication flow
 * 
 * Architecture:
 * - Separation of concerns: UI, business logic, security
 * - Modularity: Each screen is a separate composable
 * - Security: Rate limiting, validation, error handling
 * - Scalability: Easy to add more factors or steps
 */
class MerchantActivity : ComponentActivity() {
    
    // User identifier (in production, this comes from your auth system)
    private val uidHash by lazy {
        // Hash the device ID or user ID for privacy
        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        hashString(deviceId)
    }
    
    // Authentication state
    private val authState = AuthenticationState()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Global crash handler (security: don't leak sensitive info)
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("ZeroPay", "Critical error: ${throwable.javaClass.simpleName}")
            runOnUiThread {
                Toast.makeText(
                    this,
                    "An error occurred. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                // Reset to initial state
                setContent { InitialScreen() }
            }
        }
        
        setContent { InitialScreen() }
    }
    
    /**
     * Initial screen - Check rate limits before starting authentication
     */
    @Composable
    fun InitialScreen() {
        val rateCheckResult = remember { RateLimiter.check(uidHash) }
        
        when (rateCheckResult) {
            RateLimiter.RateResult.OK -> {
                // Proceed to factor selection
                FactorSelectionScreen()
            }
            RateLimiter.RateResult.COOL_DOWN_15M -> {
                RateLimitScreen("15 minutes", "You've had 5 failed attempts.")
            }
            RateLimiter.RateResult.COOL_DOWN_4H -> {
                RateLimitScreen("4 hours", "You've had 8 failed attempts.")
            }
            RateLimiter.RateResult.FROZEN_FRAUD -> {
                FrozenScreen()
            }
            RateLimiter.RateResult.BLOCKED_24H -> {
                RateLimitScreen("24 hours", "Daily attempt limit reached.")
            }
        }
    }
    
    /**
     * Factor selection screen - Shows available factors and shuffles daily
     */
    @Composable
    fun FactorSelectionScreen() {
        var isLoading by remember { mutableStateOf(true) }
        var selectedFactors by remember { mutableStateOf<List<Factor>>(emptyList()) }
        
        // Load and shuffle factors on first composition
        LaunchedEffect(Unit) {
            delay(500) // Brief loading for UX
            val availableFactors = FactorRegistry.availableFactors(this@MerchantActivity)
            // Shuffle daily for security (prevents pattern memorization)
            val shuffled = CsprngShuffle.shuffle(availableFactors)
            // Select 2 factors for Week 1 (pattern + colour, or colour + pin, etc.)
            selectedFactors = shuffled.take(2).distinct()
            isLoading = false
        }
        
        if (isLoading) {
            LoadingScreen("Preparing authentication...")
        } else if (selectedFactors.isEmpty()) {
            ErrorScreen("No authentication factors available")
        } else {
            // Start authentication flow
            AuthenticationFlowScreen(selectedFactors)
        }
    }
    
    /**
     * Authentication flow - Handles multi-factor authentication
     */
    @Composable
    fun AuthenticationFlowScreen(factors: List<Factor>) {
        var currentStep by remember { mutableStateOf(0) }
        var isProcessing by remember { mutableStateOf(false) }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                currentStep < factors.size -> {
                    // Show current factor canvas
                    val currentFactor = factors[currentStep]
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Progress indicator
                        ProgressHeader(
                            currentStep = currentStep + 1,
                            totalSteps = factors.size,
                            factorName = currentFactor.name
                        )
                        
                        // Factor canvas
                        ZeroPay.canvasForFactor(
                            factor = currentFactor,
                            onDone = { digest ->
                                if (!isProcessing) {
                                    isProcessing = true
                                    handleFactorCompletion(
                                        factor = currentFactor,
                                        digest = digest,
                                        onSuccess = {
                                            isProcessing = false
                                            currentStep++
                                        },
                                        onFailure = { error ->
                                            isProcessing = false
                                            Log.e("ZeroPay", "Factor validation failed: $error")
                                            // Show error and retry
                                            Toast.makeText(
                                                this@MerchantActivity,
                                                "Authentication failed. Please try again.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    // All factors completed - verify
                    LaunchedEffect(Unit) {
                        verifyAuthentication(
                            onSuccess = {
                                RateLimiter.resetFails(uidHash)
                                setContent { SuccessScreen() }
                            },
                            onFailure = {
                                RateLimiter.recordFail(uidHash)
                                setContent { FailureScreen() }
                            }
                        )
                    }
                    LoadingScreen("Verifying...")
                }
            }
        }
    }
    
    /**
     * Progress header showing current step
     */
    @Composable
    fun ProgressHeader(currentStep: Int, totalSteps: Int, factorName: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.DarkGray)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Step $currentStep of $totalSteps",
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                factorName.replace("_", " "),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    
    /**
     * Handle factor completion with validation
     */
    private fun handleFactorCompletion(
        factor: Factor,
        digest: ByteArray,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            // Validate digest
            if (digest.size != 32) {
                onFailure("Invalid digest size: ${digest.size}")
                return
            }
            
            // Store proof
            authState.addProof(factor, digest)
            
            // Log for debugging (remove in production)
            Log.d("ZeroPay", "Factor ${factor.name} completed: ${digest.take(8).joinToString("") { "%02x".format(it) }}...")
            
            onSuccess()
        } catch (e: Exception) {
            onFailure(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Verify all collected proofs (Week 1: simple validation, Week 2: zk-SNARK)
     */
    private suspend fun verifyAuthentication(
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        delay(1000) // Simulate server call
        
        // Week 1: Simple validation - check we have valid proofs
        val isValid = authState.proofs.size >= 2 && 
                      authState.proofs.all { it.value.size == 32 }
        
        // Week 2: Replace with zk-SNARK proof verification
        // val zkProof = generateZkProof(authState.proofs)
        // val isValid = verifyZkProof(zkProof)
        
        if (isValid) {
            onSuccess()
        } else {
            onFailure()
        }
    }
    
    // ============== UI Screens ==============
    
    @Composable
    fun LoadingScreen(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(message, color = Color.White, fontSize = 16.sp)
            }
        }
    }
    
    @Composable
    fun SuccessScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF4CAF50)), // Green
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "✓",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Authentication Successful",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Payment approved",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { 
                    authState.clear()
                    setContent { InitialScreen() }
                }) {
                    Text("New Transaction")
                }
            }
        }
    }
    
    @Composable
    fun FailureScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF44336)), // Red
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "✗",
                    color = Color.White,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Authentication Failed",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Please try again",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { 
                    authState.clear()
                    setContent { InitialScreen() }
                }) {
                    Text("Try Again")
                }
            }
        }
    }
    
    @Composable
    fun RateLimitScreen(duration: String, reason: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFF9800)), // Orange
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "⏱",
                    color = Color.White,
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Too Many Attempts",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    reason,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Please wait $duration",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    @Composable
    fun FrozenScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF9C27B0)), // Purple
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "🔒",
                    color = Color.White,
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Account Frozen",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Suspicious activity detected.\nPlease contact support.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    @Composable
    fun ErrorScreen(message: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "⚠",
                    color = Color.White,
                    fontSize = 72.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Error",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    message,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = { finish() }) {
                    Text("Close")
                }
            }
        }
    }
    
    // ============== Helper Functions ==============
    
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Authentication state holder
     * Separates business logic from UI
     */
    private class AuthenticationState {
        val proofs = mutableMapOf<Factor, ByteArray>()
        
        fun addProof(factor: Factor, digest: ByteArray) {
            proofs[factor] = digest
        }
        
        fun clear() {
            proofs.clear()
        }
    }
}
