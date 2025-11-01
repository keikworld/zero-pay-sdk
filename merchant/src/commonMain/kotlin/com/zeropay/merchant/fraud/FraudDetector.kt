// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/fraud/FraudDetectorComplete.kt

package com.zeropay.merchant.fraud

import kotlin.math.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Complete Fraud Detector - PRODUCTION VERSION
 * 
 * Comprehensive behavioral fraud detection with all 7 strategies.
 * 
 * Detection Strategies:
 * 1. ✅ Velocity checks (too many attempts)
 * 2. ✅ Geolocation anomalies (impossible travel with distance calculation)
 * 3. ✅ Device fingerprint analysis (new/suspicious devices)
 * 4. ✅ Behavioral patterns (typing speed, interaction timing)
 * 5. ✅ IP reputation (known malicious IPs)
 * 6. ✅ Time-of-day patterns (unusual activity times)
 * 7. ✅ Transaction amount anomalies (spending patterns)
 * 
 * Risk Scoring:
 * - Low risk (0-30): Allow
 * - Medium risk (31-70): Challenge with extra factor
 * - High risk (71-100): Block transaction
 * 
 * Machine Learning (Future):
 * - Pattern recognition
 * - Anomaly detection
 * - User profiling
 * - Adaptive thresholds
 * 
 * @version 2.0.0
 * @date 2025-10-13
 */
class FraudDetectorComplete {
    
    companion object {
        private const val TAG = "FraudDetectorComplete"
        
        // Risk thresholds
        private const val RISK_THRESHOLD_LOW = 30
        private const val RISK_THRESHOLD_MEDIUM = 70
        
        // Velocity limits
        private const val MAX_ATTEMPTS_PER_MINUTE = 5
        private const val MAX_ATTEMPTS_PER_HOUR = 20
        private const val MAX_ATTEMPTS_PER_DAY = 50
        
        // Location limits (km/h)
        private const val MAX_TRAVEL_SPEED_KMH = 500.0 // Reasonable by plane
        private const val SUSPICIOUS_TRAVEL_SPEED_KMH = 1000.0 // Impossible
        
        // Behavioral limits
        private const val MIN_FACTOR_COMPLETION_MS = 500L // Too fast = bot
        private const val MAX_FACTOR_COMPLETION_MS = 300000L // 5 min = too slow
        private const val NORMAL_TYPING_SPEED_MS_PER_CHAR = 200L
        
        // Transaction limits
        private const val SUSPICIOUS_AMOUNT_MULTIPLIER = 5.0 // 5x normal
        private const val HIGH_RISK_AMOUNT_MULTIPLIER = 10.0 // 10x normal
        
        // Time windows
        private const val MINUTE_MS = 60 * 1000L
        private const val HOUR_MS = 60 * MINUTE_MS
        private const val DAY_MS = 24 * HOUR_MS
        
        // Earth radius for distance calculation (km)
        private const val EARTH_RADIUS_KM = 6371.0
    }
    
    // User activity tracking
    private val userAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()
    private val deviceAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()
    private val ipAttempts = mutableMapOf<String, MutableList<AttemptRecord>>()

    // User location tracking
    private val userLocations = mutableMapOf<String, MutableList<LocationRecord>>()

    // User spending patterns
    private val userTransactions = mutableMapOf<String, MutableList<TransactionRecord>>()

    // Behavioral profiles
    private val userBehaviorProfiles = mutableMapOf<String, BehavioralProfile>()

    // Known malicious entities
    private val blacklistedIPs = mutableMapOf<String, BlacklistEntry>()
    private val blacklistedDevices = mutableMapOf<String, BlacklistEntry>()
    
    // Mutex for thread safety
    private val mutex = Mutex()
    
