/**
 * ZeroPay Memory Wipe Utility
 * 
 * Secure memory handling to prevent sensitive data from leaking:
 * - Zero memory buffers after use
 * - Secure string handling
 * - Prevent sensitive data in heap dumps
 * - Defense against memory forensics
 * 
 * Why This Matters:
 * - JavaScript garbage collector doesn't zero memory
 * - Sensitive data (keys, digests, plaintext) can remain in memory
 * - Memory dumps can expose secrets
 * - V8 heap snapshots can leak data
 * 
 * Security Model:
 * - Immediately wipe buffers after cryptographic operations
 * - Never keep sensitive data in memory longer than needed
 * - Overwrite strings (limited in JS, but do what we can)
 * - Use Buffer.alloc() instead of Buffer.allocUnsafe()
 * 
 * @version 1.0.0
 * @date 2025-10-11
 */

const crypto = require('crypto');

// ============================================================================
// BUFFER WIPING
// ============================================================================

/**
 * Securely wipe a buffer by overwriting with zeros
 * 
 * Process:
 * 1. Overwrite with zeros
 * 2. Overwrite with random data (defense against recovery attempts)
 * 3. Overwrite with zeros again
 * 
 * @param {Buffer} buffer - Buffer to wipe
 * @returns {Buffer} Same buffer (now zeroed)
 */
function wipeBuffer(buffer) {
  if (!Buffer.isBuffer(buffer)) {
    throw new TypeError('wipeBuffer requires a Buffer');
  }
  
  if (buffer.length === 0) {
    return buffer;
  }
  
  try {
    // Pass 1: Overwrite with zeros
    buffer.fill(0);
    
    // Pass 2: Overwrite with random data (makes recovery harder)
    crypto.randomFillSync(buffer);
    
    // Pass 3: Overwrite with zeros again
    buffer.fill(0);
    
    return buffer;
    
  } catch (error) {
    console.error('❌ Error wiping buffer:', error);
    // Even if wiping fails, try to zero it
    try {
      buffer.fill(0);
    } catch (e) {
      // Ignore
    }
    return buffer;
  }
}

/**
 * Wipe multiple buffers
 * 
 * @param {...Buffer} buffers - Buffers to wipe
 */
function wipeBuffers(...buffers) {
  for (const buffer of buffers) {
    if (Buffer.isBuffer(buffer)) {
      wipeBuffer(buffer);
    }
  }
}

/**
 * Create a secure buffer that auto-wipes on destruction
 * 
 * Note: JavaScript doesn't have destructors, so this relies on
 * explicit cleanup. Use with try/finally.
 * 
 * @param {number} size - Buffer size
 * @returns {Object} Secure buffer wrapper
 */
function createSecureBuffer(size) {
  const buffer = Buffer.alloc(size); // Allocate zeroed buffer
  let wiped = false;
  
  return {
    buffer,
    
    /**
     * Get the underlying buffer (use with caution)
     */
    get() {
      if (wiped) {
        throw new Error('SecureBuffer has been wiped');
      }
      return buffer;
    },
    
    /**
     * Wipe the buffer
     */
    wipe() {
      if (!wiped) {
        wipeBuffer(buffer);
        wiped = true;
      }
    },
    
    /**
     * Check if buffer has been wiped
     */
    isWiped() {
      return wiped;
    }
  };
}

// ============================================================================
// STRING WIPING (Limited in JavaScript)
// ============================================================================

/**
 * Attempt to wipe a string from memory
 * 
 * LIMITATION: JavaScript strings are immutable, so we can't actually
 * overwrite them. This function creates a new empty string and tries
 * to trigger garbage collection, but there's no guarantee the original
 * string is wiped from memory.
 * 
 * Best practice: Use Buffers for sensitive data, not strings.
 * 
 * @param {string} str - String to wipe (actually just dereferenced)
 * @returns {null}
 */
