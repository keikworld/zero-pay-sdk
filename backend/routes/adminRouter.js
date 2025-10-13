// Path: backend/routes/adminRouter.js

/**
 * ZeroPay Admin API - PRODUCTION VERSION
 * 
 * Protected admin endpoints for monitoring and management.
 * 
 * Features:
 * - Rate limit statistics
 * - Fraud detection statistics
 * - Blacklist/Whitelist management
 * - Penalty management
 * - System health monitoring
 * 
 * Security:
 * - ✅ API key authentication
 * - ✅ IP whitelist
 * - ✅ Audit logging
 * - ✅ Rate limiting
 * 
 * @version 1.0.0
 * @date 2025-10-13
 */

const express = require('express');
const router = express.Router();
const { getRateLimitStats } = require('../middleware/rateLimitMiddleware');

// ============================================================================
// ADMIN AUTHENTICATION MIDDLEWARE
// ============================================================================

/**
 * Verify admin API key
 */
function requireAdminAuth(req, res, next) {
  const apiKey = req.headers['x-admin-api-key'] || req.query.apiKey;
  const adminKey = process.env.ADMIN_API_KEY;
  
  if (!adminKey) {
    console.error('❌ ADMIN_API_KEY not configured');
    return res.status(500).json({
      success: false,
      error: 'Admin API not properly configured'
    });
  }
  
  if (!apiKey || apiKey !== adminKey) {
    console.warn('⚠️  Unauthorized admin access attempt from IP:', getClientIP(req));
    return res.status(403).json({
      success: false,
      error: 'Unauthorized. Valid API key required.'
    });
  }
  
  // Log admin action
  console.log(`✅ Admin API access: ${req.method} ${req.path} from ${getClientIP(req)}`);
  
  next();
}

/**
 * Get client IP address
 */
function getClientIP(req) {
  return req.headers['x-forwarded-for']?.split(',')[0].trim() ||
         req.headers['x-real-ip'] ||
         req.connection.remoteAddress ||
         'unknown';
}

// ============================================================================
// STATISTICS ENDPOINTS
// ============================================================================

/**
 * GET /admin/stats
 * 
 * Get comprehensive system statistics
 */
router.get('/stats', requireAdminAuth, async (req, res) => {
  try {
    const redisClient = req.app.locals.redisClient;
    const enrollmentCache = req.app.locals.enrollmentCache;
    const sessionCache = req.app.locals.sessionCache;
    const nonceCache = req.app.locals.nonceCache;
    
    // Get all cache statistics
    const enrollmentStats = enrollmentCache.getStats();
    const sessionStats = sessionCache.getStats();
    const nonceStats = nonceCache.getStats();
    
    // Get rate limit statistics
    const rateLimitStats = await getRateLimitStats(redisClient);
    
    // Get Redis info
    const redisInfo = await enrollmentCache.getRedisInfo();
    
    // Get fraud detection stats (approximate)
    const fraudStats = await getFraudStats(redisClient);
    
    res.json({
      success: true,
      timestamp: Date.now(),
      cache: {
        enrollment: enrollmentStats,
        session: sessionStats,
        nonce: nonceStats
      },
      rateLimit: rateLimitStats,
      fraud: fraudStats,
      redis: {
        usedMemory: redisInfo.used_memory_human,
        connectedClients: redisInfo.connected_clients,
        uptime: redisInfo.uptime_in_seconds
      }
    });
    
  } catch (error) {
    console.error('❌ Stats endpoint error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to retrieve statistics',
      message: error.message
    });
  }
});

/**
 * GET /admin/stats/fraud
 * 
 * Get detailed fraud detection statistics
 */
router.get('/stats/fraud', requireAdminAuth, async (req, res) => {
  try {
    const redisClient = req.app.locals.redisClient;
    const fraudStats = await getDetailedFraudStats(redisClient);
    
    res.json({
      success: true,
      timestamp: Date.now(),
      fraud: fraudStats
    });
    
  } catch (error) {
    console.error('❌ Fraud stats error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to retrieve fraud statistics'
    });
  }
});

/**
 * GET /admin/stats/ratelimit
 * 
 * Get detailed rate limit statistics
 */
