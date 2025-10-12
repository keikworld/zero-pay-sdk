// Path: backend/routes/verificationRouter.js

/**
 * Verification Router - Double Decryption Endpoints
 * 
 * Endpoints:
 * - POST /v1/verification/initiate  - Start verification session
 * - POST /v1/verification/verify    - Verify factors with double decryption
 * - GET /v1/verification/status/:session_id - Check verification status
 * 
 * Double Decryption Flow:
 * 1. Retrieve factor digests from Redis
 * 2. Constant-time comparison with input digests
 * 3. If match: Retrieve wrapped key from PostgreSQL
 * 4. Unwrap with KMS → get derived key
 * 5. Derive key from input factors
 * 6. Constant-time comparison: unwrapped vs derived
 * 7. Success = both layers match
 * 
 * Security:
 * - Rate limiting (prevent brute force)
 * - Nonce validation (replay protection)
 * - Constant-time comparisons
 * - Session tokens (short-lived)
 * - Audit logging
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */

const express = require('express');
const crypto = require('crypto');
const { decrypt } = require('../crypto/encryption');
const { verifyWithDoubleEncryption } = require('../crypto/doubleLayerCrypto');
const { getWrappedKey } = require('../database/database');
const { validateNonce } = require('../middleware/nonceValidator');
const { createRetrievalRateLimiter } = require('../middleware/rateLimiter');
const { wipeBuffer, secureCompare } = require('../crypto/memoryWipe');

const router = express.Router();

// Session storage (in production, use Redis)
const verificationSessions = new Map();

// ============================================================================
// VALIDATION HELPERS
// ============================================================================

/**
 * Validate UUID format
 */
function isValidUUID(uuid) {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return uuidRegex.test(uuid);
}

/**
 * Validate digest format
 */
function isValidDigest(digest) {
  return typeof digest === 'string' && /^[0-9a-f]{64}$/i.test(digest);
}

/**
 * Generate session ID
 */
function generateSessionId() {
  return crypto.randomUUID();
}

// ============================================================================
// POST /v1/verification/initiate
// Initiate verification session
// ============================================================================

router.post('/initiate',
  createRetrievalRateLimiter,
  validateNonce,
  async (req, res) => {
    try {
      const { user_uuid } = req.body;
      
      // Validation: UUID
      if (!isValidUUID(user_uuid)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid user_uuid format'
        });
      }
      
      // Check if enrollment exists in Redis
      const redisClient = req.app.locals.redisClient;
      const key = `enrollment:${user_uuid}`;
      const encrypted = await redisClient.get(key);
      
      if (!encrypted) {
        return res.status(404).json({
          success: false,
          error: 'Enrollment not found or expired'
        });
      }
      
      // Decrypt to get factor count
      const decrypted = await decrypt(encrypted);
      const enrollmentData = JSON.parse(decrypted);
      
      // Generate session
      const sessionId = generateSessionId();
      const session = {
        sessionId,
        uuid: user_uuid,
        factorCount: enrollmentData.factor_count,
        createdAt: Date.now(),
        expiresAt: Date.now() + (5 * 60 * 1000), // 5 minutes
        attempts: 0,
        maxAttempts: 3,
        status: 'pending'
      };
      
      verificationSessions.set(sessionId, session);
      
      console.log(`✅ Verification session initiated: ${sessionId}`);
      
      res.json({
        success: true,
        session_id: sessionId,
        uuid: user_uuid,
        factor_count: enrollmentData.factor_count,
        expires_in: 300, // 5 minutes
        message: 'Verification session created'
      });
      
    } catch (error) {
      console.error('❌ Error initiating verification:', error.message);
      
      res.status(500).json({
        success: false,
        error: 'Internal server error'
      });
    }
  }
);

// ============================================================================
// POST /v1/verification/verify
// Verify factors with double decryption
// ============================================================================

