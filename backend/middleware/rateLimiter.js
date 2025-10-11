/**
 * ZeroPay Enhanced Rate Limiter
 *
 * Multi-layer rate limiting for DoS/DDoS protection:
 * - Layer 1: Per-IP rate limiting (general protection)
 * - Layer 2: Per-User/UUID rate limiting (abuse prevention)
 * - Layer 3: Per-Endpoint rate limiting (targeted protection)
 *
 * Features:
 * - Redis-backed (distributed, persistent across restarts)
 * - Sliding window algorithm (more accurate than fixed window)
 * - Custom rate limits per endpoint
 * - Configurable via environment variables
 * - Detailed logging for security monitoring
 *
 * @version 1.0.0
 * @date 2025-10-11
 */

const rateLimit = require('express-rate-limit');
const RedisStore = require('rate-limit-redis');

// ============================================================================
// CONSTANTS
// ============================================================================

const RATE_LIMIT_PREFIX = 'ratelimit:';

// Default rate limits (overrideable via env vars)
const DEFAULT_RATE_LIMITS = {
  general: {
    max: parseInt(process.env.RATE_LIMIT_MAX) || 100,
    windowMs: (parseInt(process.env.RATE_LIMIT_WINDOW_MINUTES) || 15) * 60 * 1000
  },
  perUser: {
    max: parseInt(process.env.PER_USER_RATE_LIMIT_MAX) || 3,
    windowMs: 60 * 60 * 1000 // 1 hour
  },
  nonce: {
    max: 10,
    windowMs: 60 * 1000 // 1 minute
  },
  enrollment: {
    max: 5,
    windowMs: 60 * 1000 // 1 minute
  },
  retrieval: {
    max: 20,
    windowMs: 60 * 1000 // 1 minute
  },
  deletion: {
    max: 3,
    windowMs: 60 * 60 * 1000 // 1 hour
  }
};

// ============================================================================
// GENERAL RATE LIMITER (Per-IP)
// ============================================================================

/**
 * Create general rate limiter for all endpoints
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware
 */
function createGeneralRateLimiter(redisClient) {
  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + 'general:',
    }),
    windowMs: DEFAULT_RATE_LIMITS.general.windowMs,
    max: DEFAULT_RATE_LIMITS.general.max,
    message: {
      error: 'Too many requests',
      message: `Rate limit exceeded. Please try again later.`,
      retryAfter: Math.ceil(DEFAULT_RATE_LIMITS.general.windowMs / 1000),
      code: 'RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true, // Return rate limit info in `RateLimit-*` headers
    legacyHeaders: false, // Disable `X-RateLimit-*` headers
    keyGenerator: (req) => {
      // Use IP address as key
      return req.ip || req.socket.remoteAddress;
    },
    skip: (req) => {
      // Skip rate limiting for health checks
      return req.path === '/health';
    },
    onLimitReached: (req, res, options) => {
      console.warn('⚠️  Rate limit reached:', {
        ip: req.ip,
        endpoint: req.path,
        timestamp: new Date().toISOString()
      });
    }
  });
}

// ============================================================================
// PER-USER RATE LIMITER
// ============================================================================

/**
 * Rate limiter by user UUID (prevents enrollment spam)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {Object} options - Custom options
 * @returns {Function} Express middleware
 */
function createPerUserRateLimiter(redisClient, options = {}) {
  const config = {
    max: options.max || DEFAULT_RATE_LIMITS.perUser.max,
    windowMs: options.windowMs || DEFAULT_RATE_LIMITS.perUser.windowMs,
    prefix: options.prefix || 'user:'
  };

  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + config.prefix,
    }),
    windowMs: config.windowMs,
    max: config.max,
    message: {
      error: 'Too many enrollments',
      message: `You can only enroll ${config.max} time(s) per hour. Please try again later.`,
      retryAfter: Math.ceil(config.windowMs / 1000),
      code: 'USER_RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      // Use user UUID from request body or params
      const uuid = req.body?.user_uuid || req.params?.uuid;

      if (!uuid) {
        // Fallback to IP if no UUID (shouldn't happen if validation is before this)
        console.warn('⚠️  No UUID found for per-user rate limiting, using IP');
        return req.ip;
      }

      return uuid;
    },
    skip: (req) => {
      // Skip if no UUID available (let validation middleware catch it)
      return !req.body?.user_uuid && !req.params?.uuid;
    },
    onLimitReached: (req, res, options) => {
      const uuid = req.body?.user_uuid || req.params?.uuid;
      console.warn('⚠️  Per-user rate limit reached:', {
        uuid,
        ip: req.ip,
        endpoint: req.path,
        timestamp: new Date().toISOString()
      });
    }
  });
}

// ============================================================================
// ENDPOINT-SPECIFIC RATE LIMITERS
// ============================================================================

/**
 * Rate limiter for nonce endpoint
 * Prevents nonce generation spam
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware
 */
function createNonceRateLimiter(redisClient) {
  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + 'nonce:',
    }),
    windowMs: DEFAULT_RATE_LIMITS.nonce.windowMs,
    max: DEFAULT_RATE_LIMITS.nonce.max,
    message: {
      error: 'Too many nonce requests',
      message: 'Nonce generation rate limit exceeded. Please try again in a minute.',
      retryAfter: 60,
      code: 'NONCE_RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => req.ip
  });
}

/**
 * Rate limiter for enrollment endpoint
 * Prevents enrollment spam per IP
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware
 */
function createEnrollmentRateLimiter(redisClient) {
  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + 'enrollment:',
    }),
    windowMs: DEFAULT_RATE_LIMITS.enrollment.windowMs,
    max: DEFAULT_RATE_LIMITS.enrollment.max,
    message: {
      error: 'Too many enrollment requests',
      message: `Maximum ${DEFAULT_RATE_LIMITS.enrollment.max} enrollments per minute. Please try again later.`,
      retryAfter: 60,
      code: 'ENROLLMENT_RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => req.ip
  });
}

