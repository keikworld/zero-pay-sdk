/**
 * ZeroPay Encryption Module
 * 
 * AES-256-GCM encryption for data at rest in Redis
 * 
 * Security Features:
 * - AES-256-GCM (Galois/Counter Mode)
 * - Authenticated encryption (prevents tampering)
 * - Random IV (Initialization Vector) per encryption
 * - 96-bit IV (GCM standard)
 * - 128-bit authentication tag
 * - Constant-time operations where possible
 * 
 * Format:
 * Ciphertext = IV (12 bytes) + Auth Tag (16 bytes) + Encrypted Data
 * All encoded as hexadecimal string
 * 
 * Usage:
 *   const { encrypt, decrypt } = require('./crypto/encryption');
 *   const encrypted = await encrypt(plaintext);
 *   const decrypted = await decrypt(encrypted);
 * 
 * @version 1.0.0
 * @date 2025-10-11
 */

const crypto = require('crypto');

// ============================================================================
// CONSTANTS
// ============================================================================

const ALGORITHM = 'aes-256-gcm';
const KEY_LENGTH = 32;      // 256 bits
const IV_LENGTH = 12;       // 96 bits (GCM standard)
const AUTH_TAG_LENGTH = 16; // 128 bits

// ============================================================================
// KEY MANAGEMENT
// ============================================================================

/**
 * Get encryption key from environment variable
 * 
 * ⚠️ PRODUCTION WARNING:
 * In production, load keys from HashiCorp Vault, AWS KMS, or similar
 * Environment variables are acceptable for development only
 * 
 * @returns {Buffer} 32-byte encryption key
 * @throws {Error} If key is invalid or missing
 */
function getEncryptionKey() {
  const key = process.env.ENCRYPTION_KEY;
  
  if (!key) {
    throw new Error('ENCRYPTION_KEY not set in environment variables');
  }
  
  if (key.length !== KEY_LENGTH * 2) {
    throw new Error(
      `Invalid ENCRYPTION_KEY: must be ${KEY_LENGTH * 2} hex characters (${KEY_LENGTH} bytes). ` +
      `Got: ${key.length} characters`
    );
  }
  
  // Validate hex format
  if (!/^[0-9a-f]{64}$/i.test(key)) {
    throw new Error('ENCRYPTION_KEY must be hexadecimal (0-9, a-f)');
  }
  
  return Buffer.from(key, 'hex');
}

// ============================================================================
// ENCRYPTION
// ============================================================================

/**
 * Encrypt plaintext using AES-256-GCM
 * 
 * Process:
 * 1. Generate random 96-bit IV
 * 2. Create cipher with key and IV
 * 3. Encrypt plaintext
 * 4. Get authentication tag
 * 5. Concatenate: IV + tag + ciphertext
 * 6. Encode as hexadecimal
 * 
 * @param {string} plaintext - Data to encrypt
 * @returns {Promise<string>} Hex-encoded encrypted data
 * @throws {Error} If encryption fails
 * 
 * @example
 * const encrypted = await encrypt('{"user":"test","data":"sensitive"}');
 * // Returns: "a1b2c3...def" (hex string)
 */
async function encrypt(plaintext) {
  if (!plaintext) {
    throw new Error('Plaintext required for encryption');
  }
  
  if (typeof plaintext !== 'string') {
    throw new Error('Plaintext must be a string');
  }
  
  try {
    // Get encryption key
    const key = getEncryptionKey();
    
    // Generate random IV (never reuse IVs!)
    const iv = crypto.randomBytes(IV_LENGTH);
    
    // Create cipher
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv);
    
    // Encrypt data
    let encrypted = cipher.update(plaintext, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    
    // Get authentication tag (proves data hasn't been tampered with)
    const authTag = cipher.getAuthTag();
    
    // Concatenate: IV + authTag + ciphertext (all as hex)
    const result = iv.toString('hex') + authTag.toString('hex') + encrypted;
    
    // Zero sensitive data (defense in depth)
    key.fill(0);
    iv.fill(0);
    
    return result;
    
  } catch (error) {
    throw new Error(`Encryption failed: ${error.message}`);
  }
}

// ============================================================================
// DECRYPTION
// ============================================================================

/**
 * Decrypt ciphertext using AES-256-GCM
 * 
 * Process:
 * 1. Extract IV (first 24 hex chars = 12 bytes)
 * 2. Extract auth tag (next 32 hex chars = 16 bytes)
 * 3. Extract ciphertext (remaining)
 * 4. Create decipher with key and IV
 * 5. Set auth tag for verification
 * 6. Decrypt and verify
 * 
 * @param {string} ciphertext - Hex-encoded encrypted data
 * @returns {Promise<string>} Decrypted plaintext
 * @throws {Error} If decryption fails or authentication fails
 * 
 * @example
 * const decrypted = await decrypt('a1b2c3...def');
 * // Returns: '{"user":"test","data":"sensitive"}'
 */
