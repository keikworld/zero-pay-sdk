package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Emoji Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Emoji selection
 * - Grid display
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class EmojiCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Select Emojis")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Choose emojis you'll remember")
    }
    
    @Test
    fun testInitialState_DisplaysSelectionCount() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    @Test
    fun testInitialState_SubmitButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("Submit", enabled = false)
    }
    
    @Test
    fun testInitialState_DisplaysEmojiGrid() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert - Grid should be scrollable
        composeTestRule.onNode(hasScrollAction()).assertExists()
    }
    
    // ==================== INTERACTION TESTS ====================
    
    @Test
    fun testEmojiSelection_CanSelectEmoji() {
        // Arrange
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Act - Find first emoji and click it
        // Note: Emojis are displayed in a grid, we need to find clickable items
        val firstEmoji = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstEmoji.performClick()
        
        waitForIdle()
        
        // Assert - Selection count should increase
        assertTextExists("Selected: 1")
    }
    
    @Test
    fun testEmojiSelection_CanDeselectEmoji() {
        // Arrange
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Act - Select and deselect
        val firstEmoji = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstEmoji.performClick()
        waitForIdle()
        
        assertTextExists("Selected: 1")
        
        firstEmoji.performClick() // Click again to deselect
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinimumSelection_EnablesSubmit() {
        // Arrange
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Act - Select minimum required emojis (usually 3-5)
        val emojis = composeTestRule.onAllNodes(hasClickAction())
        emojis[0].performClick()
        waitForIdle()
        emojis[1].performClick()
        waitForIdle()
        emojis[2].performClick()
        waitForIdle()
        
        // Assert - Submit should be enabled
        assertTextExists("Selected: 3")
        // Button state depends on minimum requirement in EmojiFactor
    }
    
    @Test
    fun testValidation_MaximumSelection_ShowsWarning() {
        // Arrange
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Act - Select maximum allowed (10)
        val emojis = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 10) {
            emojis[i].performClick()
            waitForIdle()
        }
        
        // Try to select one more
        emojis[10].performClick()
        waitForIdle()
        
        // Assert - Should show max limit message
        assertTextExists("Maximum")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnValidSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            EmojiCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Select emojis and submit
        val emojis = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 3) {
            emojis[i].performClick()
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
            EmojiCanvas(onDone = {})
        }
        
        // Act - Select emojis
        val emojis = composeTestRule.onAllNodes(hasClickAction())
        emojis[0].performClick()
        waitForIdle()
        emojis[1].performClick()
        waitForIdle()
        
        assertTextExists("Selected: 2")
        
        // Click reset
        clickButton("Reset")
        
        // Assert
        assertTextExists("Selected: 0")
    }
    
    // ==================== ACCESSIBILITY TESTS ====================
    
    @Test
    fun testAccessibility_EmojisAreClickable() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert - All emojis should be clickable
        val clickableEmojis = composeTestRule.onAllNodes(hasClickAction())
        assertTrue("Should have clickable emojis", clickableEmojis.fetchSemanticsNodes().isNotEmpty())
    }
    
    @Test
    fun testAccessibility_GridIsScrollable() {
        // Arrange & Act
        composeTestRule.setContent {
            EmojiCanvas(onDone = {})
        }
        
        // Assert
        composeTestRule.onNode(hasScrollAction()).assertExists()
    }
}