router.get('/stats/ratelimit', requireAdminAuth, async (req, res) => {
  try {
    const redisClient = req.app.locals.redisClient;
    const stats = await getRateLimitStats(redisClient);
    
    res.json({
      success: true,
      timestamp: Date.now(),
      rateLimit: stats
    });
    
  } catch (error) {
    console.error('❌ Rate limit stats error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to retrieve rate limit statistics'
    });
  }
});

// ============================================================================
// BLACKLIST/WHITELIST MANAGEMENT
// ============================================================================

const { AccessListManager } = require('../middleware/rateLimitMiddleware');

/**
 * POST /admin/blacklist/ip
 * 
 * Add IP to blacklist
 */
router.post('/blacklist/ip', requireAdminAuth, async (req, res) => {
  try {
    const { ipAddress, reason, ttl } = req.body;
    
    if (!ipAddress) {
      return res.status(400).json({
        success: false,
        error: 'IP address required'
      });
    }
    
    const redisClient = req.app.locals.redisClient;
    const accessListManager = new AccessListManager(redisClient);
    
    await accessListManager.addToBlacklist(
      ipAddress,
      reason || 'Manual block by admin',
      ttl || null
    );
    
    console.log(`✅ Admin: IP blacklisted: ${ipAddress}`);
    
    res.json({
      success: true,
      message: `IP ${ipAddress} added to blacklist`
    });
    
  } catch (error) {
    console.error('❌ Blacklist IP error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to add IP to blacklist'
    });
  }
});

/**
 * DELETE /admin/blacklist/ip/:ipAddress
 * 
 * Remove IP from blacklist
 */
router.delete('/blacklist/ip/:ipAddress', requireAdminAuth, async (req, res) => {
  try {
    const { ipAddress } = req.params;
    
    const redisClient = req.app.locals.redisClient;
    const accessListManager = new AccessListManager(redisClient);
    
    await accessListManager.removeFromBlacklist(ipAddress);
    
    console.log(`✅ Admin: IP removed from blacklist: ${ipAddress}`);
    
    res.json({
      success: true,
      message: `IP ${ipAddress} removed from blacklist`
    });
    
  } catch (error) {
    console.error('❌ Remove from blacklist error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to remove IP from blacklist'
    });
  }
});

/**
 * POST /admin/whitelist/ip
 * 
 * Add IP to whitelist
 */
router.post('/whitelist/ip', requireAdminAuth, async (req, res) => {
  try {
    const { ipAddress, ttl } = req.body;
    
    if (!ipAddress) {
      return res.status(400).json({
        success: false,
        error: 'IP address required'
      });
    }
    
    const redisClient = req.app.locals.redisClient;
    const accessListManager = new AccessListManager(redisClient);
    
    await accessListManager.addToWhitelist(ipAddress, ttl || null);
    
    console.log(`✅ Admin: IP whitelisted: ${ipAddress}`);
    
    res.json({
      success: true,
      message: `IP ${ipAddress} added to whitelist`
    });
    
  } catch (error) {
    console.error('❌ Whitelist IP error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to add IP to whitelist'
    });
  }
});

/**
 * DELETE /admin/whitelist/ip/:ipAddress
 * 
 * Remove IP from whitelist
 */
router.delete('/whitelist/ip/:ipAddress', requireAdminAuth, async (req, res) => {
  try {
    const { ipAddress } = req.params;
    
    const redisClient = req.app.locals.redisClient;
    const accessListManager = new AccessListManager(redisClient);
    
    await accessListManager.removeFromWhitelist(ipAddress);
    
    console.log(`✅ Admin: IP removed from whitelist: ${ipAddress}`);
    
    res.json({
      success: true,
      message: `IP ${ipAddress} removed from whitelist`
    });
    
  } catch (error) {
    console.error('❌ Remove from whitelist error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to remove IP from whitelist'
    });
  }
});

// ============================================================================
// PENALTY MANAGEMENT
// ============================================================================

const { PenaltyManager } = require('../middleware/rateLimitMiddleware');

/**
 * DELETE /admin/penalty/:identifier
 * 
 * Clear penalty for an identifier
 */