/**
 * Rate limiter for retrieval endpoint
 * Prevents credential stuffing / brute force
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware
 */
function createRetrievalRateLimiter(redisClient) {
  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + 'retrieval:',
    }),
    windowMs: DEFAULT_RATE_LIMITS.retrieval.windowMs,
    max: DEFAULT_RATE_LIMITS.retrieval.max,
    message: {
      error: 'Too many retrieval requests',
      message: `Maximum ${DEFAULT_RATE_LIMITS.retrieval.max} retrievals per minute.`,
      retryAfter: 60,
      code: 'RETRIEVAL_RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      // Combine IP + UUID for more granular limiting
      const uuid = req.params?.uuid || 'unknown';
      return `${req.ip}:${uuid}`;
    }
  });
}

/**
 * Rate limiter for deletion endpoint
 * Prevents deletion spam (GDPR right to erasure)
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Function} Express middleware
 */
function createDeletionRateLimiter(redisClient) {
  return rateLimit({
    store: new RedisStore({
      client: redisClient,
      prefix: RATE_LIMIT_PREFIX + 'deletion:',
    }),
    windowMs: DEFAULT_RATE_LIMITS.deletion.windowMs,
    max: DEFAULT_RATE_LIMITS.deletion.max,
    message: {
      error: 'Too many deletion requests',
      message: `Maximum ${DEFAULT_RATE_LIMITS.deletion.max} deletions per hour.`,
      retryAfter: 3600,
      code: 'DELETION_RATE_LIMIT_EXCEEDED'
    },
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => {
      const uuid = req.params?.uuid || req.body?.user_uuid || 'unknown';
      return `${req.ip}:${uuid}`;
    }
  });
}

// ============================================================================
// MONITORING
// ============================================================================

/**
 * Get rate limit stats for monitoring
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Promise<Object>} Rate limit statistics
 */
async function getRateLimitStats(redisClient) {
  try {
    const pattern = RATE_LIMIT_PREFIX + '*';
    const keys = [];

    // Scan all rate limit keys
    let cursor = 0;
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: pattern,
        COUNT: 100
      });
      cursor = result.cursor;
      keys.push(...result.keys);
    } while (cursor !== 0);

    // Group by type
    const stats = {
      total_keys: keys.length,
      by_type: {
        general: 0,
        user: 0,
        nonce: 0,
        enrollment: 0,
        retrieval: 0,
        deletion: 0
      }
    };

    keys.forEach(key => {
      if (key.includes(':general:')) stats.by_type.general++;
      else if (key.includes(':user:')) stats.by_type.user++;
      else if (key.includes(':nonce:')) stats.by_type.nonce++;
      else if (key.includes(':enrollment:')) stats.by_type.enrollment++;
      else if (key.includes(':retrieval:')) stats.by_type.retrieval++;
      else if (key.includes(':deletion:')) stats.by_type.deletion++;
    });

    return stats;

  } catch (error) {
    console.error('Error getting rate limit stats:', error);
    return { error: error.message };
  }
}

/**
 * Clear all rate limits (for testing/emergency)
 *
 * ⚠️  WARNING: Use with extreme caution!
 * This will remove all rate limit tracking, potentially allowing DoS attacks.
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @returns {Promise<number>} Number of keys deleted
 */
async function clearAllRateLimits(redisClient) {
  console.warn('⚠️  Clearing ALL rate limits - this should only be done in development!');

  try {
    const pattern = RATE_LIMIT_PREFIX + '*';
    const keys = [];

    let cursor = 0;
    do {
      const result = await redisClient.scan(cursor, {
        MATCH: pattern,
        COUNT: 100
      });
      cursor = result.cursor;
      keys.push(...result.keys);
    } while (cursor !== 0);

    if (keys.length === 0) {
      return 0;
    }

    // Delete all rate limit keys
    await redisClient.del(keys);

    console.log(`✅ Cleared ${keys.length} rate limit entries`);
    return keys.length;

  } catch (error) {
    console.error('Error clearing rate limits:', error);
    throw error;
  }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  createGeneralRateLimiter,
  createPerUserRateLimiter,
  createNonceRateLimiter,
  createEnrollmentRateLimiter,
  createRetrievalRateLimiter,
  createDeletionRateLimiter,
  getRateLimitStats,
  clearAllRateLimits,
  DEFAULT_RATE_LIMITS
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
// In server.js:

const {
  createGeneralRateLimiter,
  createPerUserRateLimiter,
  createNonceRateLimiter,
  createEnrollmentRateLimiter,
  createRetrievalRateLimiter
} = require('./middleware/rateLimiter');

// Apply general rate limiter to all endpoints
app.use(createGeneralRateLimiter(redisClient));

// Nonce endpoint (limit nonce generation)
app.get('/v1/auth/nonce',
  createNonceRateLimiter(redisClient),
  nonceHandler
);

// Enrollment endpoint (IP + per-user limiting)
app.post('/v1/enrollment/store',
  createEnrollmentRateLimiter(redisClient),    // Per-IP limit
  createPerUserRateLimiter(redisClient),       // Per-UUID limit
  enrollmentHandler
);

// Retrieval endpoint (prevent brute force)
app.get('/v1/enrollment/retrieve/:uuid',
  createRetrievalRateLimiter(redisClient),
  retrievalHandler
);

// Rate limit stats endpoint (for monitoring)
app.get('/admin/rate-limits/stats', async (req, res) => {
  const stats = await getRateLimitStats(redisClient);
  res.json(stats);
});
*/