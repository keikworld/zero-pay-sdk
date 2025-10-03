package com.zeropay.sdk.zksnark

import com.zeropay.sdk.Factor
import com.zeropay.sdk.crypto.CryptoUtils
import java.io.ByteArrayOutputStream

/**
 * zkSNARK Preparation Module
 * 
 * Prepares authentication proofs for zero-knowledge circuit verification.
 * Target circuit size: 80-100kb (optimized for mobile)
 * 
 * Architecture:
 * - CircuitInput: Structured data for circuit
 * - PedersenCommitment: Cryptographic commitments
 * - WitnessGenerator: Private inputs for prover
 * - PublicInputs: Verifier-visible data
 * 
 * Week 2 Goal: Generate zk-SNARK proofs
 * Circuit proves: "I know N valid factor digests" without revealing them
 */

/**
 * Circuit Input Structure
 * 
 * Optimized for 80-100kb constraint:
 * - Compact serialization (VarInt encoding)
 * - Minimal overhead
 * - Forward-compatible versioning
 */
data class CircuitInput(
    val factorCommitments: List<ByteArray>,   // Pedersen commitments of factors
    val publicInputs: ByteArray,              // Public data (visible to verifier)
    val auxiliaryData: ByteArray,             // Metadata (session, timestamp)
    val version: Int = 1                      // Circuit version for compatibility
) {
    /**
     * Serialize for circuit input
     * Target: <100kb for mobile efficiency
     */
    fun serialize(): ByteArray {
        return ByteArrayOutputStream().apply {
            // Version byte (forward compatibility)
            write(version)
            
            // Number of factor commitments (VarInt)
            writeVarInt(factorCommitments.size)
            
            // Each commitment (padded to 32 bytes for circuit consistency)
            factorCommitments.forEach { commitment ->
                write(commitment.padTo32Bytes())
            }
            
            // Public inputs length + data
            writeVarInt(publicInputs.size)
            write(publicInputs)
            
            // Auxiliary data length + data
            writeVarInt(auxiliaryData.size)
            write(auxiliaryData)
        }.toByteArray()
    }
    
    /**
     * Deserialize from circuit input
     */
    companion object {
        fun deserialize(data: ByteArray): CircuitInput {
            val stream = data.inputStream()
            
            // Read version
            val version = stream.read()
            
            // Read commitments
            val commitmentCount = stream.readVarInt()
            val commitments = List(commitmentCount) {
                ByteArray(32).also { stream.read(it) }
            }
            
            // Read public inputs
            val publicInputsSize = stream.readVarInt()
            val publicInputs = ByteArray(publicInputsSize).also { stream.read(it) }
            
            // Read auxiliary data
            val auxSize = stream.readVarInt()
            val auxiliaryData = ByteArray(auxSize).also { stream.read(it) }
            
            return CircuitInput(
                factorCommitments = commitments,
                publicInputs = publicInputs,
                auxiliaryData = auxiliaryData,
                version = version
            )
        }
    }
    
    /**
     * Get size estimate in bytes
     */
    fun estimateSize(): Int {
        return 1 + // version
               4 + // commitment count
               (factorCommitments.size * 32) + // commitments
               4 + publicInputs.size + // public inputs
               4 + auxiliaryData.size  // auxiliary data
    }
    
    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var v = value
        while (v >= 0x80) {
            write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        write(v and 0x7F)
    }
    
    private fun java.io.InputStream.readVarInt(): Int {
        var result = 0
        var shift = 0
        
        while (true) {
            val byte = read()
            result = result or ((byte and 0x7F) shl shift)
            
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        
        return result
    }
    
    private fun ByteArray.padTo32Bytes(): ByteArray {
        return when {
            size == 32 -> this
            size < 32 -> this + ByteArray(32 - size)
            else -> take(32).toByteArray()
        }
    }
}

/**
 * Pedersen Commitment Scheme
 * 
 * Cryptographic commitment:
 * - Hiding: Commitment reveals nothing about value
 * - Binding: Cannot change value after commitment
 * 
 * Used for factor digests in zkSNARK circuit
 */
object PedersenCommitment {
    
    /**
     * Commit to a value with randomness
     * 
     * Commitment = H(G^value * H^randomness)
     * where G, H are generator points
     * 
     * Simplified: C = SHA256(value || randomness)
     */
    fun commit(value: ByteArray, randomness: ByteArray): ByteArray {
        require(value.isNotEmpty()) { "Value cannot be empty" }
        require(randomness.size >= 32) { "Randomness must be at least 32 bytes" }
        
        // Combine value and randomness
        val combined = value + randomness
        
        // Generate commitment using SHA-256
        return CryptoUtils.sha256(combined)
    }
    
    /**
     * Commit with auto-generated randomness
     */
    fun commit(value: ByteArray): CommitmentData {
        val randomness = CryptoUtils.secureRandomBytes(32)
        val commitment = commit(value, randomness)
        
        return CommitmentData(
            commitment = commitment,
            randomness = randomness
        )
    }
    
    /**
     * Batch commit multiple values
     * Used for committing to all factor digests at once
     */
    fun batchCommit(values: List<ByteArray>): List<CommitmentData> {
        return values.map { value ->
            commit(value)
        }
    }
    
    /**
     * Verify commitment opens correctly
     */
    fun verify(
        commitment: ByteArray,
        value: ByteArray,
        randomness: ByteArray
    ): Boolean {
        val recomputed = commit(value, randomness)
        return constantTimeEquals(commitment, recomputed)
    }
    
    /**
     * Homomorphic property: Combine commitments
     * Used for aggregating factor proofs
     */
    fun combine(commitments: List<ByteArray>): ByteArray {
        require(commitments.isNotEmpty()) { "Cannot combine empty commitments" }
        
        // XOR all commitments (simplified homomorphic operation)
        val result = ByteArray(32)
        
        commitments.forEach { commitment ->
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor commitment[i].toInt()).toByte()
            }
        }
        
        return result
    }
    
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
    
    data class CommitmentData(
        val commitment: ByteArray,  // Public commitment
        val randomness: ByteArray   // Private randomness (for opening)
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CommitmentData) return false
            return commitment.contentEquals(other.commitment) &&
                   randomness.contentEquals(other.randomness)
        }
        
        override fun hashCode(): Int {
            return 31 * commitment.contentHashCode() + randomness.contentHashCode()
        }
    }
}

