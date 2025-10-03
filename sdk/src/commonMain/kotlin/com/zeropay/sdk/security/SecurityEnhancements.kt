package com.zeropay.sdk.security

import com.zeropay.sdk.crypto.CryptoUtils
import com.zeropay.sdk.Factor
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Critical Security Enhancements for ZeroPay SDK
 * 
 * Implements enhancements from enhancements.txt:
 * 1. Factor Digest Binding (Merkle tree)
 * 2. Anti-Hammering Protection (Proof of Work)
 * 3. Pattern Security Analysis
 * 4. Voice Liveness Detection
 * 5. Secure Memory Handling
 */

// ============== 1. Factor Digest Binding ==============

object FactorDigestBinding {
    
    /**
     * Bind multiple factor digests using Merkle tree
     * Prevents replay attacks by linking all factors together
     */
    fun bindDigests(
        factorDigests: Map<Factor, ByteArray>,
        sessionId: ByteArray,
        timestamp: Long,
        userUuid: String
    ): ByteArray {
        // Create Merkle tree of factor digests
        val merkleRoot = MerkleTree.computeRoot(factorDigests.values.toList())
        
        // Bind with session and time
        val binding = ByteArrayOutputStream().apply {
            write(merkleRoot)
            write(sessionId)
            write(CryptoUtils.longToBytes(timestamp))
            write(userUuid.toByteArray())
        }.toByteArray()
        
        return CryptoUtils.sha256(binding)
    }
    
    /**
     * Verify binding matches expected factors
     */
    fun verifyBinding(
        boundDigest: ByteArray,
        factorDigests: Map<Factor, ByteArray>,
        sessionId: ByteArray,
        timestamp: Long,
        userUuid: String
    ): Boolean {
        val computedBinding = bindDigests(factorDigests, sessionId, timestamp, userUuid)
        return constantTimeEquals(boundDigest, computedBinding)
    }
    
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}

// ============== Merkle Tree Implementation ==============

object MerkleTree {
    
    fun computeRoot(leaves: List<ByteArray>): ByteArray {
        require(leaves.isNotEmpty()) { "Cannot compute Merkle root of empty list" }
        
        if (leaves.size == 1) return leaves[0]
        
        var currentLevel = leaves.toList()
        
        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<ByteArray>()
            
            for (i in currentLevel.indices step 2) {
                val left = currentLevel[i]
                val right = if (i + 1 < currentLevel.size) {
                    currentLevel[i + 1]
                } else {
                    left // Duplicate last node if odd number
                }
                
                // Hash pair
                val combined = left + right
                nextLevel.add(CryptoUtils.sha256(combined))
            }
            
            currentLevel = nextLevel
        }
        
        return currentLevel[0]
    }
}

// ============== 2. Anti-Hammering Protection ==============

object ProofOfWork {
    
    /**
     * Generate challenge for proof-of-work
     * Client must solve before submitting authentication
     */
    fun generateChallenge(difficulty: Int = 20): ByteArray {
        require(difficulty in 1..30) { "Difficulty must be between 1 and 30" }
        return CryptoUtils.secureRandomBytes(32)
    }
    
    /**
     * Verify proof-of-work solution
     * Checks if hash has required number of leading zero bits
     */
    fun verifySolution(challenge: ByteArray, nonce: Long, difficulty: Int): Boolean {
        val solution = challenge + CryptoUtils.longToBytes(nonce)
        val hash = CryptoUtils.sha256(solution)
        
        // Check if first 'difficulty' bits are zero
        val requiredZeros = difficulty / 8
        val remainingBits = difficulty % 8
        
        // Check full zero bytes
        for (i in 0 until requiredZeros) {
            if (hash[i] != 0.toByte()) return false
        }
        
        // Check remaining bits if any
        if (remainingBits > 0) {
            val byte = hash[requiredZeros].toInt() and 0xFF
            val mask = (1 shl (8 - remainingBits)) - 1
            if ((byte and mask.inv()) != 0) return false
        }
        
        return true
    }
    
    /**
     * Adaptive difficulty based on authentication frequency
     */
    fun getAdaptiveDifficulty(recentAttempts: Int): Int {
        return when {
            recentAttempts < 3 -> 16  // Easy for normal use
            recentAttempts < 10 -> 20 // Medium for elevated activity
            recentAttempts < 20 -> 24 // Hard for suspicious activity
            else -> 28                 // Very hard for potential attack
        }
    }
}

// ============== 3. Pattern Security Analysis ==============

object PatternSecurityAnalyzer {
    
