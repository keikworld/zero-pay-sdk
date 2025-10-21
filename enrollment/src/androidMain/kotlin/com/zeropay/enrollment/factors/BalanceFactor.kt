package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * Balance Factor - PRODUCTION VERSION (Enrollment Wrapper)
 *
 * Wraps SDK BalanceFactor for enrollment UI.
 *
 * This module provides UI-specific helpers while delegating
 * core digest generation to SDK's BalanceFactor.
 *
 * @version 1.0.0
 * @date 2025-10-20
 */
object BalanceFactor {
    
    private const val MIN_DURATION_MS = 3000  // 3 seconds
    private const val MAX_DURATION_MS = 5000  // 5 seconds
    private const val SAMPLE_RATE_HZ = 50     // 50 samples/second
    private const val MIN_SAMPLES = 150       // 3 seconds * 50Hz
    private const val MAX_SAMPLES = 250       // 5 seconds * 50Hz
    
    data class AccelerometerSample(
        val x: Float,
        val y: Float,
        val z: Float,
        val timestamp: Long
    )
    
    /**
     * Process balance sensor data for enrollment
     * 
     * @param samples List of accelerometer samples
     * @return Result with SHA-256 digest or error
     */
    fun processBalanceData(samples: List<AccelerometerSample>): Result<ByteArray> {
        try {
            // ========== INPUT VALIDATION ==========
            
            if (samples.size < MIN_SAMPLES) {
                return Result.failure(
                    IllegalArgumentException(
                        "Too few samples. Hold device steady for at least $MIN_DURATION_MS ms."
                    )
                )
            }
            
            if (samples.size > MAX_SAMPLES) {
                return Result.failure(
                    IllegalArgumentException(
                        "Too many samples. Maximum $MAX_DURATION_MS ms allowed."
                    )
                )
            }
            
            // ========== MOVEMENT PATTERN ANALYSIS ==========
            
            // Extract balance features
            val features = extractBalanceFeatures(samples)
            
            // Convert to SDK BalancePoint format and use SDK digest method
            val balancePoints = samples.map { sample ->
                com.zeropay.sdk.factors.BalanceFactor.BalancePoint(
                    x = sample.x,
                    y = sample.y,
                    z = sample.z,
                    timestamp = sample.timestamp
                )
            }

            // Use SDK's digest method
            val digest = com.zeropay.sdk.factors.BalanceFactor.digest(balancePoints)

            // Memory wiping
            Arrays.fill(features, 0.toByte())
            
            return Result.success(digest)
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
    
    /**
     * Extract balance features from accelerometer data
     */
    private fun extractBalanceFeatures(samples: List<AccelerometerSample>): ByteArray {
        val features = mutableListOf<Byte>()
        
        // Add all sample data
        samples.forEach { sample ->
            // Convert float to bytes (simple implementation)
            features.addAll(sample.x.toBits().toLong().let { CryptoUtils.longToBytes(it) }.toList())
            features.addAll(sample.y.toBits().toLong().let { CryptoUtils.longToBytes(it) }.toList())
            features.addAll(sample.z.toBits().toLong().let { CryptoUtils.longToBytes(it) }.toList())
            features.addAll(CryptoUtils.longToBytes(sample.timestamp).toList())
        }
        
        return features.toByteArray()
    }
    
    fun verifyBalanceData(
        inputSamples: List<AccelerometerSample>,
        storedDigest: ByteArray
    ): Boolean {
        val result = processBalanceData(inputSamples)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        return CryptoUtils.constantTimeEquals(inputDigest, storedDigest)
    }
    
    fun getMinDurationMs(): Long = MIN_DURATION_MS.toLong()
    fun getMaxDurationMs(): Long = MAX_DURATION_MS.toLong()
    fun getSampleRate(): Int = SAMPLE_RATE_HZ
}
