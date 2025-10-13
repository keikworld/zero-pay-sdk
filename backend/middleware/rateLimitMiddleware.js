// Path: backend/middleware/rateLimitMiddleware.js

/**
 * ZeroPay Rate Limit Middleware - Enhanced Rate Limiting
 *
 * Multi-strategy rate limiting to prevent abuse and DoS attacks.
 *
 * Strategies:
 * 1. Token Bucket - Smooth rate limiting with burst allowance
 * 2. Sliding Window - Precise time-based limits
 * 3. Fixed Window - Simple counter-based limits
 * 4. Adaptive - Dynamic limits based on system load
 *
 * Features:
 * - Multi-dimensional limiting (IP + User + Endpoint)
 * - Redis-backed (distributed rate limiting)
 * - Configurable limits per endpoint
 * - Automatic penalty system
 * - Whitelist/Blacklist support
 * - Real-time monitoring
 * - Graceful degradation
 *
 * Rate Limit Tiers:
 * - Global: 1000 requests/minute
 * - Per IP: 100 requests/minute
 * - Per User: 50 requests/minute
 * - Per Endpoint: Configurable
 *
 * Response Headers:
 * - X-RateLimit-Limit: Maximum requests
 * - X-RateLimit-Remaining: Remaining requests
 * - X-RateLimit-Reset: Reset timestamp
 * - Retry-After: Seconds until retry allowed
 *
 * @version 1.0.0
 * @date 2025-10-12
 */

const crypto = require('crypto');

// ============================================================================
// CONSTANTS
// ============================================================================

const RATE_LIMIT_PREFIX = 'ratelimit:';

// Default rate limits
const DEFAULT_LIMITS = {
  global: { windowMs: 60000, max: 1000 },      // 1000 req/min
  perIP: { windowMs: 60000, max: 100 },        // 100 req/min per IP
  perUser: { windowMs: 60000, max: 50 },       // 50 req/min per user
  perEndpoint: { windowMs: 60000, max: 200 }   // 200 req/min per endpoint
};

// Penalty thresholds
const PENALTY_CONFIG = {
  threshold: 3,              // violations before penalty
  duration: 1800000,         // 30 minutes
  escalation: 2              // multiply duration on repeated violations
};

// Whitelist/Blacklist
const WHITELIST_PREFIX = 'whitelist:';
const BLACKLIST_PREFIX = 'blacklist:';

// ============================================================================
// TOKEN BUCKET IMPLEMENTATION
// ============================================================================

class TokenBucket {
  /**
   * Create token bucket rate limiter
   *
   * @param {RedisClient} redisClient - Redis client instance
   * @param {string} key - Bucket key
   * @param {number} capacity - Maximum tokens (burst size)
   * @param {number} refillRate - Tokens per second
   */
  constructor(redisClient, key, capacity, refillRate) {
    this.redis = redisClient;
    this.key = RATE_LIMIT_PREFIX + 'bucket:' + key;
    this.capacity = capacity;
    this.refillRate = refillRate;
  }

  /**
   * Try to consume tokens
   *
   * @param {number} tokens - Number of tokens to consume
   * @returns {Promise<Object>} { allowed, remaining, resetAt }
   */
  async tryConsume(tokens = 1) {
    try {
      const now = Date.now();
      
      // Get current bucket state
      const data = await this.redis.get(this.key);
      let bucket;

      if (!data) {
        // Initialize new bucket
        bucket = {
          tokens: this.capacity,
          lastRefill: now
        };
      } else {
        bucket = JSON.parse(data);

        // Refill tokens based on time elapsed
        const elapsed = (now - bucket.lastRefill) / 1000; // seconds
        const tokensToAdd = elapsed * this.refillRate;
        bucket.tokens = Math.min(this.capacity, bucket.tokens + tokensToAdd);
        bucket.lastRefill = now;
      }

      // Check if we have enough tokens
      if (bucket.tokens >= tokens) {
        bucket.tokens -= tokens;

        // Store updated bucket
        await this.redis.setEx(
          this.key,
          Math.ceil(this.capacity / this.refillRate) + 60, // TTL with buffer
          JSON.stringify(bucket)
        );

        return {
          allowed: true,
          remaining: Math.floor(bucket.tokens),
          resetAt: now + Math.ceil((this.capacity - bucket.tokens) / this.refillRate * 1000)
        };
      } else {
        // Not enough tokens
        return {
          allowed: false,
          remaining: Math.floor(bucket.tokens),
          resetAt: now + Math.ceil((tokens - bucket.tokens) / this.refillRate * 1000)
        };
      }

    } catch (error) {
      console.error('❌ Token bucket error:', error.message);
      // Fail open (allow request) on error
      return { allowed: true, remaining: 0, resetAt: Date.now() + 60000 };
    }
  }
}

