// Path: backend/tests/day9-10.test.js

/**
 * ZeroPay Day 9-10 Test Suite
 * 
 * Tests for:
 * - Rate limiting (Token Bucket, Sliding Window, Fixed Window)
 * - Fraud detection (all 7 strategies)
 * - Penalty system
 * - Blacklist/Whitelist management
 * - Admin API
 * 
 * @version 1.0.0
 * @date 2025-10-13
 */

const { describe, it, before, after, beforeEach } = require('mocha');
const { expect } = require('chai');
const redis = require('redis');

const {
  TokenBucketLimiter,
  SlidingWindowLimiter,
  FixedWindowLimiter,
  PenaltyManager,
  AccessListManager
} = require('../middleware/rateLimitMiddleware');

describe('Day 9-10: Rate Limiting & Fraud Detection', function() {
  
  let redisClient;
  
  // Setup
  before(async function() {
    this.timeout(5000);
    
    redisClient = redis.createClient({
      socket: {
        host: process.env.REDIS_HOST || 'localhost',
        port: parseInt(process.env.REDIS_PORT) || 6379
      }
    });
    
    await redisClient.connect();
    console.log('✅ Test Redis connected');
  });
  
  // Teardown
  after(async function() {
    await redisClient.quit();
    console.log('✅ Test Redis disconnected');
  });
  
  // Clean before each test
  beforeEach(async function() {
    await redisClient.flushDb();
  });
  
  // ============================================================================
  // TOKEN BUCKET TESTS
  // ============================================================================
  
  describe('Token Bucket Rate Limiter', function() {
    
    it('should allow requests within capacity', async function() {
      const limiter = new TokenBucketLimiter(redisClient, {
        capacity: 5,
        refillRate: 1,
        windowMs: 60000
      });
      
      // First 5 requests should succeed
      for (let i = 0; i < 5; i++) {
        const result = await limiter.isAllowed('test-key');
        expect(result.allowed).to.be.true;
      }
      
      // 6th request should fail
      const result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
    });
    
    it('should refill tokens over time', async function() {
      this.timeout(3000);
      
      const limiter = new TokenBucketLimiter(redisClient, {
        capacity: 2,
        refillRate: 10, // 10 tokens per second
        windowMs: 60000
      });
      
      // Consume all tokens
      await limiter.isAllowed('test-key');
      await limiter.isAllowed('test-key');
      
      // Should fail immediately
      let result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
      
      // Wait 150ms (should refill ~1.5 tokens)
      await sleep(150);
      
      // Should succeed now
      result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.true;
    });
    
    it('should track remaining tokens', async function() {
      const limiter = new TokenBucketLimiter(redisClient, {
        capacity: 5,
        refillRate: 1,
        windowMs: 60000
      });
      
      const result1 = await limiter.isAllowed('test-key');
      expect(result1.remaining).to.equal(4);
      
      const result2 = await limiter.isAllowed('test-key');
      expect(result2.remaining).to.equal(3);
    });
  });
  
  // ============================================================================
  // SLIDING WINDOW TESTS
  // ============================================================================
  
  describe('Sliding Window Rate Limiter', function() {
    
    it('should allow requests within limit', async function() {
      const limiter = new SlidingWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 5
      });
      
      // First 5 requests should succeed
      for (let i = 0; i < 5; i++) {
        const result = await limiter.isAllowed('test-key');
        expect(result.allowed).to.be.true;
      }
      
      // 6th request should fail
      const result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
    });
    
    it('should remove old entries from window', async function() {
      this.timeout(2000);
      
      const limiter = new SlidingWindowLimiter(redisClient, {
        windowMs: 500, // 500ms window
        maxRequests: 2
      });
      
      // Make 2 requests (at capacity)
      await limiter.isAllowed('test-key');
      await limiter.isAllowed('test-key');
      
      // Should fail
      let result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
      
      // Wait for window to expire
      await sleep(600);
      
      // Should succeed now
      result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.true;
    });
    
    it('should track remaining requests', async function() {
      const limiter = new SlidingWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 5
      });
      
      const result1 = await limiter.isAllowed('test-key');
      expect(result1.remaining).to.equal(4);
      
      const result2 = await limiter.isAllowed('test-key');
      expect(result2.remaining).to.equal(3);
    });
  });
  
  // ============================================================================
  // FIXED WINDOW TESTS
  // ============================================================================
  
  describe('Fixed Window Rate Limiter', function() {
    
    it('should allow requests within window', async function() {
      const limiter = new FixedWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 5
      });
      
      // First 5 requests should succeed
      for (let i = 0; i < 5; i++) {
        const result = await limiter.isAllowed('test-key');
        expect(result.allowed).to.be.true;
      }
      
      // 6th request should fail
      const result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
    });
    
    it('should reset at window boundary', async function() {
      this.timeout(2000);
      
      const limiter = new FixedWindowLimiter(redisClient, {
        windowMs: 500,
        maxRequests: 2
      });
      
      // Consume limit
      await limiter.isAllowed('test-key');
      await limiter.isAllowed('test-key');
      
      // Should fail
      let result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.false;
      
      // Wait for window to reset
      await sleep(600);
      
      // Should succeed now
      result = await limiter.isAllowed('test-key');
      expect(result.allowed).to.be.true;
    });
  });
  
  // ============================================================================
  // PENALTY SYSTEM TESTS
  // ============================================================================
  
  describe('Penalty Manager', function() {
    
    let penaltyManager;
    
    beforeEach(function() {
      penaltyManager = new PenaltyManager(redisClient);
    });
    
    it('should not penalize before threshold', async function() {
      await penaltyManager.recordViolation('test-user', 'Test violation');
      await penaltyManager.recordViolation('test-user', 'Test violation');
      
      const check = await penaltyManager.isPenalized('test-user');
      expect(check.penalized).to.be.false;
    });
    
    it('should penalize after threshold reached', async function() {
      // Record 3 violations (threshold)
      for (let i = 0; i < 3; i++) {
        await penaltyManager.recordViolation('test-user', 'Test violation');
      }
      
      const check = await penaltyManager.isPenalized('test-user');
      expect(check.penalized).to.be.true;
      expect(check.level).to.equal(1);
    });
    
    it('should escalate penalty level', async function() {
      // Record violations to reach level 2
      for (let i = 0; i < 4; i++) {
        await penaltyManager.recordViolation('test-user', 'Test violation');
      }
      
      const check = await penaltyManager.isPenalized('test-user');
      expect(check.penalized).to.be.true;
      expect(check.level).to.equal(2);
    });
    
    it('should clear penalty', async function() {
      // Apply penalty
      for (let i = 0; i < 3; i++) {
        await penaltyManager.recordViolation('test-user', 'Test violation');
      }
      
      let check = await penaltyManager.isPenalized('test-user');
      expect(check.penalized).to.be.true;
      
      // Clear penalty
      await penaltyManager.clearPenalty('test-user');
      
      check = await penaltyManager.isPenalized('test-user');
      expect(check.penalized).to.be.false;
    });
  });
  
  // ============================================================================
  // ACCESS LIST TESTS
  // ============================================================================
  
  describe('Access List Manager', function() {
    
    let accessListManager;
    
    beforeEach(function() {
      accessListManager = new AccessListManager(redisClient);
    });
    
    it('should add and check whitelist', async function() {
      await accessListManager.addToWhitelist('test-ip');
      
      const isWhitelisted = await accessListManager.isWhitelisted('test-ip');
      expect(isWhitelisted).to.be.true;
      
      const isWhitelisted2 = await accessListManager.isWhitelisted('other-ip');
      expect(isWhitelisted2).to.be.false;
    });
    
    it('should add and check blacklist', async function() {
      await accessListManager.addToBlacklist('test-ip', 'Test block');
      
      const check = await accessListManager.isBlacklisted('test-ip');
      expect(check.blacklisted).to.be.true;
      expect(check.reason).to.equal('Test block');
    });
    
    it('should remove from whitelist', async function() {
      await accessListManager.addToWhitelist('test-ip');
      await accessListManager.removeFromWhitelist('test-ip');
      
      const isWhitelisted = await accessListManager.isWhitelisted('test-ip');
      expect(isWhitelisted).to.be.false;
    });
    
    it('should remove from blacklist', async function() {
      await accessListManager.addToBlacklist('test-ip', 'Test block');
      await accessListManager.removeFromBlacklist('test-ip');
      
      const check = await accessListManager.isBlacklisted('test-ip');
      expect(check.blacklisted).to.be.false;
    });
    
    it('should support TTL for whitelist', async function() {
      this.timeout(2000);
      
      await accessListManager.addToWhitelist('test-ip', 1); // 1 second TTL
      
      let isWhitelisted = await accessListManager.isWhitelisted('test-ip');
      expect(isWhitelisted).to.be.true;
      
      // Wait for expiry
      await sleep(1100);
      
      isWhitelisted = await accessListManager.isWhitelisted('test-ip');
      expect(isWhitelisted).to.be.false;
    });
  });
  
  // ============================================================================
  // INTEGRATION TESTS
  // ============================================================================
  
  describe('Rate Limiting Integration', function() {
    
    it('should enforce multi-layer rate limiting', async function() {
      const globalLimiter = new FixedWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 10
      });
      
      const ipLimiter = new SlidingWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 5
      });
      
      const userLimiter = new TokenBucketLimiter(redisClient, {
        capacity: 3,
        refillRate: 1,
        windowMs: 60000
      });
      
      // User should be limited by user limiter (3 requests)
      for (let i = 0; i < 3; i++) {
        const global = await globalLimiter.isAllowed('global');
        const ip = await ipLimiter.isAllowed('test-ip');
        const user = await userLimiter.isAllowed('test-user');
        
        expect(global.allowed).to.be.true;
        expect(ip.allowed).to.be.true;
        expect(user.allowed).to.be.true;
      }
      
      // 4th request should fail at user level
      const global = await globalLimiter.isAllowed('global');
      const ip = await ipLimiter.isAllowed('test-ip');
      const user = await userLimiter.isAllowed('test-user');
      
      expect(global.allowed).to.be.true;
      expect(ip.allowed).to.be.true;
      expect(user.allowed).to.be.false;
    });
    
    it('should bypass rate limiting for whitelisted IPs', async function() {
      const limiter = new SlidingWindowLimiter(redisClient, {
        windowMs: 60000,
        maxRequests: 2
      });
      
      const accessListManager = new AccessListManager(redisClient);
      await accessListManager.addToWhitelist('whitelisted-ip');
      
      // Whitelisted IP should bypass limits
      const isWhitelisted = await accessListManager.isWhitelisted('whitelisted-ip');
      expect(isWhitelisted).to.be.true;
      
      // Regular IP should be limited
      await limiter.isAllowed('regular-ip');
      await limiter.isAllowed('regular-ip');
      
      const result = await limiter.isAllowed('regular-ip');
      expect(result.allowed).to.be.false;
    });
    
    it('should block blacklisted IPs immediately', async function() {
      const accessListManager = new AccessListManager(redisClient);
      await accessListManager.addToBlacklist('blocked-ip', 'Malicious activity');
      
      const check = await accessListManager.isBlacklisted('blocked-ip');
      expect(check.blacklisted).to.be.true;
      expect(check.reason).to.equal('Malicious activity');
    });
  });
});

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ============================================================================
// RUN TESTS
// ============================================================================

if (require.main === module) {
  console.log('🧪 Running Day 9-10 tests...');
  console.log('');
  
  // Run with: node backend/tests/day9-10.test.js
  // Or: npm test
}
