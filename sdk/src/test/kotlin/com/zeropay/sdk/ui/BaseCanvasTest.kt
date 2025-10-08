package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule

/**
 * Base class for Canvas UI tests
 * 
 * Provides common test utilities and helpers for all Canvas components
 */
abstract class BaseCanvasTest {
    
    @get:Rule
    val composeTestRule: ComposeTestRule = createComposeRule()
    
    /**
     * Wait for UI to be idle before assertions
     */
    protected fun waitForIdle() {
        composeTestRule.waitForIdle()
    }
    
    /**
     * Find button by text
     */
    protected fun findButton(text: String): SemanticsNodeInteraction {
        return composeTestRule.onNodeWithText(text, useUnmergedTree = true)
    }
    
    /**
     * Find text field by label
     */
    protected fun findTextField(label: String): SemanticsNodeInteraction {
        return composeTestRule.onNodeWithText(label, useUnmergedTree = true)
    }
    
    /**
     * Assert text exists on screen
     */
    protected fun assertTextExists(text: String) {
        composeTestRule.onNodeWithText(text, substring = true).assertExists()
    }
    
    /**
     * Assert text does not exist on screen
     */
    protected fun assertTextDoesNotExist(text: String) {
        composeTestRule.onNodeWithText(text, substring = true).assertDoesNotExist()
    }
    
    /**
     * Click button by text
     */
    protected fun clickButton(text: String) {
        findButton(text).performClick()
        waitForIdle()
    }
    
    /**
     * Assert button is enabled
     */
    protected fun assertButtonEnabled(text: String, enabled: Boolean = true) {
        if (enabled) {
            findButton(text).assertIsEnabled()
        } else {
            findButton(text).assertIsNotEnabled()
        }
    }
}