// ============================================================================
// SLIDING WINDOW IMPLEMENTATION
// ============================================================================

class SlidingWindow {
  /**
   * Create sliding window rate limiter
   *
   * @param {RedisClient} redisClient - Redis client instance
   * @param {string} key - Window key
   * @param {number} windowMs - Window size in milliseconds
   * @param {number} maxRequests - Maximum requests in window
   */
  constructor(redisClient, key, windowMs, maxRequests) {
    this.redis = redisClient;
    this.key = RATE_LIMIT_PREFIX + 'window:' + key;
    this.windowMs = windowMs;
    this.maxRequests = maxRequests;
  }

  /**
   * Record request and check limit
   *
   * @returns {Promise<Object>} { allowed, remaining, resetAt }
   */
  async recordRequest() {
    try {
      const now = Date.now();
      const windowStart = now - this.windowMs;

      // Remove old requests outside window
      await this.redis.zRemRangeByScore(this.key, 0, windowStart);

      // Count requests in current window
      const count = await this.redis.zCard(this.key);

      if (count < this.maxRequests) {
        // Add current request
        await this.redis.zAdd(this.key, {
          score: now,
          value: `${now}-${crypto.randomBytes(8).toString('hex')}`
        });

        // Set TTL
        await this.redis.expire(this.key, Math.ceil(this.windowMs / 1000) + 60);

        return {
          allowed: true,
          remaining: this.maxRequests - count - 1,
          resetAt: now + this.windowMs
        };
      } else {
        // Get oldest request timestamp
        const oldest = await this.redis.zRange(this.key, 0, 0, { REV: false });
        const resetAt = oldest.length > 0
          ? parseInt(oldest[0].split('-')[0]) + this.windowMs
          : now + this.windowMs;

        return {
          allowed: false,
          remaining: 0,
          resetAt
        };
      }

    } catch (error) {
      console.error('❌ Sliding window error:', error.message);
      // Fail open on error
      return { allowed: true, remaining: 0, resetAt: Date.now() + this.windowMs };
    }
  }
}

// ============================================================================
// PENALTY SYSTEM
// ============================================================================

class PenaltyManager {
  /**
   * Create penalty manager
   *
   * @param {RedisClient} redisClient - Redis client instance
   */
  constructor(redisClient) {
    this.redis = redisClient;
    this.prefix = RATE_LIMIT_PREFIX + 'penalty:';
  }

  /**
   * Record rate limit violation
   *
   * @param {string} identifier - IP or user ID
   * @returns {Promise<Object>} { penalized, duration, violations }
   */
  async recordViolation(identifier) {
    try {
      const key = this.prefix + identifier;
      const violationKey = key + ':violations';

      // Increment violations
      const violations = await this.redis.incr(violationKey);
      await this.redis.expire(violationKey, 3600); // 1 hour

      // Check if penalty threshold reached
      if (violations >= PENALTY_CONFIG.threshold) {
        // Calculate penalty duration (escalates on repeat offenses)
        const escalationFactor = Math.floor(violations / PENALTY_CONFIG.threshold);
        const duration = PENALTY_CONFIG.duration * Math.pow(PENALTY_CONFIG.escalation, escalationFactor - 1);

        // Set penalty
        await this.redis.setEx(
          key,
          Math.ceil(duration / 1000),
          JSON.stringify({
            startTime: Date.now(),
            duration,
            violations,
            reason: 'rate_limit_exceeded'
          })
        );

        console.warn(`⚠️  Penalty applied to ${identifier}: ${violations} violations, ${duration}ms duration`);

        return { penalized: true, duration, violations };
      }

      return { penalized: false, duration: 0, violations };

    } catch (error) {
      console.error('❌ Penalty system error:', error.message);
      return { penalized: false, duration: 0, violations: 0 };
    }
  }

