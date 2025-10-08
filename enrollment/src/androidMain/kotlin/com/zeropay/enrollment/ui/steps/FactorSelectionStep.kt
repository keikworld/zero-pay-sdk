// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/steps/FactorSelectionStep.kt

package com.zeropay.enrollment.ui.steps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.sdk.Factor
import com.zeropay.sdk.factors.FactorRegistry

/**
 * Factor Selection Step - PRODUCTION VERSION
 * 
 * Features:
 * - Grid display of 13 available factors
 * - Visual availability checking
 * - Category grouping
 * - Selection validation (min 6 factors)
 * - Category diversity check (PSD3 SCA)
 * 
 * @param selectedFactors Currently selected factors
 * @param onFactorsSelected Callback when selection changes
 * @param onContinue Callback to proceed to next step
 * @param onCancel Callback to cancel enrollment
 * 
 * @version 2.0.0
 * @date 2025-10-08
 */
@Composable
fun FactorSelectionStep(
    selectedFactors: List<Factor>,
    onFactorsSelected: (List<Factor>) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Available factors (check device support)
    val availableFactors = remember {
        EnrollmentConfig.AVAILABLE_FACTORS.filter { factor ->
            FactorRegistry.isAvailable(context, factor)
        }
    }
    
    // Group factors by category
    val factorsByCategory = remember(availableFactors) {
        availableFactors.groupBy { factor ->
            EnrollmentConfig.FACTOR_CATEGORIES[factor] ?: EnrollmentConfig.FactorCategory.KNOWLEDGE
        }
    }
    
    // Validation
    val validationResult = remember(selectedFactors) {
        EnrollmentConfig.validateFactorSelection(selectedFactors)
    }
    
    val canContinue = validationResult is EnrollmentConfig.ValidationResult.Valid
    
    // ==================== UI LAYOUT ====================
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F1E))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ========== HEADER ==========
            
            Text(
                text = "Select ${EnrollmentConfig.MIN_FACTORS}+ Authentication Factors",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            // ========== SELECTION SUMMARY ==========
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Selected: ${selectedFactors.size}/${EnrollmentConfig.MAX_FACTORS}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (selectedFactors.isNotEmpty()) {
                            TextButton(onClick = { onFactorsSelected(emptyList()) }) {
                                Text("Clear All", color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                    
                    LinearProgressIndicator(
                        progress = (selectedFactors.size.toFloat() / EnrollmentConfig.MIN_FACTORS).coerceAtMost(1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = if (canContinue) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    // Validation message
                    when (validationResult) {
                        is EnrollmentConfig.ValidationResult.Valid -> {
                            Text(
                                text = "✓ Ready to continue",
                                color = Color(0xFF4CAF50),
                                fontSize = 14.sp
                            )
                        }
                        is EnrollmentConfig.ValidationResult.Invalid -> {
                            Text(
                                text = validationResult.message,
                                color = Color(0xFFFF9800),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            
            // ========== CATEGORY INFO ==========
            
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
                        text = "📋 Factor Categories (PSD3 SCA Compliant)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    EnrollmentConfig.FactorCategory.values().forEach { category ->
                        val categoryFactors = factorsByCategory[category] ?: emptyList()
                        val selectedInCategory = selectedFactors.count { 
                            EnrollmentConfig.FACTOR_CATEGORIES[it] == category 
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = EnrollmentConfig.getCategoryDisplayName(category),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "$selectedInCategory/${categoryFactors.size}",
                                color = if (selectedInCategory > 0) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // ========== FACTOR GRID ==========
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(availableFactors) { factor ->
                    val isSelected = selectedFactors.contains(factor)
                    val category = EnrollmentConfig.FACTOR_CATEGORIES[factor]
                    
                    FactorCard(
                        factor = factor,
                        category = category,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onFactorsSelected(selectedFactors - factor)
                            } else {
                                if (selectedFactors.size < EnrollmentConfig.MAX_FACTORS) {
                                    onFactorsSelected(selectedFactors + factor)
                                }
                            }
                        }
                    )
                }
            }
            
            // ========== ACTION BUTTONS ==========
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
                
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                    enabled = canContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.Gray
                    )
                ) {
                    Text("Continue →")
                }
            }
        }
    }
}

/**
 * Factor Card Component
 */
@Composable
private fun FactorCard(
    factor: Factor,
    category: EnrollmentConfig.FactorCategory?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = when (category) {
        EnrollmentConfig.FactorCategory.KNOWLEDGE -> Color(0xFF2196F3)
        EnrollmentConfig.FactorCategory.INHERENCE -> Color(0xFF9C27B0)
        EnrollmentConfig.FactorCategory.POSSESSION -> Color(0xFFFF9800)
        null -> Color.Gray
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E3A1E) else Color(0xFF1A1A2E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Category badge
            if (category != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = category.name,
                        color = categoryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Factor icon
            Text(
                text = EnrollmentConfig.getFactorIcon(factor),
                fontSize = 48.sp
            )
            
            // Factor name
            Text(
                text = EnrollmentConfig.getFactorDisplayName(factor),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            
            // Selection indicator
            if (isSelected) {
                Text(
                    text = "✓ Selected",
                    color = Color(0xFF4CAF50),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
