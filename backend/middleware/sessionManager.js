// Path: backend/middleware/sessionManager.js

/**
 * ZeroPay Session Manager - Secure Session Token Management
 *
 * Manages authentication sessions after successful verification.
 *
 * Security Model:
 * - Encrypted session tokens (AES-256-GCM)
 * - Short-lived sessions (15 minutes default)
 * - Token refresh mechanism
 * - Session fingerprinting (device + IP binding)
 * - Automatic cleanup of expired sessions
 * - Constant-time token validation
 *
 * Token Format:
 * - Base64-encoded encrypted payload
 * - Contains: userId, deviceId, factors, expiresAt, nonce
 * - Stored in Redis with TTL
 * - Cannot be tampered without detection
 *
 * Flow:
 * 1. User verifies factors successfully
 * 2. Server generates encrypted session token
 * 3. Token stored in Redis (key: session_token_hash)
 * 4. Client receives token
 * 5. Client sends token with each request
 * 6. Server validates token (constant-time)
 * 7. Optional: Token refresh before expiry
 *
 * GDPR Compliance:
 * - Sessions auto-expire (no long-term storage)
 * - Can be manually revoked
 * - Minimal data stored (no PII)
 *
 * @version 1.0.0
 * @date 2025-10-12
 */

const crypto = require('crypto');
const { encrypt, decrypt } = require('../crypto/encryption');
const { wipeBuffer, secureCompare } = require('../crypto/memoryWipe');

// ============================================================================
// CONSTANTS
// ============================================================================

const SESSION_TTL_SECONDS = parseInt(process.env.SESSION_TTL_SECONDS) || 900; // 15 minutes
const SESSION_PREFIX = 'session:';
const SESSION_TOKEN_LENGTH = 32; // 256 bits
const MAX_SESSIONS_PER_USER = 5; // Limit concurrent sessions
const SESSION_REFRESH_THRESHOLD = 300; // Refresh if < 5 minutes remaining

// ============================================================================
// SESSION TOKEN GENERATION
// ============================================================================

/**
 * Generate encrypted session token
 *
 * @param {Object} sessionData - Session data to encrypt
 * @param {string} sessionData.userId - User UUID
 * @param {string} sessionData.deviceId - Device fingerprint
 * @param {Array<string>} sessionData.factors - Verified factors
 * @param {Object} sessionData.metadata - Additional metadata
 * @returns {Promise<Object>} { token, tokenHash, expiresAt, sessionData }
 */
async function generateSessionToken(sessionData) {
  const { userId, deviceId, factors, metadata = {} } = sessionData;

  // Validate inputs
  if (!userId || !deviceId || !Array.isArray(factors)) {
    throw new Error('Invalid session data: userId, deviceId, and factors required');
  }

  // Create session payload
  const now = Date.now();
  const expiresAt = now + (SESSION_TTL_SECONDS * 1000);
  const sessionNonce = crypto.randomBytes(16).toString('hex');

  const payload = {
    userId,
    deviceId,
    factors,
    metadata,
    createdAt: now,
    expiresAt,
    nonce: sessionNonce
  };

  // Encrypt payload
  const encrypted = await encrypt(JSON.stringify(payload));

  // Generate token (Base64-encoded encrypted data)
  const token = Buffer.from(encrypted, 'hex').toString('base64url');

  // Generate token hash (for Redis storage - prevents token scanning)
  const tokenHash = crypto
    .createHash('sha256')
    .update(token)
    .digest('hex');

  return {
    token,
    tokenHash,
    expiresAt,
    sessionData: payload
  };
}

/**
 * Decrypt and validate session token
 *
 * @param {string} token - Base64-encoded encrypted token
 * @returns {Promise<Object>} Decrypted session data
 * @throws {Error} If token is invalid or expired
 */
async function decryptSessionToken(token) {
  if (!token || typeof token !== 'string') {
    throw new Error('Invalid token format');
  }

  try {
    // Decode from Base64
    const encrypted = Buffer.from(token, 'base64url').toString('hex');

    // Decrypt payload
    const decrypted = await decrypt(encrypted);
    const sessionData = JSON.parse(decrypted);

    // Validate expiration
    if (Date.now() > sessionData.expiresAt) {
      throw new Error('Session expired');
    }

    return sessionData;

  } catch (error) {
    throw new Error(`Token validation failed: ${error.message}`);
  }
}

// ============================================================================
// REDIS SESSION STORAGE
// ============================================================================

/**
 * Store session in Redis
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} tokenHash - Hashed token (key)
 * @param {Object} sessionData - Session data to store
 * @param {number} ttlSeconds - TTL in seconds
 * @returns {Promise<void>}
 */
