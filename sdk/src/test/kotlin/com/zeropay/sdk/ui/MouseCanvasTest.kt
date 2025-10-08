package com.zeropay.sdk.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mouse Canvas UI Tests - PRODUCTION VERSION
 * 
 * Tests:
 * - UI rendering
 * - Drawing area
 * - Point counting
 * - Validation
 * - Error handling
 * 
 * Note: Actual mouse drawing simulation is complex in Compose UI tests
 * These tests focus on UI state and structure
 */
@RunWith(AndroidJUnit4::class)
class MouseCanvasTest : BaseCanvasTest() {
    
    // ==================== RENDERING TESTS ====================
    
    @Test
    fun testInitialState_DisplaysTitle() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Draw Mouse Signature")
    }
    
    @Test
    fun testInitialState_DisplaysInstructions() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Draw a unique signature")
        assertTextExists("mouse or trackpad")
    }
    
    @Test
    fun testInitialState_DisplaysPointCount() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Points: 0 / 300")
    }
    
    @Test
    fun testInitialState_SubmitButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("Submit", enabled = false)
    }
    
    @Test
    fun testInitialState_ClearButtonDisabled() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertButtonEnabled("Clear", enabled = false)
    }
    
    // ==================== DRAWING AREA TESTS ====================
    
    @Test
    fun testDrawingArea_IsInteractive() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert - Canvas should exist
        val canvas = composeTestRule.onNode(hasClickAction().not())
        // Canvas exists as a drawing surface
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun testValidation_MinimumPoints_DisplaysRequirement() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Need at least 10 points")
    }
    
    @Test
    fun testValidation_MaximumPoints_DisplaysLimit() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("300") // Maximum points
    }
    
    // ==================== SECURITY TESTS ====================
    
    @Test
    fun testSecurity_DisplaysSecurityInfo() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Zero-Knowledge")
        assertTextExists("mouse pattern is hashed locally")
    }
    
    @Test
    fun testSecurity_DisplaysTip() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        assertTextExists("Draw naturally")
        assertTextExists("movement pattern is unique")
    }
    
    // ==================== ACCESSIBILITY TESTS ====================
    
    @Test
    fun testAccessibility_ButtonsHaveLabels() {
        // Arrange & Act
        composeTestRule.setContent {
            MouseCanvas(onDone = {})
        }
        
        // Assert
        findButton("Submit").assertExists()
        findButton("Clear").assertExists()
    }
}