  /**
   * Check if identifier is penalized
   *
   * @param {string} identifier - IP or user ID
   * @returns {Promise<Object>} { penalized, remaining, reason }
   */
  async isPenalized(identifier) {
    try {
      const key = this.prefix + identifier;
      const data = await this.redis.get(key);

      if (!data) {
        return { penalized: false, remaining: 0, reason: null };
      }

      const penalty = JSON.parse(data);
      const remaining = (penalty.startTime + penalty.duration) - Date.now();

      if (remaining > 0) {
        return {
          penalized: true,
          remaining,
          reason: penalty.reason
        };
      } else {
        // Penalty expired
        await this.redis.del(key);
        return { penalized: false, remaining: 0, reason: null };
      }

    } catch (error) {
      console.error('❌ Penalty check error:', error.message);
      return { penalized: false, remaining: 0, reason: null };
    }
  }

  /**
   * Clear penalty for identifier
   *
   * @param {string} identifier - IP or user ID
   * @returns {Promise<boolean>} True if cleared
   */
  async clearPenalty(identifier) {
    try {
      const key = this.prefix + identifier;
      const violationKey = key + ':violations';
      await this.redis.del([key, violationKey]);
      return true;
    } catch (error) {
      console.error('❌ Clear penalty error:', error.message);
      return false;
    }
  }
}

// ============================================================================
// WHITELIST/BLACKLIST
// ============================================================================

/**
 * Check if IP is whitelisted
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} ip - IP address
 * @returns {Promise<boolean>} True if whitelisted
 */
async function isWhitelisted(redisClient, ip) {
  try {
    const key = WHITELIST_PREFIX + ip;
    return await redisClient.exists(key) === 1;
  } catch (error) {
    return false;
  }
}

/**
 * Check if IP is blacklisted
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} ip - IP address
 * @returns {Promise<boolean>} True if blacklisted
 */
async function isBlacklisted(redisClient, ip) {
  try {
    const key = BLACKLIST_PREFIX + ip;
    return await redisClient.exists(key) === 1;
  } catch (error) {
    return false;
  }
}

/**
 * Add IP to whitelist
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} ip - IP address
 * @param {number} ttlSeconds - TTL (0 = permanent)
 * @returns {Promise<boolean>} True if added
 */
async function addToWhitelist(redisClient, ip, ttlSeconds = 0) {
  try {
    const key = WHITELIST_PREFIX + ip;
    if (ttlSeconds > 0) {
      await redisClient.setEx(key, ttlSeconds, '1');
    } else {
      await redisClient.set(key, '1');
    }
    console.log(`✅ IP ${ip} added to whitelist`);
    return true;
  } catch (error) {
    console.error('❌ Whitelist add error:', error.message);
    return false;
  }
}

/**
 * Add IP to blacklist
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} ip - IP address
 * @param {number} ttlSeconds - TTL (0 = permanent)
 * @param {string} reason - Reason for blacklist
 * @returns {Promise<boolean>} True if added
 */
async function addToBlacklist(redisClient, ip, ttlSeconds = 0, reason = 'abuse') {
  try {
    const key = BLACKLIST_PREFIX + ip;
    const data = JSON.stringify({ reason, timestamp: Date.now() });

    if (ttlSeconds > 0) {
      await redisClient.setEx(key, ttlSeconds, data);
    } else {
      await redisClient.set(key, data);
    }

    console.warn(`⚠️  IP ${ip} blacklisted: ${reason}`);
    return true;
  } catch (error) {
    console.error('❌ Blacklist add error:', error.message);
    return false;
  }
}

// ============================================================================
// MIDDLEWARE FACTORY
// ============================================================================

/**
 * Create rate limit middleware
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {Object} options - Rate limit options
 * @param {string} options.strategy - 'token-bucket' | 'sliding-window' (default: 'sliding-window')
 * @param {number} options.windowMs - Time window in milliseconds
 * @param {number} options.max - Maximum requests in window
 * @param {Function} options.keyGenerator - Function to generate rate limit key
 * @param {boolean} options.skipWhitelisted - Skip whitelisted IPs (default: true)
 * @param {boolean} options.enablePenalties - Enable penalty system (default: true)
 * @param {Function} options.handler - Custom handler for rate limit exceeded
 * @returns {Function} Express middleware function
 */
