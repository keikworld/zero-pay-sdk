# ZeroPay SDK

**Device-Free, Passwordless Payment Authentication System**

ZeroPay is a next-generation authentication platform that combines zero-knowledge proofs with multi-factor authentication to enable secure, privacy-preserving payment verification without requiring physical devices or passwords.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9+-blue.svg)](https://kotlinlang.org)
[![PSD3 SCA Compliant](https://img.shields.io/badge/PSD3-SCA%20Compliant-green.svg)](https://www.ecb.europa.eu)

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [Usage](#usage)
- [Security](#security)
- [Compliance](#compliance)
- [API Documentation](#api-documentation)
- [Contributing](#contributing)
- [License](#license)

## Features

### Core Capabilities

- **13+ Authentication Factors** across 5 categories (biometric, knowledge, behavior, possession, location)
- **Zero-Knowledge Proofs** using ZK-SNARKs for privacy-preserving verification
- **Double-Layer Encryption** with PBKDF2 key derivation and KMS wrapping
- **Device-Free Authentication** - users can verify from any device using their memorized factors
- **Blockchain Integration** with Solana Pay, Phantom Wallet, and Ethereum support
- **Payment Gateway Abstraction** supporting 13+ providers (Stripe, Adyen, Google Pay, etc.)
- **Redis-Backed Caching** with TLS encryption and 24-hour TTL
- **Constant-Time Operations** to prevent timing attacks
- **Memory Wiping** for sensitive data protection

### Authentication Factors

#### Knowledge Factors
- **PIN**: 4-12 digit PIN with sequence/repeat validation
- **Pattern**: Visual pattern lock (3+ strokes)
- **Words**: Memorable word sequence (3-8 words)
- **Colour**: Color sequence selection (3-6 colors)
- **Emoji**: Emoji sequence selection (3-8 emojis)

#### Biometric Factors
- **Face**: Face recognition via Android BiometricPrompt
- **Fingerprint**: Fingerprint authentication via BiometricPrompt
- **Voice**: Text-based voice phrase (not audio biometric)

#### Behavioral Factors
- **RhythmTap**: Tap rhythm pattern recognition
- **MouseDraw**: Mouse gesture drawing pattern
- **StylusDraw**: Stylus drawing pattern
- **ImageTap**: Image region tap sequence

#### Possession Factors
- **NFC**: NFC tag authentication

#### Location Factors
- **Balance**: Device tilt/balance gesture (not GPS)

## Architecture

### Module Structure

```
zeropay-android/
├── sdk/                    # Core SDK (Kotlin Multiplatform)
│   ├── crypto/            # SHA-256, PBKDF2, AES-256-GCM
│   ├── factors/           # Factor implementations
│   ├── security/          # Security primitives
│   ├── blockchain/        # Solana/Ethereum integration
│   └── gateway/           # Payment gateway abstraction
├── enrollment/            # User enrollment module
│   ├── ui/               # 5-step enrollment wizard
│   ├── payment/          # Payment provider linking
│   └── consent/          # GDPR consent management
├── merchant/             # Merchant verification module
│   ├── verification/     # Session-based verification
│   └── fraud/           # Fraud detection & rate limiting
└── backend/             # Node.js API server
    ├── routes/          # REST API endpoints
    ├── crypto/          # Double-layer encryption
    └── services/        # Blockchain RPC services
```

### Authentication Flow

#### Enrollment
1. User selects 6+ factors from 13 available types (minimum 2 categories for PSD3 SCA)
2. User completes each factor (UI generates SHA-256 digest locally)
3. SDK derives encryption key from factor digests using PBKDF2 (100K iterations)
4. SDK encrypts digests with derived key (AES-256-GCM)
5. Backend wraps encryption key with KMS master key
6. Encrypted digests stored in Redis (24h TTL), wrapped key in PostgreSQL
7. User receives UUID (stored only on device, never on server)

#### Verification
1. Merchant creates verification session with user's UUID
2. Backend retrieves enrolled factors from Redis
3. User completes required factors (generates fresh digests)
4. VerificationManager compares digests using constant-time comparison
5. If all match, optional ZK-SNARK proof generated for privacy
6. Returns boolean success + cryptographic proof (never reveals which factor failed)

### Data Storage

- **KeyStore (Android)**: Primary storage for UUID and cached digests
- **Redis (Backend)**: Secondary cache with 24h TTL, TLS 1.3 encryption
- **PostgreSQL (Backend)**: KMS-wrapped encryption keys only
- **No Raw Data**: Only SHA-256 digests stored, never plaintext inputs

## Getting Started

### Prerequisites

- **Android**: Android Studio Hedgehog (2023.1.1+), Gradle 8.0+, JDK 17+
- **Backend**: Node.js 18+, Redis 7+, PostgreSQL 14+
- **Optional**: AWS KMS account, Solana RPC endpoint

### Installation

#### Android SDK

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    // Core SDK
    implementation("com.zeropay:sdk:1.0.0")

    // Enrollment module (optional)
    implementation("com.zeropay:enrollment:1.0.0")

    // Merchant module (optional)
    implementation("com.zeropay:merchant:1.0.0")
}
```

Or build from source:

```bash
git clone https://github.com/your-org/zeropay-android.git
cd zeropay-android
./gradlew build
```

#### Backend Server

```bash
cd backend
npm install

# Copy environment template
cp .env.example .env
# Edit .env with your configuration

# Generate TLS certificates for Redis
npm run generate:certs

# Start Redis with TLS
npm run redis:start

# Start development server
npm run dev
```

### Configuration

#### Android Configuration

```kotlin
// Initialize SDK
val zeroPay = ZeroPay(
    context = applicationContext,
    config = ZeroPayConfig(
        baseUrl = "https://your-backend.com",
        enableBiometrics = true,
        enableBlockchain = true,
        securityLevel = SecurityLevel.HIGH
    )
)

// Configure enrollment
val enrollmentConfig = EnrollmentConfig(
    minimumFactors = 6,
    minimumCategories = 2,
    requireBiometric = true,
    allowWeakPasswords = false,
    consentRequired = true
)
```

#### Backend Configuration

Create `.env` file:

```bash
# Server
PORT=3000
NODE_ENV=production

# Redis
REDIS_HOST=localhost
REDIS_PORT=6380
REDIS_PASSWORD=your_secure_password
REDIS_USERNAME=zeropay-backend
REDIS_TLS_ENABLED=true

# Database
DATABASE_URL=postgresql://user:password@localhost:5432/zeropay

# KMS (AWS)
AWS_REGION=us-east-1
AWS_KMS_KEY_ID=your-kms-key-id

# Blockchain (Solana)
SOLANA_RPC_URL=https://api.mainnet-beta.solana.com

# Admin
ADMIN_API_KEY=your_admin_key
```

## Usage

### User Enrollment

```kotlin
// Initialize enrollment manager
val enrollmentManager = EnrollmentManager(
    context = context,
    config = enrollmentConfig,
    zeroPay = zeroPay
)

// Start enrollment flow
enrollmentManager.startEnrollment(
    onSuccess = { result ->
        // User enrolled successfully
        val uuid = result.uuid
        val alias = result.alias
        println("Enrolled with UUID: $uuid")
    },
    onError = { error ->
        println("Enrollment failed: ${error.message}")
    }
)
```

### Merchant Verification

```kotlin
// Initialize verification manager
val verificationManager = VerificationManager(
    context = context,
    config = merchantConfig,
    zeroPay = zeroPay
)

// Start verification session
verificationManager.startVerification(
    uuid = userUuid,
    amount = 99.99,
    currency = "USD",
    onSuccess = { result ->
        if (result.verified) {
            println("Payment verified!")
            println("Proof: ${result.zkProof}")
        } else {
            println("Verification failed")
        }
    },
    onError = { error ->
        println("Verification error: ${error.message}")
    }
)
```

### Payment Provider Integration

```kotlin
// Link payment provider during enrollment
enrollmentManager.linkPaymentProvider(
    provider = PaymentProvider.STRIPE,
    credentials = mapOf(
        "publishableKey" to "pk_test_...",
        "customerId" to "cus_..."
    ),
    onSuccess = { tokenId ->
        println("Payment provider linked: $tokenId")
    },
    onError = { error ->
        println("Linking failed: ${error.message}")
    }
)
```

### Blockchain Wallet Integration

```kotlin
// Link Phantom wallet (Solana)
val walletManager = WalletLinkingManager(zeroPay)

walletManager.linkPhantomWallet(
    context = context,
    onSuccess = { walletAddress ->
        println("Wallet linked: $walletAddress")
    },
    onError = { error ->
        println("Wallet linking failed: ${error.message}")
    }
)
```

## Security

### Cryptographic Primitives

- **Hashing**: SHA-256 for all digest generation
- **Key Derivation**: PBKDF2 with 100,000 iterations, SHA-256 HMAC
- **Encryption**: AES-256-GCM with 96-bit nonces
- **KMS**: AWS KMS for master key wrapping
- **Randomness**: SecureRandom (never Math.random())

### Security Best Practices

#### Constant-Time Comparison

All digest comparisons use constant-time algorithms to prevent timing attacks:

```kotlin
fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].toInt() xor b[i].toInt())
    }
    return result == 0
}
```

#### Memory Wiping

Sensitive data is wiped from memory after use:

```kotlin
val pin = getUserPin()
val digest = sha256(pin.toByteArray())
try {
    // Use digest
} finally {
    digest.fill(0)  // Clear sensitive data
}
```

#### Rate Limiting

Multi-layer rate limiting prevents brute-force attacks:
- **Global**: 1000 requests/minute (all users)
- **Per IP**: 100 requests/minute with exponential penalty
- **Per User**: 50 requests/minute with backoff
- **Blockchain**: 20 requests/minute (RPC quota protection)

### Threat Model

ZeroPay protects against:
- Timing attacks (constant-time operations)
- Replay attacks (nonce validation, session expiry)
- Man-in-the-middle (TLS 1.3, certificate pinning)
- Brute-force attacks (rate limiting, account lockout)
- Memory dumps (memory wiping, secure storage)
- Device loss (zero-knowledge proofs, no device binding)

## Compliance

### PSD3 SCA (Strong Customer Authentication)

ZeroPay meets PSD3 SCA requirements:
- Minimum 6 authentication factors required
- Minimum 2 factor categories (biometric, knowledge, behavior, possession, location)
- Transaction amount validation
- Dynamic linking with cryptographic proof

### GDPR (General Data Protection Regulation)

ZeroPay is GDPR compliant:
- **Consent Management**: Explicit consent tracking with timestamps
- **Right to Erasure**: One-click deletion of all user data
- **Data Portability**: Export user data as JSON
- **Privacy by Design**: Only cryptographic hashes stored, no biometric data
- **Data Retention**: Automatic 24-hour TTL on all cached data

### Security Certifications

ZeroPay follows industry best practices:
- OWASP Mobile Application Security Guidelines
- NIST Cryptographic Standards (FIPS 140-2)
- ISO 27001 Information Security Management
- SOC 2 Type II (backend infrastructure)

## API Documentation

### REST API Endpoints

#### Enrollment
```
POST /api/enrollment/enroll
Body: {
  "factors": [{ "type": "PIN", "digest": "sha256_hash" }],
  "consent": { "timestamp": "2025-10-18T...", "ipAddress": "..." }
}
Response: { "uuid": "...", "alias": "...", "expiresAt": "..." }
```

#### Verification
```
POST /api/verification/verify
Body: {
  "uuid": "...",
  "factors": [{ "type": "PIN", "digest": "sha256_hash" }],
  "sessionId": "..."
}
Response: { "verified": true, "zkProof": "...", "confidence": 0.98 }
```

#### Blockchain
```
POST /api/blockchain/verify-wallet
Body: { "walletAddress": "...", "signature": "...", "message": "..." }
Response: { "verified": true, "network": "solana" }
```

See [Backend API Documentation](./backend/README.md) for full endpoint reference.

## Build Commands

### Android

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :sdk:build
./gradlew :enrollment:build
./gradlew :merchant:build

# Run tests
./gradlew test
./gradlew :sdk:test --tests "com.zeropay.sdk.*"

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Generate documentation
./gradlew dokkaHtml
```

### Backend

```bash
cd backend

# Start development server
npm run dev

# Start production server
npm start

# Run tests
npm test
npm run test:crypto
npm run test:blockchain

# Start Redis with TLS
npm run redis:start

# Connect to Redis CLI
npm run redis:cli
```

## Testing

### Unit Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew :sdk:test --tests "com.zeropay.sdk.RateLimiterTest"

# Run factor tests
./gradlew :sdk:test --tests "com.zeropay.sdk.factors.*"
```

### Integration Tests

```bash
# Backend integration tests
cd backend
npm test -- tests/day9-10.test.js
```

### End-to-End Tests

```bash
# Run E2E test script
./tests/e2e-test.sh
```

## Contributing

We welcome contributions! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

### Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use KDoc for public APIs
- Max function length: 50 lines
- Always use constant-time operations for crypto comparisons
- Always wipe sensitive data from memory

### Pull Request Process

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Security Issues

Please report security vulnerabilities privately to security@zeropay.com. Do not create public issues for security concerns.

## Roadmap

- [ ] iOS SDK (Swift/Kotlin Multiplatform)
- [ ] Web SDK (WASM)
- [ ] Additional payment providers (PayPal, Square, etc.)
- [ ] Biometric liveness detection
- [ ] Multi-device synchronization
- [ ] Offline verification mode
- [ ] Hardware security module (HSM) integration
- [ ] Additional blockchain networks (Ethereum L2s, Bitcoin)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Built with [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html)
- Compose UI framework
- [Ktor](https://ktor.io/) for networking
- [Redis](https://redis.io/) for caching
- [PostgreSQL](https://www.postgresql.org/) for persistent storage
- [Solana Web3.js](https://solana-labs.github.io/solana-web3.js/) for blockchain integration

## Documentation

- [Implementation Plan](./ZEROPAY_IMPLEMENTATION_PLAN_v2.md) - 30-day development roadmap
- [Development Guide](./development_guide.md) - Architecture and patterns
- [Demo Script](./DEMO_SCRIPT.md) - End-to-end demonstration
- [Claude Code Guide](./CLAUDE.md) - AI assistant instructions

## Support

- Documentation: [https://docs.zeropay.com](https://docs.zeropay.com)
- Issues: [GitHub Issues](https://github.com/your-org/zeropay-android/issues)
- Discussions: [GitHub Discussions](https://github.com/your-org/zeropay-android/discussions)
- Email: support@zeropay.com

---

Made with ❤️ by the ZeroPay Team
