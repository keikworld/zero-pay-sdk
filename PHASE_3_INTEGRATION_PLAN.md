# Phase 3: SDK Manager Integration Plan

**Date:** 2025-10-18
**Status:** 🟢 READY TO START
**Goal:** Integrate existing SDK managers with new API clients

---

## 🎯 Overview

Phase 3 connects the existing high-level managers (`EnrollmentManager`, `VerificationManager`) with the new API clients (`EnrollmentClient`, `VerificationClient`) created in Phase 2.

**Current Architecture:**
```
EnrollmentManager → RedisCacheClient (direct)
VerificationManager → RedisCacheClient (direct)
```

**Target Architecture:**
```
EnrollmentManager → EnrollmentClient (API) → Backend → Redis
                 ↓ RedisCacheClient (fallback)

VerificationManager → VerificationClient (API) → Backend → Redis
                   ↓ RedisCacheClient (fallback)
```

---

## 📊 Files to Modify

### 1. EnrollmentManager.kt ✅ EXISTS
**Path:** `enrollment/src/androidMain/kotlin/com/zeropay/enrollment/EnrollmentManager.kt`

**Current Dependencies:**
- `KeyStoreManager` ✅
- `RedisCacheClient` (direct) ⚠️ NEEDS UPDATE
- `ConsentManager` ✅
- `PaymentProviderManager` ✅

**Planned Changes:**
- Add `EnrollmentClient` as primary backend interface
- Keep `RedisCacheClient` as fallback
- Add graceful degradation logic
- Update `enrollWithSession()` to use API
- Update `retrieveEnrollment()` to use API
- Update `updateEnrollment()` to use API
- Update `deleteEnrollment()` to use API

**Estimated Changes:** 50-100 lines

---

### 2. VerificationManager.kt ✅ EXISTS
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/VerificationManager.kt`

**Current Dependencies:**
- `RedisCacheClient` (direct) ⚠️ NEEDS UPDATE
- `DigestComparator` ✅
- `ProofGenerator` ✅
- `FraudDetector` ✅
- `RateLimiter` ✅

**Planned Changes:**
- Add `VerificationClient` as primary backend interface
- Keep `RedisCacheClient` as fallback
- Update `createSession()` to use API
- Update `verifyUser()` to use API
- Add session status checking via API
- Update error handling

**Estimated Changes:** 40-80 lines

---

### 3. Create Integration Layer (NEW)
**Path:** `sdk/src/commonMain/kotlin/com/zeropay/sdk/integration/BackendIntegration.kt`

**Purpose:**
Provides unified interface with automatic fallback logic.

**Features:**
- Primary: API client (network)
- Fallback: Redis cache client (local)
- Automatic retry with exponential backoff
- Health check endpoint
- Circuit breaker pattern
- Metrics tracking

**Estimated Lines:** 400-500

---

## 🔄 Integration Strategy

### Strategy 1: Gradual Migration (RECOMMENDED)

**Approach:**
Add API clients alongside existing cache clients, with feature flags.

**Benefits:**
- Zero downtime
- Easy rollback
- A/B testing possible
- Incremental validation

**Implementation:**
```kotlin
class EnrollmentManager(
    private val keyStoreManager: KeyStoreManager,
    private val redisCacheClient: RedisCacheClient,
    private val enrollmentClient: EnrollmentClient? = null, // NEW
    private val useApi: Boolean = false // Feature flag
) {
    suspend fun enrollWithSession(session: EnrollmentSession): EnrollmentResult {
        return if (useApi && enrollmentClient != null) {
            // NEW: Use API
            enrollViaApi(session)
        } else {
            // EXISTING: Use direct cache
            enrollViaCache(session)
        }
    }
}
```

---

### Strategy 2: Hybrid Approach (PRODUCTION)

**Approach:**
Always try API first, fall back to cache on failure.

**Benefits:**
- Best of both worlds
- Resilient to backend outages
- Automatic failover
- No manual intervention

**Implementation:**
```kotlin
suspend fun enrollWithSession(session: EnrollmentSession): EnrollmentResult {
    // Try API first
    enrollmentClient?.let { client ->
        val apiResult = tryEnrollViaApi(client, session)
        if (apiResult.isSuccess) {
            return apiResult.getOrThrow()
        }
        // Log API failure, continue to fallback
        Log.w(TAG, "API enrollment failed, trying cache fallback")
    }

    // Fallback to direct cache
    return enrollViaCache(session)
}
```

---

## 📋 Detailed Implementation Plan

### Task 1: Create BackendIntegration Utility ✅

**File:** `sdk/src/commonMain/kotlin/com/zeropay/sdk/integration/BackendIntegration.kt`

**Components:**
1. `BackendIntegration` class
   - Health check
   - Retry logic
   - Circuit breaker
   - Metrics

2. `FallbackStrategy` enum
   - API_ONLY
   - CACHE_ONLY
   - API_FIRST_CACHE_FALLBACK (default)
   - CACHE_FIRST_API_SYNC

3. `IntegrationMetrics` data class
   - API success/failure count
   - Cache success/failure count
   - Average latency
   - Circuit breaker state

**Estimated Time:** 2-3 hours

---

### Task 2: Update EnrollmentManager ✅

**Changes Required:**

#### 2.1: Add EnrollmentClient Dependency
```kotlin
class EnrollmentManager(
    private val keyStoreManager: KeyStoreManager,
    private val redisCacheClient: RedisCacheClient,
    private val enrollmentClient: EnrollmentClient? = null, // NEW
    private val backendIntegration: BackendIntegration? = null // NEW
)
```

#### 2.2: Update enrollWithSession()
```kotlin
suspend fun enrollWithSession(
    session: EnrollmentSession
): EnrollmentResult = withContext(Dispatchers.IO) {
    // Existing validation steps...

    // NEW: Try API enrollment
    val apiResult = tryApiEnrollment(session)
    if (apiResult != null) {
        return@withContext apiResult
    }

    // FALLBACK: Existing cache-based enrollment
    return@withContext enrollViaCacheLegacy(session)
}

