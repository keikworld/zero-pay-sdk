// Path: backend/middleware/rateLimitMiddleware.js

/**
 * ZeroPay Rate Limit Middleware - PRODUCTION VERSION
 *
 * Multi-strategy rate limiting to prevent abuse and DoS attacks.
 *
 * Strategies:
 * 1. Token Bucket - Smooth rate limiting with burst allowance
 * 2. Sliding Window - Precise time-based limits
 * 3. Fixed Window - Simple counter-based limits
 * 4. Penalty System - Escalating blocks for repeated violations
 *
 * Features:
 * - ✅ Redis-backed distributed rate limiting
 * - ✅ Multi-dimensional (IP + User + Endpoint)
 * - ✅ Configurable limits per endpoint
 * - ✅ Automatic penalty system
 * - ✅ Whitelist/Blacklist support
 * - ✅ Real-time monitoring
 * - ✅ Graceful degradation
 * - ✅ Response headers (X-RateLimit-*)
 *
 * Rate Limit Tiers:
 * - Global: 1000 requests/minute
 * - Per IP: 100 requests/minute
 * - Per User: 50 requests/minute
 * - Per Endpoint: Configurable
 *
 * @version 2.0.0
 * @date 2025-10-13
 */

const crypto = require('crypto');

// ============================================================================
// CONSTANTS
// ============================================================================

const RATE_LIMIT_PREFIX = 'ratelimit:';
const WHITELIST_PREFIX = 'whitelist:';
const BLACKLIST_PREFIX = 'blacklist:';
const PENALTY_PREFIX = 'penalty:';

// Default rate limits
const DEFAULT_LIMITS = {
  global: { windowMs: 60000, max: 1000 },      // 1000 req/min
  perIP: { windowMs: 60000, max: 100 },        // 100 req/min per IP
  perUser: { windowMs: 60000, max: 50 },       // 50 req/min per user
  perEndpoint: { windowMs: 60000, max: 200 }   // 200 req/min per endpoint
};

// Penalty configuration
const PENALTY_CONFIG = {
  threshold: 3,              // violations before penalty
  durations: [
    30 * 60 * 1000,          // 1st penalty: 30 minutes
    2 * 60 * 60 * 1000,      // 2nd penalty: 2 hours
    24 * 60 * 60 * 1000      // 3rd+ penalty: 24 hours
  ]
};

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

/**
 * Get client identifier (IP address)
 */
function getClientId(req) {
  return req.headers['x-forwarded-for']?.split(',')[0].trim() ||
         req.headers['x-real-ip'] ||
         req.connection.remoteAddress ||
         req.socket.remoteAddress ||
         'unknown';
}

/**
 * Get user identifier from session or auth token
 */
function getUserId(req) {
  return req.session?.userId || 
         req.user?.id || 
         req.headers['x-user-id'] ||
         null;
}

/**
 * Generate Redis key for rate limiting
 */
function generateKey(prefix, identifier, window) {
  const timestamp = Math.floor(Date.now() / window);
  return `${RATE_LIMIT_PREFIX}${prefix}:${identifier}:${timestamp}`;
}

/**
 * Set rate limit headers
 */
function setRateLimitHeaders(res, limit, remaining, reset) {
  res.setHeader('X-RateLimit-Limit', limit);
  res.setHeader('X-RateLimit-Remaining', Math.max(0, remaining));
  res.setHeader('X-RateLimit-Reset', reset);
  
  if (remaining <= 0) {
    res.setHeader('Retry-After', Math.ceil((reset - Date.now()) / 1000));
  }
}

// ============================================================================
// TOKEN BUCKET RATE LIMITER
// ============================================================================

class TokenBucketLimiter {
  constructor(redisClient, options = {}) {
    this.redis = redisClient;
    this.capacity = options.capacity || 100;
    this.refillRate = options.refillRate || 10; // tokens per second
    this.windowMs = options.windowMs || 60000;
  }