async function decrypt(ciphertext) {
  if (!ciphertext) {
    throw new Error('Ciphertext required for decryption');
  }
  
  if (typeof ciphertext !== 'string') {
    throw new Error('Ciphertext must be a string');
  }
  
  // Minimum length check (IV + tag + at least 1 byte of data)
  const minLength = (IV_LENGTH + AUTH_TAG_LENGTH + 1) * 2;
  if (ciphertext.length < minLength) {
    throw new Error(`Ciphertext too short: expected at least ${minLength} chars, got ${ciphertext.length}`);
  }
  
  try {
    // Get encryption key
    const key = getEncryptionKey();
    
    // Extract components from concatenated hex string
    const ivHex = ciphertext.slice(0, IV_LENGTH * 2);
    const authTagHex = ciphertext.slice(IV_LENGTH * 2, (IV_LENGTH + AUTH_TAG_LENGTH) * 2);
    const encryptedHex = ciphertext.slice((IV_LENGTH + AUTH_TAG_LENGTH) * 2);
    
    // Convert from hex to buffers
    const iv = Buffer.from(ivHex, 'hex');
    const authTag = Buffer.from(authTagHex, 'hex');
    
    // Validate extracted components
    if (iv.length !== IV_LENGTH) {
      throw new Error(`Invalid IV length: expected ${IV_LENGTH}, got ${iv.length}`);
    }
    if (authTag.length !== AUTH_TAG_LENGTH) {
      throw new Error(`Invalid auth tag length: expected ${AUTH_TAG_LENGTH}, got ${authTag.length}`);
    }
    
    // Create decipher
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv);
    
    // Set authentication tag (this will be verified during decryption)
    decipher.setAuthTag(authTag);
    
    // Decrypt data
    let decrypted = decipher.update(encryptedHex, 'hex', 'utf8');
    decrypted += decipher.final('utf8');
    
    // Zero sensitive data
    key.fill(0);
    iv.fill(0);
    authTag.fill(0);
    
    return decrypted;
    
  } catch (error) {
    // Authentication failure means data was tampered with
    if (error.message.includes('Unsupported state or unable to authenticate data')) {
      throw new Error('Decryption failed: Authentication failed (data may have been tampered with)');
    }
    throw new Error(`Decryption failed: ${error.message}`);
  }
}

// ============================================================================
// TESTING
// ============================================================================

/**
 * Test encryption/decryption functionality
 * 
 * @returns {Promise<boolean>} True if all tests pass
 */
async function test() {
  console.log('🔐 Testing Encryption Module...');
  console.log('================================');
  
  try {
    // Test 1: Basic encryption/decryption
    console.log('\nTest 1: Basic encryption/decryption');
    const plaintext1 = 'Hello, World!';
    const encrypted1 = await encrypt(plaintext1);
    const decrypted1 = await decrypt(encrypted1);
    
    console.log('  Plaintext:  ', plaintext1);
    console.log('  Encrypted:  ', encrypted1.slice(0, 64) + '...');
    console.log('  Decrypted:  ', decrypted1);
    console.log('  Match:', plaintext1 === decrypted1 ? '✅' : '❌');
    
    if (plaintext1 !== decrypted1) {
      throw new Error('Decrypted text does not match original');
    }
    
    // Test 2: JSON data
    console.log('\nTest 2: JSON data encryption');
    const jsonData = JSON.stringify({
      user_uuid: 'test-uuid-123',
      factors: {
        PIN: 'abcd1234',
        PATTERN: 'xyz789'
      },
      timestamp: Date.now()
    });
    const encrypted2 = await encrypt(jsonData);
    const decrypted2 = await decrypt(encrypted2);
    
    console.log('  Original JSON:', jsonData.slice(0, 50) + '...');
    console.log('  Decrypted JSON:', decrypted2.slice(0, 50) + '...');
    console.log('  Match:', jsonData === decrypted2 ? '✅' : '❌');
    
    if (jsonData !== decrypted2) {
      throw new Error('Decrypted JSON does not match original');
    }
    
    // Test 3: Different IVs for same plaintext
    console.log('\nTest 3: IV uniqueness (same plaintext → different ciphertext)');
    const plaintext3 = 'Same text';
    const encrypted3a = await encrypt(plaintext3);
    const encrypted3b = await encrypt(plaintext3);
    
    console.log('  Encryption 1:', encrypted3a.slice(0, 32) + '...');
    console.log('  Encryption 2:', encrypted3b.slice(0, 32) + '...');
    console.log('  Different:', encrypted3a !== encrypted3b ? '✅' : '❌');
    
    if (encrypted3a === encrypted3b) {
      throw new Error('IV is not random - security vulnerability!');
    }
    
    // Test 4: Tampering detection
    console.log('\nTest 4: Tampering detection');
    const plaintext4 = 'Original message';
    const encrypted4 = await encrypt(plaintext4);
    const tampered = encrypted4.slice(0, -2) + 'FF'; // Change last byte
    
    try {
      await decrypt(tampered);
      console.log('  Tampering detected: ❌ (should have failed)');
      throw new Error('Tampering was not detected - security vulnerability!');
    } catch (error) {
      if (error.message.includes('Authentication failed')) {
        console.log('  Tampering detected: ✅');
      } else {
        throw error;
      }
    }
    
    // Test 5: Performance
    console.log('\nTest 5: Performance test (1000 operations)');
    const iterations = 1000;
    const testData = JSON.stringify({ test: 'data'.repeat(100) });
    
    console.time('  1000 encrypt operations');
    for (let i = 0; i < iterations; i++) {
      await encrypt(testData);
    }
    console.timeEnd('  1000 encrypt operations');
    
    console.time('  1000 decrypt operations');
    const encryptedTest = await encrypt(testData);
    for (let i = 0; i < iterations; i++) {
      await decrypt(encryptedTest);
    }
    console.timeEnd('  1000 decrypt operations');
    
    console.log('\n================================');
    console.log('✅ All tests passed!');
    console.log('================================\n');
    
    return true;
    
  } catch (error) {
    console.error('\n❌ Test failed:', error.message);
    console.error('================================\n');
    return false;
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  encrypt,
  decrypt,
  test
};

// Run tests if executed directly
if (require.main === module) {
  test().then(success => {
    process.exit(success ? 0 : 1);
  });
}