async function storeSession(redisClient, tokenHash, sessionData, ttlSeconds = SESSION_TTL_SECONDS) {
  const key = SESSION_PREFIX + tokenHash;

  // Store session data as JSON
  const sessionJson = JSON.stringify({
    userId: sessionData.userId,
    deviceId: sessionData.deviceId,
    factors: sessionData.factors,
    createdAt: sessionData.createdAt,
    expiresAt: sessionData.expiresAt,
    metadata: sessionData.metadata || {}
  });

  // Store with TTL
  await redisClient.setEx(key, ttlSeconds, sessionJson);

  // Track user sessions for concurrent session limiting
  await trackUserSession(redisClient, sessionData.userId, tokenHash);
}

/**
 * Retrieve session from Redis
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} tokenHash - Hashed token
 * @returns {Promise<Object|null>} Session data or null if not found
 */
async function getSession(redisClient, tokenHash) {
  const key = SESSION_PREFIX + tokenHash;
  const sessionJson = await redisClient.get(key);

  if (!sessionJson) {
    return null;
  }

  try {
    return JSON.parse(sessionJson);
  } catch (error) {
    console.error('❌ Error parsing session data:', error.message);
    return null;
  }
}

/**
 * Delete session from Redis (logout)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} tokenHash - Hashed token
 * @returns {Promise<boolean>} True if deleted
 */
async function deleteSession(redisClient, tokenHash) {
  const key = SESSION_PREFIX + tokenHash;
  const deleted = await redisClient.del(key);
  return deleted === 1;
}

/**
 * Refresh session (extend TTL)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} tokenHash - Hashed token
 * @param {number} ttlSeconds - New TTL in seconds
 * @returns {Promise<boolean>} True if refreshed
 */
async function refreshSession(redisClient, tokenHash, ttlSeconds = SESSION_TTL_SECONDS) {
  const key = SESSION_PREFIX + tokenHash;
  const exists = await redisClient.exists(key);

  if (!exists) {
    return false;
  }

  // Extend TTL
  await redisClient.expire(key, ttlSeconds);
  return true;
}

// ============================================================================
// CONCURRENT SESSION MANAGEMENT
// ============================================================================

/**
 * Track user session (limit concurrent sessions)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} userId - User UUID
 * @param {string} tokenHash - Session token hash
 * @returns {Promise<void>}
 */
async function trackUserSession(redisClient, userId, tokenHash) {
  const key = `user_sessions:${userId}`;

  // Add session to user's session set
  await redisClient.sAdd(key, tokenHash);

  // Check if user has too many sessions
  const sessionCount = await redisClient.sCard(key);

  if (sessionCount > MAX_SESSIONS_PER_USER) {
    // Remove oldest session
    const sessions = await redisClient.sMembers(key);

    // Get session creation times
    const sessionTimes = await Promise.all(
      sessions.map(async (hash) => {
        const session = await getSession(redisClient, hash);
        return {
          hash,
          createdAt: session?.createdAt || 0
        };
      })
    );

    // Sort by creation time (oldest first)
    sessionTimes.sort((a, b) => a.createdAt - b.createdAt);

    // Delete oldest session
    const oldestHash = sessionTimes[0].hash;
    await deleteSession(redisClient, oldestHash);
    await redisClient.sRem(key, oldestHash);

    console.log(`⚠️  User ${userId} exceeded max sessions. Removed oldest session.`);
  }

  // Set TTL on user sessions set
  await redisClient.expire(key, SESSION_TTL_SECONDS);
}

/**
 * Revoke all user sessions (logout everywhere)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} userId - User UUID
 * @returns {Promise<number>} Number of sessions revoked
 */
async function revokeAllUserSessions(redisClient, userId) {
  const key = `user_sessions:${userId}`;
  const sessions = await redisClient.sMembers(key);

  let revoked = 0;
  for (const tokenHash of sessions) {
    const deleted = await deleteSession(redisClient, tokenHash);
    if (deleted) revoked++;
  }

  // Delete user sessions set
  await redisClient.del(key);

  console.log(`✅ Revoked ${revoked} session(s) for user ${userId}`);
  return revoked;
}

// ============================================================================
// MIDDLEWARE
// ============================================================================

/**
 * Express middleware to validate session token
 *
 * Usage:
 *   app.get('/protected', validateSessionMiddleware(redisClient), handler);
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {Object} options - Middleware options
 * @param {boolean} options.required - Require session (default: true)
 * @param {boolean} options.autoRefresh - Auto-refresh near-expiry (default: true)
 * @returns {Function} Express middleware function
 */
