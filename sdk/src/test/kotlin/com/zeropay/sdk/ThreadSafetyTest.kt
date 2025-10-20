package com.zeropay.sdk

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.security.KeyDerivation
import com.zeropay.sdk.factors.PinFactor
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Thread Safety Tests
 * 
 * Ensures all thread-safe components work correctly under concurrent access
 */
class ThreadSafetyTest {
    
    @Test
    fun testRateLimiter_ConcurrentAccess_ThreadSafe() {
        val uid = "test-concurrent-${System.currentTimeMillis()}"
        val threadCount = 50
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        
        val threads = (1..threadCount).map {
            thread {
                barrier.await() // Synchronize start
                RateLimiter.recordFail(uid)
                latch.countDown()
            }
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        
        // Should have exactly threadCount failures
        val result = RateLimiter.check(uid)
        assertEquals(RateLimiter.RateResult.FROZEN_FRAUD, result)
    }
    
    @Test
    fun testKeyDerivation_ConcurrentDerivation_NoCrash() {
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)
        
        val threads = (1..threadCount).map { i ->
            thread {
                try {
                    val uuid = "user_$i"
                    val factors = listOf("pin_$i", "colour_$i")
                    val derivedKey = KeyDerivation.deriveKey(uuid, factors)
                    assertEquals(32, derivedKey.size)
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        
        assertEquals("All derivations should succeed", 0, errors.get())
    }
    
    @Test
    fun testConstantTime_ConcurrentComparison_Consistent() {
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 1 }
        val c = ByteArray(32) { 2 }
        
        val threadCount = 100
        val latch = CountDownLatch(threadCount)
        val results = mutableListOf<Boolean>()
        val lock = Any()
        
        val threads = (1..threadCount).map {
            thread {
                val result = ConstantTime.equals(a, b)
                synchronized(lock) {
                    results.add(result)
                }
                latch.countDown()
            }
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        
        // All results should be true
        assertTrue(results.all { it })
        
        // Different arrays should be false
        assertFalse(ConstantTime.equals(a, c))
    }
    
    @Test
    fun testPinFactor_ConcurrentVerification_ThreadSafe() {
        val pin = "123456"
        val derived = PinFactor.digest(pin)
        
        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val successes = AtomicInteger(0)
        
        val threads = (1..threadCount).map {
            thread {
                if (PinFactor.verify(pin, derived)) {
                    successes.incrementAndGet()
                }
                latch.countDown()
            }
        }
        
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        
        assertEquals("All verifications should succeed", threadCount, successes.get())
    }
    
    @Test
    fun testRateLimiter_Stats_Consistent() {
        val uid = "test-stats-${System.currentTimeMillis()}"
        
        // Record some failures
        repeat(5) {
            RateLimiter.recordFail(uid)
        }
        
        // Get stats from multiple threads
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val statsList = mutableListOf<RateLimiter.Stats>()
        val lock = Any()
        
        val threads = (1..threadCount).map {
            thread {
                val stats = RateLimiter.getStats()
                synchronized(lock) {
                    statsList.add(stats)
                }
                latch.countDown()
            }
        }
        
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        threads.forEach { it.join() }
        
        // All stats should be consistent
        val firstStats = statsList.first()
        assertTrue(statsList.all { 
            it.totalAttempts == firstStats.totalAttempts 
        })
    }
}
