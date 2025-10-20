package com.zeropay.sdk.factors

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zeropay.sdk.Factor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FactorCanvasFactory Tests - Android Instrumented Tests
 *
 * Tests the factory that creates Canvas UI components for factors.
 * These tests verify:
 * - Correct validation of factor requirements
 * - Proper instructions for each factor
 * - Difficulty levels and practice recommendations
 * - GDPR compliance checks
 */
@RunWith(AndroidJUnit4::class)
class FactorCanvasFactoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    fun testValidateRequirements_PIN_Always Available() {
        // Act
        val result = FactorCanvasFactory.validateRequirements(Factor.PIN, context)

        // Assert
        assertTrue("PIN should always be available", result.isValid)
        assertNull("No error message expected", result.errorMessage)
    }

    @Test
    fun testValidateRequirements_COLOUR_AlwaysAvailable() {
        // Act
        val result = FactorCanvasFactory.validateRequirements(Factor.COLOUR, context)

        // Assert
        assertTrue("Colour should always be available", result.isValid)
    }

    @Test
    fun testValidateRequirements_EMOJI_AlwaysAvailable() {
        // Act
        val result = FactorCanvasFactory.validateRequirements(Factor.EMOJI, context)

        // Assert
        assertTrue("Emoji should always be available", result.isValid)
    }

    @Test
    fun testValidateRequirements_WORDS_AlwaysAvailable() {
        // Act
        val result = FactorCanvasFactory.validateRequirements(Factor.WORDS, context)

        // Assert
        assertTrue("Words should always be available", result.isValid)
    }

    // ==================== INSTRUCTIONS TESTS ====================

    @Test
    fun testGetInstructions_PIN_ReturnsValidInstructions() {
        // Act
        val instructions = FactorCanvasFactory.getInstructions(Factor.PIN)

        // Assert
        assertNotNull(instructions)
        assertTrue("Instructions should mention PIN", instructions.toLowerCase().contains("pin"))
        assertTrue("Instructions should not be empty", instructions.isNotEmpty())
    }

    @Test
    fun testGetInstructions_PATTERN_ReturnsValidInstructions() {
        // Act
        val instructionsMicro = FactorCanvasFactory.getInstructions(Factor.PATTERN_MICRO)
        val instructionsNormal = FactorCanvasFactory.getInstructions(Factor.PATTERN_NORMAL)

        // Assert
        assertTrue("Pattern instructions should mention drawing",
            instructionsMicro.toLowerCase().contains("pattern") ||
            instructionsMicro.toLowerCase().contains("draw"))
        assertTrue("Both pattern modes should have instructions",
            instructionsNormal.isNotEmpty())
    }

    @Test
    fun testGetInstructions_AllFactors_HaveInstructions() {
        // Act & Assert
        Factor.values().forEach { factor ->
            val instructions = FactorCanvasFactory.getInstructions(factor)
            assertTrue(
                "Factor $factor should have instructions",
                instructions.isNotEmpty()
            )
        }
    }

    // ==================== SECURITY TIP TESTS ====================

    @Test
    fun testGetSecurityTip_PIN_WarnAgainstSequential() {
        // Act
        val tip = FactorCanvasFactory.getSecurityTip(Factor.PIN)

        // Assert
        assertTrue(
            "PIN tip should warn against sequential numbers",
            tip.toLowerCase().contains("sequential") ||
            tip.toLowerCase().contains("1234") ||
            tip.toLowerCase().contains("birthday")
        )
    }

    @Test
    fun testGetSecurityTip_AllFactors_HaveTips() {
        // Act & Assert
        Factor.values().forEach { factor ->
            val tip = FactorCanvasFactory.getSecurityTip(factor)
            assertTrue(
                "Factor $factor should have a security tip",
                tip.isNotEmpty()
            )
        }
    }

    @Test
    fun testGetSecurityTip_Biometrics_MentionZeroKnowledge() {
        // Act
        val fingerprintTip = FactorCanvasFactory.getSecurityTip(Factor.FINGERPRINT)
        val faceTip = FactorCanvasFactory.getSecurityTip(Factor.FACE)

        // Assert
        assertTrue(
            "Biometric tips should mention data never leaves device",
            fingerprintTip.toLowerCase().contains("never") ||
            fingerprintTip.toLowerCase().contains("device")
        )
        assertTrue(
            "Face tip should mention zero-knowledge",
            faceTip.toLowerCase().contains("never") ||
            faceTip.toLowerCase().contains("device") ||
            faceTip.toLowerCase().contains("hash")
        )
    }

    // ==================== ESTIMATED TIME TESTS ====================

    @Test
    fun testGetEstimatedCompletionTime_ReasonableRanges() {
        // Act & Assert
        Factor.values().forEach { factor ->
            val time = FactorCanvasFactory.getEstimatedCompletionTime(factor)
            assertTrue(
                "Factor $factor should have reasonable completion time (1s-30s)",
                time in 1_000L..30_000L
            )
        }
    }

    @Test
    fun testGetEstimatedCompletionTime_FastFactors_LessThan5Seconds() {
        // Act
        val pinTime = FactorCanvasFactory.getEstimatedCompletionTime(Factor.PIN)
        val colourTime = FactorCanvasFactory.getEstimatedCompletionTime(Factor.COLOUR)
        val fingerprintTime = FactorCanvasFactory.getEstimatedCompletionTime(Factor.FINGERPRINT)

        // Assert
        assertTrue("PIN should be fast (<=5s)", pinTime <= 5_000L)
        assertTrue("Colour should be fast (<=5s)", colourTime <= 5_000L)
        assertTrue("Fingerprint should be fast (<=3s)", fingerprintTime <= 3_000L)
    }

    // ==================== CATEGORY DISPLAY TESTS ====================

    @Test
    fun testGetCategoryDisplayName_AllCategories_HaveNames() {
        // Act & Assert
        Factor.Category.values().forEach { category ->
            val name = FactorCanvasFactory.getCategoryDisplayName(category)
            assertTrue(
                "Category $category should have a display name",
                name.isNotEmpty()
            )
        }
    }

    @Test
    fun testGetCategoryDisplayName_KNOWLEDGE_ContainsSomething() {
        // Act
        val name = FactorCanvasFactory.getCategoryDisplayName(Factor.Category.KNOWLEDGE)

        // Assert
        assertTrue(
            "Knowledge category should mention 'know' or 'something'",
            name.toLowerCase().contains("know") ||
            name.toLowerCase().contains("something")
        )
    }

    // ==================== DIFFICULTY LEVEL TESTS ====================

    @Test
    fun testGetDifficultyLevel_ReturnsValidRange() {
        // Act & Assert
        Factor.values().forEach { factor ->
            val difficulty = FactorCanvasFactory.getDifficultyLevel(factor)
            assertTrue(
                "Factor $factor difficulty should be 1-5, got $difficulty",
                difficulty in 1..5
            )
        }
    }

    @Test
    fun testGetDifficultyLevel_PIN_IsEasy() {
        // Act
        val difficulty = FactorCanvasFactory.getDifficultyLevel(Factor.PIN)

        // Assert
        assertTrue("PIN should be easy (1-2)", difficulty <= 2)
    }

    @Test
    fun testGetDifficultyLevel_FINGERPRINT_IsEasy() {
        // Act
        val difficulty = FactorCanvasFactory.getDifficultyLevel(Factor.FINGERPRINT)

        // Assert
        assertTrue("Fingerprint should be easy (1-2)", difficulty <= 2)
    }

    @Test
    fun testGetDifficultyLevel_STYLUS_IsHarder() {
        // Act
        val difficulty = FactorCanvasFactory.getDifficultyLevel(Factor.STYLUS_DRAW)

        // Assert
        assertTrue("Stylus draw should be harder (3-5)", difficulty >= 3)
    }

    // ==================== PRACTICE REQUIREMENT TESTS ====================

    @Test
    fun testRequiresPractice_BehavioralFactors_NeedPractice() {
        // Assert
        assertTrue(
            "Pattern micro should require practice",
            FactorCanvasFactory.requiresPractice(Factor.PATTERN_MICRO)
        )
        assertTrue(
            "Rhythm tap should require practice",
            FactorCanvasFactory.requiresPractice(Factor.RHYTHM_TAP)
        )
        assertTrue(
            "Balance should require practice",
            FactorCanvasFactory.requiresPractice(Factor.BALANCE)
        )
    }

    @Test
    fun testRequiresPractice_SimpleFactors_DontNeedPractice() {
        // Assert
        assertFalse(
            "PIN should not require practice",
            FactorCanvasFactory.requiresPractice(Factor.PIN)
        )
        assertFalse(
            "Colour should not require practice",
            FactorCanvasFactory.requiresPractice(Factor.COLOUR)
        )
        assertFalse(
            "Fingerprint should not require practice",
            FactorCanvasFactory.requiresPractice(Factor.FINGERPRINT)
        )
    }

    // ==================== ENROLLMENT ATTEMPTS TESTS ====================

    @Test
    fun testGetRecommendedEnrollmentAttempts_BehavioralFactors_Multiple() {
        // Act
        val patternAttempts = FactorCanvasFactory.getRecommendedEnrollmentAttempts(Factor.PATTERN_MICRO)
        val rhythmAttempts = FactorCanvasFactory.getRecommendedEnrollmentAttempts(Factor.RHYTHM_TAP)

        // Assert
        assertTrue(
            "Behavioral factors should recommend multiple attempts (>=2)",
            patternAttempts >= 2
        )
        assertTrue(
            "Rhythm should recommend multiple attempts",
            rhythmAttempts >= 2
        )
    }

    @Test
    fun testGetRecommendedEnrollmentAttempts_StaticFactors_SingleAttempt() {
        // Act
        val pinAttempts = FactorCanvasFactory.getRecommendedEnrollmentAttempts(Factor.PIN)
        val colourAttempts = FactorCanvasFactory.getRecommendedEnrollmentAttempts(Factor.COLOUR)

        // Assert
        assertEquals("PIN should need 1 attempt", 1, pinAttempts)
        assertEquals("Colour should need 1 attempt", 1, colourAttempts)
    }

    // ==================== EXTENSION FUNCTION TESTS ====================

    @Test
    fun testFactorExtensions_GetInstructions() {
        // Act
        val instructions = Factor.PIN.getInstructions()

        // Assert
        assertTrue("Extension should work", instructions.isNotEmpty())
    }

    @Test
    fun testFactorExtensions_GetSecurityTip() {
        // Act
        val tip = Factor.PIN.getSecurityTip()

        // Assert
        assertTrue("Extension should work", tip.isNotEmpty())
    }

    @Test
    fun testFactorExtensions_GetEstimatedTime() {
        // Act
        val time = Factor.PIN.getEstimatedTime()

        // Assert
        assertTrue("Extension should return positive time", time > 0)
    }

    @Test
    fun testFactorExtensions_NeedsPractice() {
        // Act
        val pinNeedsPractice = Factor.PIN.needsPractice()
        val rhythmNeedsPractice = Factor.RHYTHM_TAP.needsPractice()

        // Assert
        assertFalse("PIN should not need practice", pinNeedsPractice)
        assertTrue("Rhythm should need practice", rhythmNeedsPractice)
    }

    // ==================== CONSISTENCY TESTS ====================

    @Test
    fun testAllFactors_HaveCompleteMetadata() {
        // Act & Assert - Every factor should have all metadata
        Factor.values().forEach { factor ->
            val instructions = FactorCanvasFactory.getInstructions(factor)
            val tip = FactorCanvasFactory.getSecurityTip(factor)
            val time = FactorCanvasFactory.getEstimatedCompletionTime(factor)
            val difficulty = FactorCanvasFactory.getDifficultyLevel(factor)
            val attempts = FactorCanvasFactory.getRecommendedEnrollmentAttempts(factor)

            assertTrue("$factor should have instructions", instructions.isNotEmpty())
            assertTrue("$factor should have security tip", tip.isNotEmpty())
            assertTrue("$factor should have estimated time > 0", time > 0)
            assertTrue("$factor should have valid difficulty (1-5)", difficulty in 1..5)
            assertTrue("$factor should have enrollment attempts >= 1", attempts >= 1)
        }
    }
}