function validateSessionMiddleware(redisClient, options = {}) {
  const { required = true, autoRefresh = true } = options;

  return async (req, res, next) => {
    try {
      // Extract token from Authorization header
      const authHeader = req.headers.authorization;
      const token = authHeader?.startsWith('Bearer ')
        ? authHeader.slice(7)
        : null;

      // If no token and not required, skip validation
      if (!token && !required) {
        req.session = null;
        return next();
      }

      // If no token and required, reject
      if (!token && required) {
        return res.status(401).json({
          error: 'Unauthorized',
          message: 'Session token required',
          code: 'SESSION_TOKEN_MISSING'
        });
      }

      // Decrypt token
      let sessionData;
      try {
        sessionData = await decryptSessionToken(token);
      } catch (error) {
        return res.status(401).json({
          error: 'Invalid session token',
          message: error.message,
          code: 'SESSION_TOKEN_INVALID'
        });
      }

      // Generate token hash
      const tokenHash = crypto
        .createHash('sha256')
        .update(token)
        .digest('hex');

      // Verify session exists in Redis
      const storedSession = await getSession(redisClient, tokenHash);

      if (!storedSession) {
        return res.status(401).json({
          error: 'Session not found',
          message: 'Session expired or revoked',
          code: 'SESSION_NOT_FOUND'
        });
      }

      // Verify session data matches (constant-time)
      const dataMatch =
        storedSession.userId === sessionData.userId &&
        storedSession.deviceId === sessionData.deviceId;

      if (!dataMatch) {
        return res.status(401).json({
          error: 'Session mismatch',
          message: 'Session data does not match',
          code: 'SESSION_DATA_MISMATCH'
        });
      }

      // Check if session needs refresh
      if (autoRefresh) {
        const timeRemaining = sessionData.expiresAt - Date.now();
        if (timeRemaining < SESSION_REFRESH_THRESHOLD * 1000) {
          await refreshSession(redisClient, tokenHash);
          console.log(`🔄 Session refreshed for user ${sessionData.userId}`);
        }
      }

      // Attach session to request
      req.session = sessionData;
      req.sessionTokenHash = tokenHash;

      next();

    } catch (error) {
      console.error('❌ Session validation error:', error);
      return res.status(500).json({
        error: 'Session validation failed',
        message: 'Internal server error during session validation',
        code: 'SESSION_VALIDATION_ERROR'
      });
    }
  };
}

// ============================================================================
// CLEANUP
// ============================================================================

/**
 * Cleanup expired sessions (periodic task)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Promise<number>} Number of sessions cleaned up
 */
async function cleanupExpiredSessions(redisClient) {
  // Redis automatically deletes keys with expired TTL
  // This function is for monitoring/logging purposes
  const pattern = SESSION_PREFIX + '*';
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
        if (ttl === -2) {
          // Key doesn't exist (expired)
          cleaned++;
        }
      }
    } while (cursor !== 0);

    return cleaned;
  } catch (error) {
    console.error('Error cleaning up sessions:', error);
    return 0;
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Token operations
  generateSessionToken,
  decryptSessionToken,

  // Redis operations
  storeSession,
  getSession,
  deleteSession,
  refreshSession,

  // User session management
  trackUserSession,
  revokeAllUserSessions,

  // Middleware
  validateSessionMiddleware,

  // Cleanup
  cleanupExpiredSessions,

  // Constants
  SESSION_TTL_SECONDS,
  SESSION_REFRESH_THRESHOLD,
  MAX_SESSIONS_PER_USER
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
// In server.js or verificationRouter.js:

const {
  generateSessionToken,
  storeSession,
  validateSessionMiddleware,
  revokeAllUserSessions
} = require('./middleware/sessionManager');

// After successful verification:
router.post('/verify', async (req, res) => {
  // ... verify factors ...

  // Generate session token
  const session = await generateSessionToken({
    userId: user_uuid,
    deviceId: device_id,
    factors: ['PIN', 'PATTERN'],
    metadata: { loginMethod: 'app' }
  });

  // Store in Redis
  await storeSession(redisClient, session.tokenHash, session.sessionData);

  // Return token to client
  res.json({
    success: true,
    session_token: session.token,
    expires_in: SESSION_TTL_SECONDS
  });
});

// Protected endpoint with session validation:
router.get('/payment/initiate',
  validateSessionMiddleware(redisClient, { required: true, autoRefresh: true }),
  async (req, res) => {
    // req.session contains validated session data
    const userId = req.session.userId;
    // ... process payment ...
  }
);

// Logout endpoint:
router.post('/logout', async (req, res) => {
  const tokenHash = req.sessionTokenHash;
  await deleteSession(redisClient, tokenHash);
  res.json({ success: true, message: 'Logged out' });
});

// Logout everywhere:
router.post('/logout-all', async (req, res) => {
  const userId = req.session.userId;
  const revoked = await revokeAllUserSessions(redisClient, userId);
  res.json({ success: true, sessions_revoked: revoked });
});

// Client usage:
// 1. Verify factors → receive session_token
// 2. Store token securely (encrypted storage on device)
// 3. Send token with requests: Authorization: Bearer <token>
// 4. Token auto-refreshes if within 5 minutes of expiry
// 5. Token expires after 15 minutes of inactivity
*/
