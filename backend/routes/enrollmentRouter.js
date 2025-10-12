// Path: backend/routes/enrollmentRouter.js

/**
 * Enrollment Router - Double Encryption Endpoints
 * 
 * Endpoints:
 * - POST /v1/enrollment/store      - Store enrollment with double encryption
 * - GET /v1/enrollment/retrieve/:uuid - Retrieve factor digests
 * - PUT /v1/enrollment/update      - Update factors (key rotation)
 * - DELETE /v1/enrollment/delete/:uuid - GDPR deletion
 * - GET /v1/enrollment/export/:uuid - Export user data (GDPR Article 15)
 * 
 * Double Encryption Flow:
 * 1. Factor digests → Redis (24h TTL, AES-256-GCM encrypted)
 * 2. Derive key from UUID + digests (PBKDF2)
 * 3. Wrap derived key with KMS (AWS KMS)
 * 4. Store wrapped key → PostgreSQL (permanent, for key rotation)
 * 
 * Security:
 * - Rate limiting (per IP + per UUID)
 * - Nonce validation (replay protection)
 * - Input validation (UUID, digests, factor count)
 * - Audit logging (GDPR compliance)
 * - Memory wiping
 * 
 * @version 1.0.0
 * @date 2025-10-12
 */

const express = require('express');
const redis = require('redis');
const { encrypt, decrypt } = require('../crypto/encryption');
const { enrollWithDoubleEncryption, deleteWithDoubleEncryption } = require('../crypto/doubleLayerCrypto');
const { storeWrappedKey, getWrappedKey, deleteWrappedKey, exportUserData } = require('../database/database');
const { validateNonce } = require('../middleware/nonceValidator');
const { createEnrollmentRateLimiter, createPerUserRateLimiter } = require('../middleware/rateLimiter');

const router = express.Router();

// ============================================================================
// VALIDATION HELPERS
// ============================================================================

/**
 * Validate UUID format (RFC 4122)
 */
function isValidUUID(uuid) {
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return uuidRegex.test(uuid);
}

/**
 * Validate digest format (64 hex chars = 32 bytes SHA-256)
 */
function isValidDigest(digest) {
  return typeof digest === 'string' && /^[0-9a-f]{64}$/i.test(digest);
}

/**
 * Sanitize device ID (alphanumeric + hyphens only)
 */
function sanitizeDeviceId(deviceId) {
  if (typeof deviceId !== 'string') return null;
  return deviceId.replace(/[^a-zA-Z0-9-]/g, '').slice(0, 128);
}

// ============================================================================
// POST /v1/enrollment/store
// Store enrollment with double encryption
// ============================================================================

router.post('/store',
  createEnrollmentRateLimiter,
  createPerUserRateLimiter,
  validateNonce,
  async (req, res) => {
    const startTime = Date.now();
    
    try {
      const { user_uuid, factors, device_id, ttl_seconds = 86400 } = req.body;
      
      // Validation: Required fields
      if (!user_uuid || !factors || !device_id) {
        return res.status(400).json({
          success: false,
          error: 'Missing required fields: user_uuid, factors, device_id'
        });
      }
      
      // Validation: UUID format
      if (!isValidUUID(user_uuid)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid user_uuid format (must be valid UUID v4)'
        });
      }
      
      // Validation: Minimum 2 factors (PSD3 SCA compliance)
      const factorCount = Object.keys(factors).length;
      if (factorCount < 2) {
        return res.status(400).json({
          success: false,
          error: 'At least 2 factors required (PSD3 SCA compliance)'
        });
      }
      
      // Validation: Maximum 10 factors (DoS protection)
      if (factorCount > 10) {
        return res.status(400).json({
          success: false,
          error: 'Maximum 10 factors allowed'
        });
      }
      
      // Validation: Digest format for each factor
      for (const [factorName, digest] of Object.entries(factors)) {
        if (!isValidDigest(digest)) {
          return res.status(400).json({
            success: false,
            error: `Invalid digest format for factor '${factorName}' (must be 64 hex chars)`
          });
        }
      }
      
      // Validation: Device ID
      const sanitizedDeviceId = sanitizeDeviceId(device_id);
      if (!sanitizedDeviceId) {
        return res.status(400).json({
          success: false,
          error: 'Invalid device_id (must be alphanumeric + hyphens, max 128 chars)'
        });
      }
      
      // Validation: TTL (cap at 24 hours for security)
      const ttl = Math.min(Math.max(parseInt(ttl_seconds) || 86400, 60), 86400);
      
      // DOUBLE ENCRYPTION
      console.log(`📍 Starting double encryption for UUID: ${user_uuid.slice(0, 8)}...`);
      
      // Layer 1 + Layer 2: Derive + KMS Wrap
      const doubleEncryptionResult = await enrollWithDoubleEncryption({
        uuid: user_uuid,
        factorDigests: factors,
        context: {
          device_id: sanitizedDeviceId,
          ip_address: req.ip
        }
      });
      
      // Store wrapped key in PostgreSQL
      await storeWrappedKey({
        uuid: user_uuid,
        wrappedKey: doubleEncryptionResult.wrappedKey,
        kmsKeyId: doubleEncryptionResult.kmsKeyId,
        keyVersion: doubleEncryptionResult.keyVersion,
        factorCount: doubleEncryptionResult.factorCount,
        deviceId: sanitizedDeviceId,
        metadata: {
          enrollment_timestamp: Date.now(),
          factor_types: Object.keys(factors)
        },
        ipAddress: req.ip
      });
      
      // Create enrollment data for Redis
      const enrollmentData = {
        user_uuid,
        factors,  // Already digests (not raw)
        created_at: Date.now(),
        expires_at: Date.now() + (ttl * 1000),
        device_id: sanitizedDeviceId,
        factor_count: factorCount
      };
      
      // Encrypt enrollment data before storing in Redis (AES-256-GCM)
      const plaintext = JSON.stringify(enrollmentData);
      const encrypted = await encrypt(plaintext);
      
      // Store in Redis with TTL
      const redisClient = req.app.locals.redisClient;
      const key = `enrollment:${user_uuid}`;
      await redisClient.setEx(key, ttl, encrypted);
      
      const duration = Date.now() - startTime;
      console.log(`✅ Enrollment complete for ${user_uuid.slice(0, 8)}... (${duration}ms)`);
      console.log(`   - Factor count: ${factorCount}`);
      console.log(`   - Wrapped key stored in PostgreSQL`);
      console.log(`   - Factor digests stored in Redis (TTL: ${ttl}s)`);
      
      res.json({
        success: true,
        enrollment_id: user_uuid,
        expires_at: enrollmentData.expires_at,
        ttl_seconds: ttl,
        factor_count: factorCount,
        double_encryption: true,
        duration_ms: duration,
        message: 'Enrollment stored successfully with double encryption'
      });
      
    } catch (error) {
      console.error('❌ Error storing enrollment:', error.message);
      
      // Don't leak internal errors
      res.status(500).json({
        success: false,
        error: 'Internal server error',
        message: 'Enrollment failed'
      });
    }
  }
);