  /**
   * Check if request is allowed using Token Bucket algorithm
   * 
   * Redis implementation:
   * - bucket:<key> stores: [tokens, lastRefill]
   * - Atomically refill and consume token
   */
  async isAllowed(key, cost = 1) {
    const now = Date.now();
    const bucketKey = `${RATE_LIMIT_PREFIX}bucket:${key}`;
    
    try {
      // Lua script for atomic token bucket operation
      const script = `
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refillRate = tonumber(ARGV[2])
        local cost = tonumber(ARGV[3])
        local now = tonumber(ARGV[4])
        local ttl = tonumber(ARGV[5])
        
        -- Get current bucket state
        local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
        local tokens = tonumber(bucket[1]) or capacity
        local lastRefill = tonumber(bucket[2]) or now
        
        -- Calculate refill
        local timePassed = (now - lastRefill) / 1000
        local tokensToAdd = timePassed * refillRate
        tokens = math.min(capacity, tokens + tokensToAdd)
        
        -- Try to consume
        if tokens >= cost then
          tokens = tokens - cost
          redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', now)
          redis.call('EXPIRE', key, ttl)
          return {1, tokens}
        else
          return {0, tokens}
        end
      `;
      
      const result = await this.redis.eval(script, {
        keys: [bucketKey],
        arguments: [
          this.capacity.toString(),
          this.refillRate.toString(),
          cost.toString(),
          now.toString(),
          Math.ceil(this.windowMs / 1000).toString()
        ]
      });
      
      return {
        allowed: result[0] === 1,
        remaining: Math.floor(result[1]),
        resetTime: now + this.windowMs
      };
      
    } catch (error) {
      console.error('❌ Token bucket error:', error.message);
      // Fail open (allow request) on Redis errors
      return { allowed: true, remaining: this.capacity, resetTime: now + this.windowMs };
    }
  }
}

// ============================================================================
// SLIDING WINDOW RATE LIMITER
// ============================================================================

class SlidingWindowLimiter {
  constructor(redisClient, options = {}) {
    this.redis = redisClient;
    this.windowMs = options.windowMs || 60000;
    this.maxRequests = options.maxRequests || 100;
  }

  /**
   * Check if request is allowed using Sliding Window algorithm
   * 
   * Redis implementation:
   * - Uses sorted set with timestamps as scores
   * - Removes old entries and counts recent ones
   */
  async isAllowed(key) {
    const now = Date.now();
    const windowStart = now - this.windowMs;
    const windowKey = `${RATE_LIMIT_PREFIX}window:${key}`;
    
    try {
      // Lua script for atomic sliding window operation
      const script = `
        local key = KEYS[1]
        local windowStart = tonumber(ARGV[1])
        local now = tonumber(ARGV[2])
        local maxRequests = tonumber(ARGV[3])
        local ttl = tonumber(ARGV[4])
        
        -- Remove old entries
        redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)
        
        -- Count current requests
        local count = redis.call('ZCARD', key)
        
        if count < maxRequests then
          -- Add this request
          redis.call('ZADD', key, now, now)
          redis.call('EXPIRE', key, ttl)
          return {1, maxRequests - count - 1}
        else
          return {0, 0}
        end
      `;
      
      const result = await this.redis.eval(script, {
        keys: [windowKey],
        arguments: [
          windowStart.toString(),
          now.toString(),
          this.maxRequests.toString(),
          Math.ceil(this.windowMs / 1000).toString()
        ]
      });
      
      return {
        allowed: result[0] === 1,
        remaining: result[1],
        resetTime: now + this.windowMs
      };
      
    } catch (error) {
      console.error('❌ Sliding window error:', error.message);
      // Fail open on Redis errors
      return { allowed: true, remaining: this.maxRequests, resetTime: now + this.windowMs };
    }
  }
}

// ============================================================================
// FIXED WINDOW RATE LIMITER
// ============================================================================

class FixedWindowLimiter {
  constructor(redisClient, options = {}) {
    this.redis = redisClient;
    this.windowMs = options.windowMs || 60000;
    this.maxRequests = options.maxRequests || 100;
  }

  /**
   * Check if request is allowed using Fixed Window algorithm
   * 
   * Simplest implementation - resets at fixed intervals
   */
  async isAllowed(key) {
    const now = Date.now();
    const windowKey = generateKey('fixed', key, this.windowMs);
    const ttl = Math.ceil(this.windowMs / 1000);
    
    try {
      const count = await this.redis.incr(windowKey);
      
      if (count === 1) {
        await this.redis.expire(windowKey, ttl);
      }
      
      const remaining = Math.max(0, this.maxRequests - count);
      const resetTime = Math.ceil(now / this.windowMs) * this.windowMs + this.windowMs;
      
      return {
        allowed: count <= this.maxRequests,
        remaining,
        resetTime
      };
      
    } catch (error) {
      console.error('❌ Fixed window error:', error.message);
      return { allowed: true, remaining: this.maxRequests, resetTime: now + this.windowMs };
    }
  }
}

