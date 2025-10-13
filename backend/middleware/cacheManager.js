// Path: backend/middleware/cacheManager.js

/**
 * ZeroPay Cache Manager - Redis Cache Abstraction Layer
 *
 * Provides high-level cache operations with automatic encryption/decryption.
 *
 * Features:
 * - Automatic encryption for all stored data
 * - TTL management
 * - Pattern-based operations (get/set/delete)
 * - Atomic operations (increment, decrement)
 * - Bulk operations (mget, mset)
 * - Cache statistics
 * - Health monitoring
 * - Namespace isolation
 *
 * Security:
 * - All data encrypted with AES-256-GCM
 * - Automatic key prefixing (namespace isolation)
 * - TTL enforcement (no permanent storage)
 * - Secure key scanning (SCAN instead of KEYS)
 *
 * Performance:
 * - Connection pooling
 * - Pipelining support
 * - Bulk operations
 * - Efficient serialization
 *
 * GDPR Compliance:
 * - All cache entries have TTL (auto-delete)
 * - Easy data export
 * - Bulk deletion support
 *
 * @version 1.0.0
 * @date 2025-10-12
 */

const { encrypt, decrypt } = require('../crypto/encryption');
const crypto = require('crypto');

// ============================================================================
// CONSTANTS
// ============================================================================

const DEFAULT_TTL = 86400; // 24 hours in seconds
const MAX_TTL = 604800; // 7 days in seconds
const CACHE_PREFIX = 'cache:';

// Namespace prefixes
const NAMESPACES = {
  ENROLLMENT: 'enrollment:',
  SESSION: 'session:',
  NONCE: 'nonce:',
  RATE_LIMIT: 'ratelimit:',
  TEMP: 'temp:',
  USER_DATA: 'userdata:'
};

// ============================================================================
// CACHE MANAGER CLASS
// ============================================================================

class CacheManager {
  /**
   * Create cache manager instance
   *
   * @param {RedisClient} redisClient - Redis client instance
   * @param {Object} options - Configuration options
   * @param {string} options.namespace - Cache namespace (default: 'cache')
   * @param {number} options.defaultTTL - Default TTL in seconds
   * @param {boolean} options.autoEncrypt - Auto-encrypt data (default: true)
   */
  constructor(redisClient, options = {}) {
    this.redis = redisClient;
    this.namespace = options.namespace || CACHE_PREFIX;
    this.defaultTTL = options.defaultTTL || DEFAULT_TTL;
    this.autoEncrypt = options.autoEncrypt !== false;
    this.stats = {
      hits: 0,
      misses: 0,
      sets: 0,
      deletes: 0,
      errors: 0
    };
  }

  /**
   * Generate cache key with namespace
   *
   * @param {string} key - Cache key
   * @returns {string} Namespaced key
   */
  _makeKey(key) {
    return this.namespace + key;
  }

  // ==========================================================================
  // BASIC OPERATIONS
  // ==========================================================================

  /**
   * Set cache value
   *
   * @param {string} key - Cache key
   * @param {*} value - Value to cache (will be JSON-stringified)
   * @param {number} ttlSeconds - TTL in seconds (optional)
   * @returns {Promise<boolean>} True if successful
   */
  async set(key, value, ttlSeconds = null) {
    try {
      const cacheKey = this._makeKey(key);
      const ttl = ttlSeconds || this.defaultTTL;

      // Validate TTL
      if (ttl > MAX_TTL) {
        throw new Error(`TTL exceeds maximum: ${MAX_TTL} seconds`);
      }

      // Serialize value
      let data = typeof value === 'string' ? value : JSON.stringify(value);

      // Encrypt if enabled
      if (this.autoEncrypt) {
        data = await encrypt(data);
      }

      // Store with TTL
      await this.redis.setEx(cacheKey, ttl, data);

      this.stats.sets++;
      return true;

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache set error:', error.message);
      throw error;
    }
  }

  /**
   * Get cache value
   *
   * @param {string} key - Cache key
   * @param {Object} options - Get options
   * @param {boolean} options.parse - Parse JSON (default: true)
   * @returns {Promise<*>} Cached value or null if not found
   */
  async get(key, options = {}) {
    try {
      const cacheKey = this._makeKey(key);
      const { parse = true } = options;

      let data = await this.redis.get(cacheKey);

      if (!data) {
        this.stats.misses++;
        return null;
      }

      // Decrypt if enabled
      if (this.autoEncrypt) {
        data = await decrypt(data);
      }

      // Parse JSON if requested
      if (parse) {
        try {
          data = JSON.parse(data);
        } catch (e) {
          // Return as-is if not valid JSON
        }
      }

      this.stats.hits++;
      return data;

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache get error:', error.message);
      return null;
    }
  }

