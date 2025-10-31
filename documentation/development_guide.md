# 📚 ZeroPay SDK - Comprehensive Development Guide

## Table of Contents
1. [Code Style](#code-style)
2. [Architecture](#architecture)
3. [Security](#security)
4. [Error Handling](#error-handling)
5. [Testing](#testing)
6. [Performance](#performance)
7. [GDPR & Privacy](#gdpr--privacy)
8. [Documentation](#documentation)

---

## 1. Code Style

### Kotlin Style Guide

#### Naming Conventions
```kotlin
// Classes: PascalCase
class FactorRegistry

// Functions: camelCase
fun availableFactors(): List<Factor>

// Constants: SCREAMING_SNAKE_CASE
const val MAX_RETRY_ATTEMPTS = 3

// Private members: camelCase with underscore prefix (optional)
private val _state = MutableStateFlow<State>(State.Idle)

// Package names: lowercase
package com.zeropay.sdk.factors
```

#### File Organization
```kotlin
// 1. Package declaration
package com.zeropay.sdk.factors

// 2. Imports (grouped and sorted)
import android.content.Context
import androidx.compose.runtime.*
import com.zeropay.sdk.Factor
import java.security.MessageDigest

// 3. File-level documentation
/**
 * Factor Registry
 * 
 * Manages available authentication factors for the device.
 */

// 4. Constants
private const val TAG = "FactorRegistry"

// 5. Main class/object
object FactorRegistry {
    // Implementation
}

// 6. Extension functions (if any)
private fun Context.hasFeature(feature: String): Boolean {
    return packageManager.hasSystemFeature(feature)
}
```

#### Function Length
```kotlin
// ✅ GOOD: Short, focused functions (<20 lines)
fun validateDigest(digest: ByteArray): Boolean {
    if (digest.size != 32) return false
    if (digest.all { it == 0.toByte() }) return false
    return true
}

// ❌ BAD: Long functions (>50 lines)
// Split into smaller, focused functions
```

#### Comments
```kotlin
// Use KDoc for public APIs
/**
 * Generates a cryptographic digest from factor data.
 *
 * @param data Raw factor data
 * @return SHA-256 digest (32 bytes)
 * @throws IllegalArgumentException if data is empty
 */
fun generateDigest(data: ByteArray): ByteArray

// Use inline comments sparingly
// Only when code intent isn't obvious
val hash = data.sha256() // Irreversible transformation

// ❌ BAD: Obvious comments
val x = 5 // Set x to 5
```

---

## 2. Architecture

### Layered Architecture

```
┌─────────────────────────────────────┐
│   Presentation Layer (UI)           │
│   - Compose UI                      │
│   - ViewModels (if needed)          │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│   Business Logic Layer              │
│   - Factor management               │
│   - Authentication flow             │
│   - Rate limiting                   │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│   Security Layer                    │
│   - Cryptography                    │
│   - Anti-tampering                  │
│   - zkSNARK preparation             │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│   Storage Layer                     │
│   - KeyStore                        │
│   - EncryptedSharedPreferences      │
└────────────┬────────────────────────┘
             │
┌────────────▼────────────────────────┐
│   Network Layer                     │
│   - API clients                     │
│   - Certificate pinning             │
└─────────────────────────────────────┘
```

### Module Structure

```
zeropay-android/
├── sdk/                          # Core SDK (Kotlin Multiplatform)
│   ├── commonMain/              # Platform-agnostic code
│   │   ├── Factor.kt            # 15 factor enum with metadata (923 lines)
│   │   ├── RateLimiter.kt       # Multi-layer rate limiting
│   │   ├── crypto/              # SHA-256, PBKDF2, constant-time ops
│   │   │   ├── CryptoUtils.kt
│   │   │   ├── ConstantTime.kt
│   │   │   ├── KeyDerivation.kt
│   │   │   └── DoubleLayerEncryption.kt
│   │   ├── factors/             # Factor implementations (15 total)
│   │   │   ├── processors/      # PinProcessor, ColorProcessor, etc.
│   │   │   └── validation/      # Input validation logic
│   │   ├── security/            # Security primitives
│   │   │   ├── SecureMemory.kt
│   │   │   ├── SecurityPolicy.kt
│   │   │   └── AntiTampering.kt
│   │   ├── blockchain/          # Solana integration only
│   │   │   ├── PhantomWalletProvider.kt
│   │   │   ├── SolanaClient.kt
│   │   │   ├── WalletLinkingManager.kt
│   │   │   └── SolanaPayUrlGenerator.kt
│   │   ├── gateway/             # 14 payment gateway abstractions
│   │   │   ├── impl/            # StripeGateway, AdyenGateway, etc.
│   │   │   ├── GatewayProvider.kt
│   │   │   └── PaymentHandoffManager.kt
│   │   ├── network/             # API clients
│   │   │   ├── VerificationClient.kt
│   │   │   └── EnrollmentClient.kt
│   │   └── zksnark/             # ZK-SNARK preparation layer
│   │
│   ├── androidMain/             # Android-specific code
│   │   ├── factors/             # 15 Compose UI canvases
│   │   │   ├── PinCanvas.kt
│   │   │   ├── PatternCanvas.kt
│   │   │   ├── BiometricCanvas.kt
│   │   │   └── ... (12 more)
│   │   ├── storage/             # KeyStore, EncryptedSharedPreferences
│   │   │   ├── KeyStoreManager.kt
│   │   │   └── SecureStorage.kt
│   │   ├── ui/                  # Reusable UI components
│   │   └── AndroidManifest.xml
│   │
│   └── test/                    # Unit tests (38 test files)
│       └── kotlin/com/zeropay/sdk/
│
├── enrollment/                   # Enrollment module
│   ├── commonMain/
│   │   ├── EnrollmentManager.kt # Core orchestration
│   │   └── consent/             # GDPR consent management
│   ├── androidMain/
│   │   ├── ui/                  # 5-step enrollment wizard (~7,375 lines)
│   │   │   ├── steps/           # Consent, Selection, Capture, Payment, Confirm
│   │   │   └── factors/         # 15 factor enrollment canvases
│   │   └── payment/             # 14 payment provider integrations
│   │       └── providers/       # StripeProvider, AdyenProvider, etc.
│   │
├── merchant/                     # Merchant verification module
│   ├── commonMain/
│   │   └── MerchantConfig.kt
│   ├── androidMain/
│   │   ├── verification/        # Currently disabled for testing
│   │   │   ├── VerificationManager.kt.disabled
│   │   │   ├── DigestComparator.kt.disabled
│   │   │   └── ProofGenerator.kt.disabled
│   │   ├── fraud/              # 7-strategy fraud detection (disabled)
│   │   │   ├── FraudDetector.kt.disabled
│   │   │   └── RateLimiter.kt.disabled
│   │   └── ui/                 # 17 verification canvases (disabled)
│   │
└── backend/                     # Node.js API server
    ├── server.js                # Express server with TLS Redis
    ├── routes/                  # REST API endpoints
    │   ├── enrollmentRouter.js  # 5 endpoints
    │   ├── verificationRouter.js # 3 endpoints
    │   ├── blockchainRouter.js  # 7 Solana endpoints
    │   └── adminRouter.js       # Admin API
    ├── crypto/                  # Double-layer encryption
    │   ├── doubleLayerCrypto.js # 18KB implementation
    │   ├── keyDerivation.js     # PBKDF2 100K iterations
    │   ├── kmsProvider.js       # AWS KMS integration
    │   └── memoryWipe.js        # Secure memory wiping
    ├── middleware/              # Rate limiting, session, nonce
    │   ├── rateLimiter.js
    │   ├── sessionManager.js
    │   └── nonceValidator.js
    ├── database/                # PostgreSQL for wrapped keys
    └── services/                # Solana RPC, wallet verification
```

### Dependency Injection (Simple)

```kotlin
// ✅ GOOD: Constructor injection
class AuthenticationManager(
    private val keyStore: KeyStoreManager,
    private val rateLimiter: RateLimiter,
    private val apiClient: SecureApiClient
) {
    // Implementation
}

// ❌ BAD: Hard-coded dependencies
class AuthenticationManager {
    private val keyStore = KeyStoreManager(context) // Tight coupling
}
```

### Separation of Concerns

```kotlin
// ✅ GOOD: Each class has one responsibility

// Data layer
class KeyStoreManager(context: Context) {
    fun store(key: String, value: ByteArray)
    fun retrieve(key: String): ByteArray?
}

// Business logic
class AuthenticationService(private val storage: KeyStoreManager) {
    fun authenticate(factors: List<Factor>): Boolean
}

// UI layer
@Composable
fun AuthenticationScreen(service: AuthenticationService) {
    // Only UI logic
}
```

---

## 3. Security

### Cryptography Rules

#### Always Use These
```kotlin
// ✅ SHA-256 for hashing
val hash = CryptoUtils.sha256(data)

// ✅ HMAC-SHA256 for signing
val signature = CryptoUtils.hmacSha256(key, data)

// ✅ SecureRandom for randomness
val nonce = CryptoUtils.secureRandomBytes(32)

// ✅ Constant-time comparison
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
}
```

#### Never Do These
```kotlin
// ❌ NEVER: MD5 or SHA-1 (broken)
val badHash = MessageDigest.getInstance("MD5") // NO!

// ❌ NEVER: Math.random() for security
val badRandom = (Math.random() * 1000).toInt() // NO!

// ❌ NEVER: Direct equality (timing attack)
if (digest1 == digest2) // NO! Use constantTimeEquals()

// ❌ NEVER: Hardcoded keys/secrets
const val API_KEY = "sk_live_abc123" // NO! Use KeyStore
```

### Memory Security

```kotlin
// ✅ GOOD: Auto-zeroing sensitive data
SecureByteArray(32).use { secureData ->
    // Use secureData
    // Automatically zeroed on close
}

// ✅ GOOD: Clear after use
fun authenticate(pin: String) {
    val digest = hash(pin)
    try {
        // Use digest
    } finally {
        digest.fill(0) // Clear memory
    }
}

// ❌ BAD: Sensitive data lingers
fun authenticate(pin: String) {
    val digest = hash(pin)
    // digest stays in memory!
}
```

### Input Validation

```kotlin
// ✅ GOOD: Validate all inputs
fun digest(pin: String): ByteArray {
    require(pin.length in 4..12) { "PIN must be 4-12 digits" }
    require(pin.all { it.isDigit() }) { "PIN must be numeric" }
    // Process
}

// ❌ BAD: No validation
fun digest(pin: String): ByteArray {
    return sha256(pin.toByteArray()) // Accepts anything!
}
```

---

## 4. Error Handling

### Exception Hierarchy

```kotlin
// Use sealed classes for type-safe errors
sealed class AuthError {
    object NetworkError : AuthError()
    data class ValidationError(val field: String) : AuthError()
    data class RateLimitError(val retryAfter: Long) : AuthError()
}

// Return Result type for recoverable errors
fun authenticate(): Result<ByteArray> {
    return try {
        val digest = performAuth()
        Result.success(digest)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### Error Messages

```kotlin
// ✅ GOOD: User-friendly messages
throw FactorNotAvailableException(
    factor = "Face",
    reason = "No camera available"
).apply {
    getUserMessage() // "Face authentication is not available"
    getSuggestedAction() // "Try using PIN instead"
}

// ❌ BAD: Technical jargon
throw Exception("android.

## 9. Enrollment Flow Architecture

### Complete User Journey
[Diagram showing: UUID generation → Factor selection → Digest generation → 
 Local encryption → Redis cache → Confirmation]

### Code Example
[Full enrollment flow code snippet]

## 10. Backend API Specification

### Endpoints
- POST /v1/enrollment/store
- GET /v1/enrollment/retrieve/:uuid
- DELETE /v1/enrollment/delete/:uuid

[Include request/response examples]

## 11. Redis Cache Integration

### TTL Management
[24-hour expiration logic]

### Security Considerations
[Encryption at rest, SSL in transit]
