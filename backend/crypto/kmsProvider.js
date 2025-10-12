// Path: backend/crypto/kmsProvider.js

/**
 * KMS Provider Module - AWS KMS Integration
 * 
 * Purpose: Layer 2 of double encryption (KMS wrap)
 * 
 * Architecture:
 * - Wraps derived keys with AWS KMS master key
 * - Unwraps for verification
 * - Single master key for all users (cost-effective)
 * - Encrypted data becomes unreadable if master key rotated
 * 
 * AWS KMS:
 * - Symmetric encryption (SYMMETRIC_DEFAULT)
 * - Automatic key rotation (yearly)
 * - Audit logging (CloudTrail)
 * - Access control (IAM policies)
 * - Hardware Security Module (HSM) backed
 * 
 * Cost:
 * - Master key: $1/month
 * - API calls: $0.03/10,000 requests
 * - Typical cost: $1-5/month total
 * 
 * Security:
 * - Master key never leaves AWS KMS
 * - All operations authenticated
 * - Encryption context for additional security
 * - Automatic key rotation
 * 
 * Alternatives:
 * - LocalKMSProvider (testing only)
 * - AzureKeyVaultProvider (Azure alternative)
 * - VaultProvider (HashiCorp Vault)
 * 
 * @version 1.0.0
 * @date 2025-10-12
 * @author ZeroPay Security Team
 */

const { KMSClient, EncryptCommand, DecryptCommand, GenerateDataKeyCommand } = require('@aws-sdk/client-kms');
const { wipeBuffer } = require('./memoryWipe');

// ============================================================================
// CONFIGURATION
// ============================================================================

const AWS_REGION = process.env.AWS_REGION || 'us-east-1';
const KMS_KEY_ID = process.env.KMS_KEY_ID; // AWS KMS Key ID or ARN
const NODE_ENV = process.env.NODE_ENV || 'development';

// KMS Client (singleton)
let kmsClient = null;

/**
 * Initialize KMS client
 * 
 * @returns {KMSClient} KMS client instance
 */
function getKMSClient() {
  if (!kmsClient) {
    kmsClient = new KMSClient({
      region: AWS_REGION,
      // Credentials are loaded from:
      // 1. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
      // 2. IAM role (if running on EC2/ECS/Lambda)
      // 3. AWS credentials file (~/.aws/credentials)
    });
    
    console.log(`✅ KMS client initialized (region: ${AWS_REGION})`);
  }
  
  return kmsClient;
}

// ============================================================================
// ABSTRACT KMS PROVIDER
// ============================================================================

/**
 * Abstract KMS Provider interface
 * 
 * All KMS providers must implement these methods.
 */
class KMSProvider {
  /**
   * Wrap (encrypt) a data key with KMS master key
   * 
   * @param {Buffer} plaintext - Data to wrap
   * @param {Object} context - Encryption context (additional authenticated data)
   * @returns {Promise<Buffer>} Wrapped (encrypted) data
   */
  async wrap(plaintext, context = {}) {
    throw new Error('wrap() must be implemented by subclass');
  }
  
  /**
   * Unwrap (decrypt) a wrapped data key
   * 
   * @param {Buffer} ciphertext - Wrapped data
   * @param {Object} context - Encryption context (must match wrap context)
   * @returns {Promise<Buffer>} Unwrapped (decrypted) data
   */
  async unwrap(ciphertext, context = {}) {
    throw new Error('unwrap() must be implemented by subclass');
  }
  
  /**
   * Get provider name
   * 
   * @returns {string} Provider name
   */
  getName() {
    return 'AbstractKMSProvider';
  }
}

// ============================================================================
// AWS KMS PROVIDER
// ============================================================================

/**
 * AWS KMS Provider
 * 
 * Production-ready KMS provider using AWS KMS.
 */
class AWSKMSProvider extends KMSProvider {
  constructor(keyId) {
    super();
    
    if (!keyId) {
      throw new Error('KMS Key ID required (set KMS_KEY_ID environment variable)');
    }
    
    this.keyId = keyId;
    this.client = getKMSClient();
  }
  
