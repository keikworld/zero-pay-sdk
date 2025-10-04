package com.zeropay.sdk

import com.zeropay.sdk.crypto.CryptoUtils
import java.security.SecureRandom

/**
 * Cryptographically Secure Pseudo-Random Number Generator Shuffle
 * 
 * Uses SecureRandom for unpredictable factor ordering
 * Prevents timing attacks and prediction
 */
object CsprngShuffle {
    
    private val secureRandom = SecureRandom()
    
    /**
     * Fisher-Yates shuffle with CSPRNG
     * 
     * Security: Unpredictable order prevents:
     * - Factor prediction
     * - Timing attacks
     * - Pattern analysis
     */
    fun <T> shuffle(list: List<T>): List<T> {
        val mutableList = list.toMutableList()
        
        // Fisher-Yates shuffle with SecureRandom
        for (i in mutableList.size - 1 downTo 1) {
            // Generate cryptographically secure random index
            val j = secureRandom.nextInt(i + 1)
            
            // Swap
            val temp = mutableList[i]
            mutableList[i] = mutableList[j]
            mutableList[j] = temp
        }
        
        return mutableList
    }
    
    /**
     * Shuffle and take N elements
     */
    fun <T> shuffleAndTake(list: List<T>, count: Int): List<T> {
        require(count <= list.size) { "Cannot take $count elements from list of size ${list.size}" }
        return shuffle(list).take(count)
    }
    
    /**
     * Shuffle with seed (for testing only - DO NOT use in production)
     */
    @Deprecated("Only for testing", ReplaceWith("shuffle(list)"))
    fun <T> shuffleWithSeed(list: List<T>, seed: Long): List<T> {
        val testRandom = SecureRandom()
        testRandom.setSeed(seed)
        
        val mutableList = list.toMutableList()
        for (i in mutableList.size - 1 downTo 1) {
            val j = testRandom.nextInt(i + 1)
            val temp = mutableList[i]
            mutableList[i] = mutableList[j]
            mutableList[j] = temp
        }
        return mutableList
    }
}
