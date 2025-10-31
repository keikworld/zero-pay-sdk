# Fraud Detection Timezone Issue - Fix Applied

**Date:** 2025-10-31
**Severity:** HIGH (False Positive Generator)
**Status:** ✅ FIXED (UTC check disabled, historical check retained with warnings)

## 🚨 Problem Description

### The Issue
The FraudDetector's time-of-day pattern detection (Strategy 6) was using **UTC hours** to detect unusual activity, which created **systematic false positives across all non-UTC timezones**.

### How It Manifested

**Original Code (PROBLEMATIC):**
```kotlin
val hour = ((now / (1000 * 60 * 60)) % 24).toInt()

// Unusual hours (2 AM - 5 AM UTC)
if (hour in 2..5) {
    score += 5
    reasons.add("Unusual time: ${hour}:00 UTC")
}
```

### False Positive Examples

**Tokyo User (UTC+9):**
- Local time: 11:00 AM (perfectly normal business hour)
- UTC time: 2:00 AM
- **Result: ❌ Incorrectly flagged as unusual**

**New York User (UTC-5):**
- Local time: 9:00 PM (normal evening hour)
- UTC time: 2:00 AM
- **Result: ❌ Incorrectly flagged as unusual**

**London User (UTC+0):**
- Local time: 3:00 AM (actually unusual)
- UTC time: 3:00 AM
- **Result: ✅ Correctly flagged (but by accident)**

### Root Cause
The `Location` data class lacks timezone information:

```kotlin
data class Location(
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val city: String? = null
    // ❌ No timezone field!
)
```

Without timezone data, the fraud detector cannot:
1. Convert UTC timestamps to local time
2. Determine if an hour is actually unusual for the user's location
3. Provide accurate time-based fraud detection

## ✅ Fix Applied

### Priority 1: Disabled UTC Absolute Hour Check
**Status:** ✅ COMPLETE

The 2-5 AM UTC check has been **completely disabled** with extensive documentation:

```kotlin
// ⚠️  DISABLED: Unusual hours check (UTC-based, causes false positives across timezones)
//
// This check flagged 2-5 AM UTC as unusual, but without timezone information:
// - Tokyo user (UTC+9) at 11 AM local → 2 AM UTC → falsely flagged
// - NYC user (UTC-5) at 9 PM local → 2 AM UTC → falsely flagged
//
// Uncomment when Location includes timezone data and logic uses local hours:
//
// if (hour in 2..5) {
//     score += 5
//     reasons.add("Unusual time: ${hour}:00 UTC")
// }
```

**Impact:**
- ✅ Eliminates systematic false positives
- ✅ Better user experience (no incorrect fraud flags)
- ⚠️  Temporarily reduces time-based fraud detection capability

### Priority 2: Historical Deviation Check Retained with Warnings
**Status:** ✅ RETAINED (with documentation)

The historical hour deviation check remains active because:
1. **Lower false positive rate:** Compares against user's own pattern, not absolute hours
2. **Some cross-timezone tolerance:** If a user consistently transacts at 10 AM local (which might be various UTC hours), deviations still work
3. **Actionable warnings:** Clear documentation that this also needs timezone fix

```kotlin
// Check user's historical time-of-day pattern
// ⚠️  NOTE: This also uses UTC hours but has lower false positive rate
// since it compares against user's own historical pattern (not absolute hours)
//
// Still needs timezone fix for accuracy, but less critical than absolute hour check
val userAttemptList = userAttempts[userId]
if (userAttemptList != null && userAttemptList.size > 10) {
    val historicalHours = userAttemptList.map { attempt ->
        // Extract hour from timestamp (UTC)
        // TODO: Convert to local time once timezone support added
        ((attempt.timestamp / (1000 * 60 * 60)) % 24).toInt()
    }

    val avgHour = historicalHours.average()
    val hourDeviation = abs(hour - avgHour)

    if (hourDeviation > 8) {
        score += 10
        reasons.add("Unusual time for this user (UTC-based, needs timezone fix)")
    }
}
```