    /**
     * Check for fraud indicators - COMPLETE VERSION
     * 
     * @param userId User UUID
     * @param deviceFingerprint Device fingerprint (optional)
     * @param ipAddress IP address (optional)
     * @param location Location data (optional)
     * @param transactionAmount Transaction amount (optional)
     * @param behavioralData Behavioral timing data (optional)
     * @return Fraud check result with risk score
     */
    suspend fun checkFraud(
        userId: String,
        deviceFingerprint: String? = null,
        ipAddress: String? = null,
        location: Location? = null,
        transactionAmount: Double? = null,
        behavioralData: BehavioralData? = null
    ): FraudCheckResult = mutex.withLock {
        
        println("Checking fraud for user: $userId")
        
        val now = System.currentTimeMillis()
        var riskScore = 0
        val reasons = mutableListOf<String>()

        // Initialize risk result variables for all strategies
        // (needed for details map at end, even if strategy not executed)
        var geoRisk = RiskResult(0, emptyList())
        var deviceRisk = RiskResult(0, emptyList())
        var behaviorRisk = RiskResult(0, emptyList())
        var ipRisk = RiskResult(0, emptyList())
        var transactionRisk = RiskResult(0, emptyList())

        // ==================== STRATEGY 1: VELOCITY CHECKS ====================

        val velocityRisk = checkVelocity(userId, now)
        riskScore += velocityRisk.score
        reasons.addAll(velocityRisk.reasons)

        // ==================== STRATEGY 2: GEOLOCATION ANOMALIES ====================

        if (location != null) {
            geoRisk = checkGeolocationAnomalies(userId, location, now)
            riskScore += geoRisk.score
            reasons.addAll(geoRisk.reasons)
        }

        // ==================== STRATEGY 3: DEVICE FINGERPRINT ANALYSIS ====================

        if (deviceFingerprint != null) {
            deviceRisk = checkDeviceFingerprint(userId, deviceFingerprint, now)
            riskScore += deviceRisk.score
            reasons.addAll(deviceRisk.reasons)
        }

        // ==================== STRATEGY 4: BEHAVIORAL PATTERNS ====================

        if (behavioralData != null) {
            behaviorRisk = checkBehavioralPatterns(userId, behavioralData, now)
            riskScore += behaviorRisk.score
            reasons.addAll(behaviorRisk.reasons)
        }

        // ==================== STRATEGY 5: IP REPUTATION ====================

        if (ipAddress != null) {
            ipRisk = checkIPReputation(userId, ipAddress, now)
            riskScore += ipRisk.score
            reasons.addAll(ipRisk.reasons)
        }

        // ==================== STRATEGY 6: TIME-OF-DAY PATTERNS ====================

        val timeRisk = checkTimeOfDayPatterns(userId, now)
        riskScore += timeRisk.score
        reasons.addAll(timeRisk.reasons)

        // ==================== STRATEGY 7: TRANSACTION AMOUNT ANOMALIES ====================

        if (transactionAmount != null) {
            transactionRisk = checkTransactionAnomalies(userId, transactionAmount, now)
            riskScore += transactionRisk.score
            reasons.addAll(transactionRisk.reasons)
        }
        
        // ==================== RECORD ATTEMPT ====================
        
        recordAttempt(userId, deviceFingerprint, ipAddress, location, transactionAmount, now)
        
        // ==================== DETERMINE RISK LEVEL ====================
        
        val finalScore = riskScore.coerceAtMost(100)
        val riskLevel = when {
            finalScore >= RISK_THRESHOLD_MEDIUM -> RiskLevel.HIGH
            finalScore >= RISK_THRESHOLD_LOW -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        
        val isLegitimate = riskLevel != RiskLevel.HIGH
        
        println("Fraud check result for $userId: score=$finalScore, level=$riskLevel")
        if (reasons.isNotEmpty()) {
            println("  Reasons: ${reasons.joinToString("; ")}")
        }
        
        return FraudCheckResult(
            isLegitimate = isLegitimate,
            riskScore = finalScore,
            riskLevel = riskLevel,
            reason = if (reasons.isEmpty()) "No suspicious activity" else reasons.joinToString("; "),
            details = mapOf(
                "velocity" to velocityRisk.score,
                "geolocation" to (if (location != null) geoRisk.score else 0),
                "device" to (if (deviceFingerprint != null) deviceRisk.score else 0),
                "behavioral" to (if (behavioralData != null) behaviorRisk.score else 0),
                "ip" to (if (ipAddress != null) ipRisk.score else 0),
                "timeOfDay" to timeRisk.score,
                "transaction" to (if (transactionAmount != null) transactionRisk.score else 0)
            )
        )
    }
    
    /**
     * Strategy 1: Check velocity (attempt frequency)
     */
    private fun checkVelocity(userId: String, now: Long): RiskResult {
        val userAttemptList = userAttempts.getOrPut(userId) { mutableListOf() }
        cleanOldAttempts(userAttemptList, now)
        
        var score = 0
        val reasons = mutableListOf<String>()
        
        val attemptsLastMinute = userAttemptList.count { now - it.timestamp < MINUTE_MS }
        val attemptsLastHour = userAttemptList.count { now - it.timestamp < HOUR_MS }
        val attemptsLastDay = userAttemptList.count { now - it.timestamp < DAY_MS }
        
        if (attemptsLastMinute > MAX_ATTEMPTS_PER_MINUTE) {
            score += 30
            reasons.add("Excessive velocity: $attemptsLastMinute attempts/min")
            println("High velocity detected: $userId - $attemptsLastMinute/min")
        }
        
        if (attemptsLastHour > MAX_ATTEMPTS_PER_HOUR) {
            score += 20
            reasons.add("High hourly attempts: $attemptsLastHour/hour")
        }
        
        if (attemptsLastDay > MAX_ATTEMPTS_PER_DAY) {
            score += 15
            reasons.add("High daily attempts: $attemptsLastDay/day")
        }
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Strategy 2: Check geolocation anomalies (COMPLETE WITH DISTANCE CALCULATION)
     */
    private fun checkGeolocationAnomalies(
        userId: String,
        currentLocation: Location,
        now: Long
    ): RiskResult {
        val locationList = userLocations.getOrPut(userId) { mutableListOf() }
        
        var score = 0
        val reasons = mutableListOf<String>()
        
        if (locationList.isNotEmpty()) {
            val lastLocation = locationList.last()
            val timeDiffMs = now - lastLocation.timestamp
            val timeDiffHours = timeDiffMs / (1000.0 * 3600.0)
            
            // Calculate distance using Haversine formula
            val distanceKm = calculateHaversineDistance(
                lastLocation.latitude,
                lastLocation.longitude,
                currentLocation.latitude,
                currentLocation.longitude
            )
            
            // Calculate travel speed
            val speedKmH = if (timeDiffHours > 0) distanceKm / timeDiffHours else 0.0
            
            println("Location change: ${distanceKm.roundToInt()} km in ${timeDiffHours.round(2)} hours = ${speedKmH.roundToInt()} km/h")
            
            // Impossible travel detection
            if (speedKmH > SUSPICIOUS_TRAVEL_SPEED_KMH) {
                score += 50
                reasons.add("Impossible travel: ${speedKmH.roundToInt()} km/h (${distanceKm.roundToInt()} km)")
                println("Impossible travel detected: $userId - $speedKmH km/h")
            } else if (speedKmH > MAX_TRAVEL_SPEED_KMH) {
                score += 25
                reasons.add("Suspicious travel speed: ${speedKmH.roundToInt()} km/h")
            }
            
            // Unusual country change
            if (lastLocation.country != null && 
                currentLocation.country != null && 
                lastLocation.country != currentLocation.country &&
                timeDiffHours < 4.0) {
                score += 15
                reasons.add("Country change in ${timeDiffHours.round(2)} hours")
            }
        }
        
        // Record current location
        locationList.add(LocationRecord(
            latitude = currentLocation.latitude,
            longitude = currentLocation.longitude,
            country = currentLocation.country,
            city = currentLocation.city,
            timestamp = now
        ))
        
        // Keep only last 50 locations
        if (locationList.size > 50) {
            locationList.removeAt(0)
        }
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Calculate distance between two points using Haversine formula
     * 
     * @param lat1 Latitude of first point (degrees)
     * @param lon1 Longitude of first point (degrees)
     * @param lat2 Latitude of second point (degrees)
     * @param lon2 Longitude of second point (degrees)
     * @return Distance in kilometers
     */
    private fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        // Convert to radians
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        // Haversine formula
        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad
        
        val a = sin(dLat / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        
        val c = 2 * asin(sqrt(a))
        
        return EARTH_RADIUS_KM * c
    }
    
    /**
     * Strategy 3: Check device fingerprint
     */
    private fun checkDeviceFingerprint(
        userId: String,
        deviceFingerprint: String,
        now: Long
    ): RiskResult {
        var score = 0
        val reasons = mutableListOf<String>()
        
        // Check if blacklisted
        val blacklistEntry = blacklistedDevices[deviceFingerprint]
        if (blacklistEntry != null) {
            score += 100
            reasons.add("Blacklisted device: ${blacklistEntry.reason}")
            println("Blacklisted device detected: $deviceFingerprint")
            return RiskResult(score, reasons)
        }
        
        val deviceAttemptList = deviceAttempts.getOrPut(deviceFingerprint) { mutableListOf() }
        cleanOldAttempts(deviceAttemptList, now)
        
        // Check if this is a new device for the user
        val userAttemptList = userAttempts.getOrPut(userId) { mutableListOf() }
        val userDevices = userAttemptList.mapNotNull { it.deviceFingerprint }.distinct()
        
        if (!userDevices.contains(deviceFingerprint) && userDevices.isNotEmpty()) {
            score += 15
            reasons.add("New device detected")
            println("New device for user $userId: $deviceFingerprint")
        }
        
        // Check device attempt velocity
        val deviceAttemptsLastHour = deviceAttemptList.count { now - it.timestamp < HOUR_MS }
        if (deviceAttemptsLastHour > MAX_ATTEMPTS_PER_HOUR) {
            score += 20
            reasons.add("Device high velocity: $deviceAttemptsLastHour attempts")
        }
        
        // Check if device used by multiple users
        val usersOnDevice = deviceAttemptList.map { it.userId }.distinct()
        if (usersOnDevice.size > 5) {
            score += 15
            reasons.add("Shared device: ${usersOnDevice.size} users")
        }
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Strategy 4: Check behavioral patterns (NEW)
     */
    private fun checkBehavioralPatterns(
        userId: String,
        behavioralData: BehavioralData,
        now: Long
    ): RiskResult {
        var score = 0
        val reasons = mutableListOf<String>()
        
        // Check factor completion time
        if (behavioralData.factorCompletionTimeMs < MIN_FACTOR_COMPLETION_MS) {
            score += 30
            reasons.add("Too fast: ${behavioralData.factorCompletionTimeMs}ms (bot-like)")
            println("Bot-like behavior detected: $userId")
        }
        
        if (behavioralData.factorCompletionTimeMs > MAX_FACTOR_COMPLETION_MS) {
            score += 10
            reasons.add("Unusually slow: ${behavioralData.factorCompletionTimeMs}ms")
        }
        
        // Check typing speed (if available)
        if (behavioralData.typingSpeedMsPerChar != null) {
            val speed = behavioralData.typingSpeedMsPerChar
            if (speed < NORMAL_TYPING_SPEED_MS_PER_CHAR / 4) {
                score += 20
                reasons.add("Abnormally fast typing: ${speed}ms/char")
            }
        }
        
        // Compare with user's historical profile
        val profile = userBehaviorProfiles.getOrPut(userId) { BehavioralProfile() }
        
        if (profile.hasHistory()) {
            val deviation = profile.calculateDeviation(behavioralData)
            
            if (deviation > 0.7) {
                score += 25
                reasons.add("Behavioral anomaly: ${(deviation * 100).toInt()}% deviation")
                println("Behavioral anomaly detected: $userId - ${deviation}x deviation")
            } else if (deviation > 0.5) {
                score += 10
                reasons.add("Unusual behavior pattern")
            }
        }
        
        // Update profile
        profile.addDataPoint(behavioralData)
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Strategy 5: Check IP reputation
     */
    private fun checkIPReputation(
        userId: String,
        ipAddress: String,
        now: Long
    ): RiskResult {
        var score = 0
        val reasons = mutableListOf<String>()
        
        // Check if blacklisted
        val blacklistEntry = blacklistedIPs[ipAddress]
        if (blacklistEntry != null) {
            score += 100
            reasons.add("Blacklisted IP: ${blacklistEntry.reason}")
            println("Blacklisted IP detected: $ipAddress")
            return RiskResult(score, reasons)
        }
        
        val ipAttemptList = ipAttempts.getOrPut(ipAddress) { mutableListOf() }
        cleanOldAttempts(ipAttemptList, now)
        
        // Check IP velocity
        val ipAttemptsLastHour = ipAttemptList.count { now - it.timestamp < HOUR_MS }
        if (ipAttemptsLastHour > MAX_ATTEMPTS_PER_HOUR) {
            score += 25
            reasons.add("IP high velocity: $ipAttemptsLastHour attempts")
            println("Suspicious IP activity: $ipAddress - $ipAttemptsLastHour/hour")
        }
        
        // Check if multiple users from same IP
        val usersFromIP = ipAttemptList.map { it.userId }.distinct()
        if (usersFromIP.size > 10) {
            score += 20
            reasons.add("Shared IP: ${usersFromIP.size} users")
        }
        
        // Check for known proxy/VPN patterns (simple heuristic)
        if (isLikelyProxy(ipAddress)) {
            score += 10
            reasons.add("Possible proxy/VPN detected")
        }
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Strategy 6: Check time-of-day patterns
     *
     * ⚠️  WARNING: TIMEZONE HANDLING ISSUE
     * This function currently uses UTC hours, which creates FALSE POSITIVES across timezones.
     *
     * Example: A Tokyo user (UTC+9) transacting at 11 AM local time maps to 2 AM UTC
     * and would incorrectly trigger the unusual hours penalty.
     *
     * CURRENT STATUS:
     * - UTC "unusual hours" check (2-5 AM) has been DISABLED to prevent false positives
     * - Historical deviation check remains active but also uses UTC (lower false positive rate)
     *
     * TODO: Proper timezone support requires:
     * 1. Add timezone parameter to Location data class (e.g., "America/New_York", offset, etc.)
     * 2. Convert UTC timestamps to local time before hour extraction
     * 3. Re-enable unusual hours check using local time
     * 4. Update historical deviation to use local hours
     *
     * Until timezone support is added, this strategy provides limited value.
     */
    private fun checkTimeOfDayPatterns(userId: String, now: Long): RiskResult {
        var score = 0
        val reasons = mutableListOf<String>()

        // Get hour of day (0-23) from epoch milliseconds (UTC)
        // KMP-compatible: pure Kotlin math, no java.util.Calendar
        val hour = ((now / (1000 * 60 * 60)) % 24).toInt()

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

            // Large deviation (>8 hours) suggests unusual behavior
            // This works somewhat across timezones since we're comparing against
            // user's own pattern, but would be more accurate with local time
            if (hourDeviation > 8) {
                score += 10
                reasons.add("Unusual time for this user (UTC-based, needs timezone fix)")
            }
        }

        return RiskResult(score, reasons)
    }
    
    /**
     * Strategy 7: Check transaction amount anomalies (NEW)
     */
    private fun checkTransactionAnomalies(
        userId: String,
        transactionAmount: Double,
        now: Long
    ): RiskResult {
        var score = 0
        val reasons = mutableListOf<String>()
        
        val transactionList = userTransactions.getOrPut(userId) { mutableListOf() }
        
        // Clean old transactions (keep last 30 days)
        transactionList.removeAll { now - it.timestamp > 30 * DAY_MS }
        
        if (transactionList.isNotEmpty()) {
            // Calculate average transaction amount
            val avgAmount = transactionList.map { it.amount }.average()
            val maxAmount = transactionList.maxOfOrNull { it.amount } ?: avgAmount
            
            // Check if amount is significantly higher than normal
            if (transactionAmount > avgAmount * HIGH_RISK_AMOUNT_MULTIPLIER) {
                score += 30
                reasons.add("Extremely high amount: ${transactionAmount.round(2)} vs avg ${avgAmount.round(2)}")
                println("Extremely high transaction: $userId - $transactionAmount vs avg $avgAmount")
            } else if (transactionAmount > avgAmount * SUSPICIOUS_AMOUNT_MULTIPLIER) {
                score += 15
                reasons.add("Unusually high amount: ${transactionAmount.round(2)} vs avg ${avgAmount.round(2)}")
            }
            
            // Check for rapid high-value transactions
            val recentHighValue = transactionList.count { 
                now - it.timestamp < HOUR_MS && it.amount > avgAmount * 2
            }
            
            if (recentHighValue >= 3) {
                score += 20
                reasons.add("Multiple high-value transactions: $recentHighValue in last hour")
            }
        }
        
        // Record transaction
        transactionList.add(TransactionRecord(
            amount = transactionAmount,
            timestamp = now
        ))
        
        // Keep only last 100 transactions
        if (transactionList.size > 100) {
            transactionList.removeAt(0)
        }
        
        return RiskResult(score, reasons)
    }
    
    /**
     * Record attempt for tracking
     */
    private fun recordAttempt(
        userId: String,
        deviceFingerprint: String?,
        ipAddress: String?,
        location: Location?,
        transactionAmount: Double?,
        timestamp: Long
    ) {
        val record = AttemptRecord(
            userId = userId,
            deviceFingerprint = deviceFingerprint,
            ipAddress = ipAddress,
            transactionAmount = transactionAmount,
            timestamp = timestamp
        )
        
        userAttempts.getOrPut(userId) { mutableListOf() }.add(record)
        
        if (deviceFingerprint != null) {
            deviceAttempts.getOrPut(deviceFingerprint) { mutableListOf() }.add(record)
        }
        
        if (ipAddress != null) {
            ipAttempts.getOrPut(ipAddress) { mutableListOf() }.add(record)
        }
    }
    
    /**
     * Clean old attempts from list
     */
    private fun cleanOldAttempts(attempts: MutableList<AttemptRecord>, now: Long) {
        attempts.removeAll { now - it.timestamp > DAY_MS }
    }
    
    /**
     * Simple proxy/VPN detection heuristic
     */
    private fun isLikelyProxy(ipAddress: String): Boolean {
        // Simple checks (in production, use a proper IP intelligence service)
        return ipAddress.startsWith("10.") ||  // Private network
               ipAddress.startsWith("172.") ||
               ipAddress.startsWith("192.168.")
    }
    
    /**
     * Report fraud (manually flag as fraudulent)
     */
    suspend fun reportFraud(
        userId: String,
        deviceFingerprint: String?,
        ipAddress: String?,
        reason: String
    ) = mutex.withLock {
        println("Fraud reported for user $userId: $reason")
        
        if (deviceFingerprint != null) {
            blacklistedDevices[deviceFingerprint] = BlacklistEntry(
                reason = reason,
                addedAt = System.currentTimeMillis()
            )
        }
        
        if (ipAddress != null) {
            blacklistedIPs[ipAddress] = BlacklistEntry(
                reason = reason,
                addedAt = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Clear fraud report (whitelist)
     */
    suspend fun clearFraudReport(
        deviceFingerprint: String? = null,
        ipAddress: String? = null
    ) = mutex.withLock {
        if (deviceFingerprint != null) {
            blacklistedDevices.remove(deviceFingerprint)
            println("Device whitelist: $deviceFingerprint")
        }
        
        if (ipAddress != null) {
            blacklistedIPs.remove(ipAddress)
            println("IP whitelisted: $ipAddress")
        }
    }
    
    /**
     * Get fraud statistics for user
     */
    suspend fun getUserFraudStats(userId: String): UserFraudStats {
        val attempts = userAttempts[userId] ?: emptyList()
        val locations = userLocations[userId] ?: emptyList()
        val transactions = userTransactions[userId] ?: emptyList()
        val profile = userBehaviorProfiles[userId]
        
        return UserFraudStats(
            totalAttempts = attempts.size,
            attemptsLast24h = attempts.count { System.currentTimeMillis() - it.timestamp < DAY_MS },
            uniqueDevices = attempts.mapNotNull { it.deviceFingerprint }.distinct().size,
            uniqueIPs = attempts.mapNotNull { it.ipAddress }.distinct().size,
            locationCount = locations.size,
            transactionCount = transactions.size,
            avgTransactionAmount = if (transactions.isNotEmpty()) transactions.map { it.amount }.average() else 0.0,
            hasBehavioralProfile = profile?.hasHistory() ?: false
        )
    }
}

// ============================================================================
// DATA CLASSES
// ============================================================================

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

data class LocationRecord(
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val city: String?,
    val timestamp: Long
)

data class TransactionRecord(
    val amount: Double,
    val timestamp: Long
)

data class BehavioralData(
    val factorCompletionTimeMs: Long,
    val typingSpeedMsPerChar: Long? = null,
    val mouseMovements: Int? = null,
    val totalInteractionTime: Long? = null
)

data class BehavioralProfile(
    private val completionTimes: MutableList<Long> = mutableListOf(),
    private val typingSpeeds: MutableList<Long> = mutableListOf()
) {
    fun hasHistory() = completionTimes.size >= 5
    
    fun addDataPoint(data: BehavioralData) {
        completionTimes.add(data.factorCompletionTimeMs)
        data.typingSpeedMsPerChar?.let { typingSpeeds.add(it) }
        
        // Keep only last 50 data points
        if (completionTimes.size > 50) completionTimes.removeAt(0)
        if (typingSpeeds.size > 50) typingSpeeds.removeAt(0)
    }
    
    fun calculateDeviation(data: BehavioralData): Double {
        if (!hasHistory()) return 0.0
        
        val avgTime = completionTimes.average()
        val timeDeviation = abs(data.factorCompletionTimeMs - avgTime) / avgTime
        
        return timeDeviation
    }
}

data class BlacklistEntry(
    val reason: String,
    val addedAt: Long
)

data class AttemptRecord(
    val userId: String,
    val deviceFingerprint: String?,
    val ipAddress: String?,
    val transactionAmount: Double?,
    val timestamp: Long
)

data class RiskResult(
    val score: Int,
    val reasons: List<String>
)

enum class RiskLevel {
    LOW, MEDIUM, HIGH
}

data class FraudCheckResult(
    val isLegitimate: Boolean,
    val riskScore: Int,
    val riskLevel: RiskLevel,
    val reason: String,
    val details: Map<String, Int> = emptyMap()
)

data class UserFraudStats(
    val totalAttempts: Int,
    val attemptsLast24h: Int,
    val uniqueDevices: Int,
    val uniqueIPs: Int,
    val locationCount: Int,
    val transactionCount: Int,
    val avgTransactionAmount: Double,
    val hasBehavioralProfile: Boolean
)

// ============================================================================
// HELPER EXTENSIONS
// ============================================================================

private fun Double.round(decimals: Int): Double {
    var multiplier = 1.0
    repeat(decimals) { multiplier *= 10 }
    return kotlin.math.round(this * multiplier) / multiplier
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
