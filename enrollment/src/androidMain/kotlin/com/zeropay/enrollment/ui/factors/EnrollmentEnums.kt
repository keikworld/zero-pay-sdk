// Path: enrollment/src/androidMain/kotlin/com/zeropay/enrollment/ui/factors/EnrollmentEnums.kt

package com.zeropay.enrollment.ui.factors

/**
 * Shared enums for enrollment UI factors
 *
 * These enums are used across multiple factor enrollment canvases
 * to avoid redeclaration errors and maintain consistency.
 *
 * @version 1.0.0
 * @date 2025-10-25
 */

/**
 * Biometric status for Face and Fingerprint enrollment
 */
internal enum class BiometricStatus {
    CHECKING,
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NOT_ENROLLED,
    SECURITY_UPDATE_REQUIRED,
    UNSUPPORTED,
    UNKNOWN
}

/**
 * Biometric stage for Face and Fingerprint enrollment
 */
internal enum class BiometricStage {
    INITIAL,
    CONFIRM
}

/**
 * Draw stage for Stylus and Mouse draw enrollment
 */
internal enum class DrawStage {
    INITIAL,
    CONFIRM
}