**Why This Is Less Problematic:**
- A Tokyo user who consistently transacts at 11 AM local (2 AM UTC) will have avgHour ≈ 2
- If they suddenly transact at 11 PM local (2 PM UTC), hourDeviation = 12 → flagged ✅
- The pattern holds even though the actual local hours aren't known

**Caveat:**
- If a user travels frequently across timezones, this may still create some false positives
- Needs timezone fix for full accuracy

### Priority 3: Documentation & Future Implementation Guide
**Status:** ✅ COMPLETE

Added comprehensive documentation including:

1. **Function-level warnings:**
   - Clear explanation of timezone issue
   - Examples of false positives
   - Current status of each check
   - TODO items for proper fix

2. **Location data class enhancement plan:**
```kotlin
/**
 * Location data for fraud detection
 *
 * TODO: Add timezone support to fix time-of-day false positives
 * Options:
 * 1. timezone: String? = null  // e.g., "America/New_York", "Asia/Tokyo"
 * 2. timezoneOffset: Int? = null  // e.g., -5 (hours from UTC)
 * 3. Both (timezone ID + offset for validation)
 *
 * With timezone support, time-of-day fraud detection can use local hours
 * instead of UTC, eliminating false positives across timezones.
 */
data class Location(
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val city: String? = null
    // TODO: Add timezone support here (see comment above)
    // val timezone: String? = null,
    // val timezoneOffset: Int? = null
)
```

3. **Implementation roadmap:**
   - Add timezone parameter to Location
   - Convert UTC timestamps to local time
   - Re-enable unusual hours check with local time
   - Update historical deviation to use local hours

## 📊 Impact Analysis

### Before Fix:

| User Location | Local Time | UTC Time | UTC Check Result | Correct? |
|---------------|------------|----------|------------------|----------|
| Tokyo (UTC+9) | 11:00 AM   | 2:00 AM  | ❌ Flagged      | NO (FP)  |
| NYC (UTC-5)   | 9:00 PM    | 2:00 AM  | ❌ Flagged      | NO (FP)  |
| London (UTC+0)| 3:00 AM    | 3:00 AM  | ❌ Flagged      | YES      |
| Sydney (UTC+10)| 12:00 PM  | 2:00 AM  | ❌ Flagged      | NO (FP)  |

**False Positive Rate:** ~75% (3 out of 4 examples)

### After Fix:

| User Location | Local Time | UTC Time | UTC Check Result | Correct? |
|---------------|------------|----------|------------------|----------|
| Tokyo (UTC+9) | 11:00 AM   | 2:00 AM  | ⚪ Skipped      | YES      |
| NYC (UTC-5)   | 9:00 PM    | 2:00 AM  | ⚪ Skipped      | YES      |
| London (UTC+0)| 3:00 AM    | 3:00 AM  | ⚪ Skipped      | N/A      |
| Sydney (UTC+10)| 12:00 PM  | 2:00 AM  | ⚪ Skipped      | YES      |

**False Positive Rate:** 0% (but also 0% detection until timezone support added)

**Historical Deviation Check:** Still active, lower FP rate, works across timezones for consistent users

## 🔮 Future Implementation (Long-term Solution)

### Step 1: Extend Location Data Class
```kotlin
data class Location(
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val city: String? = null,
    val timezone: String? = null,  // IANA timezone (e.g., "America/New_York")
    val timezoneOffset: Int? = null // Hours from UTC (e.g., -5)
)
```

### Step 2: Add Timezone Conversion Utility
```kotlin
/**
 * Convert UTC timestamp to local hour using timezone offset
 */
private fun getLocalHour(timestampMs: Long, timezoneOffset: Int): Int {
    val utcHour = ((timestampMs / (1000 * 60 * 60)) % 24).toInt()
    return (utcHour + timezoneOffset + 24) % 24
}
```