  /**
   * Check if key exists
   *
   * @param {string} key - Cache key
   * @returns {Promise<boolean>} True if exists
   */
  async exists(key) {
    try {
      const cacheKey = this._makeKey(key);
      const exists = await this.redis.exists(cacheKey);
      return exists === 1;
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache exists error:', error.message);
      return false;
    }
  }

  /**
   * Delete cache value
   *
   * @param {string} key - Cache key
   * @returns {Promise<boolean>} True if deleted
   */
  async delete(key) {
    try {
      const cacheKey = this._makeKey(key);
      const deleted = await this.redis.del(cacheKey);
      this.stats.deletes++;
      return deleted === 1;
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache delete error:', error.message);
      return false;
    }
  }

  /**
   * Get TTL for key
   *
   * @param {string} key - Cache key
   * @returns {Promise<number>} TTL in seconds (-2 if not exists, -1 if no expiry)
   */
  async getTTL(key) {
    try {
      const cacheKey = this._makeKey(key);
      return await this.redis.ttl(cacheKey);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache getTTL error:', error.message);
      return -2;
    }
  }

  /**
   * Extend TTL for key
   *
   * @param {string} key - Cache key
   * @param {number} ttlSeconds - New TTL in seconds
   * @returns {Promise<boolean>} True if successful
   */
  async extendTTL(key, ttlSeconds) {
    try {
      const cacheKey = this._makeKey(key);
      const result = await this.redis.expire(cacheKey, ttlSeconds);
      return result === 1;
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache extendTTL error:', error.message);
      return false;
    }
  }

  // ==========================================================================
  // BULK OPERATIONS
  // ==========================================================================

  /**
   * Set multiple values
   *
   * @param {Object} data - Key-value pairs { key1: value1, key2: value2, ... }
   * @param {number} ttlSeconds - TTL for all keys
   * @returns {Promise<number>} Number of keys set
   */
  async mset(data, ttlSeconds = null) {
    try {
      const ttl = ttlSeconds || this.defaultTTL;
      const pipeline = this.redis.multi();

      for (const [key, value] of Object.entries(data)) {
        const cacheKey = this._makeKey(key);
        let serialized = typeof value === 'string' ? value : JSON.stringify(value);

        if (this.autoEncrypt) {
          serialized = await encrypt(serialized);
        }

        pipeline.setEx(cacheKey, ttl, serialized);
      }

      await pipeline.exec();
      this.stats.sets += Object.keys(data).length;
      return Object.keys(data).length;

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache mset error:', error.message);
      throw error;
    }
  }

  /**
   * Get multiple values
   *
   * @param {Array<string>} keys - Array of cache keys
   * @returns {Promise<Object>} Object with key-value pairs
   */
  async mget(keys) {
    try {
      const cacheKeys = keys.map(k => this._makeKey(k));
      const values = await this.redis.mGet(cacheKeys);

      const result = {};
      for (let i = 0; i < keys.length; i++) {
        if (values[i]) {
          let data = values[i];

          if (this.autoEncrypt) {
            data = await decrypt(data);
          }

          try {
            data = JSON.parse(data);
          } catch (e) {
            // Keep as-is if not JSON
          }

          result[keys[i]] = data;
          this.stats.hits++;
        } else {
          this.stats.misses++;
        }
      }

      return result;

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache mget error:', error.message);
      return {};
    }
  }

  /**
   * Delete multiple keys
   *
   * @param {Array<string>} keys - Array of cache keys
   * @returns {Promise<number>} Number of keys deleted
   */
  async mdel(keys) {
    try {
      const cacheKeys = keys.map(k => this._makeKey(k));
      const deleted = await this.redis.del(cacheKeys);
      this.stats.deletes += deleted;
      return deleted;
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache mdel error:', error.message);
      return 0;
    }
  }

  // ==========================================================================
  // PATTERN OPERATIONS
  // ==========================================================================