    data class PatternMetrics(
        val velocityVariance: Float,
        val accelerationJitter: Float,
        val angleConsistency: Float,
        val pressureVariance: Float,
        val isLikelyHuman: Boolean
    )
    
    private const val MIN_HUMAN_VARIANCE = 0.01f
    private const val MAX_ACCELERATION_JITTER = 100f
    
    /**
     * Analyze pattern for human characteristics
     * Detects automated/robotic input patterns
     */
    fun analyzePattern(points: List<PatternPoint>): PatternMetrics {
        require(points.size >= 3) { "Need at least 3 points for analysis" }
        
        // Calculate velocities between consecutive points
        val velocities = points.windowed(2).map { (p1, p2) ->
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dt = (p2.t - p1.t).coerceAtLeast(1)
            sqrt(dx * dx + dy * dy) / dt
        }
        
        // Calculate accelerations
        val accelerations = velocities.windowed(2).map { (v1, v2) ->
            abs(v2 - v1)
        }
        
        // Calculate angles between segments
        val angles = points.windowed(3).map { (p1, p2, p3) ->
            val angle1 = kotlin.math.atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
            val angle2 = kotlin.math.atan2((p3.y - p2.y).toDouble(), (p3.x - p2.x).toDouble())
            abs(angle2 - angle1)
        }
        
        // Calculate pressure variance (if available)
        val pressures = points.mapNotNull { it.pressure }
        val pressureVariance = if (pressures.size > 1) {
            calculateVariance(pressures)
        } else {
            0f
        }
        
        val velocityVariance = calculateVariance(velocities)
        val accelerationJitter = if (accelerations.isNotEmpty()) {
            accelerations.average().toFloat()
        } else {
            0f
        }
        val angleConsistency = if (angles.isNotEmpty()) {
            1f - (angles.map { it.toFloat() }.average().toFloat() / Math.PI.toFloat())
        } else {
            0f
        }
        
        // Determine if pattern appears human
        val isLikelyHuman = velocityVariance >= MIN_HUMAN_VARIANCE &&
                           accelerationJitter < MAX_ACCELERATION_JITTER &&
                           pressureVariance > 0.001f
        
        return PatternMetrics(
            velocityVariance = velocityVariance,
            accelerationJitter = accelerationJitter,
            angleConsistency = angleConsistency,
            pressureVariance = pressureVariance,
            isLikelyHuman = isLikelyHuman
        )
    }
    
    data class PatternPoint(
        val x: Float,
        val y: Float,
        val t: Long,
        val pressure: Float? = null
    )
    
    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        val squaredDiffs = values.map { (it - mean) * (it - mean) }
        return squaredDiffs.average().toFloat()
    }
}

// ============== 4. Voice Liveness Detection ==============

object VoiceLivenessDetector {
    
    /**
     * Generate random challenge phrase for user to speak
     * Prevents replay attacks
     */
    fun generateChallenge(): String {
        val words = listOf(
            "red", "blue", "green", "yellow", "orange", "purple",
            "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "alpha", "beta", "gamma", "delta", "echo", "foxtrot"
        )
        
        // Select 3 random words
        val shuffled = words.shuffled()
        return shuffled.take(3).joinToString(" ")
    }
    
    /**
     * Validate audio has human voice characteristics
     * Basic checks - in production, use proper audio analysis library
     */
    fun validateAudioSpectrum(audioBytes: ByteArray): Boolean {
        require(audioBytes.isNotEmpty()) { "Audio data cannot be empty" }
        
        // Basic validation checks
        val hasVariance = checkAmplitudeVariance(audioBytes)
        val hasNaturalPattern = checkNaturalPattern(audioBytes)
        val notTooClean = checkNotTooClean(audioBytes)
        
        return hasVariance && hasNaturalPattern && notTooClean
    }
    
    /**
     * Check if audio has natural amplitude variance (not synthetic)
     */
    private fun checkAmplitudeVariance(audioBytes: ByteArray): Boolean {
        if (audioBytes.size < 100) return false
        
        val samples = audioBytes.map { it.toInt() and 0xFF }
        val mean = samples.average()
        val variance = samples.map { (it - mean) * (it - mean) }.average()
        
        // Natural audio has variance, synthetic might be too consistent
        return variance > 10.0
    }
    
    /**
     * Check for natural audio patterns (not perfectly regular)
     */
    private fun checkNaturalPattern(audioBytes: ByteArray): Boolean {
        if (audioBytes.size < 100) return false
        
        // Check for repeating patterns (suspicious)
        val chunkSize = 50
        val chunks = audioBytes.toList().windowed(chunkSize, chunkSize)
        
        // If too many identical chunks, likely synthetic
        val uniqueChunks = chunks.distinct().size
        return uniqueChunks > chunks.size / 2
    }
    