function wipeString(str) {
  if (typeof str !== 'string') {
    return null;
  }
  
  // JavaScript strings are immutable, so we can't actually wipe them
  // The best we can do is dereference and hope GC cleans up
  
  // Try to trigger GC (only works with --expose-gc flag)
  if (global.gc) {
    global.gc();
  }
  
  return null;
}

/**
 * Convert string to buffer, operate on it, then wipe
 * 
 * Use this pattern for sensitive string operations:
 * 
 * @example
 * withSecureString('sensitive data', (buffer) => {
 *   // Use buffer
 *   const hash = crypto.createHash('sha256').update(buffer).digest();
 * });
 * // buffer is automatically wiped after callback
 * 
 * @param {string} str - Input string
 * @param {Function} callback - Function that receives buffer
 * @returns {*} Callback return value
 */
function withSecureString(str, callback) {
  const buffer = Buffer.from(str, 'utf8');
  
  try {
    return callback(buffer);
  } finally {
    wipeBuffer(buffer);
  }
}

// ============================================================================
// SECURE OPERATIONS
// ============================================================================

/**
 * Compare two buffers in constant time and wipe after
 * 
 * @param {Buffer} a - First buffer
 * @param {Buffer} b - Second buffer
 * @param {boolean} wipeAfter - Wipe buffers after comparison (default: true)
 * @returns {boolean} True if equal
 */
function secureCompare(a, b, wipeAfter = true) {
  if (!Buffer.isBuffer(a) || !Buffer.isBuffer(b)) {
    throw new TypeError('secureCompare requires Buffers');
  }
  
  try {
    // Use constant-time comparison (crypto.timingSafeEqual)
    if (a.length !== b.length) {
      return false;
    }
    
    return crypto.timingSafeEqual(a, b);
    
  } finally {
    if (wipeAfter) {
      wipeBuffers(a, b);
    }
  }
}

/**
 * Hash data and wipe input buffer
 * 
 * @param {Buffer} data - Data to hash
 * @param {string} algorithm - Hash algorithm (default: 'sha256')
 * @param {boolean} wipeInput - Wipe input buffer (default: true)
 * @returns {Buffer} Hash digest
 */
function secureHash(data, algorithm = 'sha256', wipeInput = true) {
  if (!Buffer.isBuffer(data)) {
    throw new TypeError('secureHash requires a Buffer');
  }
  
  try {
    const hash = crypto.createHash(algorithm);
    hash.update(data);
    return hash.digest();
    
  } finally {
    if (wipeInput) {
      wipeBuffer(data);
    }
  }
}

/**
 * Encrypt data and wipe plaintext buffer
 * 
 * @param {Buffer} plaintext - Data to encrypt
 * @param {Buffer} key - Encryption key
 * @param {Buffer} iv - Initialization vector
 * @param {boolean} wipeInputs - Wipe input buffers (default: true)
 * @returns {Object} { ciphertext, authTag }
 */
function secureEncrypt(plaintext, key, iv, wipeInputs = true) {
  if (!Buffer.isBuffer(plaintext) || !Buffer.isBuffer(key) || !Buffer.isBuffer(iv)) {
    throw new TypeError('secureEncrypt requires Buffers');
  }
  
  try {
    const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
    const ciphertext = Buffer.concat([
      cipher.update(plaintext),
      cipher.final()
    ]);
    const authTag = cipher.getAuthTag();
    
    return { ciphertext, authTag };
    
  } finally {
    if (wipeInputs) {
      wipeBuffers(plaintext, key, iv);
    }
  }
}

// ============================================================================
// MEMORY PRESSURE MONITORING
// ============================================================================

/**
 * Get memory usage statistics
 * 
 * @returns {Object} Memory stats
 */
