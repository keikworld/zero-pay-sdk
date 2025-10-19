package com.zeropay.sdk.security

import android.content.Context
import com.zeropay.sdk.security.AntiTampering.Severity
import com.zeropay.sdk.security.AntiTampering.Threat

/**
 * SecurityPolicy - Graduated Response Framework
 *
 * Evaluates security threats and determines appropriate actions.
 * Implements a graduated response system from warnings to permanent blocks.
 *
 * Action Levels:
 * - ALLOW: No security concerns, proceed normally
 * - WARN: Low-risk issues, show warning but allow
 * - DEGRADE: Medium-risk, allow with restrictions
 * - BLOCK_TEMPORARY: High-risk, block until user resolves
 * - BLOCK_PERMANENT: Critical, permanent block
 */
object SecurityPolicy {

    /**
     * Security action levels - graduated response
     */
    enum class SecurityAction {
        ALLOW,              // No threats detected - proceed normally
        WARN,               // Low-risk threats - show warning, allow proceed
        DEGRADE,            // Medium-risk - allow with restrictions/monitoring
        BLOCK_TEMPORARY,    // High-risk - block until user resolves issue
        BLOCK_PERMANENT     // Critical - permanent block, cannot proceed
    }

    /**
     * Security decision result
     */
    data class SecurityDecision(
        val action: SecurityAction,
        val threats: List<Threat>,
        val severity: Severity,
        val userMessage: String,
        val resolutionInstructions: List<String>,
        val allowRetry: Boolean,
        val merchantAlert: MerchantAlert?
    )

    /**
     * Merchant alert payload
     */
    data class MerchantAlert(
        val alertType: AlertType,
        val severity: Severity,
        val threats: List<String>,
        val userId: String?,
        val timestamp: Long,
        val requiresAction: Boolean
    )

    enum class AlertType {
        SECURITY_THREAT_DETECTED,
        DEGRADED_MODE_ACTIVE,
        FRAUD_ATTEMPT_SUSPECTED,
        PERMANENT_BLOCK_ISSUED
    }

    /**
     * Configuration for security policy behavior
     */
    data class SecurityPolicyConfig(
        // Root/Jailbreak
        val rootedDeviceAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,
        val magiskAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Debugging
        val debuggerAction: SecurityAction = SecurityAction.BLOCK_TEMPORARY,
        val developerModeAction: SecurityAction = SecurityAction.BLOCK_TEMPORARY,
        val adbEnabledAction: SecurityAction = SecurityAction.BLOCK_TEMPORARY,
        val adbConnectedAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Emulators
        val emulatorAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Hooking frameworks
        val hookingFrameworkAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,
        val fridaAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Integrity
        val apkModifiedAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,
        val signatureInvalidAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Other
        val mockLocationAction: SecurityAction = SecurityAction.WARN,
        val sslPinningBypassAction: SecurityAction = SecurityAction.BLOCK_PERMANENT,

        // Merchant options
        val merchantCanOverrideDegradedMode: Boolean = true,
        val alertMerchantOnDegradedMode: Boolean = true,
        val alertMerchantOnBlockedAttempt: Boolean = true
    )

    private var config: SecurityPolicyConfig = SecurityPolicyConfig()

    /**
     * Update policy configuration
     */
    fun configure(newConfig: SecurityPolicyConfig) {
        config = newConfig
    }

    /**
     * Get current configuration
     */
    fun getConfig(): SecurityPolicyConfig = config

