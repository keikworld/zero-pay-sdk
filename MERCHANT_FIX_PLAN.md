# Merchant Module - Systematic Fix Plan

## 🎯 Executive Summary

**Root Cause Identified:** The merchant module files were disabled due to compilation errors from:
1. **Syntax errors** - Using `MutableMap<>()` instead of `mutableMapOf<>()`
2. **Wrong imports** - Using `android.util.Log` in commonMain (use `println()` instead)
3. **Wrong dependencies** - Importing from `enrollment` module instead of `sdk`
4. **JVM-specific APIs in commonMain** - Using `java.util.concurrent.*` and `java.security.*`

**Key Finding:** The SDK uses `System.currentTimeMillis()` extensively in commonMain and compiles fine because we're Android-only (see `sdk/build.gradle.kts` line 16: `androidTarget()` only). We can use the same approach.

---

## 📋 Fix Strategy

### Phase 1: Foundation - Data Models (HIGH PRIORITY)
**Goal:** Re-enable core data models with minimal dependencies

#### 1.1 Transaction.kt ✅ READY TO FIX
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt.disabled`

**Issues:**
- ✅ Uses `System.currentTimeMillis()` - OK (SDK uses it)

**Fixes Needed:**
- None! File is already KMP-compatible for Android-only target

**Action:**
```bash
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt
```

---

#### 1.2 VerificationResult.kt ✅ READY TO FIX
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt.disabled`

**Issues:**
- ✅ Uses `System.currentTimeMillis()` - OK (SDK uses it)
- ✅ References `MerchantConfig.VerificationError` - exists

**Fixes Needed:**
- None! File is already compatible

**Action:**
```bash
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt
```

---

#### 1.3 VerificationSession.kt ✅ READY TO FIX
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt.disabled`

**Issues:**
- ✅ Uses `System.currentTimeMillis()` - OK (SDK uses it)

**Fixes Needed:**
- None! File is already compatible

**Action:**
```bash
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt
```

---

### Phase 2: Core Logic - Verification & Fraud Detection

#### 2.1 DigestComparator.kt ⚠️ NEEDS FIXES
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/DigestComparator.kt.disabled`

**Issues:**
1. ❌ `import android.util.Log` (line 5)
2. ❌ `import java.security.MessageDigest` (line 6)
3. ✅ `System.currentTimeMillis()` - OK

**Fixes:**
```kotlin
// REMOVE:
import android.util.Log
import java.security.MessageDigest

// CHANGE println() calls (lines 64, 75, 79, 84, 146):
// Already using println() - just remove Log import

// CHANGE getDigestFingerprint() method (lines 213-222):
// REPLACE java.security.MessageDigest with SDK's CryptoUtils
fun getDigestFingerprint(digest: ByteArray): String {
    return try {
        // Use SDK's sha256 function instead
        val hash = com.zeropay.sdk.security.CryptoUtils.sha256(digest)
        hash.take(8).joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        "INVALID"
    }
}
```

---

#### 2.2 ProofGenerator.kt ⚠️ NEEDS ANALYSIS
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/ProofGenerator.kt.disabled`

**Action:** Read file first to identify issues

---

#### 2.3 FraudDetector.kt ⚠️ NEEDS FIXES
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt.disabled`

**Issues:**
1. ❌ `import android.util.Log` (line 5)
2. ❌ `MutableMap<String, ...>()` - wrong syntax (lines 76-91)
3. ❌ Import after code (line 9)

**Fixes:**
```kotlin
// REMOVE:
import android.util.Log

// FIX import order - move kotlin.math.* to top with other imports

// CHANGE all MutableMap<>() to mutableMapOf<>():
// Line 76-91: Change all instances
private val userAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()
private val deviceAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()
private val ipAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()
private val userLocations = mutableMapOf<String, MutableList<LocationRecord>>()
private val userTransactions = mutableMapOf<String, MutableList<TransactionRecord>>()
private val userBehaviorProfiles = mutableMapOf<String, BehavioralProfile>()
private val blacklistedIPs = mutableMapOf<String, BlacklistEntry>()
private val blacklistedDevices = mutableMapOf<String, BlacklistEntry>()

// REPLACE Log.i() calls with println()
// Search and replace all Log.i(TAG, "...") with println("$TAG: ...")
```

---

#### 2.4 RateLimiter.kt ⚠️ NEEDS FIXES
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/RateLimiter.kt.disabled`

**Action:** Read file to identify ConcurrentHashMap usage

**Fixes:**
```kotlin
// REMOVE:
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// REPLACE with:
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// CHANGE ConcurrentHashMap to mutableMapOf + Mutex
private val requests = mutableMapOf<String, MutableList<Long>>()
private val mutex = Mutex()

// WRAP all map access in mutex.withLock { ... }
```

---

#### 2.5 VerificationManager.kt ⚠️ NEEDS SIGNIFICANT FIXES
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/VerificationManager.kt.disabled`

**Action:** This is the main orchestrator - fix after dependencies are ready

**Dependencies:**
- VerificationSession.kt ✅
- VerificationResult.kt ✅
- DigestComparator.kt ⏳
- ProofGenerator.kt ⏳

---

### Phase 3: Platform-Specific

