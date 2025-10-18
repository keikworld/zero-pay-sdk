# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**ZeroPay** is a device-free, passwordless payment authentication system using zero-knowledge proofs and multi-factor authentication. The system consists of:

- **SDK** (Kotlin Multiplatform): Core authentication factors and cryptography
- **Enrollment Module**: User enrollment flow with 13+ authentication factors
- **Merchant Module**: Merchant-side verification and fraud detection
- **Backend** (Node.js): Secure API with Redis cache, double encryption, and blockchain integration
- **Online-Web**: Web-based verification interface (optional)

### Core Security Features

- **Double Encryption**: Key derivation (PBKDF2) + KMS wrapping
- **Zero-Knowledge Proofs**: ZK-SNARK proof generation for privacy
- **Multi-Factor Auth**: 13+ factors across 5 categories (biometric, knowledge, behavior, possession, location)
- **PSD3 SCA Compliance**: Minimum 6 factors, 2+ categories
- **GDPR Compliance**: 24-hour TTL, right to erasure, consent tracking
- **Constant-Time Operations**: Timing attack prevention
- **Memory Wiping**: Sensitive data cleared after use

## Build & Test Commands

### Backend (Node.js)

```bash
# Start development server
cd backend
npm install
npm run dev

# Start Redis with TLS
npm run redis:start

# Generate TLS certificates (first time only)
npm run generate:certs

# Run tests
npm test
npm run test:crypto
npm run test:blockchain

# Connect to Redis CLI
npm run redis:cli
```

### Android SDK

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :sdk:build
./gradlew :enrollment:build
./gradlew :merchant:build

# Run tests
./gradlew test
./gradlew :sdk:test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Generate documentation
./gradlew dokkaHtml
```

### Running a Single Test

```bash
# Kotlin tests (from project root)
./gradlew :sdk:test --tests "com.zeropay.sdk.RateLimiterTest"
./gradlew :sdk:test --tests "com.zeropay.sdk.factors.*"

# Backend tests (from backend directory)
npm test -- backend/tests/day9-10.test.js
```

## Architecture Overview

### Module Structure

```
zeropay-android/
├── sdk/                          # Core SDK (Kotlin Multiplatform)
│   ├── commonMain/              # Platform-agnostic code
│   │   ├── Factor.kt            # Factor enum and metadata
│   │   ├── RateLimiter.kt       # Rate limiting logic
│   │   ├── crypto/              # Cryptography (SHA-256, PBKDF2, constant-time)
│   │   ├── factors/             # Factor implementations (13+ types)
│   │   ├── security/            # Security primitives
│   │   ├── blockchain/          # Solana integration, wallet linking
│   │   ├── gateway/             # Payment gateway abstraction
│   │   └── zksnark/             # ZK-SNARK proof generation
│   ├── androidMain/             # Android-specific (UI, KeyStore)
│   │   ├── factors/             # Compose UI for each factor
│   │   ├── storage/             # KeyStore, EncryptedSharedPreferences
│   │   └── ui/                  # Composable canvases
│   └── test/                    # Unit tests
│
├── enrollment/                   # Enrollment flow module
│   ├── EnrollmentManager.kt     # Orchestrates enrollment
│   ├── ui/                      # 5-step wizard UI
│   │   ├── steps/               # Consent, Factor Selection, Capture, Payment, Confirmation
│   │   └── factors/             # Per-factor enrollment canvases
│   ├── payment/                 # Payment provider linking (Stripe, Adyen, Phantom, etc.)
│   ├── consent/                 # GDPR consent management
│   └── security/                # UUID generation, alias creation
│
├── merchant/                     # Merchant verification module
│   ├── verification/            # VerificationManager
│   │   ├── VerificationManager.kt  # Session-based verification
│   │   ├── DigestComparator.kt     # Constant-time comparison
│   │   └── ProofGenerator.kt       # ZK-SNARK proof generation
│   ├── fraud/                   # Fraud detection, rate limiting
│   └── ui/                      # Merchant verification UI
│
└── backend/                      # Node.js API server
    ├── server.js                # Express server with TLS Redis
    ├── routes/                  # API endpoints (enrollment, verification, blockchain)
    ├── crypto/                  # Double encryption, KMS integration
    ├── middleware/              # Rate limiting, session, nonce validation
    ├── database/                # PostgreSQL for wrapped keys
    └── services/                # Solana RPC, wallet verification