// ============================================================================
// GET /v1/enrollment/retrieve/:uuid
// Retrieve factor digests (for verification)
// ============================================================================

router.get('/retrieve/:uuid', async (req, res) => {
  try {
    const { uuid } = req.params;
    
    // Validation: UUID format
    if (!isValidUUID(uuid)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid UUID format'
      });
    }
    
    const redisClient = req.app.locals.redisClient;
    const key = `enrollment:${uuid}`;
    const encrypted = await redisClient.get(key);
    
    if (!encrypted) {
      return res.status(404).json({
        success: false,
        error: 'Enrollment not found or expired'
      });
    }
    
    // Decrypt data
    const decrypted = await decrypt(encrypted);
    const enrollmentData = JSON.parse(decrypted);
    
    // Defensive: Check expiration
    if (Date.now() > enrollmentData.expires_at) {
      await redisClient.del(key);
      return res.status(404).json({
        success: false,
        error: 'Enrollment expired'
      });
    }
    
    console.log(`✅ Retrieved enrollment for ${uuid.slice(0, 8)}...`);
    
    res.json({
      success: true,
      data: enrollmentData
    });
    
  } catch (error) {
    console.error('❌ Error retrieving enrollment:', error.message);
    
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// ============================================================================
// PUT /v1/enrollment/update
// Update enrollment (factor change, key rotation)
// ============================================================================

router.put('/update',
  createEnrollmentRateLimiter,
  createPerUserRateLimiter,
  validateNonce,
  async (req, res) => {
    const startTime = Date.now();
    
    try {
      const { user_uuid, old_factors, new_factors, device_id } = req.body;
      
      // Validation: Required fields
      if (!user_uuid || !old_factors || !new_factors || !device_id) {
        return res.status(400).json({
          success: false,
          error: 'Missing required fields: user_uuid, old_factors, new_factors, device_id'
        });
      }
      
      // Validation: UUID format
      if (!isValidUUID(user_uuid)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid user_uuid format'
        });
      }
      
      // Retrieve old wrapped key from PostgreSQL
      const oldWrappedKeyRecord = await getWrappedKey(user_uuid, req.ip);
      
      if (!oldWrappedKeyRecord) {
        return res.status(404).json({
          success: false,
          error: 'Enrollment not found'
        });
      }
      
      // Verify old factors and create new wrapped key
      const { updateWithDoubleEncryption } = require('../crypto/doubleLayerCrypto');
      
      const updateResult = await updateWithDoubleEncryption({
        uuid: user_uuid,
        oldFactorDigests: old_factors,
        newFactorDigests: new_factors,
        oldWrappedKeyHex: oldWrappedKeyRecord.wrapped_key,
        context: {
          device_id: sanitizeDeviceId(device_id),
          ip_address: req.ip
        }
      });
      
      // Store new wrapped key in PostgreSQL
      await storeWrappedKey({
        uuid: user_uuid,
        wrappedKey: updateResult.wrappedKey,
        kmsKeyId: oldWrappedKeyRecord.kms_key_id,
        keyVersion: oldWrappedKeyRecord.key_version + 1,
        factorCount: updateResult.newFactorCount,
        deviceId: sanitizeDeviceId(device_id),
        metadata: {
          update_timestamp: Date.now(),
          old_factor_count: updateResult.oldFactorCount,
          new_factor_count: updateResult.newFactorCount
        },
        ipAddress: req.ip
      });
      
      // Update Redis enrollment data
      const enrollmentData = {
        user_uuid,
        factors: new_factors,
        created_at: Date.now(),
        expires_at: Date.now() + 86400000, // 24h
        device_id: sanitizeDeviceId(device_id),
        factor_count: updateResult.newFactorCount
      };
      
      const encrypted = await encrypt(JSON.stringify(enrollmentData));
      const redisClient = req.app.locals.redisClient;
      await redisClient.setEx(`enrollment:${user_uuid}`, 86400, encrypted);
      
      const duration = Date.now() - startTime;
      console.log(`✅ Enrollment updated for ${user_uuid.slice(0, 8)}... (${duration}ms)`);
      
      res.json({
        success: true,
        enrollment_id: user_uuid,
        old_factor_count: updateResult.oldFactorCount,
        new_factor_count: updateResult.newFactorCount,
        duration_ms: duration,
        message: 'Enrollment updated successfully'
      });
      
    } catch (error) {
      console.error('❌ Error updating enrollment:', error.message);
      
      res.status(500).json({
        success: false,
        error: 'Internal server error',
        message: 'Update failed'
      });
    }
  }
);

