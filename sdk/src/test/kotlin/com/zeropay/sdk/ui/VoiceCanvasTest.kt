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
 * - Permission handling
 * - Recording UI states
 * - Button interactions
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