private suspend fun tryApiEnrollment(
    session: EnrollmentSession
): EnrollmentResult? {
    val client = enrollmentClient ?: return null

    return try {
        val request = buildEnrollmentRequest(session)
        val response = client.enroll(request).getOrThrow()

        // Success - also update local cache
        syncToLocalCache(session, response)

        createSuccessResult(response)
    } catch (e: Exception) {
        Log.w(TAG, "API enrollment failed: ${e.message}")
        null // Signal fallback
    }
}
```

#### 2.3: Update Other Methods
- `retrieveEnrollment()` → Use `EnrollmentClient.retrieveFactors()`
- `updateEnrollment()` → Use `EnrollmentClient.updateFactors()`
- `deleteEnrollment()` → Use `EnrollmentClient.deleteEnrollment()`

**Estimated Time:** 3-4 hours

---

### Task 3: Update VerificationManager ✅

**Changes Required:**

#### 3.1: Add VerificationClient Dependency
```kotlin
class VerificationManager(
    private val redisCacheClient: RedisCacheClient,
    private val verificationClient: VerificationClient? = null, // NEW
    private val digestComparator: DigestComparator,
    private val proofGenerator: ProofGenerator,
    private val fraudDetector: FraudDetector,
    private val rateLimiter: RateLimiter
)
```

#### 3.2: Update createSession()
```kotlin
suspend fun createSession(
    userId: String,
    merchantId: String,
    transactionAmount: Double,
    deviceFingerprint: String? = null,
    ipAddress: String? = null
): Result<VerificationSession> {
    // NEW: Try API session creation
    verificationClient?.let { client ->
        try {
            val apiSession = client.createSession(
                userUuid = userId,
                transactionId = generateTransactionId(),
                amount = transactionAmount,
                currency = "USD" // TODO: Make configurable
            ).getOrThrow()

            return Result.success(apiSession.toVerificationSession())
        } catch (e: Exception) {
            Log.w(TAG, "API session creation failed: ${e.message}")
        }
    }

    // FALLBACK: Existing local session logic
    return createLocalSession(userId, merchantId, transactionAmount)
}
```

#### 3.3: Update verifyUser()
```kotlin
suspend fun verifyUser(
    sessionId: String,
    submittedFactors: Map<Factor, ByteArray>
): VerificationResult {
    // NEW: Try API verification
    verificationClient?.let { client ->
        try {
            val factorDigests = submittedFactors.map { (factor, data) ->
                FactorDigest(
                    type = factor.name,
                    digest = sha256(data)
                )
            }

            val apiResult = client.verify(
                sessionId = sessionId,
                userUuid = getCurrentUserId(),
                factors = factorDigests
            ).getOrThrow()

            return apiResult.toVerificationResult()
        } catch (e: Exception) {
            Log.w(TAG, "API verification failed: ${e.message}")
        }
    }

    // FALLBACK: Existing local verification logic
    return verifyLocally(sessionId, submittedFactors)
}
```

**Estimated Time:** 2-3 hours

---

### Task 4: Update Factory/Builder Classes ✅

**Files to Update:**
1. `ZeroPay.kt` (main SDK entry point)
2. Enrollment module builders
3. Merchant module builders

**Add API Configuration:**
```kotlin
object ZeroPay {
    fun init(
        context: Context,
        apiConfig: ApiConfig, // NEW
        enableBackendIntegration: Boolean = true // NEW
    ) {
        // Initialize HTTP client
        val httpClient = OkHttpClientImpl(apiConfig)

        // Create API clients
        val enrollmentClient = EnrollmentClient(httpClient, apiConfig)
        val verificationClient = VerificationClient(httpClient, apiConfig)
        val blockchainClient = BlockchainClient(httpClient, apiConfig)

        // Create managers with API clients
        val enrollmentManager = EnrollmentManager(
            keyStoreManager = keyStoreManager,
            redisCacheClient = redisCacheClient,
            enrollmentClient = if (enableBackendIntegration) enrollmentClient else null
        )

        // ...
    }
}
```

**Estimated Time:** 1-2 hours

---

### Task 5: Add Configuration & Feature Flags ✅

**New File:** `sdk/src/commonMain/kotlin/com/zeropay/sdk/config/IntegrationConfig.kt`

**Configuration Options:**
```kotlin
data class IntegrationConfig(
    // Backend integration
    val enableApiIntegration: Boolean = true,
    val fallbackStrategy: FallbackStrategy = FallbackStrategy.API_FIRST_CACHE_FALLBACK,

    // Retry configuration
    val maxRetries: Int = 3,
    val initialRetryDelayMs: Long = 1000L,
    val maxRetryDelayMs: Long = 5000L,

    // Circuit breaker
    val enableCircuitBreaker: Boolean = true,
    val circuitBreakerThreshold: Int = 5,
    val circuitBreakerTimeoutMs: Long = 30000L,

    // Health check
    val enableHealthCheck: Boolean = true,
    val healthCheckIntervalMs: Long = 60000L,

    // Timeouts
    val apiTimeoutMs: Long = 10000L,
    val cacheTimeoutMs: Long = 5000L
)
```

**Estimated Time:** 1 hour

---

### Task 6: Add Comprehensive Tests ✅

**New Test Files:**

1. **BackendIntegrationTest.kt**
   - Test API-first fallback logic
   - Test circuit breaker
   - Test retry mechanism
   - Test metrics tracking

2. **EnrollmentManagerIntegrationTest.kt**
   - Test API enrollment
   - Test cache fallback
   - Test sync between API and cache
   - Test error scenarios

3. **VerificationManagerIntegrationTest.kt**
   - Test API verification
   - Test session creation via API
   - Test cache fallback
   - Test error scenarios

**Estimated Time:** 4-5 hours

---

## ✅ Testing Strategy

### Unit Tests
- Mock `EnrollmentClient` responses
- Test fallback logic triggers correctly
- Test retry logic
- Test circuit breaker state transitions

### Integration Tests
- Test with real backend (localhost)
- Test with backend unavailable (fallback)
- Test with intermittent failures (retry)
- Test with slow backend (timeout)

### End-to-End Tests
- Complete enrollment flow via API
- Complete verification flow via API
- Test mixed scenarios (enrollment via API, verification via cache)

---

## 🔒 No Breaking Changes

**Backward Compatibility:**
- All existing code continues to work
- API clients are optional dependencies
- Feature flags allow gradual rollout
- No changes to public APIs

**Migration Path:**
1. Deploy with `enableBackendIntegration = false` (no changes)
2. Enable for internal testing
3. Enable for beta users (10%)
4. Gradual rollout (25%, 50%, 100%)
5. Remove legacy cache-only path (optional)

---

## 📊 Success Metrics

### Functional
- ✅ All enrollments work via API
- ✅ All verifications work via API
- ✅ Fallback works when backend unavailable
- ✅ Zero data loss during migration

### Performance
- ✅ API latency < 500ms (p99)
- ✅ Fallback latency < 100ms
- ✅ Overall success rate > 99.9%

### Security
- ✅ No plaintext factor data in network requests
- ✅ TLS 1.3 for all API calls
- ✅ Certificate pinning enabled (production)
- ✅ Zero-knowledge maintained

---

## 🚀 Implementation Timeline

**Total Estimated Time:** 15-20 hours

| Task | Time | Status |
|------|------|--------|
| 1. BackendIntegration utility | 2-3h | Pending |
| 2. Update EnrollmentManager | 3-4h | Pending |
| 3. Update VerificationManager | 2-3h | Pending |
| 4. Update factories/builders | 1-2h | Pending |
| 5. Add configuration | 1h | Pending |
| 6. Add tests | 4-5h | Pending |
| 7. Documentation | 2h | Pending |

---

## 📋 Checklist

### Pre-Implementation
- [x] Review existing managers
- [x] Review API clients
- [x] Identify integration points
- [x] Create implementation plan
- [ ] Review plan with team

### Implementation
- [ ] Create BackendIntegration utility
- [ ] Update EnrollmentManager
- [ ] Update VerificationManager
- [ ] Update factory classes
- [ ] Add configuration system
- [ ] Write unit tests
- [ ] Write integration tests

### Post-Implementation
- [ ] Run all tests
- [ ] Performance testing
- [ ] Security review
- [ ] Documentation
- [ ] Code review
- [ ] Gradual rollout plan

---

## 🔍 Key Integration Points

### EnrollmentManager Integration

**Current Flow:**
```
User Input → EnrollmentManager → RedisCacheClient → Redis
                                → KeyStoreManager → Android KeyStore
```

**New Flow:**
```
User Input → EnrollmentManager → EnrollmentClient → Backend API → Redis/PostgreSQL
                                ↓ (fallback)
                                RedisCacheClient → Redis
                                ↓ (always)
                                KeyStoreManager → Android KeyStore
```

---

### VerificationManager Integration

**Current Flow:**
```
Merchant Request → VerificationManager → RedisCacheClient → Redis
                                       → DigestComparator (local)
```

**New Flow:**
```
Merchant Request → VerificationManager → VerificationClient → Backend API
                                       ↓ (fallback)
                                       RedisCacheClient → Redis
                                       ↓ (local verification)
                                       DigestComparator
```

---

## 🎯 Next Steps

1. **Review this plan** - Ensure all stakeholders agree
2. **Set up development environment** - Backend running, Redis configured
3. **Create feature branch** - `phase-3-manager-integration`
4. **Start with Task 1** - BackendIntegration utility
5. **Iterative testing** - Test after each task
6. **Code review** - Before merging to main

---

**Status:** ✅ **READY TO IMPLEMENT**
**Risk Level:** Low (backward compatible, incremental)
**Confidence:** High (clear requirements, existing code to build on)

---

*Generated: 2025-10-18*
*Version: 1.0.0*