```

### Authentication Flow

**Enrollment (User Side):**
1. User selects 6+ factors from 13 available types
2. User completes each factor (UI canvas generates SHA-256 digest locally)
3. SDK derives encryption key from factor digests (PBKDF2, 100K iterations)
4. SDK encrypts digests with derived key (AES-256-GCM)
5. Backend wraps encryption key with KMS master key
6. Encrypted digests stored in Redis (24h TTL), wrapped key in PostgreSQL
7. User receives UUID (device-only, never stored on server)

**Verification (Merchant Side):**
1. Merchant creates verification session with user's UUID
2. Backend retrieves enrolled factors from Redis
3. User completes required factors (generates digests)
4. VerificationManager compares digests using constant-time comparison
5. If all match, generate ZK-SNARK proof (optional)
6. Return boolean result + proof to merchant (never reveals which factor failed)

### Data Storage Strategy

- **KeyStore (Android)**: Primary storage, never leaves device
- **Redis (Backend)**: Secondary cache, 24h TTL, encrypted at rest (AES-256-GCM), TLS 1.3 in transit
- **PostgreSQL (Backend)**: KMS-wrapped encryption keys only (NOT digests)
- **No raw factor data**: Only SHA-256 digests stored, never plaintext

## Key Security Patterns

### Constant-Time Comparison

Always use constant-time comparison to prevent timing attacks:

```kotlin
// ✅ CORRECT
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
}

// ❌ WRONG - Timing attack vulnerability
if (digest1.contentEquals(digest2)) { ... }
```

### Memory Wiping

Always wipe sensitive data from memory:

```kotlin
// ✅ CORRECT
fun processPin(pin: String) {
    val digest = sha256(pin.toByteArray())
    try {
        // Use digest
    } finally {
        digest.fill(0)  // Wipe memory
    }
}

// ❌ WRONG - Data stays in memory
fun processPin(pin: String) {
    val digest = sha256(pin.toByteArray())
    return digest  // Lingers in heap
}
```

### Factor Validation

Each factor has specific validation rules in `EnrollmentConfig`:

- **PIN**: 4-12 digits, no sequences (1234), no repeats (1111)
- **Pattern**: 3+ strokes, no trivial patterns (L-shape)
- **Emoji**: 3-8 unique emojis
- **Color**: 3-6 distinct colors (#RRGGBB format)
- **Voice**: 3+ words, normalized text (no audio biometrics)
- **Face/Fingerprint**: Android BiometricPrompt API

## Common Development Tasks

### Adding a New Factor

1. Add enum to `sdk/src/commonMain/kotlin/com/zeropay/sdk/Factor.kt`
2. Implement processor in `sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/`
3. Create Compose canvas in `sdk/src/androidMain/kotlin/com/zeropay/sdk/ui/`
4. Add to `FactorRegistry.kt` with availability checks
5. Create enrollment canvas in `enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/`
6. Create verification canvas in `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/`
7. Add tests in `sdk/src/test/kotlin/com/zeropay/sdk/factors/`

### Adding a Payment Provider

1. Implement `PaymentProvider` interface in `enrollment/src/androidMain/kotlin/com/zeropay/enrollment/payment/providers/`
2. Register in `PaymentProviderManager.kt`
3. Add configuration in `EnrollmentConfig.kt`
4. Test integration flow with mock credentials

### Modifying Cryptography

**CRITICAL**: Do NOT change crypto algorithms without security review:
- SHA-256 for all hashing (no MD5, SHA-1)
- PBKDF2 with 100K iterations minimum
- AES-256-GCM for encryption
- SecureRandom for all randomness (no Math.random())

If changes needed, update:
1. `sdk/src/commonMain/kotlin/com/zeropay/sdk/crypto/CryptoUtils.kt`
2. `backend/crypto/doubleLayerCrypto.js`
3. All unit tests in `sdk/src/test/kotlin/com/zeropay/sdk/security/`

## Testing Strategy

### Unit Tests
- Factor processors: Validate digest generation, input validation
- Crypto utilities: SHA-256, PBKDF2, constant-time comparison, memory wiping
- Rate limiter: Token bucket, sliding window, penalty system
- Enrollment/Verification: Flow logic, error handling, rollback

### Integration Tests
- Backend API: Enrollment → Storage → Retrieval → Verification
- Redis: TLS connection, encryption at rest, TTL expiration
- Blockchain: Wallet linking, transaction verification, RPC calls

### End-to-End Tests
- Complete enrollment flow (6 factors)
- Complete verification flow (merchant session)
- GDPR compliance (consent, deletion)
- Payment provider linking

## Environment Variables

Backend requires the following environment variables (see `backend/.env.example`):

```bash
# Server
PORT=3000
NODE_ENV=production

