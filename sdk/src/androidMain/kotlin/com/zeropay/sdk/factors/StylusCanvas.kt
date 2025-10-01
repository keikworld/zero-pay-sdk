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
fun StylusCanvas(onDone: (List<StylusFactor.StylusPoint>) -> Unit) {
    // TODO: Implement stylus drawing canvas with pressure tracking
    // This should capture stylus input including position, pressure, tilt,
    // and orientation as biometric signatures
    
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
            "Stylus Drawing Not Implemented",
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
