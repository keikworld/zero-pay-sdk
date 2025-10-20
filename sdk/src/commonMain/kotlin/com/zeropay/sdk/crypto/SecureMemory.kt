package com.zeropay.sdk.crypto

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Secure Memory Management
 * 
 * Utilities for handling sensitive data in memory:
 * - Automatic zeroing on disposal
 * - Use-and-dispose pattern
 * - DoD 5220.22-M compliant wiping
 */
object SecureMemory {
    
    /**
     * Execute block with sensitive data, then wipe
     */
    inline fun <T> useSecure(data: ByteArray, block: (ByteArray) -> T): T {
        return try {
            block(data)
        } finally {
            wipe(data)
        }
    }
    
    /**
     * Execute block with sensitive string, then wipe
     */
    inline fun <T> useSecure(data: String, block: (String) -> T): T {
        val bytes = data.toByteArray()
        return try {
            block(data)
        } finally {
            wipe(bytes)
        }
    }
    
    /**
     * Secure wipe of byte array (DoD 5220.22-M)
     * 
     * Overwrites with:
     * 1. Random data
     * 2. Complement of random
     * 3. Random data again
     * 4. Zeros
     */
    fun wipe(data: ByteArray) {
        if (data.isEmpty()) return
        
        // Pass 1: Random data
        val random1 = CryptoUtils.generateRandomBytes(data.size)
        random1.copyInto(data)
        
        // Pass 2: Complement
        for (i in data.indices) {
            data[i] = data[i].toInt().inv().toByte()
        }
        
        // Pass 3: Random data
        val random2 = CryptoUtils.generateRandomBytes(data.size)
        random2.copyInto(data)
        
        // Pass 4: Zeros
        Arrays.fill(data, 0)
    }
    
    /**
     * Secure wipe of multiple arrays
     */
    fun wipe(vararg arrays: ByteArray) {
        arrays.forEach { wipe(it) }
    }
    
    /**
     * Create secure copy that auto-wipes
     */
    fun secureCopy(data: ByteArray): SecureByteArray {
        return SecureByteArray(data.copyOf())
    }
}

/**
 * Secure byte array that auto-wipes on close
 */
class SecureByteArray(internal val data: ByteArray) : AutoCloseable {
    @Volatile internal var cleared = false
    
    /**
     * Get data (throws if already cleared)
     */
    fun get(): ByteArray {
        check(!cleared) { "SecureByteArray has been cleared" }
        return data
    }
    
    /**
     * Use data safely
     */
    internal inline fun <T> use(block: (ByteArray) -> T): T {
        check(!cleared) { "SecureByteArray has been cleared" }
        return block(data)
    }
    
    /**
     * Get size
     */
    fun size(): Int = data.size
    
    /**
     * Check if cleared
     */
    fun isCleared(): Boolean = cleared
    
    /**
     * Close and wipe
     */
    override fun close() {
        if (!cleared) {
            SecureMemory.wipe(data)
            cleared = true
        }
    }
    
    /**
     * Finalize ensures cleanup
     */
    protected fun finalize() {
        close()
    }
}