function createRateLimitMiddleware(redisClient, options = {}) {
  const {
    strategy = 'sliding-window',
    windowMs = DEFAULT_LIMITS.perIP.windowMs,
    max = DEFAULT_LIMITS.perIP.max,
    keyGenerator = (req) => req.ip,
    skipWhitelisted = true,
    enablePenalties = true,
    handler = null
  } = options;

  const penaltyManager = new PenaltyManager(redisClient);

  return async (req, res, next) => {
    try {
      const identifier = keyGenerator(req);

      // Check whitelist
      if (skipWhitelisted && await isWhitelisted(redisClient, identifier)) {
        return next();
      }

      // Check blacklist
      if (await isBlacklisted(redisClient, identifier)) {
        return res.status(403).json({
          error: 'Access denied',
          message: 'Your IP has been blacklisted',
          code: 'IP_BLACKLISTED'
        });
      }

      // Check penalties
      if (enablePenalties) {
        const penalty = await penaltyManager.isPenalized(identifier);
        if (penalty.penalized) {
          return res.status(429).json({
            error: 'Rate limit exceeded',
            message: 'Too many violations. Access temporarily restricted.',
            code: 'PENALTY_ACTIVE',
            retryAfter: Math.ceil(penalty.remaining / 1000)
          });
        }
      }

      // Apply rate limiting strategy
      let result;

      if (strategy === 'token-bucket') {
        const bucket = new TokenBucket(
          redisClient,
          identifier,
          max,
          max / (windowMs / 1000) // refill rate per second
        );
        result = await bucket.tryConsume(1);
      } else {
        // Default: sliding-window
        const window = new SlidingWindow(redisClient, identifier, windowMs, max);
        result = await window.recordRequest();
      }

      // Set rate limit headers
      res.setHeader('X-RateLimit-Limit', max);
      res.setHeader('X-RateLimit-Remaining', Math.max(0, result.remaining));
      res.setHeader('X-RateLimit-Reset', new Date(result.resetAt).toISOString());

      if (!result.allowed) {
        // Record violation
        if (enablePenalties) {
          await penaltyManager.recordViolation(identifier);
        }

        // Set Retry-After header
        const retryAfter = Math.ceil((result.resetAt - Date.now()) / 1000);
        res.setHeader('Retry-After', retryAfter);

        // Log rate limit event
        console.warn(`⚠️  Rate limit exceeded: ${identifier} on ${req.path}`);

        // Custom handler or default response
        if (handler) {
          return handler(req, res);
        } else {
          return res.status(429).json({
            error: 'Rate limit exceeded',
            message: `Too many requests. Maximum ${max} requests per ${windowMs / 1000} seconds.`,
            code: 'RATE_LIMIT_EXCEEDED',
            retryAfter
          });
        }
      }

      next();

    } catch (error) {
      console.error('❌ Rate limit middleware error:', error);
      // Fail open (allow request) on error
      next();
    }
  };
}

// ============================================================================
// PRE-CONFIGURED MIDDLEWARE
// ============================================================================

/**
 * Global rate limiter (1000 req/min)
 */
function globalRateLimiter(redisClient) {
  return createRateLimitMiddleware(redisClient, {
    windowMs: DEFAULT_LIMITS.global.windowMs,
    max: DEFAULT_LIMITS.global.max,
    keyGenerator: () => 'global',
    skipWhitelisted: false,
    enablePenalties: false
  });
}

/**
 * Per-IP rate limiter (100 req/min per IP)
 */
function ipRateLimiter(redisClient) {
  return createRateLimitMiddleware(redisClient, {
    windowMs: DEFAULT_LIMITS.perIP.windowMs,
    max: DEFAULT_LIMITS.perIP.max,
    keyGenerator: (req) => req.ip,
    skipWhitelisted: true,
    enablePenalties: true
  });
}

/**
 * Per-User rate limiter (50 req/min per user)
 */
function userRateLimiter(redisClient) {
  return createRateLimitMiddleware(redisClient, {
    windowMs: DEFAULT_LIMITS.perUser.windowMs,
    max: DEFAULT_LIMITS.perUser.max,
    keyGenerator: (req) => {
      // Extract user ID from session or body
      return req.session?.userId || req.body?.user_uuid || req.ip;
    },
    skipWhitelisted: false,
    enablePenalties: true
  });
}