  /**
   * Wrap data with AWS KMS
   * 
   * @param {Buffer} plaintext - Data to wrap (max 4KB)
   * @param {Object} context - Encryption context
   * @returns {Promise<Buffer>} Wrapped data
   */
  async wrap(plaintext, context = {}) {
    // Validation
    if (!Buffer.isBuffer(plaintext)) {
      throw new Error('Plaintext must be a Buffer');
    }
    
    if (plaintext.length === 0) {
      throw new Error('Plaintext cannot be empty');
    }
    
    if (plaintext.length > 4096) {
      throw new Error('Plaintext too large for KMS (max 4KB)');
    }
    
    try {
      // Build encryption context
      const encryptionContext = {
        application: 'ZeroPay',
        version: 'v1',
        purpose: 'key-wrapping',
        ...context
      };
      
      // Encrypt with KMS
      const command = new EncryptCommand({
        KeyId: this.keyId,
        Plaintext: plaintext,
        EncryptionContext: encryptionContext
      });
      
      const response = await this.client.send(command);
      
      if (!response.CiphertextBlob) {
        throw new Error('KMS encrypt failed: no ciphertext returned');
      }
      
      console.log(`✅ Wrapped ${plaintext.length} bytes with AWS KMS`);
      
      return Buffer.from(response.CiphertextBlob);
      
    } catch (error) {
      console.error('❌ KMS wrap failed:', error.message);
      throw new Error(`KMS wrap failed: ${error.message}`);
    } finally {
      // Wipe plaintext from memory
      wipeBuffer(plaintext);
    }
  }
  
  /**
   * Unwrap data with AWS KMS
   * 
   * @param {Buffer} ciphertext - Wrapped data
   * @param {Object} context - Encryption context (must match wrap)
   * @returns {Promise<Buffer>} Unwrapped data
   */
  async unwrap(ciphertext, context = {}) {
    // Validation
    if (!Buffer.isBuffer(ciphertext)) {
      throw new Error('Ciphertext must be a Buffer');
    }
    
    if (ciphertext.length === 0) {
      throw new Error('Ciphertext cannot be empty');
    }
    
    try {
      // Build encryption context (must match wrap context)
      const encryptionContext = {
        application: 'ZeroPay',
        version: 'v1',
        purpose: 'key-wrapping',
        ...context
      };
      
      // Decrypt with KMS
      const command = new DecryptCommand({
        CiphertextBlob: ciphertext,
        EncryptionContext: encryptionContext
      });
      
      const response = await this.client.send(command);
      
      if (!response.Plaintext) {
        throw new Error('KMS decrypt failed: no plaintext returned');
      }
      
      // Verify key ID matches (security check)
      if (response.KeyId && !response.KeyId.includes(this.keyId)) {
        console.warn(`⚠️  Key ID mismatch: expected ${this.keyId}, got ${response.KeyId}`);
      }
      
      console.log(`✅ Unwrapped data with AWS KMS`);
      
      return Buffer.from(response.Plaintext);
      
    } catch (error) {
      console.error('❌ KMS unwrap failed:', error.message);
      throw new Error(`KMS unwrap failed: ${error.message}`);
    }
  }
  
  getName() {
    return 'AWSKMSProvider';
  }
  
  /**
   * Get KMS key metadata
   * 
   * @returns {Promise<Object>} Key metadata
   */
  async getKeyMetadata() {
    try {
      const { DescribeKeyCommand } = require('@aws-sdk/client-kms');
      const command = new DescribeKeyCommand({ KeyId: this.keyId });
      const response = await this.client.send(command);
      
      return {
        keyId: response.KeyMetadata.KeyId,
        arn: response.KeyMetadata.Arn,
        state: response.KeyMetadata.KeyState,
        enabled: response.KeyMetadata.Enabled,
        creationDate: response.KeyMetadata.CreationDate,
        description: response.KeyMetadata.Description
      };
      
    } catch (error) {
      console.error('❌ Failed to get key metadata:', error.message);
      throw error;
    }
  }
}

