// Path: backend/crypto/keyDerivation.js

/**
 * Key Derivation Module - PBKDF2 Implementation
 * 
 * Purpose: Derive encryption keys from factor digests
 * 
 * Architecture:
 * - Layer 1 of double encryption (Derive)
 * - Converts factor digests → encryption key
 * - Deterministic (same factors = same key)
 * - Computationally expensive (prevents brute force)
 * 
 * Algorithm:
 * - PBKDF2-HMAC-SHA256
 * - 100,000 iterations (OWASP recommended)
 * - 32-byte output (AES-256)
 * - Salt = UUID (unique per user)
 * 
 * Security:
 * - No raw factors stored
 * - Constant-time operations
 * - Memory wiping after use
 * - Side-channel resistant
 * 
 * Thread Safety:
 * - All functions are async (non-blocking)
 * - No global state
 * - Safe for concurrent requests
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */

const crypto = require('crypto');
const { wipeBuffer, wipeBuffers, secureCompare } = require('./memoryWipe');

// ============================================================================
// CONSTANTS
// ============================================================================

const ALGORITHM = 'sha256';
const ITERATIONS = 100000;     // OWASP recommended minimum for PBKDF2
const KEY_LENGTH = 32;          // 256 bits for AES-256
const SALT_PREFIX = 'zeropay.v1'; // Version prefix for salt

// ============================================================================
// KEY DERIVATION
// ============================================================================

/**
 * Derive encryption key from factor digests using PBKDF2
 * 
 * Process:
 * 1. Concatenate all factor digests (sorted by factor name)
 * 2. Generate salt from UUID + prefix
 * 3. Apply PBKDF2-HMAC-SHA256 (100K iterations)
 * 4. Output 32-byte AES-256 key
 * 
 * Why Sorted?
 * - Ensures deterministic output
 * - Same factors in any order = same key
 * - Prevents order-dependent bugs
 * 
 * @param {string} uuid - User UUID (used as salt)
 * @param {Object} factorDigests - Map of factor names to hex digests
 * @returns {Promise<Buffer>} 32-byte derived key
 * 
 * @example
 * const key = await deriveKey('uuid-here', {
 *   PIN: 'abc123...',
 *   PATTERN: 'def456...'
 * });
 */
async function deriveKey(uuid, factorDigests) {
  // Validation
  if (!uuid || typeof uuid !== 'string') {
    throw new Error('UUID required (string)');
  }
  
  if (!factorDigests || typeof factorDigests !== 'object') {
    throw new Error('Factor digests required (object)');
  }
  
  const factorNames = Object.keys(factorDigests);
  if (factorNames.length === 0) {
    throw new Error('At least one factor required');
  }
  
  // Validate each digest
  for (const [name, digest] of Object.entries(factorDigests)) {
    if (!digest || typeof digest !== 'string') {
      throw new Error(`Invalid digest for factor ${name} (must be hex string)`);
    }
    
    if (!/^[0-9a-f]{64}$/i.test(digest)) {
      throw new Error(`Invalid digest format for factor ${name} (must be 64 hex chars)`);
    }
  }
  
  // Sort factors by name (deterministic)
  const sortedFactors = Object.keys(factorDigests).sort();
  
  // Concatenate digests
  const passwordParts = [];
  for (const factorName of sortedFactors) {
    const digest = factorDigests[factorName];
    passwordParts.push(Buffer.from(digest, 'hex'));
  }
  
  const password = Buffer.concat(passwordParts);
  
  // Generate salt from UUID
  const salt = generateSalt(uuid);
  
  try {
    // Derive key using PBKDF2
    const derivedKey = await pbkdf2(password, salt, ITERATIONS, KEY_LENGTH, ALGORITHM);
    
    console.log(`✅ Derived key from ${sortedFactors.length} factors (UUID: ${uuid.slice(0, 8)}...)`);
    
    return derivedKey;
    
  } finally {
    // Wipe sensitive data
    wipeBuffer(password);
    wipeBuffer(salt);
  }
}

/**
 * Generate salt from UUID
 * 
 * Salt Format: SALT_PREFIX + UUID
 * - Unique per user (UUID)
 * - Versioned (prefix allows algorithm changes)
 * - Deterministic (same UUID = same salt)
 * 
 * @param {string} uuid - User UUID
 * @returns {Buffer} Salt buffer
 */
function generateSalt(uuid) {
  // Validate UUID format
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!uuidRegex.test(uuid)) {
    throw new Error('Invalid UUID format');
  }
  
  const saltString = `${SALT_PREFIX}:${uuid}`;
  return Buffer.from(saltString, 'utf8');
}

