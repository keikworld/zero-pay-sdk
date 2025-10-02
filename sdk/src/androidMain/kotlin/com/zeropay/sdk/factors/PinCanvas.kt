(
                            text = number.toString(),
                            enabled = pin.length < TARGET_PIN_LENGTH && !isProcessing,
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                                    if (pin.length < TARGET_PIN_LENGTH) {
                                        pin += number
                                    }
                                    lastClickTime = now
                                }
                            }
                        )
                    }
                }
            }
            
            // Bottom row with 0
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Empty space
                Box(modifier = Modifier.size(80.dp))
                
                PinButton(
                    text = "0",
                    enabled = pin.length < TARGET_PIN_LENGTH && !isProcessing,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                            if (pin.length < TARGET_PIN_LENGTH) {
                                pin += "0"
                            }
                            lastClickTime = now
                        }
                    }
                )
                
                // Backspace
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.DarkGray)
                        .clickable(enabled = pin.isNotEmpty() && !isProcessing) {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime >= CLICK_THROTTLE_MS) {
                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                }
                                lastClickTime = now
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "⌫",
                        color = if (pin.isNotEmpty() && !isProcessing) 
                            Color.White 
                        else 
                            Color.White.copy(alpha = 0.3f),
                        fontSize = 24.sp
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pin.isNotEmpty() && !isProcessing) {
                TextButton(onClick = { 
                    pin = "" 
                    lastClickTime = 0L
                }) {
                    Text("Clear")
                }
            }
            
            if (pin.length == TARGET_PIN_LENGTH && !isProcessing) {
                Button(
                    onClick = {
                        isProcessing = true
                        try {
                            val digest = PinFactor.digest(pin)
                            onDone(digest)
                            
                            // Security: Zero out PIN from memory
                            pin = ""
                        } catch (e: Exception) {
                            // Log error without exposing PIN
                            println("PIN processing error: ${e.javaClass.simpleName}")
                            pin = ""
                        } finally {
                            isProcessing = false
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text(if (isProcessing) "Processing..." else "Confirm")
                }
            }
        }
    }
    
    // Auto-clear PIN after 30 seconds of inactivity (security)
    LaunchedEffect(pin) {
        if (pin.isNotEmpty()) {
            kotlinx.coroutines.delay(30000)
            if (pin.length < TARGET_PIN_LENGTH) {
                pin = ""
            }
        }
    }
}

@Composable
private fun PinButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.DarkGray else Color.DarkGray.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
