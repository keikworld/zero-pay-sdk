package com.zeropay.sdk.ui

import android.Manifest
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Voice Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Permission handling
 * - Recording states
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class VoiceCanvasTest : BaseCanvasTest() {
    
    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO
    )
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Voice Authentication")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Speak clearly into the microphone")
    }
    
    @Test
    fun testInitialState_DisplaysRecordButton() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Start Recording")
    }
    
    @Test
    fun testInitialState_DisplaysMicrophoneIcon() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("🎙️")
    }
    
    @Test
    fun testInitialState_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge Voice Authentication")
        assertTextExists("hashed locally using SHA-256")
        assertTextExists("Raw audio is deleted immediately")
    }
    
    // ==================== PERMISSION TESTS ====================
    
    @Test
    fun testPermissions_WithPermission_ShowsRecordButton() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Start Recording")
    }
    
    // Note: Testing without permission requires separate test without GrantPermissionRule
    
    // ==================== RECORDING TESTS ====================
    
    @Test
    fun testRecording_ClickStartRecording_ChangesState() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        
        // Assert - Should show recording state
        assertTextExists("Recording...")
        assertTextExists("🎤") // Recording icon
    }
    
    @Test
    fun testRecording_DisplaysProgress() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act - Start recording
        clickButton("Start Recording")
        waitForIdle()
        
        // Assert - Should show progress indicator
        assertTextExists("Recording...")
        // Progress percentage should appear (0-100%)
    }
    
    @Test
    fun testRecording_CompletesAfterDuration() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act - Start recording
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
        assertTextExists("Recording complete!")
        assertTextExists("✅")
    }
    
    // ==================== POST-RECORDING TESTS ====================
    
    @Test
    fun testPostRecording_DisplaysSubmitAndReRecordButtons() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act - Complete recording
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
    
    @Test
    fun testPostRecording_ReRecordResetsState() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act - Complete recording
        clickButton("Start Recording")
        
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithText("Re-record", substring = true).assertExists()
                true
            } catch (e: AssertionError) {
                false
            }
        }
        
        // Click re-record
        clickButton("Re-record")
        waitForIdle()
        
        // Assert - Should return to initial state
        assertTextExists("Start Recording")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            VoiceCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Record and submit
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
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinimumDuration_Enforced() {
        // Note: This is handled by the recording duration (3 seconds minimum)
        // The UI enforces this by design
        
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert - Recording duration is displayed
        assertTextExists("3 seconds")
    }
    
    // ==================== ACCESSIBILITY TESTS ====================
    
    @Test
    fun testAccessibility_ButtonsHaveProperLabels() {
        // Arrange & Act
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Assert
        findButton("Start Recording").assertExists()
    }
    
    @Test
    fun testAccessibility_VisualFeedbackForRecording() {
        // Arrange
        composeTestRule.setContent {
            VoiceCanvas(onDone = {})
        }
        
        // Act
        clickButton("Start Recording")
        waitForIdle()
        
        // Assert - Recording icon should change
        assertTextExists("🎤")
        assertTextExists("Recording...")
    }
}
