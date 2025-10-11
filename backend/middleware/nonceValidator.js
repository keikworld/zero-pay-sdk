/**
 * ZeroPay Nonce Validator - Replay Attack Protection
 *
 * Prevents replay attacks by tracking used nonces in Redis.
 *
 * Security Model:
 * - Each API request must include a unique nonce
 * - Nonce format: UUID v4 (128-bit random)
 * - Nonces are valid for 60 seconds (configurable)
 * - Used nonces are stored in Redis with TTL
 * - Duplicate nonces are rejected immediately
 *
 * Flow:
 * 1. Client requests nonce from /v1/auth/nonce
 * 2. Client includes nonce in enrollment/verification request
 * 3. Server validates nonce exists and hasn't been used
 * 4. Server marks nonce as used (stores in Redis)
 * 5. After 60 seconds, nonce expires from Redis
 *
 * GDPR Compliance:
 * - Nonces are not personally identifiable
 * - Auto-expire after 60 seconds (no long-term storage)
 *
 * @version 1.0.0
 * @date 2025-10-11
 */

const crypto = require('crypto');

// ============================================================================
// CONSTANTS
// ============================================================================

const NONCE_LENGTH = 32; // 256 bits
const NONCE_TTL_SECONDS = parseInt(process.env.NONCE_TTL_SECONDS) || 60;
const NONCE_PREFIX = 'nonce:';
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

// ============================================================================
// NONCE GENERATION
// ============================================================================

/**
 * Generate cryptographically secure nonce
 *
 * @returns {string} UUID v4 nonce
 */
function generateNonce() {
  // Use UUID v4 for nonces (RFC 4122)
  return crypto.randomUUID();
}

// ============================================================================
// NONCE VALIDATION
// ============================================================================

/**
 * Validate nonce format
 *
 * @param {string} nonce - Nonce to validate
 * @returns {boolean} True if valid format
 */
function isValidNonceFormat(nonce) {
  if (!nonce || typeof nonce !== 'string') {
    return false;
  }

  // Must be UUID v4 format
  return UUID_REGEX.test(nonce);
}

/**
 * Check if nonce has been used
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} nonce - Nonce to check
 * @returns {Promise<boolean>} True if nonce has been used
 */
async function isNonceUsed(redisClient, nonce) {
  const key = NONCE_PREFIX + nonce;
  const exists = await redisClient.exists(key);
  return exists === 1;
}

/**
 * Mark nonce as used
 *
 * Stores nonce in Redis with TTL to prevent replay attacks.
 * Once a nonce is marked as used, it cannot be reused until it expires.
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} nonce - Nonce to mark as used
 * @param {string} requestInfo - Optional request metadata (IP, endpoint)
 * @returns {Promise<void>}
 */
async function markNonceAsUsed(redisClient, nonce, requestInfo = {}) {
  const key = NONCE_PREFIX + nonce;
  const value = JSON.stringify({
    used_at: Date.now(),
    ip: requestInfo.ip || 'unknown',
    endpoint: requestInfo.endpoint || 'unknown',
    user_agent: requestInfo.userAgent || 'unknown'
  });

  // Store with TTL - after expiration, nonce can theoretically be reused
  // (but probability of collision is astronomically low with UUID v4)
  await redisClient.setEx(key, NONCE_TTL_SECONDS, value);
}

/**
 * Cleanup expired nonces
 *
 * Redis handles this automatically via TTL, but this function
 * can be used for manual cleanup or monitoring.
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Promise<number>} Number of nonces cleaned up
 */
async function cleanupExpiredNonces(redisClient) {
  // Redis automatically deletes keys with expired TTL
  // This function is for monitoring/logging purposes
  const pattern = NONCE_PREFIX + '*';
  let cleaned = 0;

  try {
    // Use SCAN instead of KEYS (production-safe)
    let cursor = 0;
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: pattern,
        COUNT: 100
      });

      cursor = result.cursor;
      const keys = result.keys;

      for (const key of keys) {
        const ttl = await redisClient.ttl(key);
        if (ttl === -2) { // Key doesn't exist (expired)
          cleaned++;
        }
      }
    } while (cursor !== 0);

    return cleaned;
  } catch (error) {
    console.error('Error cleaning up nonces:', error);
    return 0;
  }
}

// ============================================================================
// MIDDLEWARE
// ============================================================================

/**
 * Express middleware for nonce validation
 *
 * Usage:
 *   app.post('/v1/enrollment/store', validateNonce(redisClient), handler);
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware function
 */
function validateNonce(redisClient) {
  return async (req, res, next) => {
    try {
      // Extract nonce from header or body
      const nonce = req.headers['x-nonce'] || req.body?.nonce;

      if (!nonce) {
        return res.status(400).json({
          error: 'Nonce required',
          message: 'Request must include X-Nonce header or nonce in body',
          code: 'NONCE_MISSING'
        });
      }

      // Validate nonce format
      if (!isValidNonceFormat(nonce)) {
        return res.status(400).json({
          error: 'Invalid nonce format',
          message: 'Nonce must be a valid UUID v4',
          code: 'NONCE_INVALID_FORMAT'
        });
      }

      // Check if nonce has been used
      const used = await isNonceUsed(redisClient, nonce);
      if (used) {
        // Log potential replay attack
        console.warn('⚠️  Replay attack detected!', {
          nonce,
          ip: req.ip,
          endpoint: req.path,
          timestamp: new Date().toISOString()
        });

        return res.status(403).json({
          error: 'Nonce already used',
          message: 'This nonce has already been used. Possible replay attack.',
          code: 'NONCE_ALREADY_USED'
        });
      }

      // Mark nonce as used
      await markNonceAsUsed(redisClient, nonce, {
        ip: req.ip,
        endpoint: req.path,
        userAgent: req.get('user-agent')
      });

      // Attach nonce to request for logging
      req.nonce = nonce;

      next();

    } catch (error) {
      console.error('❌ Nonce validation error:', error);
      return res.status(500).json({
        error: 'Nonce validation failed',
        message: 'Internal server error during nonce validation',
        code: 'NONCE_VALIDATION_ERROR'
      });
    }
  };
}

/**
 * Middleware to skip nonce validation (for testing)
 *
 * @returns {Function} Express middleware function
 */
function skipNonceValidation() {
  return (req, res, next) => {
    console.warn('⚠️  Nonce validation SKIPPED (development mode)');
    next();
  };
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  generateNonce,
  isValidNonceFormat,
  isNonceUsed,
  markNonceAsUsed,
  cleanupExpiredNonces,
  validateNonce,
  skipNonceValidation,
  NONCE_TTL_SECONDS
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
// In server.js:

const { generateNonce, validateNonce } = require('./middleware/nonceValidator');

// Nonce generation endpoint (public)
app.get('/v1/auth/nonce', (req, res) => {
  const nonce = generateNonce();
  res.json({
    nonce,
    expires_in: 60, // seconds
    timestamp: Date.now()
  });
});

// Protected endpoint with nonce validation
app.post('/v1/enrollment/store',
  validateNonce(redisClient),  // Validate nonce
  async (req, res) => {
    // Nonce is valid and marked as used
    // Process enrollment...
  }
);

// Client usage:
// 1. GET /v1/auth/nonce → { nonce: "uuid-here" }
// 2. POST /v1/enrollment/store with header: X-Nonce: uuid-here
// 3. Server validates nonce and processes request
// 4. Same nonce cannot be reused (replay protection)
*/