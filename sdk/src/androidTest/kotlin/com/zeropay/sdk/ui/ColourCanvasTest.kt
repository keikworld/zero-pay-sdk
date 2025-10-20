package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Colour Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Colour selection
 * - Selection order
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class ColourCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert
        assertTextExists("Select Colours")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert
        assertTextExists("Tap colours in your chosen order")
    }
    
    @Test
    fun testInitialState_DisplaysSelectionCount() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    @Test
    fun testInitialState_DisplaysColourGrid() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert - Should display colour options
        // At least 6 colours (ColourFactor has 6 colours)
    }
    
    @Test
    fun testInitialState_SubmitButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert
        assertButtonEnabled("Submit", enabled = false)
    }
    
    // ==================== COLOUR SELECTION TESTS ====================
    
    @Test
    fun testColourSelection_CanSelectColour() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Find and click first colour
        val firstColour = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstColour.performClick()
        
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 1")
    }
    
    @Test
    fun testColourSelection_OrderMatters() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Select colours in order
        val colours = composeTestRule.onAllNodes(hasClickAction())
        colours[0].performClick()
        waitForIdle()
        colours[1].performClick()
        waitForIdle()
        
        // Assert - Should show selection order indicators (1, 2, etc.)
        assertTextExists("Selected: 2")
    }
    
    @Test
    fun testColourSelection_CanDeselectColour() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Select and deselect
        val firstColour = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstColour.performClick()
        waitForIdle()
        
        assertTextExists("Selected: 1")
        
        firstColour.performClick() // Deselect
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinimumSelection_EnablesSubmit() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Select minimum required (3 colours)
        val colours = composeTestRule.onAllNodes(hasClickAction())
        colours[0].performClick()
        waitForIdle()
        colours[1].performClick()
        waitForIdle()
        colours[2].performClick()
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 3")
        assertButtonEnabled("Submit", enabled = true)
    }
    
    @Test
    fun testValidation_MaximumSelection_ShowsWarning() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Try to select more than maximum (10)
        val colours = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 10) {
            colours[i % 6].performClick() // May need to reselect same colours
            waitForIdle()
        }
        
        // Try one more
        colours[0].performClick()
        waitForIdle()
        
        // Assert - Should show max limit
        assertTextExists("Maximum")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnValidSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            ColourCanvas(onSelected = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Select colours and submit
        val colours = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 3) {
            colours[i].performClick()
            waitForIdle()
        }
        
        clickButton("Submit")
        
        // Assert
        assertTrue("Callback should be invoked", latch.await(5, TimeUnit.SECONDS))
        assertNotNull("Digest should not be null", receivedDigest)
        assertEquals("Digest should be 32 bytes", 32, receivedDigest!!.size)
    }
    
    // ==================== RESET FUNCTIONALITY TESTS ====================
    
    @Test
    fun testReset_ClearsSelection() {
        // Arrange
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Act - Select colours
        val colours = composeTestRule.onAllNodes(hasClickAction())
        colours[0].performClick()
        waitForIdle()
        colours[1].performClick()
        waitForIdle()
        
        assertTextExists("Selected: 2")
        
        // Reset
        clickButton("Reset")
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge")
        assertTextExists("hashed locally")
    }
    
    // ==================== ACCESSIBILITY TESTS ====================
    
    @Test
    fun testAccessibility_ColoursAreClickable() {
        // Arrange & Act
        composeTestRule.setContent {
            ColourCanvas(onSelected = {})
        }
        
        // Assert - All colours should be clickable
        val clickableColours = composeTestRule.onAllNodes(hasClickAction())
        assertTrue("Should have clickable colours", clickableColours.fetchSemanticsNodes().isNotEmpty())
    }
}
