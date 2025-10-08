package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Emoji and Colour Factor Tests - PRODUCTION VERSION
 * 
 * Combined tests for simple selection-based factors
 * 
 * Tests:
 * - Digest generation
 * - Constant-time verification
 * - Input validation
 * - Edge cases
 */
class EmojiAndColourFactorTest {
    
    // ==================== EMOJI FACTOR TESTS ====================
    
    @Test
    fun testEmojiDigest_ValidSelection_ReturnsCorrectSize() {
        // Arrange
        val selected = listOf(0, 1, 2, 3)
        
        // Act
        val digest = EmojiFactor.digest(selected)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    @Test
    fun testEmojiDigest_Deterministic() {
        // Arrange
        val selected = listOf(5, 10, 15, 20)
        
        // Act
        val digest1 = EmojiFactor.digest(selected)
        val digest2 = EmojiFactor.digest(selected)
        
        // Assert
        assertArrayEquals(digest1, digest2)
    }
    
    @Test
    fun testEmojiVerify_MatchingSelection_ReturnsTrue() {
        // Arrange
        val selected = listOf(1, 5, 9, 13)
        val digest = EmojiFactor.digest(selected)
        
        // Act
        val result = EmojiFactor.verify(selected, digest)
        
        // Assert
        assertTrue(result)
    }
    
    @Test
    fun testEmojiVerify_ConstantTime() {
        // Arrange
        val correct = listOf(0, 1, 2, 3)
        val wrong = listOf(10, 11, 12, 13)
        val digest = EmojiFactor.digest(correct)
        val iterations = 1000
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) { EmojiFactor.verify(correct, digest) }
        }
        val wrongTime = measureTimeMillis {
            repeat(iterations) { EmojiFactor.verify(wrong, digest) }
        }
        
        // Assert
        val diff = kotlin.math.abs(correctTime - wrongTime)
        val avg = (correctTime + wrongTime) / 2.0
        assertTrue("Should be constant-time", (diff / avg * 100) < 20)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testEmojiDigest_EmptySelection_ThrowsException() {
        EmojiFactor.digest(emptyList())
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testEmojiDigest_TooManyEmojis_ThrowsException() {
        val selected = List(11) { it } // Max is 10
        EmojiFactor.digest(selected)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testEmojiDigest_InvalidIndex_ThrowsException() {
        val selected = listOf(0, 1, 2, 999) // 999 out of range
        EmojiFactor.digest(selected)
    }
    
    @Test
    fun testEmojiGetEmojis_ReturnsNonEmptyList() {
        // Act
        val emojis = EmojiFactor.getEmojis()
        
        // Assert
        assertTrue("Emoji list should not be empty", emojis.isNotEmpty())
        assertTrue("Should have at least 20 emojis", emojis.size >= 20)
    }
    
    // ==================== COLOUR FACTOR TESTS ====================
    
    @Test
    fun testColourDigest_ValidSelection_ReturnsCorrectSize() {
        // Arrange
        val selected = listOf(0, 1, 2)
        
        // Act
        val digest = ColourFactor.digest(selected)
        
        // Assert
        assertEquals("Digest should be 32 bytes", 32, digest.size)
    }
    
    @Test
    fun testColourDigest_Deterministic() {
        // Arrange
        val selected = listOf(2, 4, 5)
        
        // Act
        val digest1 = ColourFactor.digest(selected)
        val digest2 = ColourFactor.digest(selected)
        
        // Assert
        assertArrayEquals(digest1, digest2)
    }
    
    @Test
    fun testColourVerify_MatchingSelection_ReturnsTrue() {
        // Arrange
        val selected = listOf(0, 2, 4)
        val digest = ColourFactor.digest(selected)
        
        // Act
        val result = ColourFactor.verify(selected, digest)
        
        // Assert
        assertTrue(result)
    }
    
    @Test
    fun testColourVerify_ConstantTime() {
        // Arrange
        val correct = listOf(0, 1, 2)
        val wrong = listOf(3, 4, 5)
        val digest = ColourFactor.digest(correct)
        val iterations = 1000
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) { ColourFactor.verify(correct, digest) }
        }
        val wrongTime = measureTimeMillis {
            repeat(iterations) { ColourFactor.verify(wrong, digest) }
        }
        
        // Assert
        val diff = kotlin.math.abs(correctTime - wrongTime)
        val avg = (correctTime + wrongTime) / 2.0
        assertTrue("Should be constant-time", (diff / avg * 100) < 20)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testColourDigest_EmptySelection_ThrowsException() {
        ColourFactor.digest(emptyList())
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testColourDigest_TooManyColours_ThrowsException() {
        val selected = List(11) { it } // Max is 10
        ColourFactor.digest(selected)
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testColourDigest_InvalidIndex_ThrowsException() {
        val selected = listOf(0, 1, 999) // 999 out of range
        ColourFactor.digest(selected)
    }
    
    @Test
    fun testColourGetColours_ReturnsNonEmptyList() {
        // Act
        val colours = ColourFactor.getColours()
        
        // Assert
        assertTrue("Colour list should not be empty", colours.isNotEmpty())
        assertEquals("Should have exactly 6 colours", 6, colours.size)
    }
    
    // ==================== SECURITY TESTS (BOTH FACTORS) ====================
    
    @Test
    fun testBothFactors_DifferentSelections_ProduceDifferentDigests() {
        // Emoji
        val emojiDigest1 = EmojiFactor.digest(listOf(0, 1, 2, 3))
        val emojiDigest2 = EmojiFactor.digest(listOf(4, 5, 6, 7))
        assertFalse(emojiDigest1.contentEquals(emojiDigest2))
        
        // Colour
        val colourDigest1 = ColourFactor.digest(listOf(0, 1, 2))
        val colourDigest2 = ColourFactor.digest(listOf(3, 4, 5))
        assertFalse(colourDigest1.contentEquals(colourDigest2))
    }
    
    @Test
    fun testBothFactors_HighEntropy() {
        // Emoji
        val emojiDigest = EmojiFactor.digest(listOf(0, 1, 2, 3))
        assertTrue(emojiDigest.toSet().size >= 20)
        
        // Colour
        val colourDigest = ColourFactor.digest(listOf(0, 1, 2))
        assertTrue(colourDigest.toSet().size >= 20)
    }
}
