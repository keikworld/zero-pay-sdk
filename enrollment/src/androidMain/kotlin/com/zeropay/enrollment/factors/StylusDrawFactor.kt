package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * StylusDrawFactor
 * Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/factors/StylusDrawFactor.kt
 * 
 * These are implemented in SDK Canvas files (MouseCanvas.kt, StylusCanvas.kt)
 * and can be used directly. The factor objects are already defined there.
 * For enrollment, we just need to expose the digest methods:
 */

object StylusDrawFactorEnrollment {
    data class StylusPoint(val x: Float, val y: Float, val pressure: Float, val t: Long)
    
    fun processStylusDrawing(points: List<StylusPoint>): Result<ByteArray> {
        return try {
            val sdkPoints = points.map {
                com.zeropay.sdk.factors.StylusFactor.StylusPoint(it.x, it.y, it.pressure, it.t)
            }
            val digest = com.zeropay.sdk.factors.StylusFactor.digestFull(sdkPoints)
            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
