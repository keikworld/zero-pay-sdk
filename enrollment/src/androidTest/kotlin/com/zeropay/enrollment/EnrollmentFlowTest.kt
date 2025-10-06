package com.zeropay.enrollment

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeropay.sdk.Factor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnrollmentFlowTest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<EnrollmentActivity>()
    
    @Test
    fun testCompleteEnrollmentFlow() {
        // Wait for factor selection screen
        composeTestRule.waitForIdle()
        
        // Select PIN factor
        composeTestRule
            .onNodeWithText(Factor.PIN.displayName)
            .performClick()
        
        // Select PATTERN factor
        composeTestRule
            .onNodeWithText(Factor.PATTERN.displayName)
            .performClick()
        
        // Click continue
        composeTestRule
            .onNodeWithText("Continue")
            .performClick()
        
        // Verify we're on enrollment screen
        composeTestRule
            .onNodeWithText("Factor 1 of 2")
            .assertExists()
    }
    
    @Test
    fun testMinimumTwoFactorsRequired() {
        composeTestRule.waitForIdle()
        
        // Select only one factor
        composeTestRule
            .onNodeWithText(Factor.PIN.displayName)
            .performClick()
        
        // Try to continue (should be disabled or show error)
        composeTestRule
            .onNodeWithText("Continue")
            .assertIsNotEnabled()
    }
}