/**
 * Witness Generator
 * 
 * Generates private inputs for zkSNARK prover
 * Witness = All private data needed to prove statement
 */
object WitnessGenerator {
    
    /**
     * Generate witness from factor proofs
     * 
     * Witness contains:
     * - Factor digests (private)
     * - Commitment randomness (private)
     * - User secrets (private)
     */
    fun generate(
        factorProofs: Map<Factor, ByteArray>,
        commitmentData: List<PedersenCommitment.CommitmentData>,
        userUuid: String,
        sessionId: ByteArray
    ): Witness {
        require(factorProofs.size == commitmentData.size) {
            "Factor proofs and commitments must match"
        }
        
        // Private inputs (never revealed to verifier)
        val privateInputs = mutableMapOf<String, ByteArray>()
        
        // Add factor digests
        factorProofs.entries.forEachIndexed { index, (factor, digest) ->
            privateInputs["factor_${index}_digest"] = digest
            privateInputs["factor_${index}_randomness"] = commitmentData[index].randomness
        }
        
        // Add user secrets
        privateInputs["user_uuid"] = userUuid.toByteArray()
        privateInputs["session_id"] = sessionId
        
        return Witness(
            privateInputs = privateInputs,
            metadata = mapOf(
                "timestamp" to System.currentTimeMillis().toString(),
                "factor_count" to factorProofs.size.toString()
            )
        )
    }
    
    /**
     * Witness data structure
     */
    data class Witness(
        val privateInputs: Map<String, ByteArray>,
        val metadata: Map<String, String>
    ) {
        /**
         * Serialize witness for circuit
         */
        fun serialize(): ByteArray {
            return ByteArrayOutputStream().apply {
                // Write private inputs count
                write(privateInputs.size)
                
                // Write each input (key length + key + value length + value)
                privateInputs.forEach { (key, value) ->
                    val keyBytes = key.toByteArray()
                    write(keyBytes.size)
                    write(keyBytes)
                    write(value.size)
                    write(value)
                }
                
                // Write metadata count
                write(metadata.size)
                
                // Write each metadata entry
                metadata.forEach { (key, value) ->
                    val keyBytes = key.toByteArray()
                    val valueBytes = value.toByteArray()
                    write(keyBytes.size)
                    write(keyBytes)
                    write(valueBytes.size)
                    write(valueBytes)
                }
            }.toByteArray()
        }
    }
}

/**
 * Public Inputs Generator
 * 
 * Generates public inputs visible to verifier
 * Public inputs: Data that verifier can see without proof
 */
object PublicInputsGenerator {
    
