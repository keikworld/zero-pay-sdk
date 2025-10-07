package com.zeropay.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zeropay.sdk.factors.FactorRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Instrumented Tests
 * 
 * These tests run on an actual Android device or emulator
 * 
 * Path: sdk/src/androidTest/kotlin/com/zeropay/sdk/AndroidInstrumentedTests.kt
 * 
 * Run with: ./gradlew :sdk:connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AndroidInstrumentedTests {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    // ==================== FACTOR REGISTRY TESTS ====================
    
    @Test
    fun testFactorRegistry_ReturnsAvailableFactors() {
        // Act
        val factors = FactorRegistry.availableFactors(context)
        
        // Assert
        assertNotNull("Should return list of factors", factors)
        assertTrue("Should have at least basic factors", factors.size >= 4)
        assertTrue("Should include COLOUR", factors.contains(Factor.COLOUR))
        assertTrue("Should include EMOJI", factors.contains(Factor.EMOJI))
        assertTrue("Should include PIN", factors.contains(Factor.PIN))
        assertTrue("Should include VOICE", factors.contains(Factor.VOICE))
    }
    
    @Test
    fun testFactorRegistry_ChecksDeviceCapabilities() {
        // Act
        val factors = FactorRegistry.availableFactors(context)
        val packageManager = context.packageManager
        
        // Assert NFC availability
        val hasNFC = packageManager.hasSystemFeature("android.hardware.nfc")
        if (hasNFC) {
            assertTrue("NFC should be in factors if hardware available", 
                factors.contains(Factor.NFC))
        }
        
        // Assert Camera availability
        val hasCamera = packageManager.hasSystemFeature("android.hardware.camera")
        if (hasCamera) {
            assertTrue("FACE should be in factors if camera available", 
                factors.contains(Factor.FACE))
        }
        
        // Assert Accelerometer availability
        val hasAccel = packageManager.hasSystemFeature("android.hardware.sensor.accelerometer")
        if (hasAccel) {
            assertTrue("BALANCE should be in factors if accelerometer available", 
                factors.contains(Factor.BALANCE))
        }
    }
    
    @Test
    fun testFactorRegistry_AlwaysHasBasicFactors() {
        // Act
        val factors = FactorRegistry.availableFactors(context)
        
        // Assert - These should ALWAYS be available
        assertTrue("COLOUR should always be available", factors.contains(Factor.COLOUR))
        assertTrue("EMOJI should always be available", factors.contains(Factor.EMOJI))
        assertTrue("PIN should always be available", factors.contains(Factor.PIN))
        assertTrue("VOICE should always be available", factors.contains(Factor.VOICE))
    }
    
    @Test
    fun testFactorRegistry_PatternAlwaysAvailable() {
        // Act
        val factors = FactorRegistry.availableFactors(context)
        
        // Assert - Pattern factors should always be available (touch screen)
        assertTrue("PATTERN_MICRO should be available", 
            factors.contains(Factor.PATTERN_MICRO))
        assertTrue("PATTERN_NORMAL should be available", 
            factors.contains(Factor.PATTERN_NORMAL))
    }
    
    // ==================== CRYPTO UTILS TESTS ====================
    
    @Test
    fun testCryptoUtils_SHA256_WorksOnDevice() {
        // Arrange
        val data = "test data on device".toByteArray()
        
        // Act
        val hash = com.zeropay.sdk.crypto.CryptoUtils.sha256(data)
        
        // Assert
        assertEquals("SHA-256 should produce 32 bytes", 32, hash.size)
    }
    
    @Test
    fun testCryptoUtils_SecureRandom_WorksOnDevice() {
        // Act
        val random1 = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(32)
        val random2 = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(32)
        
        // Assert
        assertEquals(32, random1.size)
        assertEquals(32, random2.size)
        assertFalse("Random bytes should be different", 
            random1.contentEquals(random2))
    }
    
    @Test
    fun testCryptoUtils_HMAC_WorksOnDevice() {
        // Arrange
        val key = "secret key".toByteArray()
        val data = "message".toByteArray()
        
        // Act
        val hmac = com.zeropay.sdk.crypto.CryptoUtils.hmacSha256(key, data)
        
        // Assert
        assertEquals("HMAC should produce 32 bytes", 32, hmac.size)
    }
    
    // ==================== RATE LIMITER TESTS ====================
    
    @Test
    fun testRateLimiter_WorksOnDevice() {
        // Arrange
        val testUid = "android-test-user-${System.currentTimeMillis()}"
        
        // Act & Assert - Initial check should be OK
        assertEquals(RateLimiter.RateResult.OK, RateLimiter.check(testUid))
        
        // Record 5 failures
        repeat(5) { RateLimiter.recordFail(testUid) }
        assertEquals(RateLimiter.RateResult.COOL_DOWN_15M, RateLimiter.check(testUid))
        
        // Reset
        RateLimiter.resetFails(testUid)
        assertEquals(RateLimiter.RateResult.OK, RateLimiter.check(testUid))
    }
    
    // ==================== CSPRNG SHUFFLE TESTS ====================
    
    @Test
    fun testCsprngShuffle_WorksOnDevice() {
        // Arrange
        val original = (1..10).toList()
        
        // Act
        val shuffled = CsprngShuffle.shuffle(original)
        
        // Assert
        assertEquals("Should preserve all elements", original.toSet(), shuffled.toSet())
        assertEquals("Should have same size", original.size, shuffled.size)
    }
    
    // ==================== INTEGRATION TESTS ====================
    
    @Test
    fun testFullFactorFlow_ColourAndPin() {
        // Arrange
        val colourIndices = listOf(0, 1)
        val pin = "123456"
        
        // Act
        val colourDigest = com.zeropay.sdk.factors.ColourFactor.digest(colourIndices)
        val pinDigest = com.zeropay.sdk.factors.PinFactor.digest(pin)
        
        // Assert
        assertEquals(32, colourDigest.size)
        assertEquals(32, pinDigest.size)
        assertFalse("Different factors should produce different digests",
            colourDigest.contentEquals(pinDigest))
    }
    
    @Test
    fun testDeviceId_CanBeHashed() {
        // Arrange
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
        
        // Act
        val hash = com.zeropay.sdk.crypto.CryptoUtils.sha256(deviceId.toByteArray())
        
        // Assert
        assertNotNull("Device ID should be hashable", hash)
        assertEquals(32, hash.size)
    }
    
    @Test
    fun testContext_IsAvailable() {
        // Assert
        assertNotNull("Context should be available", context)
        assertNotNull("Package manager should be available", context.packageManager)
        assertNotNull("Content resolver should be available", context.contentResolver)
    }
    
    // ==================== PERFORMANCE TESTS ====================
    
    @Test
    fun testPerformance_FactorRegistry_Fast() {
        // Act
        val startTime = System.currentTimeMillis()
        repeat(100) {
            FactorRegistry.availableFactors(context)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Assert - 100 calls should complete quickly
        assertTrue("100 FactorRegistry calls should complete in < 1 second (actual: ${elapsedTime}ms)",
            elapsedTime < 1000)
    }
    
    @Test
    fun testPerformance_DigestGeneration_Fast() {
        // Arrange
        val data = "test data".toByteArray()
        
        // Act
        val startTime = System.currentTimeMillis()
        repeat(1000) {
            com.zeropay.sdk.crypto.CryptoUtils.sha256(data)
        }
        val elapsedTime = System.currentTimeMillis() - startTime
        
        // Assert
        assertTrue("1000 SHA-256 digests should complete in < 1 second (actual: ${elapsedTime}ms)",
            elapsedTime < 1000)
    }
    
    @Test
    fun testFactorRegistry_RhythmTapAlwaysAvailable() {
        // Act
        val factors = FactorRegistry.availableFactors(context)
        
        // Assert
        assertTrue(
            "RHYTHM_TAP should always be available (no special hardware required)", 
            factors.contains(Factor.RHYTHM_TAP)
        )
    }
}
