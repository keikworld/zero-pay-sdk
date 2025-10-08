package com.zeropay.sdk.factors

import org.junit.Assert.*
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Words Factor Tests - PRODUCTION VERSION (ENHANCED)
 * 
 * Tests:
 * - Digest generation from word indices
 * - Constant-time verification (NEW)
 * - Input validation (exactly 4 words, unique)
 * - Edge cases
 * - Word list operations (search, pagination)
 * - Security enhancements
 */
class WordsFactorTest {
    
    // ==================== BASIC FUNCTIONALITY ====================
    
    @Test
    fun testDigest_ValidIndices_ReturnsCorrectSize() {
        // Arrange - 4 word indices
        val indices = listOf(0, 1, 2, 3)
        
        // Act
        val digest = WordsFactor.digest(indices)
        
        // Assert
        assertEquals("Digest should be 32 bytes (SHA-256)", 32, digest.size)
    }
    
    @Test
    fun testDigest_Deterministic_SameInputProducesSameDigest() {
        // Arrange
        val indices = listOf(10, 20, 30, 40)
        
        // Act
        val digest1 = WordsFactor.digest(indices)
        val digest2 = WordsFactor.digest(indices)
        
        // Assert
        assertArrayEquals("Same indices should produce same digest", digest1, digest2)
    }
    
    @Test
    fun testDigest_OrderIndependent_SortedBeforeHashing() {
        // Arrange - Different orders
        val indices1 = listOf(10, 20, 30, 40)
        val indices2 = listOf(40, 30, 20, 10)
        
        // Act
        val digest1 = WordsFactor.digest(indices1)
        val digest2 = WordsFactor.digest(indices2)
        
        // Assert
        assertArrayEquals(
            "Order shouldn't matter (sorted before hashing)",
            digest1,
            digest2
        )
    }
    
    @Test
    fun testDigest_DifferentWords_ProduceDifferentDigests() {
        // Arrange
        val indices1 = listOf(0, 1, 2, 3)
        val indices2 = listOf(4, 5, 6, 7)
        
        // Act
        val digest1 = WordsFactor.digest(indices1)
        val digest2 = WordsFactor.digest(indices2)
        
        // Assert
        assertFalse(
            "Different word selections should produce different digests",
            digest1.contentEquals(digest2)
        )
    }
    
    // ==================== VERIFICATION (NEW) ====================
    
    @Test
    fun testVerify_MatchingWords_ReturnsTrue() {
        // Arrange
        val indices = listOf(10, 20, 30, 40)
        val digest = WordsFactor.digest(indices)
        
        // Act
        val result = WordsFactor.verify(indices, digest)
        
        // Assert
        assertTrue("Matching words should verify successfully", result)
    }
    
    @Test
    fun testVerify_NonMatchingWords_ReturnsFalse() {
        // Arrange
        val indices1 = listOf(10, 20, 30, 40)
        val indices2 = listOf(50, 60, 70, 80)
        val digest = WordsFactor.digest(indices1)
        
        // Act
        val result = WordsFactor.verify(indices2, digest)
        
        // Assert
        assertFalse("Non-matching words should fail verification", result)
    }
    
    @Test
    fun testVerify_ConstantTime_TimingIndependent() {
        // Arrange
        val correctIndices = listOf(10, 20, 30, 40)
        val wrongIndices = listOf(50, 60, 70, 80)
        val digest = WordsFactor.digest(correctIndices)
        val iterations = 1000
        
        // Act
        val correctTime = measureTimeMillis {
            repeat(iterations) {
                WordsFactor.verify(correctIndices, digest)
            }
        }
        
        val wrongTime = measureTimeMillis {
            repeat(iterations) {
                WordsFactor.verify(wrongIndices, digest)
            }
        }
        
        // Assert
        val timeDifference = kotlin.math.abs(correctTime - wrongTime)
        val averageTime = (correctTime + wrongTime) / 2.0
        val percentageDifference = (timeDifference / averageTime) * 100
        
        assertTrue(
            "Verification should be constant-time (within 20%). " +
            "Correct: ${correctTime}ms, Wrong: ${wrongTime}ms, Diff: ${percentageDifference.toInt()}%",
            percentageDifference < 20
        )
    }
    
