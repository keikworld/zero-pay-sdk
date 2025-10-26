# Merchant Module - Disabled Files Checklist

This document tracks all files that were temporarily disabled in the merchant module to enable MerchantAlertService testing. These files need to be fixed and re-enabled for full merchant functionality.

**Total Files Disabled:** 27

**Last Updated:** 2025-10-26

---

## 📊 Summary by Category

| Category | Count | Status |
|----------|-------|--------|
| **UI Screens** | 3 | ⏳ Pending |
| **Verification Canvases** | 14 | ⏳ Pending |
| **Fraud Detection** | 3 | ⏳ Pending |
| **Verification Logic** | 3 | ⏳ Pending |
| **Data Models** | 3 | ⏳ Pending |
| **Utilities** | 1 | ⏳ Pending |

---

## 🎨 UI Screens (androidMain) - 3 Files

### ⏳ AuthenticationResultScreen.kt
**Path:** `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/AuthenticationResultScreen.kt.disabled`

**Issues:**
- Unresolved reference: `AuthenticationResult`
- Unresolved reference: `AuthenticationStatus`

**Fix Required:**
- Define `AuthenticationResult` and `AuthenticationStatus` models in commonMain
- Or import from SDK if they should be shared

---

### ⏳ MerchantVerificationScreen.kt
**Path:** `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/MerchantVerificationScreen.kt.disabled`

**Issues:**
- Multiple unresolved references to helper functions
- Missing context parameters
- References to disabled verification canvases

**Fix Required:**
- Implement missing helper functions (`handleTimeout`, `getDeviceFingerprint`, `getIPAddress`)
- Fix `VerificationProgressScreen` and `FactorChallengeScreen` implementations
- Ensure all verification canvases are fixed first

---

### ⏳ UUIDInputScreen.kt
**Path:** `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/UUIDInputScreen.kt.disabled`

**Issues:**
- Likely depends on disabled models and verification logic

**Fix Required:**
- Re-enable after models and verification logic are fixed

---

## 🔐 Verification Canvases (androidMain) - 14 Files

All verification canvas files have similar issues and need to be fixed together.

### Common Issues Across All Canvases:
1. **Import Issues:**
   - Importing from `com.zeropay.enrollment.*` instead of `com.zeropay.sdk.*`
   - Missing Android-specific imports (BiometricManager, BiometricPrompt, FragmentActivity)

2. **Implementation Issues:**
   - References to enrollment-specific classes that don't exist in SDK
   - Missing proper Android platform implementations
   - Type inference issues

### Verification Canvas Files:

- [ ] ⏳ **BalanceVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/BalanceVerificationCanvas.kt.disabled`
- [ ] ⏳ **ColourVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/ColourVerificationCanvas.kt.disabled`
- [ ] ⏳ **EmojiVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/EmojiVerificationCanvas.kt.disabled`
- [ ] ⏳ **FaceVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/FaceVerificationCanvas.kt.disabled`
- [ ] ⏳ **FingerprintVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/FingerprintVerificationCanvas.kt.disabled`
- [ ] ⏳ **ImageTapVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/ImageTapVerificationCanvas.kt.disabled`
- [ ] ⏳ **MouseDrawVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/MouseDrawVerificationCanvas.kt.disabled`
- [ ] ⏳ **NfcVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/NfcVerificationCanvas.kt.disabled`
- [ ] ⏳ **PINVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/PINVerificationCanvas.kt.disabled`
- [ ] ⏳ **PatternVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/PatternVerificationCanvas.kt.disabled`
- [ ] ⏳ **RhythmTapVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/RhythmTapVerificationCanvas.kt.disabled`
- [ ] ⏳ **StylusDrawVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/StylusDrawVerificationCanvas.kt.disabled`
- [ ] ⏳ **VoiceVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/VoiceVerificationCanvas.kt.disabled`
- [ ] ⏳ **WordsVerificationCanvas.kt** - `merchant/src/androidMain/kotlin/com/zeropay/merchant/ui/verification/WordsVerificationCanvas.kt.disabled`

**Recommended Fix Strategy:**
1. Ensure SDK has proper verification canvas interfaces/base classes
2. Fix imports to use SDK instead of enrollment
3. Add missing Android platform-specific imports
4. Test each canvas individually as you re-enable them

---

## 🚨 Fraud Detection (commonMain) - 3 Files

### ⏳ FraudDetector.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt.disabled`

**Issues:**
- Using `MutableMap<>()` as constructor (MutableMap is an interface)
- Still has Android-specific `import android.util.Log` on line 5
- Import statement appearing after non-import code (line 9)

**Fix Required:**
1. Change all `MutableMap<String, ...>()` to `mutableMapOf<String, ...>()`
2. Remove `import android.util.Log` completely
3. Use `import kotlin.math.*` correctly at top of file
4. Add proper thread safety using `kotlinx.coroutines.sync.Mutex`

---

### ⏳ RateLimiter.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/RateLimiter.kt.disabled`

**Issues:**
- Android/JVM imports in commonMain
- ConcurrentHashMap usage

**Fix Required:**
1. Replace `ConcurrentHashMap` with `mutableMapOf()` + Mutex for thread safety
2. Remove all Android/JVM-specific imports
3. Use coroutine-based concurrency

---

### ⏳ RateLimiterRedis.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/RateLimiterRedis.kt.disabled`

**Issues:**
- Same as RateLimiter.kt
- Redis client is JVM-specific

**Fix Required:**
1. This file might need to move to `jvmMain` or `androidMain` since Redis clients are platform-specific
2. Alternative: Create expect/actual pattern for Redis operations
3. Consider if Redis rate limiting is needed for mobile clients

