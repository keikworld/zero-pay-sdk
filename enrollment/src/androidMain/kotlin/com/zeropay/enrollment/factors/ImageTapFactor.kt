/**
 * ImageTapFactor - Enrollment Wrapper
 * Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/factors/ImageTapFactor.kt
 */
object ImageTapFactorEnrollment {
    
    fun processImageTaps(
        imageId: String,
        tapPoints: List<Pair<Float, Float>>
    ): Result<ByteArray> {
        return try {
            val digest = com.zeropay.sdk.factors.ImageTapFactor.digest(imageId, tapPoints)
            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun verifyImageTaps(
        imageId: String,
        inputTaps: List<Pair<Float, Float>>,
        storedDigest: ByteArray
    ): Boolean {
        return com.zeropay.sdk.factors.ImageTapFactor.verify(imageId, inputTaps, storedDigest)
    }
}