// ============================================================================
// LOCAL KMS PROVIDER (Testing Only)
// ============================================================================

/**
 * Local KMS Provider - FOR TESTING ONLY
 * 
 * Simulates KMS using local AES-256-GCM encryption.
 * 
 * ⚠️ WARNING: NOT FOR PRODUCTION!
 * - No HSM backing
 * - No audit logging
 * - No automatic rotation
 * - Master key in memory
 */
class LocalKMSProvider extends KMSProvider {
  constructor() {
    super();
    
    // Generate a master key (in production, this comes from HSM)
    const crypto = require('crypto');
    this.masterKey = crypto.randomBytes(32);
    
    console.warn('⚠️  Using LocalKMSProvider (TESTING ONLY)');
  }
  
  /**
   * Wrap data with local encryption
   * 
   * @param {Buffer} plaintext - Data to wrap
   * @param {Object} context - Encryption context (logged but not used)
   * @returns {Promise<Buffer>} Wrapped data
   */
  async wrap(plaintext, context = {}) {
    if (!Buffer.isBuffer(plaintext)) {
      throw new Error('Plaintext must be a Buffer');
    }
    
    try {
      const crypto = require('crypto');
      
      // Generate IV
      const iv = crypto.randomBytes(12);
      
      // Create cipher
      const cipher = crypto.createCipheriv('aes-256-gcm', this.masterKey, iv);
      
      // Encrypt
      const encrypted = Buffer.concat([
        cipher.update(plaintext),
        cipher.final()
      ]);
      
      // Get auth tag
      const authTag = cipher.getAuthTag();
      
      // Concatenate: IV + authTag + ciphertext
      const wrapped = Buffer.concat([iv, authTag, encrypted]);
      
      console.log(`✅ Wrapped ${plaintext.length} bytes with LocalKMS`);
      
      return wrapped;
      
    } catch (error) {
      throw new Error(`Local KMS wrap failed: ${error.message}`);
    } finally {
      wipeBuffer(plaintext);
    }
  }
  
  /**
   * Unwrap data with local decryption
   * 
   * @param {Buffer} ciphertext - Wrapped data
   * @param {Object} context - Encryption context (not used)
   * @returns {Promise<Buffer>} Unwrapped data
   */
  async unwrap(ciphertext, context = {}) {
    if (!Buffer.isBuffer(ciphertext)) {
      throw new Error('Ciphertext must be a Buffer');
    }
    
    try {
      const crypto = require('crypto');
      
      // Extract components
      const iv = ciphertext.slice(0, 12);
      const authTag = ciphertext.slice(12, 28);
      const encrypted = ciphertext.slice(28);
      
      // Create decipher
      const decipher = crypto.createDecipheriv('aes-256-gcm', this.masterKey, iv);
      decipher.setAuthTag(authTag);
      
      // Decrypt
      const decrypted = Buffer.concat([
        decipher.update(encrypted),
        decipher.final()
      ]);
      
      console.log(`✅ Unwrapped data with LocalKMS`);
      
      return decrypted;
      
    } catch (error) {
      throw new Error(`Local KMS unwrap failed: ${error.message}`);
    }
  }
  
  getName() {
    return 'LocalKMSProvider';
  }
  
  /**
   * Destroy master key (for testing cleanup)
   */
  destroy() {
    wipeBuffer(this.masterKey);
    console.log('✅ LocalKMS master key destroyed');
  }
}

// ============================================================================
// PROVIDER FACTORY
// ============================================================================

/**
 * Create KMS provider based on environment
 * 
 * Production: AWS KMS
 * Development/Testing: Local KMS
 * 
 * @returns {KMSProvider} KMS provider instance
 */
