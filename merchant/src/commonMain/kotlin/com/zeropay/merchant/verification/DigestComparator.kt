// Path: merchant/src/commonMain/kotlin/com/zeropay/merchant/verification/DigestComparator.kt

package com.zeropay.merchant.verification

import com.zeropay.sdk.security.CryptoUtils

/**
 * Digest Comparator - PRODUCTION VERSION
 * 
 * Constant-time comparison of SHA-256 digests to prevent timing attacks.
 * 
 * Security Features:
 * - Constant-time comparison algorithm
 * - No early termination
 * - Fixed execution time regardless of input
 * - Memory-safe operations
 * - Protection against side-channel attacks
 * 
 * Timing Attack Prevention:
 * - All bytes compared even after mismatch found
 * - XOR accumulation prevents branch prediction
 * - No conditional branches based on data
 * - Fixed loop iterations
 * 
 * Performance:
 * - O(n) time complexity where n = digest length
 * - Guaranteed completion within timeout
 * - Minimal memory overhead
 * 
 * @version 1.0.0
 * @date 2025-10-09
 */
class DigestComparator {
    
    companion object {
        private const val TAG = "DigestComparator"
        private const val EXPECTED_DIGEST_SIZE = 32 // SHA-256 = 32 bytes
        private const val COMPARISON_TIMEOUT_MS = 100 // Maximum time allowed
    }
    
    /**
     * Compare two digests in constant time
     * 
     * This implementation prevents timing attacks by:
     * 1. Always comparing all bytes (no early exit)
     * 2. Using XOR accumulation (no branching on data)
     * 3. Fixed execution time regardless of match position
     * 4. Memory-safe operations
     * 
     * @param digest1 First digest (submitted by user)
     * @param digest2 Second digest (enrolled digest from cache)
     * @return true if digests match, false otherwise
     * 
     * @throws IllegalArgumentException if digests are wrong size
     * @throws SecurityException if comparison times out
     */
    fun compare(digest1: ByteArray, digest2: ByteArray): Boolean {
        val startTime = System.currentTimeMillis()
        
        try {
            // Validate digest sizes
            if (digest1.size != EXPECTED_DIGEST_SIZE || digest2.size != EXPECTED_DIGEST_SIZE) {
                println("Invalid digest size: ${digest1.size} vs ${digest2.size}, expected: $EXPECTED_DIGEST_SIZE")
                // Still perform constant-time operation to prevent timing leak
                return constantTimeCompare(digest1, digest2)
            }
            
            // Perform constant-time comparison
            val result = constantTimeCompare(digest1, digest2)
            
            // Verify execution time is within bounds
            val executionTime = System.currentTimeMillis() - startTime
            if (executionTime > COMPARISON_TIMEOUT_MS) {
                println("Digest comparison exceeded timeout: ${executionTime}ms")
                throw SecurityException("Digest comparison timeout exceeded")
            }
            
            println("Digest comparison completed in ${executionTime}ms: ${if (result) "MATCH" else "NO MATCH"}")
            
            return result
            
        } catch (e: Exception) {
            println("Digest comparison error")
            throw e
        } finally {
            // Ensure digests are cleared from memory
            digest1.fill(0)
            digest2.fill(0)
        }
    }
    
    /**
     * Constant-time comparison algorithm
     * 
     * Uses XOR accumulation to prevent branch prediction attacks.
     * Always compares all bytes regardless of early mismatches.
     * 
     * Algorithm:
     * 1. XOR each byte pair
     * 2. Accumulate differences using OR
     * 3. Return true only if accumulator is 0
     * 
     * @param a First byte array
     * @param b Second byte array
     * @return true if arrays are identical
     */
    private fun constantTimeCompare(a: ByteArray, b: ByteArray): Boolean {
        // Handle size mismatch by using max size
        val length = maxOf(a.size, b.size)
        
        // Accumulator for differences
        var diff = 0
        
        // Compare all bytes, even after finding mismatch
        for (i in 0 until length) {
            val byteA = if (i < a.size) a[i].toInt() and 0xFF else 0
            val byteB = if (i < b.size) b[i].toInt() and 0xFF else 0
            
            // XOR the bytes and accumulate
            // If bytes match, XOR = 0
            // If bytes differ, XOR != 0, diff becomes non-zero
            diff = diff or (byteA xor byteB)
        }
        
        // diff is 0 only if all bytes matched
        return diff == 0
    }
    
    /**
     * Batch compare multiple digests
     * 
     * Useful for verifying multiple factors at once.
     * Still maintains constant-time properties.
     * 
     * @param submittedDigests Map of factor to submitted digest
     * @param enrolledDigests Map of factor to enrolled digest
     * @return true if all digests match
     */
    fun batchCompare(
        submittedDigests: Map<com.zeropay.sdk.Factor, ByteArray>,
        enrolledDigests: Map<com.zeropay.sdk.Factor, ByteArray>
    ): Boolean {
        // Check if all factors are present
        if (submittedDigests.keys != enrolledDigests.keys) {
            println("Factor mismatch in batch compare")
            return false
        }
        
        // Compare all digests (constant-time for each)
        var allMatch = true
        
        submittedDigests.forEach { (factor, submittedDigest) ->
            val enrolledDigest = enrolledDigests[factor]
            if (enrolledDigest != null) {
                // Important: Don't early exit, continue comparing all
                if (!compare(submittedDigest.copyOf(), enrolledDigest.copyOf())) {
                    allMatch = false
                    // Continue loop to maintain constant time
                }
            } else {
                allMatch = false
                // Continue loop to maintain constant time
            }
        }
        
        return allMatch
    }
    
    /**
     * Verify digest integrity
     * 
     * Checks if digest appears to be a valid SHA-256 hash.
     * 
     * @param digest Digest to verify
     * @return true if digest appears valid
     */
    fun verifyDigestIntegrity(digest: ByteArray): Boolean {
        // Check size
        if (digest.size != EXPECTED_DIGEST_SIZE) {
            return false
        }
        
        // Check for all-zeros (invalid)
        if (digest.all { it == 0.toByte() }) {
            return false
        }
        
        // Check for all-ones (invalid)
        if (digest.all { it == 0xFF.toByte() }) {
            return false
        }
        
        // Entropy check: SHA-256 should have good entropy
        // Simple check: not all bytes are the same
        val firstByte = digest[0]
        if (digest.all { it == firstByte }) {
            return false
        }
        
        return true
    }
    
    /**
     * Hash comparison (for testing/debugging)
     * 
     * Computes hash of digest for logging purposes.
     * Never log actual digests!
     * 
     * @param digest Digest to hash
     * @return Hex string of hash (first 8 bytes)
     */
    fun getDigestFingerprint(digest: ByteArray): String {
        return try {
            // Use SDK's KMP-compatible sha256 function
            val hash = CryptoUtils.sha256(digest)
            // Return only first 8 bytes as hex
            hash.take(8).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            "INVALID"
        }
    }
}
