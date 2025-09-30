package com.zeropay.merchant

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button // Keeping this for now, might be used by FactorCanvasFactory/ColourCanvas
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
// import androidx.compose.ui.geometry.Offset // Unused
import androidx.compose.ui.graphics.Color
// import androidx.compose.ui.graphics.drawscope.drawLine // Unused and causing error
// import androidx.compose.ui.input.pointer.pointerInput // Unused
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.factors.ColourCanvas
import com.zeropay.sdk.factors.FactorCanvasFactory
import com.zeropay.sdk.factors.FactorRegistry
import com.zeropay.sdk.CsprngShuffle // Corrected import
import com.zeropay.sdk.RateLimiter // Added import
import java.util.UUID

class MerchantActivity : ComponentActivity() {
    private val uidHash = UUID.randomUUID().toString() // demo UUID
    private var proofs = mutableListOf<ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Global crash catcher
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("ZeroPay", "Global crash", e)
            Toast.makeText(this, "Crash: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent { AuthScreen() }
    }

    @Composable
    fun AuthScreen() {
        var step by remember { mutableStateOf("pattern") }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (step) {
                "pattern" -> {
                    val available = FactorRegistry.availableFactors(context = this@MerchantActivity)
                    val dailyDeck = CsprngShuffle.shuffle(available) // Uses corrected import
                    val firstFactor = dailyDeck.first()
                    FactorCanvasFactory.CanvasForFactor(
                        factor = firstFactor,
                        onDone = { digest ->
                            proofs.add(digest)
                            step = "colour"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                "colour" -> ColourCanvas { digest ->
                    proofs.add(digest)
                    // Uses added import for RateLimiter
                    if (RateLimiter.check(uidHash) == RateLimiter.RateResult.OK) {
                        setContent { ApprovedScreen() }
                    } else {
                        setContent { RateLimitedScreen() }
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