package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PIN Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - PIN input interaction
 * - Visibility toggle
 * - Validation
 * - Error messages
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class PinCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Enter PIN")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("4-12 digits")
    }
    
    @Test
    fun testInitialState_PinIsHidden() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert - Should show "Show PIN" button initially
        assertTextExists("Show PIN")
    }
    
    @Test
    fun testInitialState_SubmitButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("Submit", enabled = false)
    }
    
    @Test
    fun testInitialState_DisplaysLengthIndicator() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Length: 0 / 12")
    }
    
    // ==================== INTERACTION TESTS ====================
    
    @Test
    fun testPinInput_TextFieldExists() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        composeTestRule.onNodeWithText("Enter PIN", substring = true).assertExists()
    }
    
    @Test
    fun testPinInput_CanTypeDigits() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Find text field and type
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("1234")
        
        waitForIdle()
        
        // Assert - Length should update
        assertTextExists("Length: 4")
    }
    
    @Test
    fun testVisibilityToggle_ShowsAndHidesPin() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Click show/hide button
        clickButton("Show PIN")
        
        // Assert - Button text should change
        assertTextExists("Hide PIN")
        
        // Act - Click again
        clickButton("Hide PIN")
        
        // Assert
        assertTextExists("Show PIN")
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_TooShortPin_ShowsError() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Enter too short PIN
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("123")
        
        waitForIdle()
        
        // Try to submit
        clickButton("Submit")
        
        // Assert - Should show error
        assertTextExists("at least 4 digits")
    }
    
    @Test
    fun testValidation_ValidPin_EnablesSubmitButton() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Enter valid PIN
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("1234")
        
        waitForIdle()
        
        // Assert - Submit should be enabled
        assertButtonEnabled("Submit", enabled = true)
    }
    
    @Test
    fun testValidation_NonNumericInput_Rejected() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Try to enter letters (should be filtered by keyboard type)
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("12ab")
        
        waitForIdle()
        
        // Assert - Should only accept numbers
        // Length should not include letters
        assertTextExists("Length: 2") // Only "12" accepted
    }
    
    @Test
    fun testValidation_MaxLength_Enforced() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Try to enter 13 digits (max is 12)
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("1234567890123")
        
        waitForIdle()
        
        // Assert - Should only accept 12 digits
        assertTextExists("Length: 12")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnValidSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            PinCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Enter valid PIN and submit
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("123456")
        waitForIdle()
        
        clickButton("Submit")
        
        // Assert - Wait for callback
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("Digest should not be null", receivedDigest)
        assertEquals("Digest should be 32 bytes", 32, receivedDigest!!.size)
    }
    
    @Test
    fun testCallback_NotInvokedOnInvalidSubmit() {
        // Arrange
        var callbackInvoked = false
        
        composeTestRule.setContent {
            PinCanvas(onDone = { callbackInvoked = true })
        }
        
        // Act - Try to submit without entering anything
        // Submit button should be disabled, but let's try clicking anyway
        waitForIdle()
        
        // Assert
        assertFalse("Callback should not be invoked", callbackInvoked)
        assertButtonEnabled("Submit", enabled = false)
    }
    
    // ==================== CLEAR FUNCTIONALITY TESTS ====================
    
    @Test
    fun testClear_ResetsPin() {
        // Arrange
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Act - Enter PIN
        val textField = composeTestRule.onNode(hasSetTextAction())
        textField.performTextInput("1234")
        waitForIdle()
        
        // Assert - Length updated
        assertTextExists("Length: 4")
        
        // Act - Click clear
        clickButton("Clear")
        
        // Assert - Length should be 0
        assertTextExists("Length: 0")
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_DisplaysPrivacyNotice() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge")
        assertTextExists("hashed with Argon2id")
    }
    
    @Test
    fun testSecurity_DisplaysSecurityTip() {
        // Arrange & Act
        composeTestRule.setContent {
            PinCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Avoid sequential")
    }
}
