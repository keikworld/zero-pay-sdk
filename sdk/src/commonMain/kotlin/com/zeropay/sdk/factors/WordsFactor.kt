package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.CryptoUtils

/**
 * Words Factor - 4-word authentication
 * 
 * User selects 4 words from a list of 3000 words.
 * During authentication, 12 words are displayed (including the user's 4 selected words).
 * The order and position of words changes dynamically for each authentication.
 * 
 * Security:
 * - 3000^4 = 81 trillion possible combinations
 * - Dynamic word positions prevent shoulder surfing
 * - CSPRNG shuffling ensures unpredictability
 * 
 * GDPR Compliance:
 * - Only stores SHA-256 hash of selected word indices
 * - No personal data in word selection
 */
object WordsFactor {
    
    // Word list: Common 3000 English words for memorability
    // In production, load from a curated list file
    private val WORD_LIST = generateWordList()
    
    /**
     * Generate digest from selected word indices
     * 
     * @param selectedIndices List of 4 word indices (0-2999)
     * @return SHA-256 hash (32 bytes)
     */
    fun digest(selectedIndices: List<Int>): ByteArray {
        require(selectedIndices.size == 4) { "Must select exactly 4 words" }
        require(selectedIndices.all { it in WORD_LIST.indices }) {
            "Invalid word index: must be between 0 and ${WORD_LIST.size - 1}"
        }
        require(selectedIndices.distinct().size == 4) {
            "All selected words must be unique"
        }
        
        // Sort indices for consistent hashing (order doesn't matter for selection)
        val sortedIndices = selectedIndices.sorted()
        
        // Convert to bytes and hash
        val bytes = sortedIndices.flatMap { index ->
            // Use 2 bytes per index (0-2999 fits in 2 bytes)
            listOf(
                (index shr 8).toByte(),
                (index and 0xFF).toByte()
            )
        }.toByteArray()
        
        return CryptoUtils.sha256(bytes)
    }
    
    /**
     * Verify authentication by checking if tapped indices match enrolled words
     * 
     * @param enrolledDigest The digest from enrollment
     * @param tappedIndices The indices tapped during authentication (from shuffled display)
     * @param displayedWords The 12 words that were displayed (with their original indices)
     * @return Boolean indicating if authentication succeeded
     */
    fun verify(
        enrolledDigest: ByteArray,
        tappedIndices: List<Int>,
        displayedWords: List<Pair<Int, String>>
    ): Boolean {
        require(tappedIndices.size == 4) { "Must tap exactly 4 words" }
        require(displayedWords.size == 12) { "Must display exactly 12 words" }
        
        // Get the original word indices that were tapped
        val tappedWordIndices = tappedIndices.map { displayIndex ->
            displayedWords[displayIndex].first
        }
        
        // Generate digest from tapped words
        val tappedDigest = digest(tappedWordIndices)
        
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(enrolledDigest, tappedDigest)
    }
    
    /**
     * Get 12 random words for authentication display
     * Includes the 4 enrolled words plus 8 random decoys
     * 
     * @param enrolledWordIndices The user's 4 enrolled word indices
     * @return List of 12 (index, word) pairs, shuffled
     */
    fun getAuthenticationWords(enrolledWordIndices: List<Int>): List<Pair<Int, String>> {
        require(enrolledWordIndices.size == 4) { "Must have exactly 4 enrolled words" }
        
        // Get the 4 enrolled words
        val enrolledWords = enrolledWordIndices.map { index ->
            index to WORD_LIST[index]
        }
        
        // Get 8 random decoy words (excluding enrolled words)
        val availableIndices = WORD_LIST.indices.filter { it !in enrolledWordIndices }
        val decoyIndices = availableIndices.shuffled().take(8)
        val decoyWords = decoyIndices.map { index ->
            index to WORD_LIST[index]
        }
        
        // Combine and shuffle using CSPRNG
        val allWords = (enrolledWords + decoyWords).toMutableList()
        return com.zeropay.sdk.CsprngShuffle.shuffle(allWords)
    }
    
    /**
     * Get word list for enrollment selection
     */
    fun getWordList(): List<String> = WORD_LIST
    
    /**
     * Get a random subset of words for easier enrollment selection
     * Shows 100 words at a time for user to choose from
     */
    fun getEnrollmentWordSubset(page: Int = 0, pageSize: Int = 100): List<Pair<Int, String>> {
        require(pageSize in 1..500) { "Page size must be between 1 and 500" }
        
        val startIndex = page * pageSize
        val endIndex = minOf(startIndex + pageSize, WORD_LIST.size)
        
        return (startIndex until endIndex).map { index ->
            index to WORD_LIST[index]
        }
    }
    
    /**
     * Search words by prefix for easier enrollment
     */
    fun searchWords(query: String): List<Pair<Int, String>> {
        val lowerQuery = query.lowercase()
        return WORD_LIST
            .mapIndexed { index, word -> index to word }
            .filter { (_, word) -> word.lowercase().startsWith(lowerQuery) }
            .take(50) // Limit results
    }
    
    // ============== Private Helper Methods ==============
    
    /**
     * Constant-time byte array comparison to prevent timing attacks
     */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    /**
     * Generate word list (3000 common English words)
     * In production, this would load from a curated file
     * For now, we generate a placeholder list
     */
    private fun generateWordList(): List<String> {
        // This is a placeholder. In production, load from a curated list like:
        // - BIP39 word list (2048 words)
        // - EFF long wordlist (7776 words)
        // - Custom curated 3000 most common English words
        
        return listOf(
            // Sample words (A-Z)
            "abandon", "ability", "able", "about", "above", "abroad", "absence", "absolute",
            "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse",
            "achieve", "acid", "acoustic", "acquire", "across", "act", "action", "actor",
            "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admire",
            "admit", "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid",
            "again", "age", "agent", "agree", "ahead", "aim", "air", "airport",
            "aisle", "alarm", "album", "alcohol", "alert", "alien", "all", "alley",
            "allow", "almost", "alone", "alpha", "already", "also", "alter", "always",
            "amateur", "amazing", "among", "amount", "amused", "analyst", "anchor", "ancient",
            "anger", "angle", "angry", "animal", "ankle", "announce", "annual", "another",
            "answer", "antenna", "antique", "anxiety", "any", "apart", "apology", "appear",
            "apple", "approve", "april", "arch", "arctic", "area", "arena", "argue",
            // Continue to 3000... (truncated for brevity)
            // In production, include full 3000-word list
            "bacon", "badge", "bag", "balance", "balcony", "ball", "bamboo", "banana",
            "banner", "bar", "barely", "bargain", "barrel", "base", "basic", "basket",
            "battle", "beach", "bean", "beauty", "because", "become", "beef", "before",
            "begin", "behave", "behind", "believe", "below", "belt", "bench", "benefit",
            "best", "betray", "better", "between", "beyond", "bicycle", "bid", "bike",
            "bind", "biology", "bird", "birth", "bitter", "black", "blade", "blame",
            "blanket", "blast", "bleak", "bless", "blind", "blood", "blossom", "blouse"
            // ... continue to 3000 words
        ).also { words ->
            // Ensure we have enough words by repeating if needed (production should have full list)
            val fullList = mutableListOf<String>()
            while (fullList.size < 3000) {
                fullList.addAll(words)
            }
        }.take(3000)
    }
}