// ============================================================================
// PENALTY MANAGER
// ============================================================================

class PenaltyManager {
  constructor(redisClient) {
    this.redis = redisClient;
  }

  /**
   * Check if entity is currently penalized
   */
  async isPenalized(identifier) {
    const key = `${PENALTY_PREFIX}${identifier}`;
    
    try {
      const penaltyData = await this.redis.get(key);
      
      if (!penaltyData) return { penalized: false };
      
      const { expiresAt, level, reason } = JSON.parse(penaltyData);
      
      if (Date.now() > expiresAt) {
        await this.redis.del(key);
        return { penalized: false };
      }
      
      return {
        penalized: true,
        expiresAt,
        level,
        reason,
        remainingMs: expiresAt - Date.now()
      };
      
    } catch (error) {
      console.error('❌ Penalty check error:', error.message);
      return { penalized: false };
    }
  }

  /**
   * Record a violation and apply penalty if threshold reached
   */
  async recordViolation(identifier, reason = 'Rate limit exceeded') {
    const violationKey = `${RATE_LIMIT_PREFIX}violations:${identifier}`;
    const penaltyKey = `${PENALTY_PREFIX}${identifier}`;
    
    try {
      // Increment violation count (24 hour window)
      const violations = await this.redis.incr(violationKey);
      
      if (violations === 1) {
        await this.redis.expire(violationKey, 86400); // 24 hours
      }
      
      // Apply penalty if threshold reached
      if (violations >= PENALTY_CONFIG.threshold) {
        const level = Math.min(violations - PENALTY_CONFIG.threshold, PENALTY_CONFIG.durations.length - 1);
        const duration = PENALTY_CONFIG.durations[level];
        const expiresAt = Date.now() + duration;
        
        const penaltyData = JSON.stringify({
          level: level + 1,
          violations,
          reason,
          expiresAt,
          appliedAt: Date.now()
        });
        
        await this.redis.setEx(penaltyKey, Math.ceil(duration / 1000), penaltyData);
        
        console.warn(`⚠️  Penalty applied to ${identifier}: Level ${level + 1}, ${violations} violations`);
        
        return { penalized: true, level: level + 1, duration };
      }
      
      return { penalized: false, violations };
      
    } catch (error) {
      console.error('❌ Record violation error:', error.message);
      return { penalized: false };
    }
  }

  /**
   * Clear penalties for an identifier (admin action)
   */
  async clearPenalty(identifier) {
    const violationKey = `${RATE_LIMIT_PREFIX}violations:${identifier}`;
    const penaltyKey = `${PENALTY_PREFIX}${identifier}`;
    
    try {
      await this.redis.del(violationKey);
      await this.redis.del(penaltyKey);
      console.log(`✅ Penalty cleared for ${identifier}`);
      return true;
    } catch (error) {
      console.error('❌ Clear penalty error:', error.message);
      return false;
    }
  }
}

// ============================================================================
// WHITELIST/BLACKLIST MANAGER
// ============================================================================

class AccessListManager {
  constructor(redisClient) {
    this.redis = redisClient;
  }

  /**
   * Check if identifier is whitelisted
   */
  async isWhitelisted(identifier) {
    const key = `${WHITELIST_PREFIX}${identifier}`;
    try {
      return await this.redis.exists(key) === 1;
    } catch (error) {
      console.error('❌ Whitelist check error:', error.message);
      return false;
    }
  }

  /**
   * Check if identifier is blacklisted
   */
  async isBlacklisted(identifier) {
    const key = `${BLACKLIST_PREFIX}${identifier}`;
    try {
      const result = await this.redis.get(key);
      if (!result) return { blacklisted: false };
      
      const data = JSON.parse(result);
      return {
        blacklisted: true,
        reason: data.reason,
        addedAt: data.addedAt
      };
    } catch (error) {
      console.error('❌ Blacklist check error:', error.message);
      return { blacklisted: false };
    }
  }

