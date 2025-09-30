package com.zeropay.merchant

import android.os.Bundle
import android.util.Log // Ensure Log is imported
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.RateLimiter
// Factor import might not be directly needed in AuthScreen anymore, but ZeroPay uses it.
// import com.zeropay.sdk.Factor 
import com.zeropay.sdk.factors.FactorRegistry // Still needed for availableFactors
import com.zeropay.sdk.factors.ColourCanvas // Added import for direct call
import com.zeropay.sdk.CsprngShuffle // Import for the new CsprngShuffle
import com.zeropay.sdk.ZeroPay // Added import for ZeroPay object
import java.util.UUID

// Placeholder CsprngShuffle previously here is now removed, using the one from com.zeropay.sdk

class MerchantActivity : ComponentActivity() {
    private val uidHash = UUID.randomUUID().toString() // demo UUID
    private var proofs = mutableListOf<ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("ZeroPay", "Global crash", e)
            Toast.makeText(this, "Crash: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent { AuthScreen() }
    }

    @Composable
    fun AuthScreen() {
        val context = LocalContext.current
        var step by remember { mutableStateOf("pattern") } // Changed to 'step' state

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (step) {
                "pattern" -> {
                    val available = FactorRegistry.availableFactors(context)
                    val dailyDeck = CsprngShuffle.shuffle(available)
                    if (dailyDeck.isNotEmpty()) {
                        val firstFactor = dailyDeck.first()
                        // ZeroPay.canvasForFactor is a passthrough to FactorCanvasFactory.CanvasForFactor (which is @Composable)
                        ZeroPay.canvasForFactor(firstFactor) { digest ->
                            proofs.add(digest)
                            step = "colour"  // next factor
                        }
                    } else {
                         Log.e("AuthScreen", "No factors available from FactorRegistry")
                         // You might want to display this message in the UI:
                         Text("No authentication factors available.", color = Color.White, modifier = Modifier.padding(24.dp))
                         // Or setContent to an ErrorScreen
                    }
                }
                "colour" -> {
                    // Direct call to ColourCanvas composable
                    ColourCanvas { digest ->
                        proofs.add(digest)
                        if (RateLimiter.check(uidHash) == RateLimiter.RateResult.OK) {
                            setContent { ApprovedScreen() }
                        } else {
                            setContent { RateLimitedScreen() }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ApprovedScreen() {
        Box(Modifier.fillMaxSize().background(Color.Green)) {
            Text("Approved", color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }

    @Composable
    fun RateLimitedScreen() {
        Box(Modifier.fillMaxSize().background(Color.Red)) {
            Text("Too many attempts – wait 15 min", color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }
}