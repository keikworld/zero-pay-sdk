// Path: merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/ColourVerificationCanvas.kt

package com.zeropay.merchant.ui.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.sdk.factors.ColourFactor
import kotlinx.coroutines.launch

/**
 * Colour Verification Canvas - MERCHANT POS VERSION
 * 
 * Quick color sequence authentication.
 * 
 * @param onSubmit Callback with digest
 * @param onTimeout Callback when time expires
 * @param remainingSeconds Remaining time
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
@Composable
fun ColourVerificationCanvas(
    onSubmit: (ByteArray) -> Unit,
    onTimeout: () -> Unit,
    remainingSeconds: Int,
    modifier: Modifier = Modifier
) {
    var selectedColors by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val minColors = 3
    val maxColors = 8
    
    suspend fun handleSubmit() {
        if (selectedColors.size < minColors) {
            errorMessage = "Select at least $minColors colors"
            return
        }
        
        isProcessing = true
        
        try {
            val result = ColourFactor.processColourSequence(selectedColors)
            if (result.isSuccess) {
                val digest = result.getOrNull()!!
                selectedColors = emptyList()
                onSubmit(digest)
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Invalid sequence"
                isProcessing = false
            }
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isProcessing = false
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "🎨 Select Colors",
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
            
            // Selected colors display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF16213E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Selected: ${selectedColors.size}/$maxColors",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (selectedColors.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedColors.forEach { colorIndex ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Factor.COLOUR_PALETTE[colorIndex])
                                        .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }
            
            // Color grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(Factor.COLOUR_PALETTE) { index, color ->
                    val isSelected = selectedColors.contains(index)
                    val selectionOrder = if (isSelected) selectedColors.indexOf(index) + 1 else null
                    
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .then(
                                if (isSelected) {
                                    Modifier.border(4.dp, Color.White, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .clickable {
                                if (isSelected) {
                                    selectedColors = selectedColors.filter { it != index }
                                } else if (selectedColors.size < maxColors) {
                                    selectedColors = selectedColors + index
                                    errorMessage = null
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectionOrder != null) {
                            Text(
                                text = selectionOrder.toString(),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Error message
            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF6B6B)
                    )
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedColors = emptyList()
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && selectedColors.isNotEmpty(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            handleSubmit()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedColors.size >= minColors && !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Submit")
                    }
                }
            }
        }
    }
}
