package com.zeropay.sdk.security

import android.content.Context
import com.zeropay.sdk.security.AntiTampering.Severity
import com.zeropay.sdk.security.AntiTampering.Threat
import com.zeropay.sdk.security.SecurityPolicy.SecurityAction
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Security Policy Tests
 *
 * Tests for the SecurityPolicy framework and graduated response system.
 */
class SecurityPolicyTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)

        // Reset security policy to default configuration
        SecurityPolicy.configure(SecurityPolicy.SecurityPolicyConfig())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== SECURITY ACTION DETERMINATION ====================

    @Test
    fun `test BLOCK_PERMANENT for rooted device`() = runBlocking {
        // Mock rooted device detection
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ROOT_DETECTED),
            severity = Severity.HIGH,
            details = mapOf(Threat.ROOT_DETECTED to "SU binary found")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.BLOCK_PERMANENT, decision.action)
        assertTrue(decision.threats.contains(Threat.ROOT_DETECTED))
        assertEquals(Severity.HIGH, decision.severity)
        assertFalse(decision.allowRetry)
        assertNotNull(decision.merchantAlert)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test BLOCK_PERMANENT for emulator`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.EMULATOR_PROPERTIES_DETECTED),
            severity = Severity.HIGH,
            details = mapOf(Threat.EMULATOR_PROPERTIES_DETECTED to "Emulator build detected")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.BLOCK_PERMANENT, decision.action)
        assertFalse(decision.allowRetry)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test BLOCK_TEMPORARY for developer mode`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.DEVELOPER_MODE_ENABLED),
            severity = Severity.MEDIUM,
            details = mapOf(Threat.DEVELOPER_MODE_ENABLED to "Developer options enabled")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.BLOCK_TEMPORARY, decision.action)
        assertTrue(decision.allowRetry)
        assertTrue(decision.resolutionInstructions.isNotEmpty())

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test BLOCK_TEMPORARY for ADB enabled`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ADB_ENABLED),
            severity = Severity.MEDIUM,
            details = mapOf(Threat.ADB_ENABLED to "USB debugging enabled")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.BLOCK_TEMPORARY, decision.action)
        assertTrue(decision.allowRetry)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test BLOCK_PERMANENT for active ADB connection`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ADB_CONNECTED),
            severity = Severity.HIGH,
            details = mapOf(Threat.ADB_CONNECTED to "ADB actively connected")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.BLOCK_PERMANENT, decision.action)
        assertFalse(decision.allowRetry)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test DEGRADE for VPN detected`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.VPN_DETECTED),
            severity = Severity.LOW,
            details = mapOf(Threat.VPN_DETECTED to "VPN connection active")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.DEGRADE, decision.action)
        assertTrue(decision.allowRetry)
        assertNotNull(decision.merchantAlert)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test WARN for mock location`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.MOCK_LOCATION_ENABLED),
            severity = Severity.LOW,
            details = mapOf(Threat.MOCK_LOCATION_ENABLED to "Mock location provider active")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.WARN, decision.action)
        assertFalse(decision.allowRetry)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test ALLOW for no threats`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = false,
            threats = emptyList(),
            severity = Severity.NONE,
            details = emptyMap()
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertEquals(SecurityAction.ALLOW, decision.action)
        assertTrue(decision.threats.isEmpty())
        assertEquals(Severity.NONE, decision.severity)

        unmockkObject(AntiTampering)
    }

    // ==================== MERCHANT ALERT GENERATION ====================

    @Test
    fun `test merchant alert generated for BLOCK_PERMANENT`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ROOT_DETECTED, Threat.MAGISK_DETECTED),
            severity = Severity.HIGH,
            details = mapOf(
                Threat.ROOT_DETECTED to "SU binary found",
                Threat.MAGISK_DETECTED to "Magisk manager detected"
            )
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext, userId = "test-user-123")

        assertNotNull(decision.merchantAlert)
        assertEquals(SecurityPolicy.AlertType.PERMANENT_BLOCK_ISSUED, decision.merchantAlert!!.alertType)
        assertEquals(Severity.HIGH, decision.merchantAlert!!.severity)
        assertEquals("test-user-123", decision.merchantAlert!!.userId)
        assertTrue(decision.merchantAlert!!.requiresAction)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test merchant alert generated for DEGRADE`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.VPN_DETECTED),
            severity = Severity.LOW,
            details = mapOf(Threat.VPN_DETECTED to "VPN active")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertNotNull(decision.merchantAlert)
        assertEquals(SecurityPolicy.AlertType.DEGRADED_MODE_ACTIVE, decision.merchantAlert!!.alertType)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test no merchant alert for WARN`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.MOCK_LOCATION_ENABLED),
            severity = Severity.LOW,
            details = mapOf(Threat.MOCK_LOCATION_ENABLED to "Mock location")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        // WARN should not generate merchant alert by default
        // (depends on config.alertMerchantOnBlockedAttempt)

        unmockkObject(AntiTampering)
    }

    // ==================== CONFIGURATION TESTS ====================

    @Test
    fun `test custom policy configuration`() {
        val customConfig = SecurityPolicy.SecurityPolicyConfig(
            developerModeAction = SecurityAction.WARN, // Override to just warn
            adbEnabledAction = SecurityAction.WARN,
            mockLocationAction = SecurityAction.BLOCK_TEMPORARY,
            merchantCanOverrideDegradedMode = false
        )

        SecurityPolicy.configure(customConfig)

        val config = SecurityPolicy.getConfig()
        assertEquals(SecurityAction.WARN, config.developerModeAction)
        assertEquals(SecurityAction.WARN, config.adbEnabledAction)
        assertEquals(SecurityAction.BLOCK_TEMPORARY, config.mockLocationAction)
        assertFalse(config.merchantCanOverrideDegradedMode)
    }

    // ==================== HELPER METHOD TESTS ====================

    @Test
    fun `test isDeviceSecure returns true for no threats`() {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = false,
            threats = emptyList(),
            severity = Severity.NONE,
            details = emptyMap()
        )

        assertTrue(SecurityPolicy.isDeviceSecure(mockContext))

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test isDeviceSecure returns false for rooted device`() {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ROOT_DETECTED),
            severity = Severity.HIGH,
            details = mapOf(Threat.ROOT_DETECTED to "Rooted")
        )

        assertFalse(SecurityPolicy.isDeviceSecure(mockContext))

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test allowsAuthentication for different actions`() {
        assertTrue(SecurityPolicy.allowsAuthentication(SecurityAction.ALLOW))
        assertTrue(SecurityPolicy.allowsAuthentication(SecurityAction.WARN))
        assertTrue(SecurityPolicy.allowsAuthentication(SecurityAction.DEGRADE))
        assertFalse(SecurityPolicy.allowsAuthentication(SecurityAction.BLOCK_TEMPORARY))
        assertFalse(SecurityPolicy.allowsAuthentication(SecurityAction.BLOCK_PERMANENT))
    }

    // ==================== MULTI-THREAT TESTS ====================

    @Test
    fun `test multiple threats escalate severity`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(
                Threat.DEVELOPER_MODE_ENABLED,
                Threat.ADB_ENABLED,
                Threat.MOCK_LOCATION_ENABLED
            ),
            severity = Severity.MEDIUM,
            details = mapOf(
                Threat.DEVELOPER_MODE_ENABLED to "Dev mode on",
                Threat.ADB_ENABLED to "ADB on",
                Threat.MOCK_LOCATION_ENABLED to "Mock location on"
            )
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        // Multiple medium threats should result in BLOCK_TEMPORARY
        assertEquals(SecurityAction.BLOCK_TEMPORARY, decision.action)
        assertEquals(3, decision.threats.size)

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test resolution instructions generated for temporary blocks`() = runBlocking {
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.DEVELOPER_MODE_ENABLED),
            severity = Severity.MEDIUM,
            details = mapOf(Threat.DEVELOPER_MODE_ENABLED to "Developer options enabled")
        )

        val decision = SecurityPolicy.evaluateThreats(mockContext)

        assertTrue(decision.resolutionInstructions.isNotEmpty())
        assertTrue(decision.resolutionInstructions.any { it.contains("Developer", ignoreCase = true) })

        unmockkObject(AntiTampering)
    }

    @Test
    fun `test user message appropriate for each action`() = runBlocking {
        // Test BLOCK_PERMANENT message
        mockkObject(AntiTampering)
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.ROOT_DETECTED),
            severity = Severity.HIGH,
            details = mapOf(Threat.ROOT_DETECTED to "Rooted")
        )

        var decision = SecurityPolicy.evaluateThreats(mockContext)
        assertTrue(decision.userMessage.contains("critical", ignoreCase = true))

        // Test BLOCK_TEMPORARY message
        every { AntiTampering.checkTamperingComprehensive(any()) } returns AntiTampering.TamperResult(
            isTampered = true,
            threats = listOf(Threat.DEVELOPER_MODE_ENABLED),
            severity = Severity.MEDIUM,
            details = mapOf(Threat.DEVELOPER_MODE_ENABLED to "Dev mode")
        )

        decision = SecurityPolicy.evaluateThreats(mockContext)
        assertTrue(decision.userMessage.contains("resolved", ignoreCase = true))

        unmockkObject(AntiTampering)
    }
}
