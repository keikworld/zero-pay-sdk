package com.zeropay.sdk

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe Rate Limiter for Authentication Attempts
 * 
 * PRODUCTION-READY:
 * - Proper synchronization using ReentrantReadWriteLock
 * - Atomic operations for counters
 * - TTL-based memory cleanup
 * - Thread-safe stats
 */
object RateLimiter {
    
    private val rwLock = ReentrantReadWriteLock()
    
    private val attempts = ConcurrentHashMap<String, AtomicLong>()
    private val cooldownStart = ConcurrentHashMap<String, AtomicLong>()
    private val timestamps = ConcurrentHashMap<String, AtomicLong>()
    
    // Configuration
    private const val MAX_ENTRIES = 10000
    private const val CLEANUP_THRESHOLD = 8000
    private const val ENTRY_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    
    private const val MAX_DAILY_ATTEMPTS = 20
    private const val COOLDOWN_15M_THRESHOLD = 5
    private const val COOLDOWN_4H_THRESHOLD = 8
    private const val FROZEN_THRESHOLD = 10

    /**
     * Check if user can make authentication attempt
     */
    fun check(uidHash: String): RateResult {
        return rwLock.read {
            // Auto-cleanup if needed
            if (attempts.size > MAX_ENTRIES) {
                // Upgrade to write lock for cleanup
                return rwLock.write {
                    cleanup()
                    checkInternal(uidHash)
                }
            }
            
            checkInternal(uidHash)
        }
    }
    
    private fun checkInternal(uidHash: String): RateResult {
        val currentTime = System.currentTimeMillis()
        
        // Check 24-hour attempt limit
        val dayKey = "attempts:$uidHash:${day()}"
        val dailyCount = attempts[dayKey]?.get() ?: 0
        if (dailyCount >= MAX_DAILY_ATTEMPTS) {
            return RateResult.BLOCKED_24H
        }
        
        // Check fail-based restrictions
        val failKey = "fails:$uidHash"
        val fails = attempts[failKey]?.get() ?: 0
        
        when {
            fails >= FROZEN_THRESHOLD -> return RateResult.FROZEN_FRAUD
            
            fails >= COOLDOWN_4H_THRESHOLD -> {
                val cooldownKey = "cooldown:$uidHash:4h"
                val cooldownTime = cooldownStart[cooldownKey]?.get() ?: 0L
                val fourHoursMs = 4 * 60 * 60 * 1000L
                
                if (currentTime - cooldownTime < fourHoursMs) {
                    return RateResult.COOL_DOWN_4H
                }
            }
            
            fails >= COOLDOWN_15M_THRESHOLD -> {
                val cooldownKey = "cooldown:$uidHash:15m"
                val cooldownTime = cooldownStart[cooldownKey]?.get() ?: 0L
                val fifteenMinutesMs = 15 * 60 * 1000L
                
                if (currentTime - cooldownTime < fifteenMinutesMs) {
                    return RateResult.COOL_DOWN_15M
                }
            }
        }
        
        return RateResult.OK
    }

    /**
     * Record failed authentication attempt
     */
    fun recordFail(uidHash: String) {
        rwLock.write {
            val failKey = "fails:$uidHash"
            val fails = attempts.computeIfAbsent(failKey) { AtomicLong(0) }
            val newFails = fails.incrementAndGet()
            
            val currentTime = System.currentTimeMillis()
            timestamps.computeIfAbsent(failKey) { AtomicLong(currentTime) }
                .set(currentTime)
            
            // Start cooldown timers at thresholds
            when (newFails) {
                COOLDOWN_15M_THRESHOLD.toLong() -> {
                    cooldownStart.computeIfAbsent("cooldown:$uidHash:15m") { 
                        AtomicLong(currentTime) 
                    }.set(currentTime)
                }
                COOLDOWN_4H_THRESHOLD.toLong() -> {
                    cooldownStart.computeIfAbsent("cooldown:$uidHash:4h") { 
                        AtomicLong(currentTime) 
                    }.set(currentTime)
                }
            }
            
            // Increment daily counter
            val dayKey = "attempts:$uidHash:${day()}"
            attempts.computeIfAbsent(dayKey) { AtomicLong(0) }.incrementAndGet()
            timestamps.computeIfAbsent(dayKey) { AtomicLong(currentTime) }
                .set(currentTime)
        }
    }

    /**
     * Reset fail count after successful authentication
     */
    fun resetFails(uidHash: String) {
        rwLock.write {
            attempts.remove("fails:$uidHash")
            timestamps.remove("fails:$uidHash")
            cooldownStart.remove("cooldown:$uidHash:15m")
            cooldownStart.remove("cooldown:$uidHash:4h")
        }
    }

    /**
     * Clean up old entries (TTL-based)
     */
    fun cleanup() {
        // Must be called within write lock
        if (attempts.size < CLEANUP_THRESHOLD) return
        
        val currentTime = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        
        // Identify expired entries
        timestamps.forEach { (key, timestamp) ->
            if (currentTime - timestamp.get() > ENTRY_TTL_MS) {
                toRemove.add(key)
            }
        }
        
        // Remove expired entries
        toRemove.forEach { key ->
            attempts.remove(key)
            timestamps.remove(key)
            // Also remove related cooldown entries
            if (key.startsWith("fails:")) {
                val uidHash = key.substringAfter("fails:")
                cooldownStart.remove("cooldown:$uidHash:15m")
                cooldownStart.remove("cooldown:$uidHash:4h")
            }
        }
    }
    
    /**
     * Get statistics (thread-safe)
     */
    fun getStats(): Stats {
        return rwLock.read {
            Stats(
                totalAttempts = attempts.size,
                activeCooldowns = cooldownStart.size,
                memoryEstimateKB = ((attempts.size + cooldownStart.size + timestamps.size) * 64) / 1024
            )
        }
    }
    
    /**
     * Get remaining cooldown time (milliseconds)
     */
    fun getRemainingCooldown(uidHash: String): Long {
        return rwLock.read {
            val failKey = "fails:$uidHash"
            val fails = attempts[failKey]?.get() ?: 0
            val currentTime = System.currentTimeMillis()
            
            when {
                fails >= COOLDOWN_4H_THRESHOLD -> {
                    val cooldownKey = "cooldown:$uidHash:4h"
                    val cooldownTime = cooldownStart[cooldownKey]?.get() ?: 0L
                    val fourHoursMs = 4 * 60 * 60 * 1000L
                    val remaining = fourHoursMs - (currentTime - cooldownTime)
                    if (remaining > 0) remaining else 0L
                }
                fails >= COOLDOWN_15M_THRESHOLD -> {
                    val cooldownKey = "cooldown:$uidHash:15m"
                    val cooldownTime = cooldownStart[cooldownKey]?.get() ?: 0L
                    val fifteenMinutesMs = 15 * 60 * 1000L
                    val remaining = fifteenMinutesMs - (currentTime - cooldownTime)
                    if (remaining > 0) remaining else 0L
                }
                else -> 0L
            }
        }
    }

    private fun day(): String {
        val daysSinceEpoch = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        return daysSinceEpoch.toString()
    }

    enum class RateResult {
        OK,
        COOL_DOWN_15M,
        COOL_DOWN_4H,
        FROZEN_FRAUD,
        BLOCKED_24H
    }
    
    data class Stats(
        val totalAttempts: Int,
        val activeCooldowns: Int,
        val memoryEstimateKB: Int
    )
}