  /**
   * Add identifier to whitelist
   */
  async addToWhitelist(identifier, ttl = null) {
    const key = `${WHITELIST_PREFIX}${identifier}`;
    try {
      if (ttl) {
        await this.redis.setEx(key, ttl, '1');
      } else {
        await this.redis.set(key, '1');
      }
      console.log(`✅ Added to whitelist: ${identifier}`);
      return true;
    } catch (error) {
      console.error('❌ Add to whitelist error:', error.message);
      return false;
    }
  }

  /**
   * Add identifier to blacklist
   */
  async addToBlacklist(identifier, reason = 'Manual block', ttl = null) {
    const key = `${BLACKLIST_PREFIX}${identifier}`;
    const data = JSON.stringify({
      reason,
      addedAt: Date.now()
    });
    
    try {
      if (ttl) {
        await this.redis.setEx(key, ttl, data);
      } else {
        await this.redis.set(key, data);
      }
      console.log(`✅ Added to blacklist: ${identifier} (${reason})`);
      return true;
    } catch (error) {
      console.error('❌ Add to blacklist error:', error.message);
      return false;
    }
  }

  /**
   * Remove identifier from whitelist
   */
  async removeFromWhitelist(identifier) {
    const key = `${WHITELIST_PREFIX}${identifier}`;
    try {
      await this.redis.del(key);
      console.log(`✅ Removed from whitelist: ${identifier}`);
      return true;
    } catch (error) {
      console.error('❌ Remove from whitelist error:', error.message);
      return false;
    }
  }

  /**
   * Remove identifier from blacklist
   */
  async removeFromBlacklist(identifier) {
    const key = `${BLACKLIST_PREFIX}${identifier}`;
    try {
      await this.redis.del(key);
      console.log(`✅ Removed from blacklist: ${identifier}`);
      return true;
    } catch (error) {
      console.error('❌ Remove from blacklist error:', error.message);
      return false;
    }
  }
}

// ============================================================================
// MAIN RATE LIMIT MIDDLEWARE
// ============================================================================

/**
 * Create global rate limiter middleware
 */
function globalRateLimiter(redisClient, options = {}) {
  const limiter = new FixedWindowLimiter(redisClient, {
    windowMs: options.windowMs || DEFAULT_LIMITS.global.windowMs,
    maxRequests: options.maxRequests || DEFAULT_LIMITS.global.max
  });
  
  return async (req, res, next) => {
    const result = await limiter.isAllowed('global');
    
    setRateLimitHeaders(res, DEFAULT_LIMITS.global.max, result.remaining, result.resetTime);
    
    if (!result.allowed) {
      return res.status(429).json({
        success: false,
        error: 'Too many requests globally. Please try again later.',
        retryAfter: Math.ceil((result.resetTime - Date.now()) / 1000)
      });
    }
    
    next();
  };
}

/**
 * Create IP-based rate limiter middleware
 */
function ipRateLimiter(redisClient, options = {}) {
  const limiter = new SlidingWindowLimiter(redisClient, {
    windowMs: options.windowMs || DEFAULT_LIMITS.perIP.windowMs,
    maxRequests: options.maxRequests || DEFAULT_LIMITS.perIP.max
  });
  
  const penaltyManager = new PenaltyManager(redisClient);
  const accessListManager = new AccessListManager(redisClient);
  
  return async (req, res, next) => {
    const clientIp = getClientId(req);
    
    // Check whitelist
    if (await accessListManager.isWhitelisted(clientIp)) {
      return next();
    }
    
    // Check blacklist
    const blacklistCheck = await accessListManager.isBlacklisted(clientIp);
    if (blacklistCheck.blacklisted) {
      return res.status(403).json({
        success: false,
        error: 'Access denied',
        reason: blacklistCheck.reason
      });
    }
    
    // Check penalty
    const penaltyCheck = await penaltyManager.isPenalized(clientIp);
    if (penaltyCheck.penalized) {
      return res.status(429).json({
        success: false,
        error: 'Too many violations. Temporarily blocked.',
        retryAfter: Math.ceil(penaltyCheck.remainingMs / 1000),
        penaltyLevel: penaltyCheck.level
      });
    }
    
    // Check rate limit
    const result = await limiter.isAllowed(clientIp);
    
    setRateLimitHeaders(res, DEFAULT_LIMITS.perIP.max, result.remaining, result.resetTime);
    
    if (!result.allowed) {
      // Record violation
      await penaltyManager.recordViolation(clientIp, 'IP rate limit exceeded');
      
      return res.status(429).json({
        success: false,
        error: 'Too many requests from this IP. Please try again later.',
        retryAfter: Math.ceil((result.resetTime - Date.now()) / 1000)
      });
    }
    
    next();
  };
}

