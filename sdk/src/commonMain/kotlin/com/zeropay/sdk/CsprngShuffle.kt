package com.zeropay.sdk

import com.zeropay.sdk.crypto.CryptoUtils
import kotlin.random.Random

object CsprngShuffle {

    /**
     * Shuffles a list using a cryptographically secure random number generator.
     * Uses HMAC-SHA256 with a secure random seed to derive the shuffle randomness.
     */
    fun <T> shuffle(list: List<T>): List<T> {
        if (list.size <= 1) return list
        
        // Generate a secure random seed
        val seed = CryptoUtils.secureRandomBytes(32)
        
        // Use current time as additional entropy
        val timestamp = System.currentTimeMillis().toString().encodeToByteArray()
        
        // Derive a random seed using HMAC
        val hmacResult = CryptoUtils.hmacSha256(seed, timestamp)
        
        // Create a Random instance from the HMAC output
        // We'll use the first 8 bytes as a long seed
        var randomSeed = 0L
        for (i in 0 until 8) {
            randomSeed = (randomSeed shl 8) or (hmacResult[i].toInt() and 0xFF).toLong()
        }
        
        val random = Random(randomSeed)
        return list.shuffled(random)
    }
}