// ============================================================================
// DELETE /v1/enrollment/delete/:uuid
// Delete enrollment (GDPR right to erasure)
// ============================================================================

router.delete('/delete/:uuid',
  validateNonce,
  async (req, res) => {
    try {
      const { uuid } = req.params;
      const { reason = 'USER_REQUEST' } = req.body;
      
      // Validation: UUID format
      if (!isValidUUID(uuid)) {
        return res.status(400).json({
          success: false,
          error: 'Invalid UUID format'
        });
      }
      
      // Delete from PostgreSQL (wrapped key)
      const deletedFromDB = await deleteWrappedKey(uuid, req.ip, reason);
      
      // Delete from Redis (factor digests)
      const redisClient = req.app.locals.redisClient;
      const key = `enrollment:${uuid}`;
      const deletedFromRedis = await redisClient.del(key);
      
      // Cryptographic deletion (wrapped key gone = data unreadable)
      await deleteWithDoubleEncryption({ uuid, reason });
      
      console.log(`✅ GDPR deletion complete for ${uuid.slice(0, 8)}...`);
      console.log(`   - PostgreSQL: ${deletedFromDB ? 'deleted' : 'not found'}`);
      console.log(`   - Redis: ${deletedFromRedis > 0 ? 'deleted' : 'not found'}`);
      console.log(`   - Reason: ${reason}`);
      
      res.json({
        success: true,
        uuid,
        deleted_from_database: deletedFromDB,
        deleted_from_cache: deletedFromRedis > 0,
        reason,
        message: 'Enrollment deleted successfully (GDPR compliant)'
      });
      
    } catch (error) {
      console.error('❌ Error deleting enrollment:', error.message);
      
      res.status(500).json({
        success: false,
        error: 'Internal server error'
      });
    }
  }
);

// ============================================================================
// GET /v1/enrollment/export/:uuid
// Export user data (GDPR Article 15 - Right of Access)
// ============================================================================

router.get('/export/:uuid', async (req, res) => {
  try {
    const { uuid } = req.params;
    
    // Validation: UUID format
    if (!isValidUUID(uuid)) {
      return res.status(400).json({
        success: false,
        error: 'Invalid UUID format'
      });
    }
    
    // Export from PostgreSQL (includes audit log)
    const exportData = await exportUserData(uuid);
    
    // Get Redis data (if exists)
    const redisClient = req.app.locals.redisClient;
    const key = `enrollment:${uuid}`;
    const encrypted = await redisClient.get(key);
    
    if (encrypted) {
      const decrypted = await decrypt(encrypted);
      const enrollmentData = JSON.parse(decrypted);
      
      // Add to export (but redact sensitive data)
      exportData.redis_cache = {
        exists: true,
        factor_count: enrollmentData.factor_count,
        device_id: enrollmentData.device_id,
        created_at: enrollmentData.created_at,
        expires_at: enrollmentData.expires_at
        // Do NOT include factor digests (sensitive)
      };
    } else {
      exportData.redis_cache = {
        exists: false
      };
    }
    
    console.log(`✅ Exported user data for ${uuid.slice(0, 8)}... (GDPR Article 15)`);
    
    res.json({
      success: true,
      data: exportData
    });
    
  } catch (error) {
    console.error('❌ Error exporting user data:', error.message);
    
    res.status(500).json({
      success: false,
      error: 'Internal server error'
    });
  }
});

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = router;