    /**
     * Check audio is not too clean (natural recording has noise)
     */
    private fun checkNotTooClean(audioBytes: ByteArray): Boolean {
        if (audioBytes.size < 100) return false
        
        // Natural recordings have some near-zero samples (background noise)
        val nearZeroCount = audioBytes.count { abs(it.toInt()) < 5 }
        val ratio = nearZeroCount.toFloat() / audioBytes.size
        
        // Should have some but not too many near-zero samples
        return ratio in 0.01f..0.3f
    }
    
    /**
     * Generate liveness hash including challenge response
     */
    fun generateLivenessHash(
        audioBytes: ByteArray,
        challengePhrase: String,
        timestamp: Long
    ): ByteArray {
        val combined = ByteArrayOutputStream().apply {
            write(audioBytes)
            write(challengePhrase.toByteArray())
            write(CryptoUtils.longToBytes(timestamp))
        }.toByteArray()
        
        return CryptoUtils.sha256(combined)
    }
}

// ============== 5. Secure Memory Handling ==============

/**
 * Secure byte array that automatically zeroes memory on close
 * Prevents sensitive data from lingering in memory
 */
class SecureByteArray(size: Int) : AutoCloseable {
    private val data = ByteArray(size)
    @Volatile private var cleared = false
    
    fun get(): ByteArray {
        check(!cleared) { "SecureByteArray has been cleared" }
        return data
    }
    
    fun use(block: (ByteArray) -> Unit) {
        if (!cleared) {
            block(data)
        }
    }
    
    override fun close() {
        if (!cleared) {
            // Overwrite with random data multiple times (DoD 5220.22-M)
            repeat(3) {
                val random = CryptoUtils.secureRandomBytes(data.size)
                random.copyInto(data)
            }
            // Final zero fill
            data.fill(0)
            cleared = true
        }
    }
    
    fun size(): Int = data.size
}

// ============== 6. Digest Validation ==============

object DigestValidator {
    
    /**
     * Validate digest format is correct
     */
    fun validateDigestFormat(digest: ByteArray): Boolean {
        return digest.size == 32 &&                      // SHA-256 size
               digest.any { it != 0.toByte() } &&        // Not all zeros
               digest.distinct().size > 16                // Sufficient entropy
    }
    
    /**
     * Validate set of factor digests
     * Ensures no duplicate digests (would be suspicious)
     */
    fun validateFactorSet(digests: Map<Factor, ByteArray>): Boolean {
        // Check all digests are valid format
        if (!digests.values.all { validateDigestFormat(it) }) {
            return false
        }
        
        // Ensure no duplicate digests
        val uniqueDigests = digests.values.map { it.toHexString() }.toSet()
        return uniqueDigests.size == digests.size
    }
    
    /**
     * Calculate entropy score of digest (0.0-1.0)
     */
    fun calculateEntropy(digest: ByteArray): Float {
        if (digest.isEmpty()) return 0f
        
        // Count unique bytes
        val uniqueBytes = digest.distinct().size
        
        // Calculate byte frequency entropy
        val frequencies = digest.groupingBy { it }.eachCount()
        val entropy = frequencies.values.sumOf { count ->
            val p = count.toDouble() / digest.size
            -p * kotlin.math.ln(p)
        }
        
        // Normalize to 0-1 scale
        val maxEntropy = kotlin.math.ln(256.0)
        return (entropy / maxEntropy).toFloat()
    }
    
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}

// ============== 7. Behavioral Anomaly Detection ==============

object BehavioralAnalyzer {
    
    data class TimingProfile(
        val factorTimings: Map<Factor, Long>,
        val totalDuration: Long,
        val timestamp: Long
    )
    