/**
 * Create user-based rate limiter middleware
 */
function userRateLimiter(redisClient, options = {}) {
  const limiter = new TokenBucketLimiter(redisClient, {
    capacity: options.capacity || DEFAULT_LIMITS.perUser.max,
    refillRate: options.refillRate || (DEFAULT_LIMITS.perUser.max / 60), // per second
    windowMs: options.windowMs || DEFAULT_LIMITS.perUser.windowMs
  });
  
  const penaltyManager = new PenaltyManager(redisClient);
  
  return async (req, res, next) => {
    const userId = getUserId(req);
    
    if (!userId) {
      // No user identified, skip user rate limiting
      return next();
    }
    
    // Check penalty
    const penaltyCheck = await penaltyManager.isPenalized(`user:${userId}`);
    if (penaltyCheck.penalized) {
      return res.status(429).json({
        success: false,
        error: 'Account temporarily restricted due to suspicious activity.',
        retryAfter: Math.ceil(penaltyCheck.remainingMs / 1000)
      });
    }
    
    // Check rate limit
    const result = await limiter.isAllowed(`user:${userId}`);
    
    setRateLimitHeaders(res, DEFAULT_LIMITS.perUser.max, result.remaining, result.resetTime);
    
    if (!result.allowed) {
      // Record violation
      await penaltyManager.recordViolation(`user:${userId}`, 'User rate limit exceeded');
      
      return res.status(429).json({
        success: false,
        error: 'Too many requests. Please slow down.',
        retryAfter: Math.ceil((result.resetTime - Date.now()) / 1000)
      });
    }
    
    next();
  };
}

/**
 * Create endpoint-specific rate limiter
 */
function endpointRateLimiter(redisClient, endpoint, options = {}) {
  const limiter = new FixedWindowLimiter(redisClient, {
    windowMs: options.windowMs || DEFAULT_LIMITS.perEndpoint.windowMs,
    maxRequests: options.maxRequests || DEFAULT_LIMITS.perEndpoint.max
  });
  
  return async (req, res, next) => {
    const clientIp = getClientId(req);
    const key = `${endpoint}:${clientIp}`;
    
    const result = await limiter.isAllowed(key);
    
    const maxRequests = options.maxRequests || DEFAULT_LIMITS.perEndpoint.max;
    setRateLimitHeaders(res, maxRequests, result.remaining, result.resetTime);
    
    if (!result.allowed) {
      return res.status(429).json({
        success: false,
        error: `Too many requests to ${endpoint}. Please try again later.`,
        retryAfter: Math.ceil((result.resetTime - Date.now()) / 1000)
      });
    }
    
    next();
  };
}

/**
 * Get rate limit statistics (for monitoring)
 */
async function getRateLimitStats(redisClient) {
  try {
    // Get all rate limit keys
    const keys = [];
    let cursor = '0';
    
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: `${RATE_LIMIT_PREFIX}*`,
        COUNT: 100
      });
      
      cursor = result.cursor;
      keys.push(...result.keys);
    } while (cursor !== '0');
    
    // Count by type
    const stats = {
      total: keys.length,
      byType: {
        bucket: keys.filter(k => k.includes(':bucket:')).length,
        window: keys.filter(k => k.includes(':window:')).length,
        fixed: keys.filter(k => k.includes(':fixed:')).length,
        violations: keys.filter(k => k.includes(':violations:')).length
      },
      penalties: await redisClient.dbSize(), // Approximate
      timestamp: Date.now()
    };
    
    return stats;
    
  } catch (error) {
    console.error('❌ Get rate limit stats error:', error.message);
    return { error: error.message };
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Limiters
  TokenBucketLimiter,
  SlidingWindowLimiter,
  FixedWindowLimiter,
  
  // Managers
  PenaltyManager,
  AccessListManager,
  
  // Middleware
  globalRateLimiter,
  ipRateLimiter,
  userRateLimiter,
  endpointRateLimiter,
  
  // Utilities
  getRateLimitStats,
  getClientId,
  getUserId,
  
  // Constants
  DEFAULT_LIMITS,
  PENALTY_CONFIG
};
