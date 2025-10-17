// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/blockchain/SolanaPayUrlGenerator.kt

package com.zeropay.sdk.blockchain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Solana Pay URL Generator
 * 
 * Simple utility for generating Solana Pay payment request URLs.
 * ZeroPay's ONLY job: Generate the URL → Merchant displays QR → Customer scans → Phantom handles everything.
 * 
 * Role: "Bouncer pointing to the bar" - NOT serving drinks!
 * 
 * Solana Pay Standard:
 * solana:<recipient>?amount=<amount>&reference=<unique_ref>&label=<merchant>&message=<description>&memo=<metadata>
 * 
 * Flow:
 * 1. User authenticates (UUID + factors) ✅
 * 2. ZeroPay returns auth token ✅
 * 3. User chooses Phantom as payment method
 * 4. **WE ARE HERE** → Generate Solana Pay URL
 * 5. Merchant displays QR code
 * 6. Customer scans with phone
 * 7. Phantom opens → Shows transaction details
 * 8. Customer reviews and signs
 * 9. Transaction broadcast to blockchain
 * 10. Merchant monitors blockchain (NOT ZeroPay)
 * 
 * What ZeroPay DOESN'T do:
 * ❌ Build transactions
 * ❌ Sign transactions
 * ❌ Monitor transactions
 * ❌ Verify payments
 * ❌ Handle private keys
 * ❌ Process money
 * 
 * What ZeroPay DOES:
 * ✅ Format payment URL
 * ✅ Generate unique reference (for merchant tracking)
 * ✅ Include ZeroPay metadata (optional)
 * ✅ That's it!
 * 
 * Security:
 * - No private keys involved
 * - No access to user wallets
 * - URL is just text (safe)
 * - Customer controls signing
 * 
 * Cost:
 * - ZeroPay: $0 (just generating text)
 * - Customer: ~$0.0002 SOL (blockchain fee, paid from their wallet)
 * 
 * @version 1.0.0
 * @date 2025-10-17
 * @author ZeroPay Blockchain Team
 */
object SolanaPayUrlGenerator {
    
    private const val SOLANA_PAY_PROTOCOL = "solana:"
    
    /**
     * Generate Solana Pay URL for payment
     * 
     * Creates a URL that encodes payment details for Phantom wallet.
     * Customer scans QR → Phantom opens with pre-filled transaction.
     * 
     * @param merchantWallet Merchant's Solana wallet address (Base58)
     * @param amountSol Payment amount in SOL (e.g., 0.05 for $5 if SOL=$100)
     * @param merchantName Merchant display name (shown in Phantom)
     * @param description Transaction description (shown in Phantom)
     * @param reference Unique reference for merchant tracking (Base58, optional)
     * @param zeroPaySessionId ZeroPay session ID (optional metadata)
     * @param userUuid User UUID (optional metadata)
     * @return Solana Pay URL string (ready for QR code)
     * 
     * @throws IllegalArgumentException if parameters invalid
     * 
     * Example:
     * ```kotlin
     * val url = SolanaPayUrlGenerator.generate(
     *     merchantWallet = "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU",
     *     amountSol = 0.05,
     *     merchantName = "Coffee Shop",
     *     description = "Cappuccino + Croissant",
     *     zeroPaySessionId = "session-abc-123",
     *     userUuid = "user-uuid-456"
     * )
     * // Result: solana:7xKXtg2...?amount=0.05&reference=UniqueRef&label=Coffee%20Shop&message=Cappuccino...
     * 
     * // Generate QR code from this URL
     * val qrCodeBitmap = QRCodeGenerator.generate(url)
     * 
     * // Display to customer
     * showQRCode(qrCodeBitmap)
     * ```
     */
    fun generate(
        merchantWallet: String,
        amountSol: Double,
        merchantName: String,
        description: String = "",
        reference: String? = null,
        zeroPaySessionId: String? = null,
        userUuid: String? = null
    ): String {
        // Validate merchant wallet
        require(isValidSolanaAddress(merchantWallet)) {
            "Invalid Solana wallet address: $merchantWallet"
        }
        
        // Validate amount
        require(amountSol > 0) {
            "Amount must be positive, got: $amountSol"
        }
        
        require(amountSol <= 1_000_000) {
            "Amount too large: $amountSol SOL (sanity check)"
        }
        
        // Validate merchant name
        require(merchantName.isNotBlank()) {
            "Merchant name cannot be blank"
        }
        
        // Generate unique reference if not provided
        val finalReference = reference ?: generateReference(zeroPaySessionId, userUuid)
        
        // Build Solana Pay URL
        val params = buildList {
            // Required: amount
            add("amount=$amountSol")
            
            // Required: reference (for merchant tracking)
            add("reference=${urlEncode(finalReference)}")
            
            // Optional: label (merchant name)
            if (merchantName.isNotBlank()) {
                add("label=${urlEncode(merchantName)}")
            }
            
            // Optional: message (transaction description)
            if (description.isNotBlank()) {
                add("message=${urlEncode(description)}")
            }
            
            // Optional: memo (on-chain metadata)
            val memo = buildMemo(zeroPaySessionId, userUuid)
            if (memo != null) {
                add("memo=${urlEncode(memo)}")
            }
        }
        
        return "$SOLANA_PAY_PROTOCOL$merchantWallet?${params.joinToString("&")}"
    }
    
