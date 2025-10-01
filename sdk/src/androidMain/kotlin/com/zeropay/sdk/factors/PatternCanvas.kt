package com.zeropay.sdk.factors
package com.zeropay.sdk.factors

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PatternCanvas(onDone: (List<PatternFactor.PatternPoint>) -> Unit) {
    // TODO: Implement pattern lock canvas (like Android lock screen pattern)
    // This should capture touch points, timing, pressure, and gesture dynamics
    // Should support both micro-timing and normalized timing modes
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Pattern Lock Not Implemented",
            color = Color.White,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "This authentication method is not yet available.\nPlease contact support to use a different factor.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
    
    // Note: onDone is intentionally NOT called - user cannot proceed
}
