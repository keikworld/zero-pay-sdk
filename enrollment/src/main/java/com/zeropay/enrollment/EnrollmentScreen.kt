// File: enrollment/src/main/java/com/zeropay/enrollment/EnrollmentScreen.kt
package com.zeropay.enrollment

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zeropay.sdk.ZeroPay
import com.zeropay.sdk.Factor
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.crypto.CryptoUtils
import com.zeropay.sdk.storage.KeyStoreManager
import com.zeropay.sdk.network.SecureApiClient
import kotlinx.coroutines.launch

@Composable
fun EnrollmentScreen(
    userUuid: String,
    keyStoreManager: KeyStoreManager,
    apiClient: SecureApiClient
) {
    var enrollmentState by remember { mutableStateOf(EnrollmentState.SELECTING_FACTORS) }
    var selectedFactors by remember { mutableStateOf<List<Factor>>(emptyList()) }
    var currentFactorIndex by remember { mutableStateOf(0) }
    var collectedDigests by remember { mutableStateOf<MutableMap<Factor, ByteArray>>(mutableMapOf()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val cacheClient = remember { RedisCacheClient(apiClient) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "ZeroPay Enrollment",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "User ID: ${userUuid.take(8)}...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Main content based on state
        when (enrollmentState) {
            EnrollmentState.SELECTING_FACTORS -> {
                FactorSelectionScreen(
                    availableFactors = ZeroPay.availableFactors(context),
                    selectedFactors = selectedFactors,
                    onFactorsSelected = { factors ->
                        if (factors.size < 2) {
                            errorMessage = "Please select at least 2 factors for security"
                        } else {
                            selectedFactors = factors
                            enrollmentState = EnrollmentState.COLLECTING_FACTORS
                            errorMessage = null
                        }
                    }
                )
            }
            
            EnrollmentState.COLLECTING_FACTORS -> {
                val currentFactor = selectedFactors[currentFactorIndex]
                
                Text(
                    text = "Factor ${currentFactorIndex + 1} of ${selectedFactors.size}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show factor canvas
                ZeroPay.canvasForFactor(
                    factor = currentFactor,
                    onDone = { digest ->
                        // Store digest
                        collectedDigests[currentFactor] = digest
                        
                        // Save locally
                        keyStoreManager.storeEnrollment(userUuid, currentFactor, digest)
                        
                        // Move to next factor or finish
                        if (currentFactorIndex < selectedFactors.size - 1) {
                            currentFactorIndex++
                        } else {
                            // All factors collected, send to cache
                            scope.launch {
                                sendToCache(
                                    cacheClient = cacheClient,
                                    userUuid = userUuid,
                                    digests = collectedDigests,
                                    onSuccess = {
                                        enrollmentState = EnrollmentState.SUCCESS
                                    },
                                    onError = { error ->
                                        errorMessage = error
                                        enrollmentState = EnrollmentState.ERROR
                                    }
                                )
                            }
                            enrollmentState = EnrollmentState.SENDING_TO_CACHE
                        }
                    },
                    onError = { error ->
                        errorMessage = error.message
                    }
                )
            }
            
            EnrollmentState.SENDING_TO_CACHE -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Sending enrollment data...")
                }
            }
            
            EnrollmentState.SUCCESS -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓ Enrollment Successful!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your factors have been securely enrolled.")
                    Text("Data will expire in 24 hours.")
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(onClick = { /* Navigate away */ }) {
                        Text("Done")
                    }
                }
            }
            
            EnrollmentState.ERROR -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✗ Enrollment Failed",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(errorMessage ?: "Unknown error occurred")
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(onClick = {
                        enrollmentState = EnrollmentState.SELECTING_FACTORS
                        errorMessage = null
                        collectedDigests.clear()
                        currentFactorIndex = 0
                    }) {
                        Text("Try Again")
                    }
                }
            }
        }
        
        // Error display
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun FactorSelectionScreen(
    availableFactors: List<Factor>,
    selectedFactors: List<Factor>,
    onFactorsSelected: (List<Factor>) -> Unit
) {
    var localSelection by remember { mutableStateOf(selectedFactors) }
    
    Column {
        Text(
            text = "Select Authentication Factors",
            style = MaterialTheme.typography.titleLarge
        )
        
        Text(
            text = "Choose at least 2 factors for secure authentication",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(availableFactors) { factor ->
                FactorSelectionItem(
                    factor = factor,
                    isSelected = localSelection.contains(factor),
                    onToggle = {
                        localSelection = if (localSelection.contains(factor)) {
                            localSelection - factor
                        } else {
                            localSelection + factor
                        }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { onFactorsSelected(localSelection) },
            modifier = Modifier.fillMaxWidth(),
            enabled = localSelection.size >= 2
        ) {
            Text("Continue with ${localSelection.size} factors")
        }
    }
}

@Composable
fun FactorSelectionItem(
    factor: Factor,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = factor.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = factor.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Security badge
            Badge {
                Text(factor.securityLevel.name)
            }
        }
    }
}

suspend fun sendToCache(
    cacheClient: RedisCacheClient,
    userUuid: String,
    digests: Map<Factor, ByteArray>,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Get device ID
    val deviceId = CryptoUtils.secureRandomBytes(16).joinToString("") { "%02x".format(it) }
    
    val result = cacheClient.storeEnrollment(
        userUuid = userUuid,
        factorDigests = digests,
        deviceId = deviceId
    )
    
    if (result.isSuccess) {
        onSuccess()
    } else {
        onError(result.exceptionOrNull()?.message ?: "Failed to store enrollment")
    }
}

enum class EnrollmentState {
    SELECTING_FACTORS,
    COLLECTING_FACTORS,
    SENDING_TO_CACHE,
    SUCCESS,
    ERROR
}
