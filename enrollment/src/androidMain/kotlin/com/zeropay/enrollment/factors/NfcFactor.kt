package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * NfcFactor - Enrollment Wrapper
 * Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/factors/NfcFactor.kt
 */
object NfcFactorEnrollment {
    
    fun processNfcTag(tagUid: ByteArray): Result<ByteArray> {
        return try {
            val digest = com.zeropay.sdk.factors.NfcFactor.digest(tagUid)
            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun verifyNfcTag(
        inputTagUid: ByteArray,
        storedDigest: ByteArray
    ): Boolean {
        return com.zeropay.sdk.factors.NfcFactor.verify(inputTagUid, storedDigest)
    }
}
