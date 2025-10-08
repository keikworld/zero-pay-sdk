/**
 * MouseDrawFactor & StylusDrawFactor
 * Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/factors/MouseDrawFactor.kt
 * 
 * These are implemented in SDK Canvas files (MouseCanvas.kt, StylusCanvas.kt)
 * and can be used directly. The factor objects are already defined there.
 * For enrollment, we just need to expose the digest methods:
 */
object MouseDrawFactorEnrollment {
    data class MousePoint(val x: Float, val y: Float, val t: Long)
    
    fun processMouseDrawing(points: List<MousePoint>): Result<ByteArray> {
        return try {
            val sdkPoints = points.map {
                com.zeropay.sdk.factors.MouseFactor.MousePoint(it.x, it.y, it.t)
            }
            val digest = com.zeropay.sdk.factors.MouseFactor.digestMicroTiming(sdkPoints)
            Result.success(digest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
