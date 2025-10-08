package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Words Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Word selection
 * - Search functionality
 * - Pagination
 * - Validation
 * - Callback invocation
 */
@RunWith(AndroidJUnit4::class)
class WordsCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Select 4 Words")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Choose 4 memorable words")
    }
    
    @Test
    fun testInitialState_DisplaysSelectionCount() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Selected: 0 / 4")
    }
    
    @Test
    fun testInitialState_DisplaysSearchBar() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Search words")
    }
    
    @Test
    fun testInitialState_DisplaysWordList() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert - List should be scrollable
        composeTestRule.onNode(hasScrollAction()).assertExists()
    }
    
    @Test
    fun testInitialState_SubmitButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("Submit", enabled = false)
    }
    
    // ==================== INTERACTION TESTS ====================
    
    @Test
    fun testWordSelection_CanSelectWord() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Find and click first word
        val firstWord = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstWord.performClick()
        
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 1 / 4")
    }
    
    @Test
    fun testWordSelection_CanDeselectWord() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Select and deselect
        val firstWord = composeTestRule.onAllNodes(hasClickAction()).onFirst()
        firstWord.performClick()
        waitForIdle()
        
        assertTextExists("Selected: 1 / 4")
        
        firstWord.performClick() // Deselect
        waitForIdle()
        
        // Assert
        assertTextExists("Selected: 0 / 4")
    }
    
    @Test
    fun testWordSelection_SelectFourWords() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Select 4 words
        val words = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 4) {
            words[i].performClick()
            waitForIdle()
        }
        
        // Assert
        assertTextExists("Selected: 4 / 4")
        assertButtonEnabled("Submit", enabled = true)
    }
    
    // ==================== SEARCH TESTS ====================
    
    @Test
    fun testSearch_CanTypeInSearchBar() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Find search field and type
        val searchField = composeTestRule.onNode(hasSetTextAction())
        searchField.performTextInput("test")
        
        waitForIdle()
        
        // Assert - Search should filter results
        // (Actual filtering behavior depends on WordsFactor.searchWords implementation)
    }
    
    @Test
    fun testSearch_ClearSearch() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Type and clear
        val searchField = composeTestRule.onNode(hasSetTextAction())
        searchField.performTextInput("test")
        waitForIdle()
        
        searchField.performTextClearance()
        waitForIdle()
        
        // Assert - Should return to full list
        // (Verification depends on implementation)
    }
    
    // ==================== PAGINATION TESTS ====================
    
    @Test
    fun testPagination_DisplaysPageButtons() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Previous")
        assertTextExists("Next")
        assertTextExists("Page 1")
    }
    
    @Test
    fun testPagination_PreviousDisabledOnFirstPage() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("← Previous", enabled = false)
    }
    
    @Test
    fun testPagination_CanNavigatePages() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Click next
        clickButton("Next →")
        
        // Assert
        assertTextExists("Page 2")
        assertButtonEnabled("← Previous", enabled = true)
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_ExactlyFourWordsRequired() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Select only 3 words
        val words = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 3) {
            words[i].performClick()
            waitForIdle()
        }
        
        // Try to submit
        clickButton("Submit")
        
        // Assert - Should show error
        assertTextExists("exactly 4 words")
    }
    
    @Test
    fun testValidation_CannotSelectMoreThanFour() {
        // Arrange
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Act - Select 4 words
        val words = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 4) {
            words[i].performClick()
            waitForIdle()
        }
        
        // Try to select 5th word
        words[4].performClick()
        waitForIdle()
        
        // Assert - Should show max limit message
        assertTextExists("Maximum 4 words")
    }
    
    // ==================== CALLBACK TESTS ====================
    
    @Test
    fun testCallback_InvokedOnValidSubmit() {
        // Arrange
        val latch = CountDownLatch(1)
        var receivedDigest: ByteArray? = null
        
        composeTestRule.setContent {
            WordsCanvas(onDone = { digest ->
                receivedDigest = digest
                latch.countDown()
            })
        }
        
        // Act - Select 4 words and submit
        val words = composeTestRule.onAllNodes(hasClickAction())
        for (i in 0 until 4) {
            words[i].performClick()
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
            WordsCanvas(onDone = {})
        }
        
        // Act - Select words
        val words = composeTestRule.onAllNodes(hasClickAction())
        words[0].performClick()
        waitForIdle()
        words[1].performClick()
        waitForIdle()
        
        assertTextExists("Selected: 2 / 4")
        
        // Click reset
        clickButton("Reset")
        
        // Assert
        assertTextExists("Selected: 0 / 4")
    }
    
    // ==================== SECURITY INFO TESTS ====================
    
    @Test
    fun testSecurity_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            WordsCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("81 trillion combinations")
        assertTextExists("Highly secure")
    }
}