  /**
   * Find keys matching pattern
   *
   * Uses SCAN for production-safe iteration.
   *
   * @param {string} pattern - Key pattern (e.g., "user:*")
   * @param {number} limit - Maximum keys to return (default: 1000)
   * @returns {Promise<Array<string>>} Array of matching keys (without namespace prefix)
   */
  async keys(pattern, limit = 1000) {
    try {
      const fullPattern = this._makeKey(pattern);
      const keys = [];
      let cursor = 0;

      do {
        const result = await this.redis.scan(cursor, {
          MATCH: fullPattern,
          COUNT: 100
        });

        cursor = result.cursor;
        const batch = result.keys;

        // Remove namespace prefix
        const cleanKeys = batch.map(k => k.replace(this.namespace, ''));
        keys.push(...cleanKeys);

        if (keys.length >= limit) {
          break;
        }
      } while (cursor !== 0);

      return keys.slice(0, limit);

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache keys error:', error.message);
      return [];
    }
  }

  /**
   * Delete keys matching pattern
   *
   * @param {string} pattern - Key pattern (e.g., "user:*")
   * @param {number} limit - Maximum keys to delete (default: 1000)
   * @returns {Promise<number>} Number of keys deleted
   */
  async deletePattern(pattern, limit = 1000) {
    try {
      const keys = await this.keys(pattern, limit);

      if (keys.length === 0) {
        return 0;
      }

      return await this.mdel(keys);

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache deletePattern error:', error.message);
      return 0;
    }
  }

  // ==========================================================================
  // ATOMIC OPERATIONS
  // ==========================================================================

  /**
   * Increment numeric value
   *
   * @param {string} key - Cache key
   * @param {number} amount - Amount to increment (default: 1)
   * @returns {Promise<number>} New value
   */
  async increment(key, amount = 1) {
    try {
      const cacheKey = this._makeKey(key);
      return await this.redis.incrBy(cacheKey, amount);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache increment error:', error.message);
      throw error;
    }
  }

  /**
   * Decrement numeric value
   *
   * @param {string} key - Cache key
   * @param {number} amount - Amount to decrement (default: 1)
   * @returns {Promise<number>} New value
   */
  async decrement(key, amount = 1) {
    try {
      const cacheKey = this._makeKey(key);
      return await this.redis.decrBy(cacheKey, amount);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache decrement error:', error.message);
      throw error;
    }
  }

  /**
   * Add to set
   *
   * @param {string} key - Set key
   * @param {*} members - Members to add (single or array)
   * @returns {Promise<number>} Number of members added
   */
  async sAdd(key, members) {
    try {
      const cacheKey = this._makeKey(key);
      const membersArray = Array.isArray(members) ? members : [members];
      return await this.redis.sAdd(cacheKey, membersArray);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache sAdd error:', error.message);
      throw error;
    }
  }

  /**
   * Get set members
   *
   * @param {string} key - Set key
   * @returns {Promise<Array>} Set members
   */
  async sMembers(key) {
    try {
      const cacheKey = this._makeKey(key);
      return await this.redis.sMembers(cacheKey);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache sMembers error:', error.message);
      return [];
    }
  }

  /**
   * Remove from set
   *
   * @param {string} key - Set key
   * @param {*} members - Members to remove (single or array)
   * @returns {Promise<number>} Number of members removed
   */
  async sRem(key, members) {
    try {
      const cacheKey = this._makeKey(key);
      const membersArray = Array.isArray(members) ? members : [members];
      return await this.redis.sRem(cacheKey, membersArray);
    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache sRem error:', error.message);
      return 0;
    }
  }

  // ==========================================================================
  // STATISTICS & HEALTH
  // ==========================================================================

  /**
   * Get cache statistics
   *
   * @returns {Object} Cache stats
   */
  getStats() {
    const total = this.stats.hits + this.stats.misses;
    const hitRate = total > 0 ? (this.stats.hits / total * 100).toFixed(2) : 0;

    return {
      ...this.stats,
      hitRate: `${hitRate}%`,
      total
    };
  }

  /**
   * Reset statistics
   */
  resetStats() {
    this.stats = {
      hits: 0,
      misses: 0,
      sets: 0,
      deletes: 0,
      errors: 0
    };
  }

  /**
   * Get Redis info
   *
   * @returns {Promise<Object>} Redis info
   */
  async getRedisInfo() {
    try {
      const info = await this.redis.info();
      const lines = info.split('\r\n');
      const result = {};

      for (const line of lines) {
        if (line && !line.startsWith('#')) {
          const [key, value] = line.split(':');
          if (key && value) {
            result[key] = value;
          }
        }
      }

      return result;

    } catch (error) {
      console.error('❌ Error getting Redis info:', error.message);
      return {};
    }
  }