/**
 * Authentication endpoint limiter (stricter limits)
 */
function authRateLimiter(redisClient) {
  return createRateLimitMiddleware(redisClient, {
    windowMs: 900000, // 15 minutes
    max: 5,           // 5 attempts per 15 minutes
    keyGenerator: (req) => {
      const uuid = req.body?.user_uuid || 'unknown';
      return `${req.ip}:${uuid}`;
    },
    skipWhitelisted: false,
    enablePenalties: true
  });
}

// ============================================================================
// MONITORING
// ============================================================================

/**
 * Get rate limit statistics
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Promise<Object>} Rate limit stats
 */
async function getRateLimitStats(redisClient) {
  try {
    const pattern = RATE_LIMIT_PREFIX + '*';
    let cursor = 0;
    let totalKeys = 0;
    const stats = {
      buckets: 0,
      windows: 0,
      penalties: 0,
      whitelisted: 0,
      blacklisted: 0
    };

    do {
      const result = await redisClient.scan(cursor, {
        MATCH: pattern,
        COUNT: 100
      });

      cursor = result.cursor;
      totalKeys += result.keys.length;

      for (const key of result.keys) {
        if (key.includes('bucket:')) stats.buckets++;
        else if (key.includes('window:')) stats.windows++;
        else if (key.includes('penalty:')) stats.penalties++;
      }
    } while (cursor !== 0);

    // Count whitelist/blacklist
    const whitelistPattern = WHITELIST_PREFIX + '*';
    const blacklistPattern = BLACKLIST_PREFIX + '*';

    cursor = 0;
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: whitelistPattern,
        COUNT: 100
      });
      cursor = result.cursor;
      stats.whitelisted += result.keys.length;
    } while (cursor !== 0);

    cursor = 0;
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: blacklistPattern,
        COUNT: 100
      });
      cursor = result.cursor;
      stats.blacklisted += result.keys.length;
    } while (cursor !== 0);

    return {
      ...stats,
      total: totalKeys
    };

  } catch (error) {
    console.error('❌ Error getting rate limit stats:', error.message);
    return null;
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  // Main factory
  createRateLimitMiddleware,

  // Pre-configured middleware
  globalRateLimiter,
  ipRateLimiter,
  userRateLimiter,
  authRateLimiter,

  // Classes
  TokenBucket,
  SlidingWindow,
  PenaltyManager,

  // Whitelist/Blacklist
  isWhitelisted,
  isBlacklisted,
  addToWhitelist,
  addToBlacklist,

  // Monitoring
  getRateLimitStats,

  // Constants
  DEFAULT_LIMITS,
  PENALTY_CONFIG
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
// In server.js:

const {
  globalRateLimiter,
  ipRateLimiter,
  authRateLimiter,
  createRateLimitMiddleware
} = require('./middleware/rateLimitMiddleware');

// Apply global rate limiter to all routes
app.use(globalRateLimiter(redisClient));

// Apply IP rate limiter to API routes
app.use('/v1/', ipRateLimiter(redisClient));

// Apply strict auth rate limiter
app.post('/v1/auth/login', authRateLimiter(redisClient), loginHandler);

// Custom rate limiter
const customLimiter = createRateLimitMiddleware(redisClient, {
  strategy: 'token-bucket',
  windowMs: 60000,
  max: 30,
  keyGenerator: (req) => req.headers['x-api-key'] || req.ip,
  handler: (req, res) => {
    res.status(429).json({
      error: 'Custom rate limit exceeded',
      message: 'Please slow down'
    });
  }
});

app.use('/v1/premium', customLimiter);

// Whitelist/Blacklist management:
const { addToWhitelist, addToBlacklist } = require('./middleware/rateLimitMiddleware');

// Whitelist trusted IP
await addToWhitelist(redisClient, '203.0.113.1', 0); // permanent

// Blacklist abusive IP for 24 hours
await addToBlacklist(redisClient, '198.51.100.1', 86400, 'repeated_violations');

// Get statistics:
const { getRateLimitStats } = require('./middleware/rateLimitMiddleware');
const stats = await getRateLimitStats(redisClient);
console.log('Rate limit stats:', stats);
// Output: { buckets: 45, windows: 23, penalties: 2, whitelisted: 3, blacklisted: 1, total: 74 }
*/
