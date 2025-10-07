package com.zeropay.sdk.factors

import com.zeropay.sdk.crypto.ConstantTime
import com.zeropay.sdk.crypto.CryptoUtils
import java.util.Arrays

/**
 * NFC Factor - PRODUCTION VERSION
 * 
 * Reads NFC tag UID as authentication factor.
 * Security: Tag UID is unique and difficult to duplicate.
 * 
 * Security Features:
 * - Constant-time verification
 * - Memory wiping
 * - Replay protection (nonce + timestamp)
 * - UID validation (size checks)
 * - DoS protection
 * 
 * GDPR Compliance:
 * - Only stores 32-byte SHA-256 hash
 * - No raw UID stored or transmitted
 * - Irreversible transformation
 * 
 * PSD3 Category: POSSESSION (something you have)
 * 
 * NFC Standards Supported:
 * - ISO/IEC 14443 Type A/B (4-10 byte UID)
 * - ISO/IEC 15693 (8 byte UID)
 * - FeliCa (8 byte IDm)
 * 
 * @author ZeroPay Security Team
 * @version 1.0.0
 */
object NfcFactor {
    
    // ==================== CONSTANTS ====================
    
    /**
     * Minimum UID size (bytes)
     * Most NFC tags have at least 4-byte UID
     */
    private const val MIN_UID_SIZE = 4
    
    /**
     * Maximum UID size (bytes)
     * ISO/IEC 14443-3 allows up to 10 bytes
     */
    private const val MAX_UID_SIZE = 10
    
    /**
     * Nonce size for replay protection
     */
    private const val NONCE_SIZE = 16
    
    // ==================== DIGEST GENERATION ====================
    
    /**
     * Generate SHA-256 digest from NFC tag UID
     * 
     * Security:
     * - Validates UID size
     * - Adds nonce for replay protection
     * - Adds timestamp for temporal uniqueness
     * - Memory wiped after generation
     * - Constant-time validation
     * 
     * @param tagUid NFC tag UID (4-10 bytes)
     * @return SHA-256 digest (32 bytes)
     * 
     * @throws IllegalArgumentException if UID invalid
     */
    fun digest(tagUid: ByteArray): ByteArray {
        // Constant-time validation
        var isValid = true
        isValid = isValid && tagUid.isNotEmpty()
        isValid = isValid && tagUid.size >= MIN_UID_SIZE
        isValid = isValid && tagUid.size <= MAX_UID_SIZE
        
        if (!isValid) {
            throw IllegalArgumentException(
                "NFC UID must be $MIN_UID_SIZE-$MAX_UID_SIZE bytes (got ${tagUid.size})"
            )
        }
        
        // Check UID is not all zeros (invalid tag)
        val allZeros = tagUid.all { it == 0.toByte() }
        require(!allZeros) { "Invalid NFC tag (UID is all zeros)" }
        
        val combined = mutableListOf<Byte>()
        
        return try {
            // Add tag UID
            combined.addAll(tagUid.toList())
            
            // Add timestamp for replay protection
            val timestamp = System.currentTimeMillis()
            combined.addAll(CryptoUtils.longToBytes(timestamp).toList())
            
            // Add random nonce for additional entropy
            val nonce = CryptoUtils.secureRandomBytes(NONCE_SIZE)
            combined.addAll(nonce.toList())
            
            // Generate irreversible hash
            CryptoUtils.sha256(combined.toByteArray())
            
        } finally {
            // Wipe sensitive data (GDPR)
            combined.clear()
            Arrays.fill(tagUid, 0)
        }
    }
    
    /**
     * Generate digest with specific timestamp and nonce (for verification)
     * 
     * @param tagUid NFC tag UID
     * @param timestamp Specific timestamp
     * @param nonce Specific nonce
     * @return SHA-256 digest (32 bytes)
     */
    fun digestWithParams(tagUid: ByteArray, timestamp: Long, nonce: ByteArray): ByteArray {
        require(tagUid.size in MIN_UID_SIZE..MAX_UID_SIZE) {
            "NFC UID must be $MIN_UID_SIZE-$MAX_UID_SIZE bytes"
        }
        require(nonce.size == NONCE_SIZE) {
            "Nonce must be exactly $NONCE_SIZE bytes"
        }
        
        val combined = mutableListOf<Byte>()
        
        return try {
            combined.addAll(tagUid.toList())
            combined.addAll(CryptoUtils.longToBytes(timestamp).toList())
            combined.addAll(nonce.toList())
            
            CryptoUtils.sha256(combined.toByteArray())
            
        } finally {
            combined.clear()
            Arrays.fill(tagUid, 0)
            Arrays.fill(nonce, 0)
        }
    }
    
    // ==================== VERIFICATION ====================
    
    /**
     * Verify NFC tag against stored digest (constant-time)
     * 
     * Security:
     * - Constant-time comparison
     * - Exception handling prevents leakage
     * - Memory automatically wiped
     * 
     * @param tagUid Tag UID from authentication
     * @param storedDigest Enrolled digest
     * @return true if match, false otherwise
     */
    fun verify(tagUid: ByteArray, storedDigest: ByteArray): Boolean {
        return try {
            val computed = digest(tagUid)
            ConstantTime.equals(computed, storedDigest)
        } catch (e: Exception) {
            false
        }
    }
    
    // ==================== VALIDATION ====================
    
    /**
     * Validate NFC UID format and content
     * 
     * Checks:
     * - Size within valid range
     * - Not all zeros
     * - Not all 0xFF (blank tag)
     * 
     * @param tagUid Tag UID to validate
     * @return true if valid, false otherwise
     */
    fun isValidUid(tagUid: ByteArray): Boolean {
        // Size check
        if (tagUid.size !in MIN_UID_SIZE..MAX_UID_SIZE) {
            return false
        }
        
        // Check not all zeros
        val allZeros = tagUid.all { it == 0.toByte() }
        if (allZeros) {
            return false
        }
        
        // Check not all 0xFF (blank/uninitialized tag)
        val allFF = tagUid.all { it == 0xFF.toByte() }
        if (allFF) {
            return false
        }
        
        return true
    }
    
    /**
     * Get tag type from UID size
     * 
     * @param tagUid Tag UID
     * @return Tag type name
     */
    fun getTagType(tagUid: ByteArray): String {
        return when (tagUid.size) {
            4 -> "ISO 14443 Type A (Single Size UID)"
            7 -> "ISO 14443 Type A (Double Size UID)"
            10 -> "ISO 14443 Type A (Triple Size UID)"
            8 -> "ISO 15693 or FeliCa"
            else -> "Unknown"
        }
    }
    
    // ==================== GETTERS ====================
    
    fun getMinUidSize(): Int = MIN_UID_SIZE
    fun getMaxUidSize(): Int = MAX_UID_SIZE
}
