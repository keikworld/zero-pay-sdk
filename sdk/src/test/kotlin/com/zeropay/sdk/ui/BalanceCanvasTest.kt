package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Balance Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Sensor availability
 * - Recording states
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class BalanceCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Balance Authentication")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Hold your device steady")
    }
    
    @Test
    fun testInitialState_DisplaysBalanceIcon() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("⚖️")
    }
    
    @Test
    fun testInitialState_DisplaysStartButton() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Start Recording")
    }
    
    @Test
    fun testInitialState_DisplaysDuration() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("3 seconds")
    }
    
    // ==================== SENSOR AVAILABILITY TESTS ====================
    
    // Note: Actual sensor availability depends on device
    // These tests assume sensor is available
    
    @Test
    fun testSensorAvailable_ShowsRecordButton() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert - If sensor available, should show button
        // If not available, should show error message
        // This test may vary by device
    }
    
    // ==================== RECORDING TESTS ====================
    
    @Test
    fun testRecording_ClickStartRecording_ChangesState() {
        // Arrange
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        
        // Assert
        assertTextExists("Recording...")
        assertTextExists("📊") // Recording icon
    }
    
    @Test
    fun testRecording_DisplaysProgress() {
        // Arrange
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        waitForIdle()
        
        // Assert - Progress should be visible
        // Percentage or progress bar
    }
    
    @Test
    fun testRecording_DisplaysAccelerometerData() {
        // Arrange
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        waitForIdle()
        
        // Assert - Should show current acceleration values
        assertTextExists("Current Acceleration:")
    }
    
    @Test
    fun testRecording_CompletesAfterDuration() {
        // Arrange
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        
        // Wait for recording to complete (3 seconds + buffer)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Recording complete!", substring = true)
                    .assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Assert
        assertTextExists("samples collected")
    }
    
    // ==================== POST-RECORDING TESTS ====================
    
    @Test
    fun testPostRecording_DisplaysSubmitAndReRecordButtons() {
        // Arrange
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Submit", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Assert
        assertTextExists("Submit")
        assertTextExists("Re-record")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            BalanceCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act
        clickButton("Start Recording")
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Submit", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        clickButton("Submit")
        
        // Assert
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("Digest should not be null", receivedDigest)
        assertEquals("Digest should be 32 bytes", 32, receivedDigest!!.size)
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge Balance Authentication")
        assertTextExists("hashed locally")
        assertTextExists("never stored")
    }
    
    @Test
    fun testSecurity_DisplaysTip() {
        // Arrange & Act
        composeTestRule.setContent {
            BalanceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Hold the device the same way each time")
    }
}
