// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/models/EnrollmentMetrics.kt

package com.zeropay.enrollment.models

/**
 * Enrollment metrics for monitoring
 */
data class EnrollmentMetrics(
    val successCount: Int,
    val failureCount: Int,
    val activeSessionCount: Int,
    val successRate: Double
) {
    fun toLogString(): String {
        return """
            |EnrollmentMetrics {
            |  success: $successCount
            |  failure: $failureCount
            |  active: $activeSessionCount
            |  rate: ${"%.2f".format(successRate)}%
            |}
        """.trimMargin()
    }
}