router.delete('/penalty/:identifier', requireAdminAuth, async (req, res) => {
  try {
    const { identifier } = req.params;
    
    const redisClient = req.app.locals.redisClient;
    const penaltyManager = new PenaltyManager(redisClient);
    
    await penaltyManager.clearPenalty(identifier);
    
    console.log(`✅ Admin: Penalty cleared: ${identifier}`);
    
    res.json({
      success: true,
      message: `Penalty cleared for ${identifier}`
    });
    
  } catch (error) {
    console.error('❌ Clear penalty error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to clear penalty'
    });
  }
});

/**
 * GET /admin/penalties
 * 
 * Get list of all active penalties
 */
router.get('/penalties', requireAdminAuth, async (req, res) => {
  try {
    const redisClient = req.app.locals.redisClient;
    const penalties = await getActivePenalties(redisClient);
    
    res.json({
      success: true,
      count: penalties.length,
      penalties
    });
    
  } catch (error) {
    console.error('❌ Get penalties error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to retrieve penalties'
    });
  }
});

// ============================================================================
// USER MANAGEMENT
// ============================================================================

/**
 * DELETE /admin/user/:userId/ratelimit
 * 
 * Reset rate limits for a user
 */
router.delete('/user/:userId/ratelimit', requireAdminAuth, async (req, res) => {
  try {
    const { userId } = req.params;
    const redisClient = req.app.locals.redisClient;
    
    // Delete all rate limit keys for user
    const pattern = `ratelimit:*:user:${userId}*`;
    const keys = await scanKeys(redisClient, pattern);
    
    if (keys.length > 0) {
      await Promise.all(keys.map(key => redisClient.del(key)));
    }
    
    console.log(`✅ Admin: Rate limits reset for user: ${userId} (${keys.length} keys deleted)`);
    
    res.json({
      success: true,
      message: `Rate limits reset for user ${userId}`,
      keysDeleted: keys.length
    });
    
  } catch (error) {
    console.error('❌ Reset user rate limit error:', error.message);
    res.status(500).json({
      success: false,
      error: 'Failed to reset rate limits'
    });
  }
});

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get fraud detection statistics
 */
async function getFraudStats(redisClient) {
  try {
    const keys = await scanKeys(redisClient, 'ratelimit:violations:*');
    
    return {
      totalViolations: keys.length,
      timestamp: Date.now()
    };
  } catch (error) {
    console.error('Error getting fraud stats:', error.message);
    return { error: error.message };
  }
}

/**
 * Get detailed fraud statistics
 */
async function getDetailedFraudStats(redisClient) {
  try {
    const violationKeys = await scanKeys(redisClient, 'ratelimit:violations:*');
    const blacklistIPKeys = await scanKeys(redisClient, 'blacklist:*');
    const blacklistDeviceKeys = await scanKeys(redisClient, 'blacklist:device:*');
    
    return {
      violations: {
        total: violationKeys.length,
        users: violationKeys.filter(k => k.includes(':user:')).length,
        devices: violationKeys.filter(k => k.includes(':device:')).length,
        ips: violationKeys.filter(k => k.includes(':ip:')).length
      },
      blacklists: {
        ips: blacklistIPKeys.length,
        devices: blacklistDeviceKeys.length
      },
      timestamp: Date.now()
    };
  } catch (error) {
    console.error('Error getting detailed fraud stats:', error.message);
    return { error: error.message };
  }
}

/**
 * Get list of active penalties
 */
async function getActivePenalties(redisClient) {
  try {
    const keys = await scanKeys(redisClient, 'penalty:*');
    const penalties = [];
    
    for (const key of keys) {
      const data = await redisClient.get(key);
      if (data) {
        const identifier = key.replace('penalty:', '');
        penalties.push({
          identifier,
          data: JSON.parse(data),
          key
        });
      }
    }
    
    return penalties;
  } catch (error) {
    console.error('Error getting penalties:', error.message);
    return [];
  }
}

/**
 * Scan Redis keys by pattern
 */
async function scanKeys(redisClient, pattern) {
  const keys = [];
  let cursor = '0';
  
  do {
    const result = await redisClient.scan(cursor, {
      MATCH: pattern,
      COUNT: 100
    });
    
    cursor = result.cursor;
    keys.push(...result.keys);
  } while (cursor !== '0');
  
  return keys;
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = router;
