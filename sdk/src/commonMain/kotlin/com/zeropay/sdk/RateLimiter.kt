package com.zeropay.sdk

import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.Volatile

/**
 * Thread-safe rate limiter for authentication attempts.
 * Tracks failed attempts and enforces cooldowns and blocks.
 * 
 * FIXED: Added automatic memory cleanup to prevent leaks
 */
object RateLimiter {

    private val lock = Any()
    
    @Volatile
    private val attempts = mutableMapOf<String, Int>()
    
    @Volatile
    private val cooldownStart = mutableMapOf<String, Long>()
    
    private val startTime = System.currentTimeMillis()
    
    // Auto-cleanup timer
    private val cleanupTimer = Timer("RateLimiter-Cleanup", true)
    
    init {
        // Schedule cleanup every hour
        cleanupTimer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                cleanup()
            }
        }, 3600000, 3600000) // 1 hour initial delay, 1 hour period
        
        // Also schedule immediate cleanup after 5 minutes
        cleanupTimer.schedule(object : TimerTask() {
            override fun run() {
                cleanup()
            }
        }, 300000) // 5 minutes
    }

    /**
     * Checks if the user can make an authentication attempt.
     * Returns the current rate limit status.
     */
    fun check(uidHash: String): RateResult {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            
            // Check 24-hour attempt limit
            val dayKey = "attempts:$uidHash:${day()}"
            val dailyCount = attempts.getOrDefault(dayKey, 0)
            if (dailyCount >= 20) {
                return RateResult.BLOCKED_24H
            }
            
            // Check fail-based restrictions
            val failKey = "fails:$uidHash"
            val fails = attempts.getOrDefault(failKey, 0)
            
            when {
                fails >= 10 -> return RateResult.FROZEN_FRAUD
                
                fails >= 8 -> {
                    // Check 4-hour cooldown
                    val cooldownKey = "cooldown:$uidHash:4h"
                    val cooldownTime = cooldownStart[cooldownKey] ?: 0L
                    val fourHoursMs = 4 * 60 * 60 * 1000L
                    
                    if (currentTime - cooldownTime < fourHoursMs) {
                        return RateResult.COOL_DOWN_4H
                    }
                }
                
                fails >= 5 -> {
                    // Check 15-minute cooldown
                    val cooldownKey = "cooldown:$uidHash:15m"
                    val cooldownTime = cooldownStart[cooldownKey] ?: 0L
                    val fifteenMinutesMs = 15 * 60 * 1000L
                    
                    if (currentTime - cooldownTime < fifteenMinutesMs) {
                        return RateResult.COOL_DOWN_15M
                    }
                }
            }
            
            // Increment daily attempt counter
            attempts[dayKey] = dailyCount + 1
            
            return RateResult.OK
        }
    }

    /**
     * Records a failed authentication attempt.
     */
    fun recordFail(uidHash: String) {
        synchronized(lock) {
            val failKey = "fails:$uidHash"
            val fails = attempts.getOrDefault(failKey, 0) + 1
            attempts[failKey] = fails
            
            val currentTime = System.currentTimeMillis()
            
            // Start cooldown timers at thresholds
            when (fails) {
                5 -> cooldownStart["cooldown:$uidHash:15m"] = currentTime
                8 -> cooldownStart["cooldown:$uidHash:4h"] = currentTime
            }
        }
    }

    /**
     * Resets fail count after successful authentication.
     */
    fun resetFails(uidHash: String) {
        synchronized(lock) {
            attempts.remove("fails:$uidHash")
            cooldownStart.remove("cooldown:$uidHash:15m")
            cooldownStart.remove("cooldown:$uidHash:4h")
        }
    }

    /**
     * Cleans up old entries to prevent memory leaks.
     * Called automatically every hour.
     */
    fun cleanup() {
        synchronized(lock) {
            val currentDay = day()
            val currentTime = System.currentTimeMillis()
            val keysToRemove = mutableListOf<String>()
            
            // Clean up old attempt entries
            for (key in attempts.keys) {
                if (key.startsWith("attempts:")) {
                    val parts = key.split(":")
                    if (parts.size >= 3) {
                        val keyDay = parts[2]
                        // Remove entries older than 2 days
                        keyDay.toLongOrNull()?.let { day ->
                            if (currentDay.toLong() - day > 1) {
                                keysToRemove.add(key)
                            }
                        }
                    }
                }
            }
            
            keysToRemove.forEach { attempts.remove(it) }
            
            // Clean up old cooldown timestamps
            val cooldownKeysToRemove = mutableListOf<String>()
            
            for ((key, timestamp) in cooldownStart) {
                // Remove cooldowns older than 24 hours
                if (currentTime - timestamp > 24 * 60 * 60 * 1000L) {
                    cooldownKeysToRemove.add(key)
                }
            }
            
            cooldownKeysToRemove.forEach { cooldownStart.remove(it) }
            
            // Log cleanup stats (privacy-safe)
            println("RateLimiter: Cleaned ${keysToRemove.size} attempt entries, ${cooldownKeysToRemove.size} cooldown entries")
        }
    }
    
    /**
     * Get current memory usage stats (for monitoring)
     */
    fun getStats(): Stats {
        synchronized(lock) {
            return Stats(
                totalAttempts = attempts.size,
                activeCooldowns = cooldownStart.size,
                memoryEstimateKB = ((attempts.size + cooldownStart.size) * 64) / 1024 // Rough estimate
            )
        }
    }
    
    /**
     * Manually shutdown cleanup timer (for testing)
     */
    fun shutdown() {
        cleanupTimer.cancel()
    }

    private fun day(): String {
        val daysSinceEpoch = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        return daysSinceEpoch.toString()
    }

    enum class RateResult {
        /** Authentication attempt is allowed */
        OK,
        
        /** 15-minute cooldown after 5 failures */
        COOL_DOWN_15M,
        
        /** 4-hour cooldown after 8 failures */
        COOL_DOWN_4H,
        
        /** Account frozen due to suspected fraud (10+ failures) */
        FROZEN_FRAUD,
        
        /** Blocked for 24 hours due to too many attempts */
        BLOCKED_24H
    }
    
    data class Stats(
        val totalAttempts: Int,
        val activeCooldowns: Int,
        val memoryEstimateKB: Int
    )
}