    /**
     * Evaluate threats and determine security action
     */
    fun evaluateThreats(
        context: Context,
        userId: String? = null,
        merchantId: String? = null
    ): SecurityDecision {
        // Run comprehensive security check
        val tamperResult = AntiTampering.checkTamperingComprehensive(context)

        // If no threats, allow
        if (!tamperResult.isTampered) {
            return SecurityDecision(
                action = SecurityAction.ALLOW,
                threats = emptyList(),
                severity = Severity.NONE,
                userMessage = "Security check passed",
                resolutionInstructions = emptyList(),
                allowRetry = false,
                merchantAlert = null
            )
        }

        // Determine action based on threats
        val action = determineAction(tamperResult.threats, tamperResult.severity)

        // Generate user message and resolution instructions
        val userMessage = generateUserMessage(action, tamperResult.threats, tamperResult.severity)
        val resolutionInstructions = generateResolutionInstructions(tamperResult.threats, action)

        // Determine if retry is allowed
        val allowRetry = when (action) {
            SecurityAction.ALLOW, SecurityAction.WARN -> false
            SecurityAction.DEGRADE, SecurityAction.BLOCK_TEMPORARY -> true
            SecurityAction.BLOCK_PERMANENT -> false
        }

        // Generate merchant alert if needed
        val merchantAlert = if (shouldAlertMerchant(action, tamperResult.severity)) {
            createMerchantAlert(action, tamperResult.threats, tamperResult.severity, userId)
        } else {
            null
        }

        return SecurityDecision(
            action = action,
            threats = tamperResult.threats,
            severity = tamperResult.severity,
            userMessage = userMessage,
            resolutionInstructions = resolutionInstructions,
            allowRetry = allowRetry,
            merchantAlert = merchantAlert
        )
    }

    /**
     * Determine appropriate action based on threats
     */
    private fun determineAction(threats: List<Threat>, severity: Severity): SecurityAction {
        // Check for any permanent block threats
        val permanentBlockThreats = setOf(
            Threat.ROOT_DETECTED,
            Threat.MAGISK_DETECTED,
            Threat.ADB_CONNECTED,
            Threat.EMULATOR_PROPERTIES_DETECTED,
            Threat.EMULATOR_FILES_DETECTED,
            Threat.GENERIC_BUILD_DETECTED,
            Threat.FRIDA_DETECTED,
            Threat.XPOSED_DETECTED,
            Threat.EDXPOSED_DETECTED,
            Threat.LSPOSED_DETECTED,
            Threat.APK_MODIFIED,
            Threat.APK_SIGNATURE_INVALID,
            Threat.SSL_PINNING_BYPASSED,
            Threat.PROCESS_INJECTION_DETECTED,
            Threat.MEMORY_TAMPERED
        )

        if (threats.any { it in permanentBlockThreats }) {
            return SecurityAction.BLOCK_PERMANENT
        }

        // Check for temporary block threats (user can resolve)
        val temporaryBlockThreats = setOf(
            Threat.DEBUGGER_ATTACHED,
            Threat.DEBUGGER_CONNECTED,
            Threat.DEVELOPER_MODE_ENABLED,
            Threat.ADB_ENABLED,
            Threat.TRACER_PID_DETECTED,
            Threat.DEBUG_PORT_OPEN
        )

        if (threats.any { it in temporaryBlockThreats }) {
            return SecurityAction.BLOCK_TEMPORARY
        }

        // Check for degraded mode threats
        val degradeThreats = setOf(
            Threat.VPN_DETECTED,
            Threat.PROXY_DETECTED
        )

        if (threats.any { it in degradeThreats }) {
            return SecurityAction.DEGRADE
        }

        // Low-risk threats - just warn
        val warnThreats = setOf(
            Threat.MOCK_LOCATION_ENABLED,
            Threat.SU_BINARY_DETECTED,
            Threat.SUPERUSER_APK_DETECTED
        )

        if (threats.any { it in warnThreats }) {
            return SecurityAction.WARN
        }

        // Fallback based on severity
        return when (severity) {
            Severity.CRITICAL -> SecurityAction.BLOCK_PERMANENT
            Severity.HIGH -> SecurityAction.BLOCK_TEMPORARY
            Severity.MEDIUM -> SecurityAction.WARN
            Severity.LOW -> SecurityAction.WARN
            Severity.NONE -> SecurityAction.ALLOW
        }
    }