function getMemoryStats() {
  const usage = process.memoryUsage();
  
  return {
    heapUsed: Math.round(usage.heapUsed / 1024 / 1024 * 100) / 100, // MB
    heapTotal: Math.round(usage.heapTotal / 1024 / 1024 * 100) / 100, // MB
    external: Math.round(usage.external / 1024 / 1024 * 100) / 100, // MB
    rss: Math.round(usage.rss / 1024 / 1024 * 100) / 100, // MB (Resident Set Size)
    timestamp: Date.now()
  };
}

/**
 * Force garbage collection (if --expose-gc flag is set)
 * 
 * Usage: node --expose-gc server.js
 * 
 * @returns {boolean} True if GC was triggered
 */
function forceGarbageCollection() {
  if (global.gc) {
    global.gc();
    return true;
  } else {
    console.warn('⚠️  GC not exposed. Run with: node --expose-gc');
    return false;
  }
}

/**
 * Monitor memory usage and warn if high
 * 
 * @param {number} thresholdMB - Warning threshold in MB (default: 512)
 */
function monitorMemory(thresholdMB = 512) {
  const stats = getMemoryStats();
  
  if (stats.heapUsed > thresholdMB) {
    console.warn(`⚠️  High memory usage: ${stats.heapUsed}MB / ${stats.heapTotal}MB`);
    
    // Try to trigger GC
    if (forceGarbageCollection()) {
      console.log('✅ Garbage collection triggered');
    }
  }
  
  return stats;
}

// ============================================================================
// AUTOMATIC CLEANUP
// ============================================================================

/**
 * Register cleanup handler for process exit
 * 
 * Wipes sensitive data before process terminates.
 * 
 * @param {Array<Buffer>} buffers - Buffers to wipe on exit
 */
function registerCleanupHandler(...buffers) {
  const cleanup = () => {
    console.log('🧹 Cleaning up sensitive data...');
    wipeBuffers(...buffers);
  };
  
  // Cleanup on various exit signals
  process.on('exit', cleanup);
  process.on('SIGINT', () => {
    cleanup();
    process.exit(0);
  });
  process.on('SIGTERM', () => {
    cleanup();
    process.exit(0);
  });
  process.on('uncaughtException', (error) => {
    console.error('❌ Uncaught exception:', error);
    cleanup();
    process.exit(1);
  });
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Buffer wiping
  wipeBuffer,
  wipeBuffers,
  createSecureBuffer,
  
  // String wiping (limited)
  wipeString,
  withSecureString,
  
  // Secure operations
  secureCompare,
  secureHash,
  secureEncrypt,
  
  // Memory monitoring
  getMemoryStats,
  forceGarbageCollection,
  monitorMemory,
  
  // Cleanup
  registerCleanupHandler
};

// ============================================================================
// USAGE EXAMPLES
// ============================================================================

/*
// Example 1: Wipe buffers after use
const { wipeBuffer } = require('./crypto/memoryWipe');

const key = Buffer.from(process.env.ENCRYPTION_KEY, 'hex');
// ... use key ...
wipeBuffer(key); // Wipe when done

// Example 2: Secure buffer auto-wipe
const { createSecureBuffer } = require('./crypto/memoryWipe');

const secureKey = createSecureBuffer(32);
try {
  crypto.randomFillSync(secureKey.get());
  // ... use secureKey.get() ...
} finally {
  secureKey.wipe(); // Always wipe
}

// Example 3: Secure string operations
const { withSecureString } = require('./crypto/memoryWipe');

withSecureString('sensitive password', (buffer) => {
  const hash = crypto.createHash('sha256').update(buffer).digest();
  return hash;
}); // buffer automatically wiped

// Example 4: Register cleanup on exit
const { registerCleanupHandler } = require('./crypto/memoryWipe');

const sensitiveKey = Buffer.from('...');
registerCleanupHandler(sensitiveKey); // Will wipe on process exit

// Example 5: Monitor memory usage
const { monitorMemory } = require('./crypto/memoryWipe');

setInterval(() => {
  monitorMemory(512); // Warn if heap > 512MB
}, 60000); // Check every minute
*/
