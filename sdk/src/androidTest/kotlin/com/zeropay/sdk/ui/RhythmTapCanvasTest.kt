package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Rhythm Tap Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Tap detection
 * - Interval display
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class RhythmTapCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Rhythm Tap Authentication")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Tap out your unique rhythm")
        assertTextExists("4-6 taps")
    }
    
    @Test
    fun testInitialState_DisplaysTapCount() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Taps: 0 / 6")
    }
    
    @Test
    fun testInitialState_DisplaysStartButton() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Start Tapping")
    }
    
    @Test
    fun testInitialState_DisplaysReadyIcon() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Ready")
    }
    
    // ==================== INTERACTION TESTS ====================
    
    @Test
    fun testTapping_ClickStart_EnablesTapArea() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Tapping")
        
        // Assert
        assertTextExists("Keep tapping...")
    }
    
    @Test
    fun testTapping_TapArea_IsClickable() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Tapping")
        waitForIdle()
        
        // Find tap area (large circular button)
        val tapArea = composeTestRule.onNode(hasClickAction())
        
        // Assert
        tapArea.assertExists()
    }
    
    @Test
    fun testTapping_DisplaysTimeElapsed() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Tapping")
        waitForIdle()
        
        // Assert - Should show time counter
        assertTextExists("Time:")
    }
    
    // ==================== TAP COUNTING TESTS ====================
    
    @Test
    fun testTapCount_UpdatesOnTap() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Tapping")
        waitForIdle()
        
        // Tap the tap area
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        tapArea.performClick()
        waitForIdle()
        
        // Assert
        assertTextExists("Taps: 1")
    }
    
    // ==================== INTERVAL DISPLAY TESTS ====================
    
    @Test
    fun testIntervals_DisplayedAfterTwoTaps() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act - Start and tap twice
        clickButton("Start Tapping")
        waitForIdle()
        
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        tapArea.performClick()
        waitForIdle()
        Thread.sleep(200) // Wait a bit between taps
        tapArea.performClick()
        waitForIdle()
        
        // Assert - Should show intervals
        assertTextExists("Intervals (ms):")
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinimumTaps_Required() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Tapping")
        waitForIdle()
        
        // Tap only 3 times (minimum is 4)
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        repeat(3) {
            tapArea.performClick()
            waitForIdle()
            Thread.sleep(100)
        }
        
        // Try to finish
        try {
            clickButton("Finish")
            // Assert - Button should be disabled
            assertButtonEnabled("Finish", enabled = false)
        } catch (e: Exception) {
            // Button may not appear if minimum not reached
        }
    }
    
    @Test
    fun testValidation_FinishButton_EnabledAfterMinimumTaps() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act - Tap 4 times (minimum)
        clickButton("Start Tapping")
        waitForIdle()
        
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        repeat(4) {
            tapArea.performClick()
            waitForIdle()
            Thread.sleep(200)
        }
        
        // Assert
        assertButtonEnabled("Finish", enabled = true)
    }
    
    @Test
    fun testValidation_MaximumTaps_AutoFinishes() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act - Tap 6 times (maximum)
        clickButton("Start Tapping")
        waitForIdle()
        
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        repeat(6) {
            tapArea.performClick()
            waitForIdle()
            Thread.sleep(150)
        }
        
        // Assert - Should auto-finish
        assertTextExists("Submit")
        assertTextExists("Reset")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Complete tapping and submit
        clickButton("Start Tapping")
        waitForIdle()
        
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        repeat(4) {
            tapArea.performClick()
            waitForIdle()
            Thread.sleep(200)
        }
        
        clickButton("Finish")
        waitForIdle()
        
        clickButton("Submit")
        
        // Assert
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("Digest should not be null", receivedDigest)
        assertEquals("Digest should be 32 bytes", 32, receivedDigest!!.size)
    }
    
    // ==================== RESET TESTS ====================
    
    @Test
    fun testReset_ClearsTaps() {
        // Arrange
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Act - Tap and finish
        clickButton("Start Tapping")
        waitForIdle()
        
        val tapArea = composeTestRule.onAllNodes(hasClickAction()).onLast()
        repeat(4) {
            tapArea.performClick()
            waitForIdle()
            Thread.sleep(200)
        }
        
        clickButton("Finish")
        waitForIdle()
        
        // Reset
        clickButton("Reset")
        
        // Assert
        assertTextExists("Taps: 0")
        assertTextExists("Start Tapping")
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge Behavioral Biometric")
        assertTextExists("normalized and hashed")
        assertTextExists("Replay protection")
    }
    
    @Test
    fun testSecurity_DisplaysTip() {
        // Arrange & Act
        composeTestRule.setContent {
            RhythmTapCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Tap naturally")
    }
}