    // ==================== VALIDATION ====================
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooFewWords_ThrowsException() {
        // Arrange - Only 3 words
        val indices = listOf(0, 1, 2)
        
        // Act
        WordsFactor.digest(indices)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_TooManyWords_ThrowsException() {
        // Arrange - 5 words
        val indices = listOf(0, 1, 2, 3, 4)
        
        // Act
        WordsFactor.digest(indices)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_DuplicateWords_ThrowsException() {
        // Arrange - Duplicate index
        val indices = listOf(0, 1, 2, 2)
        
        // Act
        WordsFactor.digest(indices)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_InvalidIndex_ThrowsException() {
        // Arrange - Index out of range
        val wordListSize = WordsFactor.getWordListSize()
        val indices = listOf(0, 1, 2, wordListSize)
        
        // Act
        WordsFactor.digest(indices)
        
        // Assert - Should throw
    }
    
    @Test(expected = IllegalArgumentException::class)
    fun testDigest_NegativeIndex_ThrowsException() {
        // Arrange
        val indices = listOf(0, 1, 2, -1)
        
        // Act
        WordsFactor.digest(indices)
        
        // Assert - Should throw
    }
    
    // ==================== EDGE CASES ====================
    
    @Test
    fun testDigest_FirstFourWords_WorksCorrectly() {
        // Arrange - First 4 indices
        val indices = listOf(0, 1, 2, 3)
        
        // Act
        val digest = WordsFactor.digest(indices)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    @Test
    fun testDigest_LastFourWords_WorksCorrectly() {
        // Arrange - Last 4 indices
        val wordListSize = WordsFactor.getWordListSize()
        val indices = listOf(
            wordListSize - 4,
            wordListSize - 3,
            wordListSize - 2,
            wordListSize - 1
        )
        
        // Act
        val digest = WordsFactor.digest(indices)
        
        // Assert
        assertEquals(32, digest.size)
    }
    
    // ==================== WORD LIST OPERATIONS ====================
    
    @Test
    fun testGetEnrollmentWordSubset_ValidPage_ReturnsCorrectSize() {
        // Arrange
        val page = 0
        val pageSize = 20
        
        // Act
        val words = WordsFactor.getEnrollmentWordSubset(page, pageSize)
        
        // Assert
        assertEquals("Should return 20 words", 20, words.size)
    }
    
    @Test
    fun testGetEnrollmentWordSubset_MultiplePages_NoOverlap() {
        // Arrange
        val pageSize = 20
        
        // Act
        val page0Words = WordsFactor.getEnrollmentWordSubset(0, pageSize)
        val page1Words = WordsFactor.getEnrollmentWordSubset(1, pageSize)
        
        // Assert
        val page0Indices = page0Words.map { it.first }.toSet()
        val page1Indices = page1Words.map { it.first }.toSet()
        
        assertTrue(
            "Pages should not overlap",
            page0Indices.intersect(page1Indices).isEmpty()
        )
    }
    
    @Test
    fun testGetAuthenticationWords_ReturnsCorrectCount() {
        // Arrange
        val enrolledWords = listOf(0, 1, 2, 3)
        
        // Act
        val authWords = WordsFactor.getAuthenticationWords(enrolledWords)
        
        // Assert
        assertEquals("Should return 12 words (4 enrolled + 8 decoys)", 12, authWords.size)
    }
    
    @Test
    fun testGetAuthenticationWords_IncludesEnrolledWords() {
        // Arrange
        val enrolledWords = listOf(10, 20, 30, 40)
        
        // Act
        val authWords = WordsFactor.getAuthenticationWords(enrolledWords)
        
        // Assert
        val authIndices = authWords.map { it.first }
        enrolledWords.forEach { enrolledIndex ->
            assertTrue(
                "Authentication words should include all enrolled words",
                authIndices.contains(enrolledIndex)
            )
        }
    }
    
    @Test
    fun testSearchWords_ValidPrefix_ReturnsMatches() {
        // Arrange
        val prefix = "a"
        
        // Act
        val results = WordsFactor.searchWords(prefix, maxResults = 50)
        
        // Assert
        assertTrue("Should return at least some results", results.isNotEmpty())
        results.forEach { (_, word) ->
            assertTrue(
                "All results should start with prefix",
                word.lowercase().startsWith(prefix.lowercase())
            )
        }
    }
    
    @Test
    fun testSearchWords_RespectMaxResults() {
        // Arrange
        val prefix = "a"
        val maxResults = 10
        
        // Act
        val results = WordsFactor.searchWords(prefix, maxResults)
        
        // Assert
        assertTrue(
            "Should not exceed max results",
            results.size <= maxResults
        )
    }
    
    // ==================== SECURITY ====================
    
    @Test
    fun testDigest_HighEntropy_NoPattern() {
        // Arrange
        val indices = listOf(10, 20, 30, 40)
        
        // Act
        val digest = WordsFactor.digest(indices)
        
        // Assert
        val uniqueBytes = digest.toSet().size
        assertTrue(
            "Digest should have high entropy (>=20 unique bytes)",
            uniqueBytes >= 20
        )
        
        assertFalse(
            "Digest should not be all zeros",
            digest.all { it == 0.toByte() }
        )
    }
    
    @Test
    fun testDigest_SmallChange_CompleteDifferentHash() {
        // Arrange - Only 1 word different
        val indices1 = listOf(10, 20, 30, 40)
        val indices2 = listOf(10, 20, 30, 41) // Last word different
        
        // Act
        val digest1 = WordsFactor.digest(indices1)
        val digest2 = WordsFactor.digest(indices2)
        
        // Assert - Avalanche effect check
        var differentBytes = 0
        for (i in digest1.indices) {
            if (digest1[i] != digest2[i]) differentBytes++
        }
        
        assertTrue(
            "Small input change should cause large hash change (avalanche effect). " +
            "Different bytes: $differentBytes/32",
            differentBytes >= 16 // At least half should be different
        )
    }
    
    // ==================== GETTERS ====================
    
    @Test
    fun testGetters_ReturnCorrectValues() {
        // Act & Assert
        assertEquals(4, WordsFactor.getWordCount())
        assertTrue(
            "Word list should have reasonable size (>=100)",
            WordsFactor.getWordListSize() >= 100
        )
    }
    
    @Test
    fun testGetWords_ReturnsDefensiveCopy() {
        // Act
        val words1 = WordsFactor.getWords()
        val words2 = WordsFactor.getWords()
        
        // Assert - Should be equal but different instances
        assertEquals("Should return same words", words1, words2)
        assertFalse("Should return defensive copy (different instances)", words1 === words2)
    }
}