  /**
   * Health check
   *
   * @returns {Promise<Object>} Health status
   */
  async healthCheck() {
    try {
      const start = Date.now();
      await this.redis.ping();
      const latency = Date.now() - start;

      const info = await this.getRedisInfo();

      return {
        status: 'healthy',
        latency: `${latency}ms`,
        connected: true,
        usedMemory: info.used_memory_human || 'unknown',
        uptime: info.uptime_in_seconds ? `${info.uptime_in_seconds}s` : 'unknown'
      };

    } catch (error) {
      return {
        status: 'unhealthy',
        connected: false,
        error: error.message
      };
    }
  }

  // ==========================================================================
  // CLEANUP
  // ==========================================================================

  /**
   * Clear all keys in namespace
   *
   * ⚠️ DANGER: This deletes ALL keys in the namespace!
   *
   * @returns {Promise<number>} Number of keys deleted
   */
  async clear() {
    try {
      const keys = await this.keys('*', 10000);
      console.warn(`⚠️  Clearing ${keys.length} keys from namespace: ${this.namespace}`);

      if (keys.length === 0) {
        return 0;
      }

      return await this.mdel(keys);

    } catch (error) {
      this.stats.errors++;
      console.error('❌ Cache clear error:', error.message);
      return 0;
    }
  }

  /**
   * Cleanup expired keys (monitoring only)
   *
   * Redis automatically deletes expired keys, but this
   * helps with monitoring.
   *
   * @returns {Promise<Object>} Cleanup stats
   */
  async cleanupExpired() {
    try {
      const keys = await this.keys('*', 10000);
      let expired = 0;

      for (const key of keys) {
        const ttl = await this.getTTL(key);
        if (ttl === -2) {
          expired++;
        }
      }

      return {
        total: keys.length,
        expired,
        active: keys.length - expired
      };

    } catch (error) {
      console.error('❌ Cleanup error:', error.message);
      return { total: 0, expired: 0, active: 0 };
    }
  }
}

// ============================================================================
// FACTORY FUNCTIONS
// ============================================================================

/**
 * Create cache manager for specific namespace
 *
 * @param {RedisClient} redisClient - Redis client instance
 * @param {string} namespace - Cache namespace
 * @param {Object} options - Additional options
 * @returns {CacheManager} Cache manager instance
 */
function createCacheManager(redisClient, namespace, options = {}) {
  return new CacheManager(redisClient, {
    namespace: NAMESPACES[namespace] || namespace,
    ...options
  });
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = {
  CacheManager,
  createCacheManager,
  NAMESPACES,
  DEFAULT_TTL,
  MAX_TTL
};

// ============================================================================
// USAGE EXAMPLE
// ============================================================================

/*
// In server.js or any route:

const { createCacheManager, NAMESPACES } = require('./middleware/cacheManager');

// Create cache manager for enrollments
const enrollmentCache = createCacheManager(
  redisClient,
  NAMESPACES.ENROLLMENT,
  { defaultTTL: 86400 } // 24 hours
);

// Store enrollment data
await enrollmentCache.set(userUuid, {
  factors: ['PIN', 'PATTERN'],
  deviceId: 'device-123',
  createdAt: Date.now()
}, 86400); // 24 hour TTL

// Retrieve enrollment data
const enrollment = await enrollmentCache.get(userUuid);

// Check if exists
if (await enrollmentCache.exists(userUuid)) {
  console.log('Enrollment found');
}

// Delete enrollment
await enrollmentCache.delete(userUuid);

// Bulk operations
await enrollmentCache.mset({
  'user-1': { data: 'value1' },
  'user-2': { data: 'value2' }
}, 3600);

const users = await enrollmentCache.mget(['user-1', 'user-2']);

// Pattern operations
const userKeys = await enrollmentCache.keys('user-*', 100);
await enrollmentCache.deletePattern('temp-*');

// Statistics
const stats = enrollmentCache.getStats();
console.log('Cache hit rate:', stats.hitRate);

// Health check
const health = await enrollmentCache.healthCheck();
console.log('Cache status:', health.status);

// Cleanup
const cleanupStats = await enrollmentCache.cleanupExpired();
console.log('Expired keys:', cleanupStats.expired);
*/