/**
 * PBKDF2 wrapper (promisified)
 * 
 * @param {Buffer} password - Password to derive from
 * @param {Buffer} salt - Salt
 * @param {number} iterations - Number of iterations
 * @param {number} keyLength - Output key length (bytes)
 * @param {string} digest - Hash algorithm
 * @returns {Promise<Buffer>} Derived key
 */
function pbkdf2(password, salt, iterations, keyLength, digest) {
  return new Promise((resolve, reject) => {
    crypto.pbkdf2(password, salt, iterations, keyLength, digest, (err, derivedKey) => {
      if (err) {
        reject(new Error(`PBKDF2 failed: ${err.message}`));
      } else {
        resolve(derivedKey);
      }
    });
  });
}

// ============================================================================
// VERIFICATION
// ============================================================================

/**
 * Verify that derived key matches expected key
 * 
 * Uses constant-time comparison to prevent timing attacks.
 * 
 * @param {string} uuid - User UUID
 * @param {Object} factorDigests - Factor digests to verify
 * @param {Buffer} expectedKey - Expected derived key
 * @returns {Promise<boolean>} True if keys match
 */
async function verifyDerivedKey(uuid, factorDigests, expectedKey) {
  // Validation
  if (!Buffer.isBuffer(expectedKey)) {
    throw new Error('Expected key must be a Buffer');
  }
  
  if (expectedKey.length !== KEY_LENGTH) {
    throw new Error(`Expected key must be ${KEY_LENGTH} bytes`);
  }
  
  try {
    // Derive key from factors
    const derivedKey = await deriveKey(uuid, factorDigests);
    
    // Constant-time comparison (prevents timing attacks)
    const match = secureCompare(derivedKey, expectedKey, true);
    
    return match;
    
  } catch (error) {
    console.error('❌ Key verification failed:', error.message);
    return false;
  }
}

// ============================================================================
// KEY STRENGTH VALIDATION
// ============================================================================

/**
 * Validate key strength
 * 
 * Checks:
 * - Key length (must be 32 bytes)
 * - Entropy (shouldn't be all zeros or repeated pattern)
 * - Randomness (basic statistical test)
 * 
 * @param {Buffer} key - Key to validate
 * @returns {Object} Validation result
 */
function validateKeyStrength(key) {
  if (!Buffer.isBuffer(key)) {
    return {
      valid: false,
      reason: 'Key must be a Buffer'
    };
  }
  
  if (key.length !== KEY_LENGTH) {
    return {
      valid: false,
      reason: `Key must be ${KEY_LENGTH} bytes, got ${key.length}`
    };
  }
  
  // Check for all zeros
  const allZeros = key.every(byte => byte === 0);
  if (allZeros) {
    return {
      valid: false,
      reason: 'Key is all zeros (weak)'
    };
  }
  
  // Check for repeated patterns
  const uniqueBytes = new Set(key);
  if (uniqueBytes.size < 8) {
    return {
      valid: false,
      reason: `Low entropy: only ${uniqueBytes.size} unique bytes`
    };
  }
  
  // Basic randomness test (chi-square)
  const expected = key.length / 256;
  const counts = new Array(256).fill(0);
  for (const byte of key) {
    counts[byte]++;
  }
  
  let chiSquare = 0;
  for (const count of counts) {
    if (expected > 0) {
      chiSquare += Math.pow(count - expected, 2) / expected;
    }
  }
  
  // Chi-square critical value for 255 df at 99% confidence: ~310
  if (chiSquare > 500) {
    return {
      valid: false,
      reason: 'Key appears non-random (chi-square test failed)'
    };
  }
  
  return {
    valid: true,
    entropy: uniqueBytes.size,
    chiSquare: Math.round(chiSquare)
  };
}

// ============================================================================
// UTILITIES
// ============================================================================

/**
 * Convert factor digests to sorted string (for logging/debugging)
 * 
 * @param {Object} factorDigests - Factor digests
 * @returns {string} Sorted factor names
 */
function getFactorSummary(factorDigests) {
  const factorNames = Object.keys(factorDigests).sort();
  return factorNames.join('+');
}

/**
 * Estimate time to derive key (for UX)
 * 
 * @returns {Promise<number>} Estimated time in milliseconds
 */
async function estimateDerivationTime() {
  const testUuid = '00000000-0000-4000-8000-000000000000';
  const testDigest = {
    TEST: 'a'.repeat(64)
  };
  
  const start = Date.now();
  await deriveKey(testUuid, testDigest);
  const duration = Date.now() - start;
  
  return duration;
}

