package com.zeropay.enrollment.factors

import com.zeropay.sdk.security.CryptoUtils
import java.util.Arrays

/**
 * ImageTapFactor - Enrollment Wrapper
 *
 * Wraps SDK ImageTapFactor for enrollment UI.
 * Uses SDK's TapPoint and ImageInfo types.
 *
 * @version 1.0.0
 * @date 2025-10-20
 */
object ImageTapFactorEnrollment {

    /**
     * Process image taps using SDK format
     */
    fun processImageTaps(
        imageId: String,
        tapPoints: List<Pair<Float, Float>>
    ): Result<ByteArray> {
        return try {
            // Convert to SDK TapPoint format
            val sdkTapPoints = tapPoints.map { (x, y) ->
                com.zeropay.sdk.factors.ImageTapFactor.TapPoint(
                    x = x,
                    y = y,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Get ImageInfo from approved images
            val approvedImages = com.zeropay.sdk.factors.ImageTapFactor.getApprovedImages()
            val imageInfo = approvedImages.find { it.imageId == imageId }
                ?: approvedImages.first() // Fallback to first image

            // Use SDK digest method
            val digest = com.zeropay.sdk.factors.ImageTapFactor.digest(sdkTapPoints, imageInfo)

            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verify image taps using SDK format
     */
    fun verifyImageTaps(
        imageId: String,
        inputTaps: List<Pair<Float, Float>>,
        storedDigest: ByteArray
    ): Boolean {
        return try {
            // Convert to SDK TapPoint format
            val sdkTapPoints = inputTaps.map { (x, y) ->
                com.zeropay.sdk.factors.ImageTapFactor.TapPoint(
                    x = x,
                    y = y,
                    timestamp = System.currentTimeMillis()
                )
            }

            // Get ImageInfo
            val approvedImages = com.zeropay.sdk.factors.ImageTapFactor.getApprovedImages()
            val imageInfo = approvedImages.find { it.imageId == imageId }
                ?: approvedImages.first()

            // Use SDK verify method
            com.zeropay.sdk.factors.ImageTapFactor.verify(storedDigest, sdkTapPoints, imageInfo)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get available images for UI
     */
    fun getAvailableImages(): List<String> {
        return com.zeropay.sdk.factors.ImageTapFactor.getApprovedImages()
            .map { it.imageId }
    }
}