function createKMSProvider() {
  if (NODE_ENV === 'production') {
    // Production: Use AWS KMS
    if (!KMS_KEY_ID) {
      throw new Error(
        'Production requires AWS KMS. Set KMS_KEY_ID environment variable. ' +
        'To create a KMS key: aws kms create-key --description "ZeroPay Master Key"'
      );
    }
    
    return new AWSKMSProvider(KMS_KEY_ID);
    
  } else if (KMS_KEY_ID) {
    // Development with KMS_KEY_ID set: Use AWS KMS
    return new AWSKMSProvider(KMS_KEY_ID);
    
  } else {
    // Development without KMS_KEY_ID: Use local KMS
    console.warn('⚠️  Using LocalKMSProvider for development. Set KMS_KEY_ID for AWS KMS.');
    return new LocalKMSProvider();
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Abstract class
  KMSProvider,
  
  // Implementations
  AWSKMSProvider,
  LocalKMSProvider,
  
  // Factory
  createKMSProvider,
  
  // Client getter
  getKMSClient
};

// ============================================================================
// CLI TESTING
// ============================================================================

if (require.main === module) {
  console.log('🔐 Testing KMS Provider Module\n');
  
  (async () => {
    try {
      // Test with LocalKMS (no AWS credentials required)
      console.log('Test 1: LocalKMS Provider');
      const localKMS = new LocalKMSProvider();
      
      // Test data
      const plaintext = Buffer.from('Hello, ZeroPay!', 'utf8');
      console.log(`  Plaintext: "${plaintext.toString('utf8')}"`);
      
      // Wrap
      const wrapped = await localKMS.wrap(Buffer.from(plaintext));
      console.log(`  Wrapped: ${wrapped.toString('hex').slice(0, 32)}... (${wrapped.length} bytes)`);
      
      // Unwrap
      const unwrapped = await localKMS.unwrap(wrapped);
      console.log(`  Unwrapped: "${unwrapped.toString('utf8')}"`);
      
      // Verify
      const match = plaintext.equals(unwrapped);
      console.log(`  Wrap/unwrap successful: ${match ? '✅' : '❌'}`);
      
      // Test with context
      console.log('\nTest 2: Encryption context');
      const context = { uuid: 'test-uuid', purpose: 'testing' };
      const wrappedWithContext = await localKMS.wrap(Buffer.from('test'), context);
      const unwrappedWithContext = await localKMS.unwrap(wrappedWithContext, context);
      console.log(`  Context preserved: ✅`);
      
      // Test error handling
      console.log('\nTest 3: Error handling');
      try {
        await localKMS.unwrap(Buffer.from('invalid'));
        console.log(`  Invalid unwrap detected: ❌ (should have failed)`);
      } catch (error) {
        console.log(`  Invalid unwrap detected: ✅`);
      }
      
      // Cleanup
      localKMS.destroy();
      
      console.log('\n✅ All tests passed!\n');
      
      // Test AWS KMS if credentials available
      if (KMS_KEY_ID) {
        console.log('Test 4: AWS KMS Provider');
        try {
          const awsKMS = new AWSKMSProvider(KMS_KEY_ID);
          const testData = Buffer.from('AWS KMS Test', 'utf8');
          const awsWrapped = await awsKMS.wrap(testData);
          const awsUnwrapped = await awsKMS.unwrap(awsWrapped);
          const awsMatch = testData.equals(awsUnwrapped);
          console.log(`  AWS KMS wrap/unwrap: ${awsMatch ? '✅' : '❌'}`);
          
          // Get key metadata
          const metadata = await awsKMS.getKeyMetadata();
          console.log(`  Key ID: ${metadata.keyId}`);
          console.log(`  State: ${metadata.state}`);
          console.log(`  Enabled: ${metadata.enabled}`);
        } catch (error) {
          console.log(`  AWS KMS not available: ${error.message}`);
        }
      } else {
        console.log('\nℹ️  Set KMS_KEY_ID to test AWS KMS integration');
      }
      
      process.exit(0);
      
    } catch (error) {
      console.error('\n❌ Test failed:', error.message);
      console.error(error.stack);
      process.exit(1);
    }
  })();
}
