package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * RhythmTapFactor - Enrollment Wrapper
 * Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/factors/RhythmTapFactor.kt
 * 
 * Wraps existing SDK RhythmTapFactor for enrollment
 */
object RhythmTapFactorEnrollment {
    
    data class RhythmTap(val timestamp: Long)
    
    fun processRhythmTaps(taps: List<RhythmTap>): Result<ByteArray> {
        // Delegate to SDK RhythmTapFactor
        return try {
            val sdkTaps = taps.map { 
                com.zeropay.sdk.factors.RhythmTapFactor.RhythmTap(it.timestamp) 
            }
            val digest = com.zeropay.sdk.factors.RhythmTapFactor.digest(sdkTaps)
            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun verifyRhythmTaps(
        inputTaps: List<RhythmTap>,
        storedDigest: ByteArray
    ): Boolean {
        val result = processRhythmTaps(inputTaps)
        if (result.isFailure) return false
        
        val inputDigest = result.getOrNull() ?: return false
        return CryptoUtils.constantTimeEquals(inputDigest, storedDigest)
    }
}