    /**
     * Generate user-friendly message
     */
    private fun generateUserMessage(
        action: SecurityAction,
        threats: List<Threat>,
        severity: Severity
    ): String {
        return when (action) {
            SecurityAction.ALLOW -> {
                "Security verification successful. You may proceed."
            }

            SecurityAction.WARN -> {
                "Security Warning: We detected ${threats.size} potential security concern(s). " +
                "You may continue, but we recommend reviewing the issues below."
            }

            SecurityAction.DEGRADE -> {
                "Security Notice: Your device has ${threats.size} security concern(s). " +
                "You may continue with limited functionality. Please review the security issues."
            }

            SecurityAction.BLOCK_TEMPORARY -> {
                "Security Block: We detected security issues that must be resolved before proceeding. " +
                "Please follow the instructions below to resolve these issues, then try again."
            }

            SecurityAction.BLOCK_PERMANENT -> {
                "Security Violation: We detected critical security issues that prevent authentication. " +
                "For your protection, authentication cannot proceed on this device."
            }
        }
    }

    /**
     * Generate resolution instructions for user
     */
    private fun generateResolutionInstructions(
        threats: List<Threat>,
        action: SecurityAction
    ): List<String> {
        if (action == SecurityAction.ALLOW) {
            return emptyList()
        }

        val instructions = mutableListOf<String>()

        // Add threat-specific instructions
        threats.forEach { threat ->
            val instruction = AntiTampering.getThreatMessage(threat)
            if (instruction.isNotEmpty() && !instructions.contains(instruction)) {
                instructions.add(instruction)
            }
        }

        // Add general instructions based on action
        when (action) {
            SecurityAction.BLOCK_TEMPORARY -> {
                instructions.add("After resolving the issues above, restart the app and try again.")
            }

            SecurityAction.BLOCK_PERMANENT -> {
                instructions.add("These security issues cannot be resolved while maintaining authentication security.")
                instructions.add("Please use a different device for authentication.")
            }

            SecurityAction.DEGRADE -> {
                instructions.add("You may proceed with authentication, but certain features may be restricted.")
            }

            else -> {
                // No additional instructions for ALLOW/WARN
            }
        }

        return instructions
    }

    /**
     * Determine if merchant should be alerted
     */
    private fun shouldAlertMerchant(action: SecurityAction, severity: Severity): Boolean {
        return when (action) {
            SecurityAction.BLOCK_PERMANENT -> config.alertMerchantOnBlockedAttempt
            SecurityAction.BLOCK_TEMPORARY -> config.alertMerchantOnBlockedAttempt && severity >= Severity.HIGH
            SecurityAction.DEGRADE -> config.alertMerchantOnDegradedMode
            else -> false
        }
    }

    /**
     * Create merchant alert payload
     */
    private fun createMerchantAlert(
        action: SecurityAction,
        threats: List<Threat>,
        severity: Severity,
        userId: String?
    ): MerchantAlert {
        val alertType = when (action) {
            SecurityAction.BLOCK_PERMANENT -> AlertType.PERMANENT_BLOCK_ISSUED
            SecurityAction.BLOCK_TEMPORARY -> if (severity == Severity.CRITICAL) {
                AlertType.FRAUD_ATTEMPT_SUSPECTED
            } else {
                AlertType.SECURITY_THREAT_DETECTED
            }
            SecurityAction.DEGRADE -> AlertType.DEGRADED_MODE_ACTIVE
            else -> AlertType.SECURITY_THREAT_DETECTED
        }

        val requiresAction = when (action) {
            SecurityAction.BLOCK_PERMANENT -> true
            SecurityAction.DEGRADE -> config.merchantCanOverrideDegradedMode
            else -> false
        }

        return MerchantAlert(
            alertType = alertType,
            severity = severity,
            threats = threats.map { it.name },
            userId = userId,
            timestamp = System.currentTimeMillis(),
            requiresAction = requiresAction
        )
    }

    /**
     * Quick check - returns true if device is secure enough to proceed
     */
    fun isDeviceSecure(context: Context): Boolean {
        val decision = evaluateThreats(context)
        return decision.action == SecurityAction.ALLOW || decision.action == SecurityAction.WARN
    }

    /**
     * Check if action allows authentication to proceed
     */
    fun allowsAuthentication(action: SecurityAction): Boolean {
        return action == SecurityAction.ALLOW ||
               action == SecurityAction.WARN ||
               action == SecurityAction.DEGRADE
    }
}
