package com.zeropay.sdk.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.util.Arrays

/**
 * Secure Key Derivation using Argon2id
 * 
 * Argon2id is the recommended variant - resistant to:
 * - GPU cracking attacks
 * - Side-channel attacks
 * - Time-memory trade-off attacks
 * 
 * OWASP Recommendations (2023):
 * - Memory: 64 MB minimum
 * - Iterations: 3 minimum
 * - Parallelism: 4 threads
 */
object KeyDerivation {
    
    // Argon2id parameters (OWASP recommendations)
    private const val ITERATIONS = 3
    private const val MEMORY_KB = 65536        // 64 MB
    private const val PARALLELISM = 4
    private const val HASH_LENGTH = 32         // 256 bits
    private const val SALT_LENGTH = 16         // 128 bits
    
    data class DerivedKey(
        val hash: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
        val memoryKB: Int,
        val parallelism: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DerivedKey) return false
            return hash.contentEquals(other.hash) &&
                   salt.contentEquals(other.salt) &&
                   iterations == other.iterations &&
                   memoryKB == other.memoryKB &&
                   parallelism == other.parallelism
        }
        
        override fun hashCode(): Int {
            var result = hash.contentHashCode()
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + iterations
            result = 31 * result + memoryKB
            result = 31 * result + parallelism
            return result
        }
        
        /**
         * Serialize for storage
         */
        fun serialize(): String {
            return "\$argon2id\$v=19\$m=$memoryKB,t=$iterations,p=$parallelism" +
                   "\$${salt.toBase64()}\$${hash.toBase64()}"
        }
        
        companion object {
            /**
             * Deserialize from storage
             */
            fun deserialize(encoded: String): DerivedKey {
                val parts = encoded.split("\$")
                require(parts.size >= 6) { "Invalid encoded format" }
                require(parts[1] == "argon2id") { "Invalid algorithm" }
                
                val params = parts[3].split(",").associate {
                    val (key, value) = it.split("=")
                    key to value.toInt()
                }
                
                return DerivedKey(
                    hash = parts[5].fromBase64(),
                    salt = parts[4].fromBase64(),
                    iterations = params["t"] ?: ITERATIONS,
                    memoryKB = params["m"] ?: MEMORY_KB,
                    parallelism = params["p"] ?: PARALLELISM
                )
            }
        }
    }
    
    /**
     * Derive key from password using Argon2id
     * 
     * @param password Raw password bytes
     * @param salt Optional salt (auto-generated if null)
     * @return DerivedKey with hash and parameters
     */
    fun deriveKey(
        password: ByteArray,
        salt: ByteArray? = null,
        iterations: Int = ITERATIONS,
        memoryKB: Int = MEMORY_KB,
        parallelism: Int = PARALLELISM
    ): DerivedKey {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        require(iterations > 0) { "Iterations must be positive" }
        require(memoryKB >= 8) { "Memory must be at least 8 KB" }
        require(parallelism > 0) { "Parallelism must be positive" }
        
        val actualSalt = salt ?: generateSalt()
        
        return try {
            val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(iterations)
                .withMemoryAsKB(memoryKB)
                .withParallelism(parallelism)
                .withSalt(actualSalt)
            
            val params = builder.build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            
            val hash = ByteArray(HASH_LENGTH)
            generator.generateBytes(password, hash)
            
            DerivedKey(
                hash = hash,
                salt = actualSalt,
                iterations = iterations,
                memoryKB = memoryKB,
                parallelism = parallelism
            )
        } finally {
            // Zero out password from memory
            Arrays.fill(password, 0)
        }
    }
    
    /**
     * Verify password against stored hash (constant-time)
     */
    fun verify(password: ByteArray, storedKey: DerivedKey): Boolean {
        return try {
            val derived = deriveKey(
                password = password,
                salt = storedKey.salt,
                iterations = storedKey.iterations,
                memoryKB = storedKey.memoryKB,
                parallelism = storedKey.parallelism
            )
            
            ConstantTime.equals(derived.hash, storedKey.hash)
        } catch (e: Exception) {
            false
        } finally {
            Arrays.fill(password, 0)
        }
    }
    
    /**
     * Generate cryptographically secure salt
     */
    private fun generateSalt(): ByteArray {
        return CryptoUtils.secureRandomBytes(SALT_LENGTH)
    }
    
    // Base64 encoding helpers
    private fun ByteArray.toBase64(): String {
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(this)
    }
    
    private fun String.fromBase64(): ByteArray {
        return java.util.Base64.getDecoder().decode(this)
    }
}