# Redis
REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=your_secure_password
REDIS_USERNAME=zeropay-backend
REDIS_CA_CERT=/path/to/ca.crt
REDIS_CLIENT_CERT=/path/to/client.crt
REDIS_CLIENT_KEY=/path/to/client.key

# Database
DATABASE_URL=postgresql://user:password@localhost:5432/zeropay

# KMS (AWS)
AWS_REGION=us-east-1
AWS_KMS_KEY_ID=your-kms-key-id
AWS_ACCESS_KEY_ID=your-access-key
AWS_SECRET_ACCESS_KEY=your-secret-key

# Blockchain (Solana)
SOLANA_RPC_URL=https://api.mainnet-beta.solana.com
SOLANA_DEVNET_RPC_URL=https://api.devnet.solana.com

# Admin
ADMIN_API_KEY=your_admin_key
```

## Important Implementation Notes

### Factor Categories (PSD3 SCA Compliance)

Factors are grouped into 5 categories. Enrollment requires minimum 2 categories:

1. **Knowledge**: PIN, Pattern, Words, Colour, Emoji
2. **Biometric**: Face, Fingerprint, Voice (text-based, not audio)
3. **Behavior**: RhythmTap, MouseDraw, StylusDraw, ImageTap
4. **Possession**: NFC
5. **Location**: Balance (not GPS coordinates)

### Blockchain Integration (Optional)

ZeroPay supports optional wallet linking for blockchain payments:
- **Phantom** (Solana): Mobile deep linking, transaction signing
- **MetaMask** (Ethereum): WalletConnect integration
- Wallet addresses cached in Redis (24h TTL), never in database
- Transaction verification via RPC polling

### GDPR Compliance

- **Consent tracking**: All consents timestamped and revocable
- **Right to erasure**: One-click deletion of all user data
- **Data portability**: Export user data as JSON
- **Privacy by design**: Only cryptographic hashes stored, no biometric data

### Rate Limiting

Multi-layer rate limiting implemented:
- **Global**: 1000 req/min (all users)
- **Per IP**: 100 req/min with penalty escalation
- **Per User**: 50 req/min with exponential backoff
- **Blockchain**: 20 req/min (RPC quota protection)

### Error Handling

All errors use sealed classes for type-safety:

```kotlin
sealed class EnrollmentError {
    object INVALID_FACTOR : EnrollmentError()
    object STORAGE_FAILURE : EnrollmentError()
    object RATE_LIMIT_EXCEEDED : EnrollmentError()
    object CRYPTO_FAILURE : EnrollmentError()
    // ...
}
```

Always provide user-friendly error messages (never expose technical details to end users).

## Code Style Guidelines

- **Kotlin**: Follow official Kotlin style guide, PascalCase for classes, camelCase for functions
- **Comments**: Use KDoc for public APIs, inline comments sparingly
- **Function length**: Max 50 lines, split into smaller functions
- **Null safety**: Use `?.`, `?:`, and `!!` judiciously (prefer safe calls)
- **Coroutines**: Always use `Dispatchers.IO` for I/O operations, `Dispatchers.Default` for CPU-bound
- **Logging**: Use structured logging with appropriate levels (DEBUG, INFO, WARN, ERROR)

## Troubleshooting

### Redis Connection Issues
```bash
# Check Redis is running with TLS
redis-cli --tls --cert backend/redis/tls/redis.crt \
  --key backend/redis/tls/redis.key \
  --cacert backend/redis/tls/ca.crt -p 6380 ping

# Should return "PONG"
```

### Gradle Build Failures
```bash
# Clear Gradle cache
./gradlew clean
rm -rf .gradle build

# Invalidate caches and restart Android Studio
# File > Invalidate Caches > Invalidate and Restart
```

### Factor Not Available on Device
Check device capabilities in `FactorRegistry.kt`:
- Face: Requires `FEATURE_FACE` or BiometricManager.BIOMETRIC_STRONG
- Fingerprint: Requires BiometricManager with enrolled fingerprints
- NFC: Requires `FEATURE_NFC` and NFC enabled

## Related Documentation

- **Implementation Plan**: `ZEROPAY_IMPLEMENTATION_PLAN_v2.md` - Full 30-day development roadmap
- **Development Guide**: `development_guide.md` - Code style, architecture patterns
- **Demo Script**: `DEMO_SCRIPT.md` - End-to-end demonstration flow
- **README**: `README.md` - Project overview
- **Backend API**: See `backend/routes/` for endpoint documentation

## Contact & Support

For security issues, contact the security team before making changes to cryptographic code.

For architecture questions, refer to the implementation plan or development guide.