    /**
     * Analyze factor timing for anomalies
     * Detects unusual patterns that might indicate fraud
     */
    fun analyzeFactorTiming(
        factorTimings: Map<Factor, Long>,
        historicalData: List<Map<Factor, Long>>
    ): Float {
        if (historicalData.isEmpty()) return 0f
        
        // Calculate average timings from history
        val avgTimings = mutableMapOf<Factor, Long>()
        
        for (factor in factorTimings.keys) {
            val historicalTimes = historicalData.mapNotNull { it[factor] }
            if (historicalTimes.isNotEmpty()) {
                avgTimings[factor] = historicalTimes.average().toLong()
            }
        }
        
        if (avgTimings.isEmpty()) return 0f
        
        // Calculate deviation score
        val deviations = factorTimings.mapNotNull { (factor, time) ->
            avgTimings[factor]?.let { avgTime ->
                if (avgTime > 0) {
                    abs(time - avgTime).toFloat() / avgTime
                } else null
            }
        }
        
        return if (deviations.isNotEmpty()) {
            deviations.average().toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Detect suspicious patterns
     * Returns anomaly score (0.0 = normal, 1.0 = highly suspicious)
     */
    fun detectAnomalies(
        currentProfile: TimingProfile,
        historicalProfiles: List<TimingProfile>
    ): Float {
        if (historicalProfiles.isEmpty()) return 0f
        
        var anomalyScore = 0f
        
        // Check 1: Too fast (automation)
        val avgDuration = historicalProfiles.map { it.totalDuration }.average()
        if (currentProfile.totalDuration < avgDuration * 0.3) {
            anomalyScore += 0.5f
        }
        
        // Check 2: Too consistent timing (robotic)
        val timingVariance = calculateTimingVariance(currentProfile, historicalProfiles)
        if (timingVariance < 0.05f) {
            anomalyScore += 0.3f
        }
        
        // Check 3: Unusual factor order
        // TODO: Implement factor order analysis
        
        return anomalyScore.coerceIn(0f, 1f)
    }
    
    private fun calculateTimingVariance(
        current: TimingProfile,
        historical: List<TimingProfile>
    ): Float {
        val allDurations = historical.map { it.totalDuration } + current.totalDuration
        if (allDurations.size < 2) return 0f
        
        val mean = allDurations.average()
        val variance = allDurations.map { (it - mean) * (it - mean) }.average()
        
        return (variance / (mean * mean)).toFloat()
    }
}

// ============== 8. Proof Serialization ==============

object ProofSerializer {
    
    /**
     * Serialize factor proofs for network transmission
     * Optimized for 80-100kb circuit constraint
     */
    fun serializeForNetwork(
        factorProofs: Map<Factor, ByteArray>,
        metadata: Map<String, Any>
    ): ByteArray {
        return ByteArrayOutputStream().apply {
            // Version byte for forward compatibility
            write(0x01)
            
            // Number of factors (varint encoded)
            writeVarInt(factorProofs.size)
            
            // Each factor proof
            factorProofs.forEach { (factor, proof) ->
                write(factor.ordinal)
                write(proof.size)
                write(proof)
            }
            
            // Metadata (compressed JSON)
            val metadataBytes = serializeMetadata(metadata)
            writeVarInt(metadataBytes.size)
            write(metadataBytes)
        }.toByteArray()
    }
    
    /**
     * Deserialize network proof back to map
     */
    fun deserializeFromNetwork(data: ByteArray): Pair<Map<Factor, ByteArray>, Map<String, Any>> {
        val stream = data.inputStream()
        
        // Read version
        val version = stream.read()
        require(version == 0x01) { "Unsupported version: $version" }
        
        // Read number of factors
        val factorCount = readVarInt(stream)
        
        // Read factor proofs
        val factorProofs = mutableMapOf<Factor, ByteArray>()
        repeat(factorCount) {
            val factorOrdinal = stream.read()
            val factor = Factor.values()[factorOrdinal]
            
            val proofSize = stream.read()
            val proof = ByteArray(proofSize)
            stream.read(proof)
            
            factorProofs[factor] = proof
        }
        
        // Read metadata
        val metadataSize = readVarInt(stream)
        val metadataBytes = ByteArray(metadataSize)
        stream.read(metadataBytes)
        val metadata = deserializeMetadata(metadataBytes)
        
        return factorProofs to metadata
    }
    
    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var v = value
        while (v >= 0x80) {
            write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        write(v and 0x7F)
    }
    
    private fun readVarInt(stream: java.io.InputStream): Int {
        var result = 0
        var shift = 0
        
        while (true) {
            val byte = stream.read()
            result = result or ((byte and 0x7F) shl shift)
            
            if ((byte and 0x80) == 0) break
            shift += 7
        }
        
        return result
    }
    
    private fun serializeMetadata(metadata: Map<String, Any>): ByteArray {
        // Simple key-value serialization
        // In production, use proper serialization (Protocol Buffers, etc.)
        return metadata.entries.joinToString("|") { (k, v) ->
            "$k:$v"
        }.toByteArray()
    }
    
    private fun deserializeMetadata(data: ByteArray): Map<String, Any> {
        val str = String(data)
        return str.split("|").associate { pair ->
            val (key, value) = pair.split(":")
            key to value
        }
    }
}