    /**
     * Generate simplified URL (minimal parameters)
     * 
     * Quick version with just wallet + amount.
     * 
     * @param merchantWallet Merchant's Solana wallet address
     * @param amountSol Payment amount in SOL
     * @return Minimal Solana Pay URL
     */
    fun generateSimple(
        merchantWallet: String,
        amountSol: Double
    ): String {
        require(isValidSolanaAddress(merchantWallet)) {
            "Invalid Solana wallet address"
        }
        
        require(amountSol > 0) {
            "Amount must be positive"
        }
        
        val reference = generateReference(null, null)
        return "$SOLANA_PAY_PROTOCOL$merchantWallet?amount=$amountSol&reference=$reference"
    }
    
    /**
     * Generate unique reference for transaction tracking
     * 
     * Reference is used by merchant to identify which ZeroPay session
     * corresponds to which blockchain transaction.
     * 
     * Format: Base58-encoded SHA-256 hash of session data
     * 
     * @param sessionId ZeroPay session ID (optional)
     * @param userUuid User UUID (optional)
     * @return Base58-encoded reference string
     */
    private fun generateReference(sessionId: String?, userUuid: String?): String {
        val data = buildString {
            append(sessionId ?: UUID.randomUUID().toString())
            append(":")
            append(userUuid ?: "anonymous")
            append(":")
            append(System.currentTimeMillis())
        }
        
        val hash = sha256(data.toByteArray())
        return base58Encode(hash)
    }
    
    /**
     * Build memo field (on-chain metadata)
     * 
     * Memo is stored on Solana blockchain and can be used for:
     * - Linking transaction to ZeroPay session
     * - Audit trail
     * - Dispute resolution
     * 
     * Format: "ZeroPay:session:uuid" (max 32 bytes)
     * 
     * @param sessionId ZeroPay session ID
     * @param userUuid User UUID
     * @return Memo string or null
     */
    private fun buildMemo(sessionId: String?, userUuid: String?): String? {
        if (sessionId == null && userUuid == null) {
            return null
        }
        
        val memo = buildString {
            append("ZeroPay")
            if (sessionId != null) {
                append(":$sessionId")
            }
            if (userUuid != null) {
                append(":$userUuid")
            }
        }
        
        // Solana memo instruction supports up to 32 bytes
        return if (memo.toByteArray().size <= 32) {
            memo
        } else {
            // Truncate if too long
            memo.take(29) + "..."
        }
    }
    
    /**
     * Validate Solana address format (Base58, 32-44 chars)
     * 
     * Basic validation - checks format only, not validity on-chain.
     * 
     * @param address Solana wallet address
     * @return true if format looks valid
     */
    private fun isValidSolanaAddress(address: String): Boolean {
        if (address.isBlank()) return false
        if (address.length !in 32..44) return false
        
        // Base58 character set: 123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz
        // (excludes 0, O, I, l to avoid confusion)
        return address.matches(Regex("[1-9A-HJ-NP-Za-km-z]+"))
    }
    
    /**
     * URL encode string
     * 
     * @param value String to encode
     * @return URL-encoded string
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }
    
    /**
     * SHA-256 hash
     * 
     * @param data Input data
     * @return 32-byte hash
     */
    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
    
    /**
     * Base58 encode (Solana standard)
     * 
     * Simple implementation - for production, use a library.
     * 
     * @param data Input bytes
     * @return Base58 string
     */
    private fun base58Encode(data: ByteArray): String {
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val base = alphabet.length
        
        if (data.isEmpty()) return ""
        
        // Count leading zeros
        var zeros = 0
        while (zeros < data.size && data[zeros] == 0.toByte()) {
            zeros++
        }
        
        // Convert to base58
        val input = data.copyOf()
        val output = StringBuilder()
        var i = zeros
        
        while (i < input.size) {
            var carry = input[i].toInt() and 0xFF
            var j = 0
            var temp = 0
            
            for (k in output.length - 1 downTo 0) {
                temp = alphabet.indexOf(output[k]) * 256 + carry
                output[k] = alphabet[temp % base]
                carry = temp / base
            }
            
            while (carry > 0) {
                output.insert(0, alphabet[carry % base])
                carry /= base
            }
            
            i++
        }
        
        // Add leading zeros
        return alphabet[0].toString().repeat(zeros) + output.toString()
    }
}

/**
 * QR Code Generator Helper (optional)
 * 
 * Recommend using a QR code library like ZXing:
 * implementation("com.google.zxing:core:3.5.2")
 * 
 * Example:
 * ```kotlin
 * object QRCodeHelper {
 *     fun generateQRCode(url: String, size: Int = 512): Bitmap {
 *         val writer = QRCodeWriter()
 *         val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)
 *         val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
 *         
 *         for (x in 0 until size) {
 *             for (y in 0 until size) {
 *                 bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
 *             }
 *         }
 *         
 *         return bitmap
 *     }
 * }
 * ```
 */
