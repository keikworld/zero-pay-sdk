package com.zeropay.merchant

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawLine
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Canvas
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when (step) {
                "pattern" -> PatternCanvas { strokeOffsets ->
                    // NOTE: PatternFactor.isValidStroke and digest must now accept List<Offset>
                    if (PatternFactor.isValidStroke(strokeOffsets)) {
                        proofs.add(PatternFactor.digest(strokeOffsets))
                        step = "colour"
                    } else {
                        // Optionally show message for invalid pattern
                    }
                }
                "colour" -> ColourMatrix(onSelected = { indices ->
                    proofs.add(ColourFactor.digest(indices))
                    if (RateLimiter.check(uidHash) == RateLimiter.RateResult.OK) {
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
    fun PatternCanvas(onDone: (List<Offset>) -> Unit) {
        var pathPoints by remember { mutableStateOf(listOf<Offset>()) }
        var isDrawing by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDrawing = true
                            pathPoints = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            pathPoints = pathPoints + change.position
                        },
                        onDragEnd = {
                            isDrawing = false
                            if (pathPoints.size > 2) { // You can change min length as needed
                                onDone(pathPoints)
                            }
                            pathPoints = emptyList()
                        },
                        onDragCancel = {
                            isDrawing = false
                            pathPoints = emptyList()
                        }
                    )
                }
        ) {
            androidx.compose.ui.graphics.Canvas(modifier = Modifier.fillMaxSize()) {
                if (pathPoints.size > 1) {
                    for (i in 0 until pathPoints.size - 1) {
                        drawLine(
                            color = Color.White,
                            start = pathPoints[i],
                            end = pathPoints[i + 1],
                            strokeWidth = 8f
                        )
                    }
                }
            }
            Text("Draw your pattern", color = Color.White, modifier = Modifier.padding(24.dp))
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
}