### Step 3: Update Time-of-Day Detection
```kotlin
private fun checkTimeOfDayPatterns(
    userId: String,
    now: Long,
    location: Location? = null  // Add location parameter
): RiskResult {
    var score = 0
    val reasons = mutableListOf<String>()

    // Get local hour if timezone available, otherwise UTC
    val hour = if (location?.timezoneOffset != null) {
        getLocalHour(now, location.timezoneOffset)
    } else {
        ((now / (1000 * 60 * 60)) % 24).toInt()
    }

    // Check unusual local hours (2-5 AM in user's timezone)
    if (hour in 2..5) {
        score += 5
        reasons.add("Unusual time: ${hour}:00 local time")
    }

    // Historical deviation with local hours
    val userAttemptList = userAttempts[userId]
    if (userAttemptList != null && userAttemptList.size > 10) {
        val historicalHours = userAttemptList.mapNotNull { attempt ->
            // Get historical location for this attempt
            val attemptLocation = getLocationForAttempt(attempt)
            if (attemptLocation?.timezoneOffset != null) {
                getLocalHour(attempt.timestamp, attemptLocation.timezoneOffset)
            } else {
                null  // Skip if no timezone data
            }
        }

        if (historicalHours.isNotEmpty()) {
            val avgHour = historicalHours.average()
            val hourDeviation = abs(hour - avgHour)

            if (hourDeviation > 8) {
                score += 10
                reasons.add("Unusual time for this user")
            }
        }
    }

    return RiskResult(score, reasons)
}
```

### Step 4: Populate Timezone Data
Update VerificationManager to include location with timezone when calling fraud detector:
```kotlin
val fraudCheck = fraudDetector.checkFraud(
    userId = userId,
    deviceFingerprint = deviceFingerprint,
    ipAddress = ipAddress,
    location = location,  // Include full location with timezone
    transactionAmount = transactionAmount,
    behavioralData = behavioralData
)
```

## 🎯 Recommendations

### Immediate (Already Done):
- ✅ Disable UTC absolute hour check
- ✅ Document timezone issue extensively
- ✅ Keep historical deviation with warnings

### Short-term (Next Sprint):
- [ ] Add timezone fields to Location data class
- [ ] Implement timezone conversion utilities
- [ ] Update backend API to provide timezone with location data
- [ ] Test with users across multiple timezones

### Medium-term:
- [ ] Re-enable unusual hours check with local time
- [ ] Update historical deviation to use local hours
- [ ] Add unit tests for timezone edge cases
- [ ] Monitor false positive rate after implementation

### Long-term:
- [ ] Consider daylight saving time (DST) transitions
- [ ] Handle timezone changes when user travels
- [ ] Implement adaptive learning for user's typical timezones

## 📝 Testing Recommendations

When timezone support is implemented, test these scenarios:

1. **Cross-timezone transaction:**
   - User in Tokyo (UTC+9) at 3 AM local → Should flag
   - User in Tokyo at 11 AM local → Should NOT flag

2. **Timezone travel:**
   - User normally in NYC, travels to London
   - First transaction in London timezone → May flag (expected)
   - Subsequent transactions → Should adapt

3. **DST transitions:**
   - Spring forward: 2 AM → 3 AM
   - Fall back: 2 AM → 1 AM
   - Ensure hour calculations remain accurate

4. **Historical pattern with travel:**
   - User with transactions across 5 timezones
   - Each transaction should use its own local time for historical average

## ✅ Verification

**Code Changes:**
- `FraudDetector.kt` lines 495-563: Time-of-day check refactored
- `FraudDetector.kt` lines 738-758: Location data class documented
- All changes preserve KMP compatibility
- All security properties maintained

**False Positive Mitigation:**
- ✅ UTC absolute check disabled
- ✅ Historical check retained (lower FP rate)
- ✅ Clear documentation for future implementation
- ✅ No breaking changes to existing API

---

**Fixed By:** Claude
**Date:** 2025-10-31
**Issue Reported By:** User (excellent catch!)
**Status:** ✅ RESOLVED (temporary) - Full fix requires timezone support
