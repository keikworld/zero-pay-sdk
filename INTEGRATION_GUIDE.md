# ZeroPay SDK Integration Guide

**Version:** 1.0.0
**Last Updated:** 2025-10-18

This guide provides step-by-step instructions for integrating the ZeroPay SDK into your Android application and connecting it to the backend server.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Backend Setup](#backend-setup)
3. [Android SDK Integration](#android-sdk-integration)
4. [Enrollment Flow](#enrollment-flow)
5. [Verification Flow](#verification-flow)
6. [Blockchain Integration](#blockchain-integration)
7. [Testing](#testing)
8. [Production Checklist](#production-checklist)
9. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### System Requirements

- **Backend:**
  - Node.js 18+
  - PostgreSQL 14+
  - Redis 7+ with TLS
  - (Optional) AWS KMS for key wrapping

- **Android:**
  - Android Studio Hedgehog (2023.1.1+)
  - JDK 17+
  - Minimum SDK: 26 (Android 8.0)
  - Target SDK: 34 (Android 14)

### Knowledge Requirements

- Kotlin programming
- Coroutines and Flow
- Jetpack Compose (for UI)
- REST API concepts
- Basic cryptography concepts

---

## Backend Setup

### Step 1: Install Dependencies

```bash
cd backend
npm install
```

### Step 2: Configure Environment

Copy the example environment file:

```bash
cp .env.example .env
```

Edit `.env` and set the following **required** variables:

```bash
# Server
NODE_ENV=development
PORT=3000

# Redis
REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_USERNAME=zeropay-backend
REDIS_PASSWORD=$(openssl rand -base64 48)  # Generate secure password

# Database
DATABASE_URL=postgresql://zeropay_app:YOUR_PASSWORD@localhost:5432/zeropay

# Encryption
ENCRYPTION_KEY=$(openssl rand -hex 32)  # Generate 32-byte key

# AWS KMS (Optional - use mock for development)
AWS_KMS_KEY_ID=your-kms-key-id
AWS_REGION=us-east-1

# Blockchain
SOLANA_RPC_URL=https://api.devnet.solana.com
BLOCKCHAIN_NETWORK=devnet
```

### Step 3: Set Up Database

```bash
# Create PostgreSQL database
createdb zeropay

# Run schema setup
npm run db:setup
```

**Expected output:**
```
✅ Connected to database
✅ Schema created successfully
📋 Created tables:
   ✓ wrapped_keys
   ✓ blockchain_wallets
   ✓ audit_log
   ✓ key_rotation_history
   ✓ gdpr_requests
```

### Step 4: Generate TLS Certificates for Redis

```bash
npm run generate:certs
```

### Step 5: Start Redis

```bash
npm run redis:start
```

### Step 6: Start Backend Server

```bash
# Development mode (auto-reload)
npm run dev

# Production mode
npm start
```

**Expected output:**
```
✅ Redis connected (TLS 1.3)
✅ Database connected
🚀 ZeroPay Backend listening on port 3000
```

---

## Android SDK Integration

### Step 1: Add Dependencies

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    // ZeroPay SDK
    implementation(project(":sdk"))

    // Or from Maven (future)
    // implementation("com.zeropay:sdk:1.0.0")
}
```

### Step 2: Configure API Connection

Create a configuration file `ApiConfiguration.kt`:

```kotlin
package com.yourapp.config

import com.zeropay.sdk.api.ApiConfig

object ApiConfiguration {

    /**
     * Development configuration
     * Use 10.0.2.2 for Android emulator (maps to host's localhost)
     */
    val development = ApiConfig.development(
        baseUrl = "http://10.0.2.2:3000",
        enableLogging = true
    )

    /**
     * Production configuration
     * IMPORTANT: Add your certificate pins!
     */
    val production = ApiConfig.production(
        baseUrl = "https://api.zeropay.com",
        certificatePins = listOf(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", // Replace with actual pin
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="  // Backup pin
        )
    )

    /**
     * Get configuration based on build type
     */
    fun getConfig(): ApiConfig {
        return if (BuildConfig.DEBUG) {
            development
        } else {
            production
        }
    }
}
```

### Step 3: Initialize SDK

In your `Application` class:

```kotlin
package com.yourapp

import android.app.Application
import com.zeropay.sdk.ZeroPay
import com.zeropay.sdk.api.ApiConfig
import com.zeropay.sdk.api.EnrollmentClient
import com.zeropay.sdk.api.VerificationClient
import com.zeropay.sdk.api.BlockchainClient
import com.zeropay.sdk.network.OkHttpClientImpl

class MyApplication : Application() {

    companion object {
        lateinit var enrollmentClient: EnrollmentClient
            private set

        lateinit var verificationClient: VerificationClient
            private set

        lateinit var blockchainClient: BlockchainClient
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Get API configuration
        val apiConfig = ApiConfiguration.getConfig()

        // Validate configuration
        apiConfig.validate()

        // Initialize HTTP client
        val httpClient = OkHttpClientImpl(apiConfig)

        // Initialize API clients
        enrollmentClient = EnrollmentClient(httpClient, apiConfig)
        verificationClient = VerificationClient(httpClient, apiConfig)
        blockchainClient = BlockchainClient(httpClient, apiConfig)

        // Initialize ZeroPay SDK
        ZeroPay.initialize(
            context = this,
            config = ZeroPay.Config(
                prewarmCache = true,
                enableDebugLogging = BuildConfig.DEBUG
            )
        )
    }
}
```

---

## Enrollment Flow

### Overview

1. User selects 6+ authentication factors
2. User completes each factor (generates SHA-256 digest locally)
3. SDK sends digests to backend (never raw factor data)
4. Backend encrypts and stores digests
5. User receives UUID (stored only on device)

### Implementation

```kotlin
package com.yourapp.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.MyApplication
import com.zeropay.sdk.Factor
import com.zeropay.sdk.models.api.FactorDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class EnrollmentViewModel : ViewModel() {

    private val _enrollmentState = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val enrollmentState: StateFlow<EnrollmentState> = _enrollmentState

    sealed class EnrollmentState {
        object Idle : EnrollmentState()
        object Loading : EnrollmentState()
        data class Success(val uuid: String, val alias: String) : EnrollmentState()
        data class Error(val message: String) : EnrollmentState()
    }

    /**
     * Enroll user with factor digests
     */
    fun enrollUser(
        selectedFactors: List<Factor>,
        factorDigests: Map<Factor, ByteArray>
    ) {
        viewModelScope.launch {
            try {
                _enrollmentState.value = EnrollmentState.Loading

                // Generate UUID (v4)
                val userUuid = UUID.randomUUID().toString()

                // Convert digests to API format
                val apiDigests = selectedFactors.mapNotNull { factor ->
                    factorDigests[factor]?.let { digest ->
                        FactorDigest(
                            type = factor.name,
                            digest = digest.joinToString("") { "%02x".format(it) }
                        )
                    }
                }

                // Validate minimum factors (PSD3 SCA)
                if (apiDigests.size < 6) {
                    _enrollmentState.value = EnrollmentState.Error(
                        "Minimum 6 factors required for PSD3 SCA compliance"
                    )
                    return@launch
                }

                // Call enrollment API
                val result = MyApplication.enrollmentClient.enroll(
                    userUuid = userUuid,
                    factors = apiDigests,
                    deviceId = getDeviceId(),
                    ttlSeconds = 86400, // 24 hours
                    gdprConsent = true
                )

                result.fold(
                    onSuccess = { response ->
                        // Save UUID to secure storage
                        saveUuidToKeyStore(userUuid)

                        _enrollmentState.value = EnrollmentState.Success(
                            uuid = response.user_uuid,
                            alias = response.alias
                        )
                    },
                    onFailure = { error ->
                        _enrollmentState.value = EnrollmentState.Error(
                            error.message ?: "Enrollment failed"
                        )
                    }
                )

            } catch (e: Exception) {
                _enrollmentState.value = EnrollmentState.Error(
                    e.message ?: "Unknown error"
                )
            } finally {
                // Wipe digests from memory
                factorDigests.values.forEach { it.fill(0) }
            }
        }
    }

    private fun getDeviceId(): String {
        // Generate anonymized device ID
        // DO NOT use IMEI or other PII
        return UUID.randomUUID().toString()
    }

    private fun saveUuidToKeyStore(uuid: String) {
        // Save to Android KeyStore
        // Implementation depends on your secure storage library
    }
}
```

### UI Example (Jetpack Compose)

```kotlin
@Composable
fun EnrollmentScreen(
    viewModel: EnrollmentViewModel = viewModel()
) {
    val state by viewModel.enrollmentState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("ZeroPay Enrollment", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is EnrollmentViewModel.EnrollmentState.Idle -> {
                Button(onClick = { /* Start enrollment */ }) {
                    Text("Start Enrollment")
                }
            }

            is EnrollmentViewModel.EnrollmentState.Loading -> {
                CircularProgressIndicator()
                Text("Enrolling...")
            }

            is EnrollmentViewModel.EnrollmentState.Success -> {
                val successState = state as EnrollmentViewModel.EnrollmentState.Success
                Text("✅ Enrollment Successful!", color = Color.Green)
                Text("UUID: ${successState.uuid}")
                Text("Alias: ${successState.alias}")
            }

            is EnrollmentViewModel.EnrollmentState.Error -> {
                val errorState = state as EnrollmentViewModel.EnrollmentState.Error
                Text("❌ Error: ${errorState.message}", color = Color.Red)
            }
        }
    }
}
```

---

## Verification Flow

### Overview

1. Merchant creates verification session
2. User completes required factors
3. SDK sends factor digests to backend
4. Backend compares digests (constant-time)
5. Returns boolean result (never reveals which factor failed)

### Implementation

```kotlin
package com.yourapp.merchant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourapp.MyApplication
import com.zeropay.sdk.models.api.FactorDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VerificationViewModel : ViewModel() {

    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState

    sealed class VerificationState {
        object Idle : VerificationState()
        object CreatingSession : VerificationState()
        data class SessionCreated(val sessionId: String, val requiredFactors: List<String>) : VerificationState()
        object Verifying : VerificationState()
        data class Verified(val confidence: Double, val zkProof: String?) : VerificationState()
        data class Failed(val message: String) : VerificationState()
        data class Error(val message: String) : VerificationState()
    }

    /**
     * Step 1: Create verification session
     */
    fun createSession(
        userUuid: String,
        amount: Double,
        currency: String
    ) {
        viewModelScope.launch {
            try {
                _verificationState.value = VerificationState.CreatingSession

                val result = MyApplication.verificationClient.createSession(
                    userUuid = userUuid,
                    amount = amount,
                    currency = currency,
                    transactionId = generateTransactionId()
                )

                result.fold(
                    onSuccess = { session ->
                        _verificationState.value = VerificationState.SessionCreated(
                            sessionId = session.session_id,
                            requiredFactors = session.required_factors
                        )
                    },
                    onFailure = { error ->
                        _verificationState.value = VerificationState.Error(
                            error.message ?: "Session creation failed"
                        )
                    }
                )

            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(
                    e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Step 2: Verify with factor digests
     */
    fun verify(
        sessionId: String,
        userUuid: String,
        factorDigests: List<FactorDigest>
    ) {
        viewModelScope.launch {
            try {
                _verificationState.value = VerificationState.Verifying

                val result = MyApplication.verificationClient.verify(
                    sessionId = sessionId,
                    userUuid = userUuid,
                    factors = factorDigests,
                    deviceId = getDeviceId()
                )

                result.fold(
                    onSuccess = { response ->
                        if (response.verified) {
                            _verificationState.value = VerificationState.Verified(
                                confidence = response.confidence_score ?: 1.0,
                                zkProof = response.zk_proof
                            )
                        } else {
                            _verificationState.value = VerificationState.Failed(
                                "Verification failed - factors do not match"
                            )
                        }
                    },
                    onFailure = { error ->
                        _verificationState.value = VerificationState.Error(
                            error.message ?: "Verification failed"
                        )
                    }
                )

            } catch (e: Exception) {
                _verificationState.value = VerificationState.Error(
                    e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun generateTransactionId(): String {
        return "TXN-${System.currentTimeMillis()}"
    }

    private fun getDeviceId(): String {
        return UUID.randomUUID().toString()
    }
}
```

---

## Blockchain Integration

### Link Phantom Wallet

```kotlin
viewModelScope.launch {
    val result = MyApplication.blockchainClient.linkWallet(
        userUuid = userUuid,
        walletAddress = phantomWalletAddress,
        blockchainNetwork = "solana",
        walletType = "phantom",
        verificationSignature = signature
    )

    result.fold(
        onSuccess = { response ->
            println("Wallet linked: ${response.wallet_address}")
            println("Verified: ${response.is_verified}")
        },
        onFailure = { error ->
            println("Linking failed: ${error.message}")
        }
    )
}
```

### Check Wallet Balance

```kotlin
viewModelScope.launch {
    val result = MyApplication.blockchainClient.getWalletBalance(
        walletAddress = "7x...abc",
        blockchainNetwork = "solana"
    )

    result.fold(
        onSuccess = { balance ->
            println("Balance: ${balance.balance} ${balance.currency}")
        },
        onFailure = { error ->
            println("Balance check failed: ${error.message}")
        }
    )
}
```

---

## Testing

### Backend Health Check

```bash
curl http://localhost:3000/health
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": "2025-10-18T12:00:00.000Z",
  "redis": "connected",
  "database": "connected"
}
```

### Test Enrollment Endpoint

```bash
curl -X POST http://localhost:3000/api/v1/enrollment/store \
  -H "Content-Type: application/json" \
  -d '{
    "user_uuid": "550e8400-e29b-41d4-a716-446655440000",
    "factors": [
      {
        "type": "PIN",
        "digest": "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3"
      }
    ],
    "nonce": "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
    "timestamp": "2025-10-18T12:00:00.000Z",
    "gdpr_consent": true
  }'
```

### Run End-to-End Tests

```bash
# Backend tests
cd backend
npm test

# Android tests
cd ..
./gradlew test
./gradlew connectedAndroidTest
```

---

## Production Checklist

### Security

- [ ] Generate strong Redis password (`openssl rand -base64 48`)
- [ ] Generate encryption key (`openssl rand -hex 32`)
- [ ] Configure AWS KMS for key wrapping
- [ ] Enable certificate pinning in Android
- [ ] Add certificate pins to production config
- [ ] Set `NODE_ENV=production`
- [ ] Enable TLS for Redis
- [ ] Enable HTTPS for backend (use Let's Encrypt)
- [ ] Set strong PostgreSQL password

### Configuration

- [ ] Update `baseUrl` to production domain
- [ ] Configure CORS allowed origins
- [ ] Set appropriate rate limits
- [ ] Configure monitoring (Sentry, etc.)
- [ ] Set up backup strategy for database
- [ ] Configure log retention
- [ ] Test GDPR endpoints (deletion, export)

### Testing

- [ ] Test enrollment flow end-to-end
- [ ] Test verification flow end-to-end
- [ ] Test blockchain operations
- [ ] Load test API endpoints
- [ ] Test rate limiting
- [ ] Test error scenarios
- [ ] Verify constant-time comparison
- [ ] Test GDPR compliance features

---

## Troubleshooting

### "Failed to connect to Redis"

**Solution:**
```bash
# Check Redis is running
redis-cli -p 6380 ping

# If not, start Redis
npm run redis:start

# Check TLS certificates exist
ls backend/redis/tls/
```

### "Database connection failed"

**Solution:**
```bash
# Check PostgreSQL is running
pg_isready

# Check database exists
psql -l | grep zeropay

# Run schema setup again
npm run db:setup
```

### "Invalid UUID format"

**Solution:** Ensure UUID is version 4 format:
```kotlin
val uuid = UUID.randomUUID().toString() // Correct
// Format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
```

### "Certificate pinning failed"

**Solution:** Generate certificate pins:
```bash
openssl s_client -connect api.zeropay.com:443 \
  | openssl x509 -pubkey -noout \
  | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary \
  | base64
```

### "Minimum 6 factors required"

**Solution:** PSD3 SCA compliance requires at least 6 factors from 2+ categories.

---

## Support

For additional help:

- **Documentation:** [README.md](./README.md)
- **Code Examples:** [DEMO_SCRIPT.md](./DEMO_SCRIPT.md)
- **Architecture:** [CLAUDE.md](./CLAUDE.md)
- **Issues:** [GitHub Issues](https://github.com/your-org/zeropay-android/issues)

---

**Last Updated:** 2025-10-18
**Version:** 1.0.0
