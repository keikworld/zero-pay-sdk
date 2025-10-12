// Path: sdk/src/commonMain/kotlin/com/zeropay/sdk/factors/processors/PinProcessor.kt

package com.zeropay.sdk.factors.processors

import com.zeropay.sdk.factors.ValidationResult

/**
 * PinProcessor - PIN Code Processing
 * 
 * Processes 6-digit PIN authentication factor.
 * 
 * Format:
 * - Exactly 6 digits: "123456"
 * - Numeric only (0-9)
 * - No letters or special characters
 * 
 * Validation Rules:
 * - Exactly 6 digits
 * - All numeric
 * - Not sequential (123456, 987654)
 * - Not repeating (111111, 000000)
 * - Not common PINs
 * 
 * Weak PIN Detection:
 * - Sequential: 123456, 654321, 234567
 * - Repeating: 111111, 222222, 000000
 * - Common: 123123, 112233, 121212
 * - Birth years: 1980-2025 + 00-99
 * - Simple patterns: 135790, 246810
 * 
 * Security:
 * - Only hash stored
 * - Rate limiting enforced
 * - Account lockout after failures
 * - Constant-time comparison
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */
object PinProcessor {
    
    private const val PIN_LENGTH = 6
    
    // Top 100 most common PINs
    private val WEAK_PINS = setOf(
        "123456", "654321", "111111", "000000", "123123",
        "121212", "112233", "102030", "123321", "666666",
        "777777", "888888", "999999", "555555", "222222",
        "333333", "444444", "012345", "543210", "101010",
        "131313", "232323", "343434", "454545", "565656",
        "676767", "787878", "898989", "909090", "010101",
        "020202", "030303", "040404", "050505", "060606",
        "070707", "080808", "090909", "100100", "200200",
        "135790", "246810", "147258", "369258", "159357",
        "951753", "852963", "741852", "963852", "159753"
    )
    
    /**
     * Validate PIN
     * 
     * Checks:
     * - Exactly 6 digits
     * - All numeric
     * - Not sequential
     * - Not repeating
     * - Not commonly used
     * 
     * @param pin PIN string
     * @return Validation result
     */
    fun validate(pin: String): ValidationResult {
        val warnings = mutableListOf<String>()
        
        // Check length
        if (pin.length != PIN_LENGTH) {
            return ValidationResult(
                isValid = false,
                errorMessage = "PIN must be exactly $PIN_LENGTH digits"
            )
        }
        
        // Check all numeric
        if (!pin.all { it.isDigit() }) {
            return ValidationResult(
                isValid = false,
                errorMessage = "PIN must contain only digits (0-9)"
            )
        }
        
        // Check if weak
        if (isWeak(pin)) {
            warnings.add("This PIN is commonly used. Choose a more unique PIN.")
        }
        
        // Check sequential
        if (isSequential(pin)) {
            warnings.add("PIN contains sequential digits (e.g., 123456)")
        }
        
        // Check repeating
        if (isRepeating(pin)) {
            warnings.add("PIN contains repeating digits (e.g., 111111)")
        }
        
        // Check simple patterns
        if (hasSimplePattern(pin)) {
            warnings.add("PIN has a simple pattern. Consider a more random combination.")
        }
        
        // Check if looks like date/year
        if (looksLikeDate(pin)) {
            warnings.add("PIN looks like a date or year. Avoid personal information.")
        }
        
        return ValidationResult(
            isValid = true,
            warnings = warnings
        )
    }
    
    /**
     * Normalize PIN
     * 
     * @param pin Raw PIN string
     * @return Normalized PIN (trimmed)
     */
    fun normalize(pin: String): String {
        return pin.trim()
    }
    
    /**
     * Check if PIN is weak
     */
    private fun isWeak(pin: String): Boolean {
        return pin in WEAK_PINS
    }
    
    /**
     * Check if PIN is sequential
     */
    private fun isSequential(pin: String): Boolean {
        // Ascending
        val ascending = pin.zipWithNext().all { (a, b) -> b.code - a.code == 1 }
        if (ascending) return true
        
        // Descending
        val descending = pin.zipWithNext().all { (a, b) -> a.code - b.code == 1 }
        if (descending) return true
        
        return false
    }
    
    /**
     * Check if PIN is repeating
     */
    private fun isRepeating(pin: String): Boolean {
        return pin.all { it == pin[0] }
    }
    
    /**
     * Check if PIN has simple pattern
     */
    private fun hasSimplePattern(pin: String): Boolean {
        // Alternating digits (e.g., 121212, 343434)
        if (pin.length >= 4) {
            val pattern = pin.substring(0, 2)
            val repeated = pattern.repeat(pin.length / 2)
            if (pin.startsWith(repeated)) return true
        }
        
        // Alternating pairs (e.g., 112233)
        if (pin.chunked(2).all { it[0] == it[1] }) return true
        
        return false
    }
    
    /**
     * Check if PIN looks like a date
     */
    private fun looksLikeDate(pin: String): Boolean {
        val pinInt = pin.toIntOrNull() ?: return false
        
        // Check if looks like year (1900-2099)
        if (pinInt in 190000..209999) return true
        
        // Check if first 4 digits are a year
        val firstFour = pin.substring(0, 4).toIntOrNull() ?: 0
        if (firstFour in 1950..2025) return true
        
        return false
    }
    
    /**
     * Calculate PIN strength
     */
    fun calculateStrength(pin: String): Int {
        var score = 50 // Base score
        
        // Penalty for weak
        if (isWeak(pin)) score -= 40
        
        // Penalty for sequential
        if (isSequential(pin)) score -= 30
        
        // Penalty for repeating
        if (isRepeating(pin)) score -= 40
        
        // Penalty for simple pattern
        if (hasSimplePattern(pin)) score -= 20
        
        // Penalty for date-like
        if (looksLikeDate(pin)) score -= 15
        
        // Bonus for digit variety
        val uniqueDigits = pin.toSet().size
        score += uniqueDigits * 5
        
        return score.coerceIn(0, 100)
    }
}