---

## 🔍 Verification Logic (commonMain) - 3 Files

### ⏳ VerificationManager.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/VerificationManager.kt.disabled`

**Issues:**
- Multiple compilation errors
- References to disabled models (VerificationSession, VerificationResult)
- Android/JVM-specific code patterns

**Fix Required:**
1. Fix all KMP compatibility issues (no Android/JVM imports)
2. Ensure all referenced models are available
3. Use coroutines properly for all suspend functions
4. Fix all type inference issues

---

### ⏳ DigestComparator.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/DigestComparator.kt.disabled`

**Issues:**
- Android Log usage
- println() with multiple parameters

**Fix Required:**
1. Replace `Log` calls with `println()`
2. Fix println to use single string parameter
3. Ensure constant-time comparison implementation is KMP-compatible

---

### ⏳ ProofGenerator.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/ProofGenerator.kt.disabled`

**Issues:**
- Similar to DigestComparator

**Fix Required:**
1. Fix KMP compatibility
2. Verify ZK-SNARK proof generation is platform-agnostic

---

## 📦 Data Models (commonMain) - 3 Files

### ⏳ VerificationSession.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt.disabled`

**Priority:** HIGH (required by VerificationManager)

**Fix Required:**
1. Make fully KMP-compatible (no Android/JVM types)
2. Use standard Kotlin types only
3. Ensure serialization works cross-platform

---

### ⏳ VerificationResult.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt.disabled`

**Priority:** HIGH (required by VerificationManager)

**Fix Required:**
1. Define proper sealed class hierarchy
2. Make KMP-compatible
3. Ensure all result types are accounted for

---

### ⏳ Transaction.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt.disabled`

**Priority:** MEDIUM

**Fix Required:**
1. Make KMP-compatible
2. Remove any Android/JVM-specific types

---

## 🔧 Utilities (commonMain) - 1 File

### ⏳ UUIDScanner.kt
**Path:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/uuid/UUIDScanner.kt.disabled`

**Issues:**
- Likely uses Android-specific camera/NFC APIs

**Fix Required:**
1. Move to `androidMain` (platform-specific)
2. Create expect/actual declarations if cross-platform support needed

---

## 📋 Recommended Fix Order

Follow this order to minimize dependency issues:

### Phase 1: Foundation (commonMain)
1. ✅ **MerchantAlertService.kt** - COMPLETE (already working)
2. ✅ **MerchantConfig.kt** - COMPLETE (already working)
3. ⏳ **Transaction.kt** - Data model
4. ⏳ **VerificationResult.kt** - Data model (HIGH PRIORITY)
5. ⏳ **VerificationSession.kt** - Data model (HIGH PRIORITY)

### Phase 2: Core Logic (commonMain)
6. ⏳ **DigestComparator.kt** - Verification logic
7. ⏳ **ProofGenerator.kt** - Verification logic
8. ⏳ **RateLimiter.kt** - Fraud detection
9. ⏳ **FraudDetector.kt** - Fraud detection
10. ⏳ **VerificationManager.kt** - Main verification orchestrator (depends on 3-9)

### Phase 3: Platform-Specific (androidMain)
11. ⏳ **UUIDScanner.kt** - Move to androidMain or create expect/actual
12. ⏳ **RateLimiterRedis.kt** - Consider JVM-only or expect/actual

### Phase 4: UI Components (androidMain)
13. ⏳ **UUIDInputScreen.kt** - Simple screen
14. ⏳ **AuthenticationResultScreen.kt** - Simple screen
15. ⏳ All **VerificationCanvas** files (14 files) - Can be done in parallel
16. ⏳ **MerchantVerificationScreen.kt** - Main orchestrator (depends on all canvases)

---

## 🔍 Testing Strategy

After re-enabling each file:

1. **Compile Test:** Run `./gradlew :merchant:compileDebugKotlin`
2. **Unit Tests:** Create/run tests for commonMain logic
3. **Integration Tests:** Test androidMain UI components
4. **End-to-End:** Test full verification flow

---

## 📝 Notes

- **KMP Compatibility Rule:** Files in `commonMain` CANNOT use:
  - `android.util.*`
  - `java.util.UUID`
  - `java.util.concurrent.*`
  - `android.content.*`
  - Any Android or JVM-specific APIs

- **Proper Patterns:**
  - Use `mutableMapOf()` not `MutableMap<>()`
  - Use `println()` not `Log.i()`
  - Use `kotlin.random.Random` not `java.util.Random`
  - Use `kotlinx.coroutines.sync.Mutex` for thread safety

- **Architecture:**
  - Merchant should import from SDK, never from enrollment
  - Enrollment and merchant are sibling modules
  - Both depend on SDK as the shared foundation

---

## 🚀 Quick Re-enable Command

When ready to fix a file:

```bash
# Example: Re-enable FraudDetector.kt
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt

# Then fix compilation errors and test
./gradlew :merchant:compileDebugKotlin
```

---

## ✅ Completion Checklist

Use this to track overall progress:

- [ ] Phase 1: Foundation (5 files) - 2/5 complete (40%)
- [ ] Phase 2: Core Logic (5 files) - 0/5 complete (0%)
- [ ] Phase 3: Platform-Specific (2 files) - 0/2 complete (0%)
- [ ] Phase 4: UI Components (17 files) - 0/17 complete (0%)

**Overall Progress:** 2/29 files complete (7%)

---

**Generated:** 2025-10-26
**ZeroPay Merchant Module - KMP Migration**