router.post('/verify',
  createRetrievalRateLimiter,
  validateNonce,
  async (req, res) => {
    const startTime = Date.now();
    
    try {
      const { session_id, user_uuid, factors } = req.body;
      
      // Validation: Required fields
      if (!session_id || !user_uuid || !factors) {
        return res.status(400).json({
          success: false,
          error: 'Missing required fields: session_id, user_uuid, factors'
        });
      }
      
      // Validation: UUID
      if (!isValidUUID(user_uuid)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid user_uuid format'
        });
      }
      
      // Validation: Session
      const session = verificationSessions.get(session_id);
      if (!session) {
        return res.status(404).json({
          success: false,
          error: 'Session not found or expired'
        });
      }
      
      // Check session expiration
      if (Date.now() > session.expiresAt) {
        verificationSessions.delete(session_id);
        return res.status(401).json({
          success: false,
          error: 'Session expired'
        });
      }
      
      // Check attempts
      if (session.attempts >= session.maxAttempts) {
        verificationSessions.delete(session_id);
        return res.status(429).json({
          success: false,
          error: 'Maximum attempts exceeded'
        });
      }
      
      session.attempts++;
      
      // Validate UUID match
      if (session.uuid !== user_uuid) {
        return res.status(400).json({
          success: false,
          error: 'UUID mismatch'
        });
      }
      
      // Validation: Factor count
      const factorCount = Object.keys(factors).length;
      if (factorCount !== session.factorCount) {
        return res.status(400).json({
          success: false,
          error: `Factor count mismatch: expected ${session.factorCount}, got ${factorCount}`
        });
      }
      
      // Validation: Digest format
      for (const [factorName, digest] of Object.entries(factors)) {
        if (!isValidDigest(digest)) {
          return res.status(400).json({
            success: false,
            error: `Invalid digest format for factor '${factorName}'`
          });
        }
      }
      
      // STEP 1: Retrieve factor digests from Redis
      console.log(`📍 Step 1: Retrieving factor digests from Redis...`);
      const redisClient = req.app.locals.redisClient;
      const key = `enrollment:${user_uuid}`;
      const encrypted = await redisClient.get(key);
      
      if (!encrypted) {
        return res.status(404).json({
          success: false,
          error: 'Enrollment not found or expired'
        });
      }
      
      const decrypted = await decrypt(encrypted);
      const enrollmentData = JSON.parse(decrypted);
      
      // STEP 2: Constant-time comparison of factor digests
      console.log(`📍 Step 2: Comparing factor digests (constant-time)...`);
      let digestMatch = true;
      
      for (const [factorName, inputDigest] of Object.entries(factors)) {
        const storedDigest = enrollmentData.factors[factorName];
        
        if (!storedDigest) {
          digestMatch = false;
          break;
        }
        
        const inputBuffer = Buffer.from(inputDigest, 'hex');
        const storedBuffer = Buffer.from(storedDigest, 'hex');
        
        if (!secureCompare(inputBuffer, storedBuffer, true)) {
          digestMatch = false;
          break;
        }
      }
      
      if (!digestMatch) {
        console.log(`❌ Digest comparison failed for ${user_uuid.slice(0, 8)}...`);
        
        return res.status(401).json({
          success: false,
          error: 'Invalid factors',
          attempts_remaining: session.maxAttempts - session.attempts
        });
      }
      
      // STEP 3: Retrieve wrapped key from PostgreSQL
      console.log(`📍 Step 3: Retrieving wrapped key from PostgreSQL...`);
      const wrappedKeyRecord = await getWrappedKey(user_uuid, req.ip);
      
      if (!wrappedKeyRecord) {
        return res.status(404).json({
          success: false,
          error: 'Wrapped key not found'
        });
      }
      
      // STEP 4: Double decryption (Unwrap + Derive + Compare)
      console.log(`📍 Step 4: Double decryption (KMS unwrap + derive + compare)...`);
      const verificationResult = await verifyWithDoubleEncryption({
        uuid: user_uuid,
        factorDigests: factors,
        wrappedKeyHex: wrappedKeyRecord.wrapped_key,
        context: {
          device_id: enrollmentData.device_id,
          ip_address: req.ip
        }
      });
      
      if (!verificationResult.success) {
        console.log(`❌ Double decryption failed for ${user_uuid.slice(0, 8)}...`);
        
        return res.status(401).json({
          success: false,
          error: 'Invalid factors',
          attempts_remaining: session.maxAttempts - session.attempts
        });
      }
      
      // SUCCESS: Generate auth token
      const authToken = crypto.randomBytes(32).toString('hex');
      
      // Update session
      session.status = 'verified';
      session.authToken = authToken;
      session.verifiedAt = Date.now();
      
      const duration = Date.now() - startTime;
      console.log(`✅ Verification successful for ${user_uuid.slice(0, 8)}... (${duration}ms)`);
      
      res.json({
        success: true,
        auth_token: authToken,
        session_id: session_id,
        uuid: user_uuid,
        duration_ms: duration,
        message: 'Authentication successful'
      });
      
    } catch (error) {
      console.error('❌ Error during verification:', error.message);
      
      res.status(500).json({
        success: false,
        error: 'Internal server error'
      });
    }
  }
);

// ============================================================================
// GET /v1/verification/status/:session_id
// Check verification status
// ============================================================================

router.get('/status/:session_id', async (req, res) => {
  try {
    const { session_id } = req.params;
    
    const session = verificationSessions.get(session_id);
    
    if (!session) {
      return res.status(404).json({
        success: false,
        error: 'Session not found'
      });
    }
    
    // Check expiration
    if (Date.now() > session.expiresAt) {
      verificationSessions.delete(session_id);
      return res.status(401).json({
        success: false,
        error: 'Session expired'
      });
    }
    
    res.json({
      success: true,
      session_id: session.sessionId,
      uuid: session.uuid,
      status: session.status,
      attempts: session.attempts,
      max_attempts: session.maxAttempts,
      expires_in: Math.floor((session.expiresAt - Date.now()) / 1000),
      created_at: session.createdAt
    });
    
  } catch (error) {
    console.error('❌ Error checking status:', error.message);
    
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// ============================================================================
// CLEANUP (periodic)
// ============================================================================

// Clean up expired sessions every 5 minutes
setInterval(() => {
  const now = Date.now();
  let cleaned = 0;
  
  for (const [sessionId, session] of verificationSessions.entries()) {
    if (now > session.expiresAt) {
      verificationSessions.delete(sessionId);
      cleaned++;
    }
  }
  
  if (cleaned > 0) {
    console.log(`🧹 Cleaned up ${cleaned} expired verification sessions`);
  }
}, 5 * 60 * 1000); // 5 minutes

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = router;