#### 3.1 RateLimiterRedis.kt 🔄 MOVE TO JVM
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/RateLimiterRedis.kt.disabled`

**Issue:** Redis client is JVM-specific

**Solution:**
1. Create `jvmMain` source set if needed
2. Move this file to `jvmMain` or create expect/actual pattern
3. Alternative: Make this backend-only (not in mobile SDK)

---

#### 3.2 UUIDScanner.kt 🔄 MOVE TO ANDROID
**File:** `merchant/src/commonMain/kotlin/com/zeropay/merchant/uuid/UUIDScanner.kt.disabled`

**Issue:** Uses Android camera/NFC APIs

**Solution:**
```bash
# Move to androidMain
mkdir -p merchant/src/androidMain/kotlin/com/zeropay/merchant/uuid
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/uuid/UUIDScanner.kt.disabled \
   merchant/src/androidMain/kotlin/com/zeropay/merchant/uuid/UUIDScanner.kt
```

---

### Phase 4: UI Components (androidMain)

All 17 UI files are in `androidMain` so they CAN use Android APIs. Issues are likely:
1. Wrong imports (from enrollment instead of SDK)
2. Missing dependencies on verification logic

#### 4.1 Verification Canvases (14 files)
**Common Fix:**
```kotlin
// CHANGE imports from:
import com.zeropay.enrollment.factors.*

// TO:
import com.zeropay.sdk.factors.*
import com.zeropay.sdk.ui.*
```

**Files:**
- PINVerificationCanvas.kt.disabled
- FaceVerificationCanvas.kt.disabled
- FingerprintVerificationCanvas.kt.disabled
- PatternVerificationCanvas.kt.disabled
- EmojiVerificationCanvas.kt.disabled
- ColourVerificationCanvas.kt.disabled
- WordsVerificationCanvas.kt.disabled
- MouseDrawVerificationCanvas.kt.disabled
- StylusDrawVerificationCanvas.kt.disabled
- VoiceVerificationCanvas.kt.disabled
- ImageTapVerificationCanvas.kt.disabled
- BalanceVerificationCanvas.kt.disabled
- RhythmTapVerificationCanvas.kt.disabled
- NfcVerificationCanvas.kt.disabled

---

#### 4.2 UI Screens (3 files)
**Files:**
- UUIDInputScreen.kt.disabled - Re-enable after models fixed
- AuthenticationResultScreen.kt.disabled - Re-enable after models fixed
- MerchantVerificationScreen.kt.disabled - Re-enable LAST (depends on everything)

---

## 🔨 Implementation Order

### Step 1: Phase 1 - Foundation Models (EASY WINS)
```bash
# Enable Transaction.kt
cd /home/user/zero-pay-sdk
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/Transaction.kt

# Enable VerificationResult.kt
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationResult.kt

# Enable VerificationSession.kt
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/models/VerificationSession.kt

# TEST COMPILATION
./gradlew :merchant:compileDebugKotlinAndroid
```

**Expected Result:** ✅ Should compile successfully (no changes needed)

---

### Step 2: Fix DigestComparator.kt
```bash
# Re-enable file
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/DigestComparator.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/DigestComparator.kt

# Edit file to fix issues (see Section 2.1 above)
```

---

### Step 3: Fix FraudDetector.kt
```bash
# Re-enable
mv merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt.disabled \
   merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetector.kt

# Edit file to fix (see Section 2.3 above)
```

---

### Step 4: Continue with remaining files...

---

## 🧪 Testing Strategy

After each fix:
```bash
# Compile test
./gradlew :merchant:compileDebugKotlinAndroid --console=plain

# If successful, commit
git add merchant/src/commonMain/kotlin/com/zeropay/merchant/models/*.kt
git commit -m "fix: Re-enable merchant data models"
```

---

## 📝 Quick Reference

### ✅ Android-Only KMP Rules (Current Setup)

**CAN USE:**
- ✅ `System.currentTimeMillis()` (SDK uses it everywhere)
- ✅ `println()` for logging
- ✅ `mutableMapOf<K, V>()` (correct syntax)
- ✅ `MutableList<T>`, `MutableMap<K, V>` types
- ✅ Standard Kotlin stdlib

**CANNOT USE:**
- ❌ `android.util.Log` → use `println()` instead
- ❌ `java.security.*` → use SDK's `CryptoUtils`
- ❌ `java.util.concurrent.*` → use `kotlinx.coroutines.sync.Mutex`
- ❌ `MutableMap<>()` constructor → use `mutableMapOf<>()`

---

## 🎯 Success Criteria

**Phase 1 Complete (Models):**
- [ ] Transaction.kt compiles
- [ ] VerificationResult.kt compiles
- [ ] VerificationSession.kt compiles
- [ ] `./gradlew :merchant:compileDebugKotlinAndroid` succeeds

**Phase 2 Complete (Logic):**
- [ ] DigestComparator.kt compiles and passes tests
- [ ] FraudDetector.kt compiles
- [ ] RateLimiter.kt compiles
- [ ] ProofGenerator.kt compiles
- [ ] VerificationManager.kt compiles

**Phase 3 Complete (Platform):**
- [ ] UUIDScanner.kt moved to androidMain
- [ ] RateLimiterRedis.kt moved/disabled

**Phase 4 Complete (UI):**
- [ ] All 14 verification canvases compile
- [ ] All 3 UI screens compile
- [ ] Full merchant module builds without errors

---

**Generated:** 2025-10-31
**Status:** Ready to implement - Start with Phase 1!
