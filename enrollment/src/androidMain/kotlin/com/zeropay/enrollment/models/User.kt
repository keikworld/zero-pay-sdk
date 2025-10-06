package com.zeropay.enrollment.models

/**
 * User model for ZeroPay enrollment
 */
data class User(
    val uuid: String,
    val alias: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Validates UUID format (RFC 4122)
         */
        fun isValidUUID(uuidString: String): Boolean {
            if (uuidString.length != 36) return false
            if (uuidString.count { it == '-' } != 4) return false
            
            val pattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            return pattern.matches(uuidString)
        }
    }
}