    /**
     * Generate public inputs
     * 
     * Public data includes:
     * - Commitments (hiding factor digests)
     * - Merkle root (binding all factors)
     * - Timestamp (replay protection)
     * - Required factor count
     */
    fun generate(
        commitments: List<ByteArray>,
        merkleRoot: ByteArray,
        timestamp: Long,
        requiredFactorCount: Int
    ): PublicInputs {
        return PublicInputs(
            commitments = commitments,
            merkleRoot = merkleRoot,
            timestamp = timestamp,
            requiredFactorCount = requiredFactorCount
        )
    }
    
    data class PublicInputs(
        val commitments: List<ByteArray>,
        val merkleRoot: ByteArray,
        val timestamp: Long,
        val requiredFactorCount: Int
    ) {
        /**
         * Serialize for circuit
         */
        fun serialize(): ByteArray {
            return ByteArrayOutputStream().apply {
                // Write commitment count
                write(commitments.size)
                
                // Write commitments
                commitments.forEach { commitment ->
                    write(commitment)
                }
                
                // Write Merkle root
                write(merkleRoot)
                
                // Write timestamp (8 bytes)
                write(CryptoUtils.longToBytes(timestamp))
                
                // Write required factor count
                write(requiredFactorCount)
            }.toByteArray()
        }
        
        /**
         * Hash public inputs for verification
         */
        fun hash(): ByteArray {
            return CryptoUtils.sha256(serialize())
        }
    }
}

/**
 * zkSNARK Proof Builder
 * 
 * Combines all components to build circuit input
 * Week 2: Integrate with actual zkSNARK library (snarkjs, bellman, etc.)
 */
object ZkProofBuilder {
    
    /**
     * Build circuit input from authentication state
     * 
     * Steps:
     * 1. Generate commitments for factors
     * 2. Generate Merkle tree of commitments
     * 3. Create witness (private inputs)
     * 4. Create public inputs
     * 5. Serialize for circuit
     */
    fun buildCircuitInput(
        factorProofs: Map<Factor, ByteArray>,
        userUuid: String,
        sessionId: ByteArray,
        timestamp: Long
    ): CircuitInput {
        // Step 1: Generate commitments
        val commitmentData = PedersenCommitment.batchCommit(
            factorProofs.values.toList()
        )
        val commitments = commitmentData.map { it.commitment }
        
        // Step 2: Generate Merkle root
        val merkleRoot = com.zeropay.sdk.security.MerkleTree.computeRoot(commitments)
        
        // Step 3: Generate witness
        val witness = WitnessGenerator.generate(
            factorProofs = factorProofs,
            commitmentData = commitmentData,
            userUuid = userUuid,
            sessionId = sessionId
        )
        
        // Step 4: Generate public inputs
        val publicInputs = PublicInputsGenerator.generate(
            commitments = commitments,
            merkleRoot = merkleRoot,
            timestamp = timestamp,
            requiredFactorCount = factorProofs.size
        )
        
        // Step 5: Build circuit input
        return CircuitInput(
            factorCommitments = commitments,
            publicInputs = publicInputs.serialize(),
            auxiliaryData = witness.metadata.entries.joinToString("|") { "${it.key}:${it.value}" }.toByteArray()
        )
    }
    
    /**
     * Estimate circuit size
     * Target: <100kb for mobile
     */
    fun estimateCircuitSize(factorCount: Int): Int {
        val commitmentSize = factorCount * 32 // 32 bytes per commitment
        val merkleSize = 32 // Merkle root
        val publicInputsSize = 50 // Metadata
        val witnessSize = factorCount * 64 // Digest + randomness per factor
        
        return commitmentSize + merkleSize + publicInputsSize + witnessSize
    }
}

/**
 * Circuit Verification (Verifier side)
 * 
 * Verifies zkSNARK proof without seeing private inputs
 * Week 2: Integrate with zkSNARK verifier library
 */
object CircuitVerifier {
    
    /**
     * Verify proof (placeholder for actual zkSNARK verification)
     * 
     * Week 2: Replace with actual zkSNARK verifier
     * For now: Simple validation
     */
    fun verify(
        circuitInput: CircuitInput,
        proof: ByteArray  // zkSNARK proof (generated by prover)
    ): Boolean {
        // Validate circuit input structure
        if (circuitInput.factorCommitments.isEmpty()) return false
        if (circuitInput.publicInputs.isEmpty()) return false
        
        // Check size constraints
        val size = circuitInput.estimateSize()
        if (size > 100_000) { // 100kb limit
            return false
        }
        
        // Week 2: Actual zkSNARK verification
        // val verificationKey = loadVerificationKey()
        // return ZkSnark.verify(verificationKey, circuitInput.publicInputs, proof)
        
        // Placeholder: Simple validation
        return proof.size == 32 && proof.any { it != 0.toByte() }
    }
}
