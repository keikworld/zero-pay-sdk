package com.zeropay.sdk.security

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.zeropay.sdk.security.AntiTampering.Threat
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.*

/**
 * Tests for new AntiTampering detection methods
 *
 * NOTE: These tests are currently disabled because they require Android framework classes
 * (Settings, AppOpsManager, etc.) which are not available in JVM unit tests.
 *
 * TODO: Move these tests to androidTest (instrumented tests) or use Robolectric
 *
 * Focuses on testing the newly added security detection capabilities:
 * - Developer mode detection
 * - ADB enabled detection
 * - ADB connected detection
 * - Mock location detection
 */
@Ignore("Requires Android framework - move to instrumented tests (androidTest)")
class AntiTamperingExtensionsTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== DEVELOPER MODE DETECTION ====================

    @Test
    fun `test developer mode detected when enabled`() {
        // Mock Settings.Global to return 1 (enabled)
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(
                any(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
        } returns 1

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.isTampered)
        assertTrue(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))
        assertTrue(result.details.containsKey(Threat.DEVELOPER_MODE_ENABLED))

        unmockkStatic(Settings.Global::class)
    }

    @Test
    fun `test developer mode not detected when disabled`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(
                any(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
        } returns 0

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))

        unmockkStatic(Settings.Global::class)
    }

    @Test
    fun `test developer mode handles exception gracefully`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(
                any(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
        } throws SecurityException("Permission denied")

        // Should not throw exception, should return false
        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))

        unmockkStatic(Settings.Global::class)
    }

    // ==================== ADB ENABLED DETECTION ====================

    @Test
    fun `test ADB detected when enabled`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(
                any(),
                Settings.Global.ADB_ENABLED,
                0
            )
        } returns 1

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.isTampered)
        assertTrue(result.threats.contains(Threat.ADB_ENABLED))
        assertEquals("USB debugging enabled", result.details[Threat.ADB_ENABLED])

        unmockkStatic(Settings.Global::class)
    }

    @Test
    fun `test ADB not detected when disabled`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(
                any(),
                Settings.Global.ADB_ENABLED,
                0
            )
        } returns 0

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.ADB_ENABLED))

        unmockkStatic(Settings.Global::class)
    }

    // ==================== ADB CONNECTED DETECTION ====================

    @Test
    fun `test ADB connection detected when running`() {
        // Mock Runtime to simulate ADB running
        val mockProcess = mockk<Process>()
        val mockInputStream = "running\n".byteInputStream()

        mockkStatic(Runtime::class)
        every { Runtime.getRuntime() } returns mockk {
            every { exec("getprop init.svc.adbd") } returns mockProcess
        }
        every { mockProcess.inputStream } returns mockInputStream

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.isTampered)
        assertTrue(result.threats.contains(Threat.ADB_CONNECTED))
        assertEquals("ADB actively connected", result.details[Threat.ADB_CONNECTED])

        unmockkStatic(Runtime::class)
    }

    @Test
    fun `test ADB connection not detected when stopped`() {
        val mockProcess = mockk<Process>()
        val mockInputStream = "stopped\n".byteInputStream()

        mockkStatic(Runtime::class)
        every { Runtime.getRuntime() } returns mockk {
            every { exec("getprop init.svc.adbd") } returns mockProcess
        }
        every { mockProcess.inputStream } returns mockInputStream

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.ADB_CONNECTED))

        unmockkStatic(Runtime::class)
    }

    @Test
    fun `test ADB connection handles exception gracefully`() {
        mockkStatic(Runtime::class)
        every { Runtime.getRuntime() } returns mockk {
            every { exec("getprop init.svc.adbd") } throws Exception("Command failed")
        }

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.ADB_CONNECTED))

        unmockkStatic(Runtime::class)
    }

    // ==================== MOCK LOCATION DETECTION ====================

    @Test
    fun `test mock location detected on Android M+`() {
        // Mock Build.VERSION.SDK_INT >= M
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.M

        val mockAppOpsManager = mockk<AppOpsManager>()
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns mockAppOpsManager
        every {
            mockAppOpsManager.checkOp(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                any(),
                any()
            )
        } returns AppOpsManager.MODE_ALLOWED

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.isTampered)
        assertTrue(result.threats.contains(Threat.MOCK_LOCATION_ENABLED))

        unmockkStatic(Build.VERSION::class)
    }

    @Test
    fun `test mock location not detected when disabled on Android M+`() {
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.M

        val mockAppOpsManager = mockk<AppOpsManager>()
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns mockAppOpsManager
        every {
            mockAppOpsManager.checkOp(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                any(),
                any()
            )
        } returns AppOpsManager.MODE_IGNORED

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.MOCK_LOCATION_ENABLED))

        unmockkStatic(Build.VERSION::class)
    }

    @Test
    fun `test mock location detected on pre-M devices`() {
        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.LOLLIPOP

        mockkStatic(Settings.Secure::class)
        every {
            Settings.Secure.getString(
                any(),
                Settings.Secure.ALLOW_MOCK_LOCATION
            )
        } returns "1"

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.threats.contains(Threat.MOCK_LOCATION_ENABLED))

        unmockkStatic(Build.VERSION::class)
        unmockkStatic(Settings.Secure::class)
    }

    // ==================== SEVERITY CALCULATION TESTS ====================

    @Test
    fun `test ADB_CONNECTED classified as HIGH severity`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(any(), Settings.Global.ADB_ENABLED, 0)
        } returns 0
        every {
            Settings.Global.getInt(any(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 0

        val mockProcess = mockk<Process>()
        val mockInputStream = "running\n".byteInputStream()

        mockkStatic(Runtime::class)
        every { Runtime.getRuntime() } returns mockk {
            every { exec("getprop init.svc.adbd") } returns mockProcess
        }
        every { mockProcess.inputStream } returns mockInputStream

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.threats.contains(Threat.ADB_CONNECTED))
        // ADB_CONNECTED is HIGH severity
        assertTrue(result.severity >= AntiTampering.Severity.HIGH)

        unmockkStatic(Settings.Global::class)
        unmockkStatic(Runtime::class)
    }

    @Test
    fun `test DEVELOPER_MODE and ADB_ENABLED classified as MEDIUM severity`() {
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(any(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 1
        every {
            Settings.Global.getInt(any(), Settings.Global.ADB_ENABLED, 0)
        } returns 1

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))
        assertTrue(result.threats.contains(Threat.ADB_ENABLED))
        assertTrue(result.severity >= AntiTampering.Severity.MEDIUM)

        unmockkStatic(Settings.Global::class)
    }

    // ==================== THREAT MESSAGE TESTS ====================

    @Test
    fun `test threat messages are user-friendly`() {
        val developerModeMessage = AntiTampering.getThreatMessage(Threat.DEVELOPER_MODE_ENABLED)
        assertTrue(developerModeMessage.contains("Developer"))
        assertTrue(developerModeMessage.contains("disable"))

        val adbMessage = AntiTampering.getThreatMessage(Threat.ADB_ENABLED)
        assertTrue(adbMessage.contains("USB Debugging"))

        val adbConnectedMessage = AntiTampering.getThreatMessage(Threat.ADB_CONNECTED)
        assertTrue(adbConnectedMessage.contains("ADB connection"))

        val mockLocationMessage = AntiTampering.getThreatMessage(Threat.MOCK_LOCATION_ENABLED)
        assertTrue(mockLocationMessage.contains("Mock location"))
    }

    // ==================== INTEGRATION TESTS ====================

    @Test
    fun `test comprehensive check with multiple new threats`() {
        // Simulate device with developer mode, ADB enabled, and mock location
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(any(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 1
        every {
            Settings.Global.getInt(any(), Settings.Global.ADB_ENABLED, 0)
        } returns 1

        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.M

        val mockAppOpsManager = mockk<AppOpsManager>()
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns mockAppOpsManager
        every {
            mockAppOpsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, any(), any())
        } returns AppOpsManager.MODE_ALLOWED

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertTrue(result.isTampered)
        assertTrue(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))
        assertTrue(result.threats.contains(Threat.ADB_ENABLED))
        assertTrue(result.threats.contains(Threat.MOCK_LOCATION_ENABLED))
        assertTrue(result.severity >= AntiTampering.Severity.MEDIUM)

        unmockkStatic(Settings.Global::class)
        unmockkStatic(Build.VERSION::class)
    }

    @Test
    fun `test clean device passes all new checks`() {
        // Mock all settings as disabled/clean
        mockkStatic(Settings.Global::class)
        every {
            Settings.Global.getInt(any(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
        } returns 0
        every {
            Settings.Global.getInt(any(), Settings.Global.ADB_ENABLED, 0)
        } returns 0

        val mockProcess = mockk<Process>()
        val mockInputStream = "stopped\n".byteInputStream()

        mockkStatic(Runtime::class)
        every { Runtime.getRuntime() } returns mockk {
            every { exec("getprop init.svc.adbd") } returns mockProcess
        }
        every { mockProcess.inputStream } returns mockInputStream

        mockkStatic(Build.VERSION::class)
        every { Build.VERSION.SDK_INT } returns Build.VERSION_CODES.M

        val mockAppOpsManager = mockk<AppOpsManager>()
        every { mockContext.getSystemService(Context.APP_OPS_SERVICE) } returns mockAppOpsManager
        every {
            mockAppOpsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, any(), any())
        } returns AppOpsManager.MODE_IGNORED

        val result = AntiTampering.checkTamperingComprehensive(mockContext)

        assertFalse(result.threats.contains(Threat.DEVELOPER_MODE_ENABLED))
        assertFalse(result.threats.contains(Threat.ADB_ENABLED))
        assertFalse(result.threats.contains(Threat.ADB_CONNECTED))
        assertFalse(result.threats.contains(Threat.MOCK_LOCATION_ENABLED))

        unmockkStatic(Settings.Global::class)
        unmockkStatic(Runtime::class)
        unmockkStatic(Build.VERSION::class)
    }
}
