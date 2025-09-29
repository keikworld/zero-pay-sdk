package com.zeropay.merchant

import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
// import androidx.compose.foundation.gestures.detectDragGestures // Not used by the new PatternCanvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.ColourFactor
import com.zeropay.sdk.PatternFactor
import com.zeropay.sdk.RateLimiter
import java.util.UUID

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
        var step by remember { mutableStateOf("pattern") }
        // var colourIndices by remember { mutableStateOf<List<Int>>(emptyList()) } // This was in (e) but not used in the provided AuthScreen logic

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (step) {
                "pattern" -> PatternCanvas { strokeEvents ->
                    // The new PatternCanvas calls onDone only if PatternFactor.isValidStroke is true,
                    // so we might not need the check here if onDone implies valid strokes.
                    // However, to be safe and align with previous logic where onDone provided raw events:
                    if (PatternFactor.isValidStroke(strokeEvents)) {
                        proofs.add(PatternFactor.digest(strokeEvents))
                        step = "colour"
                    } else {
                        // Optional: handle invalid stroke scenario if not automatically handled by PatternCanvas clearing events
                        // For example, show a message or simply wait for new input.
                        // The new PatternCanvas clears events internally if not valid.
                    }
                }
                "colour" -> ColourMatrix(onSelected = { indices ->
                    proofs.add(ColourFactor.digest(indices))
                    if (RateLimiter.check(uidHash) == RateLimiter.RateResult.OK) {
                        // demo approval
                        setContent { ApprovedScreen() }
                    } else {
                        setContent { RateLimitedScreen() }
                    }
                })
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

    @Composable
    fun PatternCanvas(onDone: (List<MotionEvent>) -> Unit) {
        val events = remember { mutableListOf<MotionEvent>() }
        var lastTime by remember { mutableLongStateOf(0L) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            // ignore hover, only drag
                            if (event.changes.any { it.pressed }) {
                                val ev = MotionEvent.obtain(
                                    System.currentTimeMillis(),
                                    System.currentTimeMillis(),
                                    MotionEvent.ACTION_MOVE,
                                    event.changes.first().position.x,
                                    event.changes.first().position.y,
                                    0
                                )
                                if (ev.eventTime - lastTime >= 20 && events.size < 300) {
                                    events.add(ev)
                                    lastTime = ev.eventTime
                                }
                                if (event.changes.all { !it.pressed }) {
                                    // finger lifted
                                    // The new PatternCanvas design calls onDone from within.
                                    // It also clears events if not valid.
                                    val currentEvents = ArrayList(events) // Create a stable copy for processing
                                    if (PatternFactor.isValidStroke(currentEvents)) {
                                        onDone(currentEvents) // Pass the collected events
                                        // The original request for PatternCanvas had `return@pointerInput` here.
                                        // This would stop further gesture detection after one successful pattern.
                                        // If this is the desired behavior (one pattern then stop):
                                        return@awaitPointerEventScope // Corrected return
                                    }
                                    events.clear() // Clear events if not valid or after processing
                                }
                            }
                        }
                    }
                }
        ) {
            Text("Draw 3 strokes", color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }

    @Composable
    fun ColourMatrix(onSelected: (List<Int>) -> Unit) {
        val colours = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan)
        var selected by remember { mutableStateOf<List<Int>>(emptyList()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tap 2 colours in order", color = Color.White)
            colours.forEachIndexed { idx, colour ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(colour)
                        .then(
                            if (selected.contains(idx)) Modifier.border(3.dp, Color.White)
                            else Modifier
                        )
                        .clickable { selected = selected + idx }
                )
            }
            if (selected.size == 2) {
                Button(onClick = { onSelected(selected) }) {
                    Text("Confirm")
                }
            }
        }
    }
} // End of MerchantActivity class

// This function is no longer directly used by the new PatternCanvas,
// but kept in case it's used elsewhere or for future reference.
private fun androidx.compose.ui.input.pointer.PointerInputChange.toMotionEvent(): android.view.MotionEvent =
    android.view.MotionEvent.obtain(
        System.currentTimeMillis(), System.currentTimeMillis(),
        if (pressed) android.view.MotionEvent.ACTION_MOVE else android.view.MotionEvent.ACTION_UP,
        position.x, position.y, 0
    )