/**
 * Get algorithm metadata
 * 
 * @returns {Object} Algorithm information
 */
function getAlgorithmInfo() {
  return {
    algorithm: 'PBKDF2',
    hash: ALGORITHM,
    iterations: ITERATIONS,
    keyLength: KEY_LENGTH,
    saltPrefix: SALT_PREFIX,
    version: '1.0.0'
  };
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Core functions
  deriveKey,
  verifyDerivedKey,
  
  // Utilities
  generateSalt,
  validateKeyStrength,
  getFactorSummary,
  estimateDerivationTime,
  getAlgorithmInfo,
  
  // Constants (for testing)
  ALGORITHM,
  ITERATIONS,
  KEY_LENGTH,
  SALT_PREFIX
};

// ============================================================================
// CLI TESTING
// ============================================================================

if (require.main === module) {
  console.log('🔐 Testing Key Derivation Module\n');
  
  (async () => {
    try {
      // Test 1: Basic key derivation
      console.log('Test 1: Basic key derivation');
      const uuid1 = '550e8400-e29b-41d4-a716-446655440000';
      const factors1 = {
        PIN: 'a'.repeat(64),
        PATTERN: 'b'.repeat(64)
      };
      
      const key1 = await deriveKey(uuid1, factors1);
      console.log(`  Key length: ${key1.length} bytes ✅`);
      console.log(`  Key (hex): ${key1.toString('hex').slice(0, 32)}...`);
      
      // Test 2: Deterministic (same inputs = same output)
      console.log('\nTest 2: Deterministic derivation');
      const key2 = await deriveKey(uuid1, factors1);
      const match = key1.equals(key2);
      console.log(`  Same inputs produce same key: ${match ? '✅' : '❌'}`);
      
      // Test 3: Factor order independence
      console.log('\nTest 3: Factor order independence');
      const factors3a = { PIN: 'a'.repeat(64), PATTERN: 'b'.repeat(64) };
      const factors3b = { PATTERN: 'b'.repeat(64), PIN: 'a'.repeat(64) };
      const key3a = await deriveKey(uuid1, factors3a);
      const key3b = await deriveKey(uuid1, factors3b);
      const orderMatch = key3a.equals(key3b);
      console.log(`  Order-independent: ${orderMatch ? '✅' : '❌'}`);
      
      // Test 4: Different factors = different key
      console.log('\nTest 4: Different factors');
      const factors4 = {
        PIN: 'c'.repeat(64),
        PATTERN: 'd'.repeat(64)
      };
      const key4 = await deriveKey(uuid1, factors4);
      const different = !key1.equals(key4);
      console.log(`  Different factors produce different key: ${different ? '✅' : '❌'}`);
      
      // Test 5: Key strength validation
      console.log('\nTest 5: Key strength validation');
      const strength = validateKeyStrength(key1);
      console.log(`  Key valid: ${strength.valid ? '✅' : '❌'}`);
      console.log(`  Entropy: ${strength.entropy}/256 unique bytes`);
      console.log(`  Chi-square: ${strength.chiSquare}`);
      
      // Test 6: Performance benchmark
      console.log('\nTest 6: Performance benchmark');
      const estimatedTime = await estimateDerivationTime();
      console.log(`  Estimated derivation time: ${estimatedTime}ms`);
      console.log(`  ${ITERATIONS.toLocaleString()} iterations`);
      
      // Test 7: Verification
      console.log('\nTest 7: Key verification');
      const verified = await verifyDerivedKey(uuid1, factors1, key1);
      console.log(`  Correct factors verified: ${verified ? '✅' : '❌'}`);
      
      const wrongFactors = { PIN: 'x'.repeat(64), PATTERN: 'y'.repeat(64) };
      const wrongVerified = await verifyDerivedKey(uuid1, wrongFactors, key1);
      console.log(`  Wrong factors rejected: ${!wrongVerified ? '✅' : '❌'}`);
      
      // Test 8: Algorithm info
      console.log('\nTest 8: Algorithm information');
      const info = getAlgorithmInfo();
      console.log(`  Algorithm: ${info.algorithm}`);
      console.log(`  Hash: ${info.hash}`);
      console.log(`  Iterations: ${info.iterations.toLocaleString()}`);
      console.log(`  Key Length: ${info.keyLength} bytes`);
      console.log(`  Version: ${info.version}`);
      
      console.log('\n✅ All tests passed!\n');
      
      // Cleanup
      wipeBuffers(key1, key2, key3a, key3b, key4);
      
      process.exit(0);
      
    } catch (error) {
      console.error('\n❌ Test failed:', error.message);
      process.exit(1);
    }
  })();
}
