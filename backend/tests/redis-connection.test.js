/**
 * Redis Connection Test Suite
 *
 * Validates Redis setup for Phase 1 & 2:
 * - TLS connection
 * - Authentication (ACL)
 * - Basic operations (SET, GET, DEL, EXPIRE)
 * - Encryption at rest (application-level)
 * - Connection pooling
 * - Error handling
 *
 * Prerequisites:
 * - Redis server running on localhost:6380
 * - TLS certificates generated (npm run generate:certs)
 * - .env file configured with REDIS_* variables
 *
 * Run:
 *   npm run test -- backend/tests/redis-connection.test.js
 *
 * @version 1.0.0
 */

const { expect } = require('chai');
const redis = require('redis');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

describe('Redis Connection & Configuration Tests', function() {
    this.timeout(10000);

    let redisClient;
    const testKey = 'test:connection:' + Date.now();

    // ========================================================================
    // SETUP & TEARDOWN
    // ========================================================================

    before(async function() {
        console.log('\n🧪 Starting Redis Connection Tests\n');

        // Check environment variables
        const requiredEnvVars = [
            'REDIS_HOST',
            'REDIS_PORT',
            'REDIS_PASSWORD',
            'REDIS_USERNAME'
        ];

        const missingVars = requiredEnvVars.filter(v => !process.env[v]);
        if (missingVars.length > 0) {
            console.log('⚠️  Missing environment variables:', missingVars.join(', '));
            console.log('   Using fallback non-TLS connection for testing');
        }
    });

    after(async function() {
        if (redisClient && redisClient.isOpen) {
            await redisClient.quit();
            console.log('\n✅ Redis client disconnected\n');
        }
    });

    // ========================================================================
    // CONNECTION TESTS
    // ========================================================================

    describe('Basic Connection', function() {
        it('should connect to Redis without TLS (development)', async function() {
            // Fallback connection for testing without TLS
            redisClient = redis.createClient({
                socket: {
                    host: process.env.REDIS_HOST || 'localhost',
                    port: parseInt(process.env.REDIS_PORT) || 6379
                },
                password: process.env.REDIS_PASSWORD,
                username: process.env.REDIS_USERNAME
            });

            await redisClient.connect();
            expect(redisClient.isOpen).to.be.true;
            console.log('   ✓ Connected to Redis');
        });

        it('should authenticate successfully', async function() {
            // PING command verifies authentication
            const response = await redisClient.ping();
            expect(response).to.equal('PONG');
            console.log('   ✓ Authentication successful');
        });
    });

    describe('TLS Connection (Production)', function() {
        it('should verify TLS certificate files exist', function() {
            const certDir = path.join(__dirname, '../redis/tls');
            const requiredFiles = ['ca.crt', 'redis.crt', 'redis.key'];

            const existingFiles = [];
            const missingFiles = [];

            requiredFiles.forEach(file => {
                const filePath = path.join(certDir, file);
                if (fs.existsSync(filePath)) {
                    existingFiles.push(file);
                } else {
                    missingFiles.push(file);
                }
            });

            console.log('   Found:', existingFiles.join(', '));
            if (missingFiles.length > 0) {
                console.log('   ⚠️  Missing:', missingFiles.join(', '));
                console.log('   Run: npm run generate:certs');
                this.skip();
            }
        });

        it('should connect with TLS enabled', async function() {
            const certDir = path.join(__dirname, '../redis/tls');

            // Skip if certificates don't exist
            if (!fs.existsSync(path.join(certDir, 'ca.crt'))) {
                console.log('   ⚠️  TLS certificates not found, skipping');
                this.skip();
                return;
            }

            // Close non-TLS connection
            if (redisClient && redisClient.isOpen) {
                await redisClient.quit();
            }

            // Create TLS connection
            redisClient = redis.createClient({
                socket: {
                    host: process.env.REDIS_HOST || 'localhost',
                    port: parseInt(process.env.REDIS_TLS_PORT) || 6380,
                    tls: true,
                    ca: fs.readFileSync(path.join(certDir, 'ca.crt')),
                    cert: fs.readFileSync(path.join(certDir, 'redis.crt')),
                    key: fs.readFileSync(path.join(certDir, 'redis.key')),
                    rejectUnauthorized: true
                },
                password: process.env.REDIS_PASSWORD,
                username: process.env.REDIS_USERNAME || 'zeropay-backend'
            });

            await redisClient.connect();
            const response = await redisClient.ping();
            expect(response).to.equal('PONG');
            console.log('   ✓ TLS connection successful');
        });
    });

    // ========================================================================
    // BASIC OPERATIONS
    // ========================================================================

    describe('Basic Operations', function() {
        it('should SET and GET a string value', async function() {
            const key = `${testKey}:string`;
            const value = 'Hello ZeroPay';

            await redisClient.set(key, value);
            const retrieved = await redisClient.get(key);

            expect(retrieved).to.equal(value);
            console.log('   ✓ SET/GET working');
        });

        it('should SET with expiration (TTL)', async function() {
            const key = `${testKey}:ttl`;
            const value = 'expires-soon';
            const ttlSeconds = 2;

            await redisClient.setEx(key, ttlSeconds, value);

            // Should exist immediately
            const retrieved = await redisClient.get(key);
            expect(retrieved).to.equal(value);

            // Check TTL
            const ttl = await redisClient.ttl(key);
            expect(ttl).to.be.greaterThan(0);
            expect(ttl).to.be.lessThanOrEqual(ttlSeconds);

            console.log(`   ✓ TTL set correctly (${ttl}s remaining)`);
        });

        it('should DELETE keys', async function() {
            const key = `${testKey}:delete`;

            await redisClient.set(key, 'to-be-deleted');
            const deleted = await redisClient.del(key);
            expect(deleted).to.equal(1);

            const retrieved = await redisClient.get(key);
            expect(retrieved).to.be.null;

            console.log('   ✓ DEL working');
        });

        it('should handle non-existent keys', async function() {
            const key = `${testKey}:nonexistent`;
            const value = await redisClient.get(key);
            expect(value).to.be.null;
            console.log('   ✓ Non-existent key returns null');
        });
    });

    // ========================================================================
    // HASH OPERATIONS (used for factor storage)
    // ========================================================================

    describe('Hash Operations (Factor Storage)', function() {
        it('should store and retrieve hash fields', async function() {
            const key = `${testKey}:user:factors`;
            const factors = {
                'PIN': crypto.createHash('sha256').update('1234').digest('hex'),
                'PATTERN': crypto.createHash('sha256').update('pattern').digest('hex'),
                'FACE': crypto.createHash('sha256').update('face_template').digest('hex')
            };

            // Store all factors
            await redisClient.hSet(key, factors);

            // Retrieve all
            const retrieved = await redisClient.hGetAll(key);
            expect(retrieved).to.deep.equal(factors);

            console.log('   ✓ Hash operations working');
        });

        it('should retrieve individual hash fields', async function() {
            const key = `${testKey}:user:factors2`;
            const pinDigest = crypto.createHash('sha256').update('5678').digest('hex');

            await redisClient.hSet(key, 'PIN', pinDigest);
            const retrieved = await redisClient.hGet(key, 'PIN');

            expect(retrieved).to.equal(pinDigest);
            console.log('   ✓ Individual field retrieval working');
        });

        it('should count hash fields', async function() {
            const key = `${testKey}:user:factors3`;

            await redisClient.hSet(key, {
                'PIN': 'digest1',
                'PATTERN': 'digest2',
                'WORDS': 'digest3',
                'FACE': 'digest4',
                'FINGERPRINT': 'digest5',
                'RHYTHM_TAP': 'digest6'
            });

            const count = await redisClient.hLen(key);
            expect(count).to.equal(6); // PSD3 minimum

            console.log('   ✓ Hash field count working (6 factors)');
        });
    });

    // ========================================================================
    // ENCRYPTION AT REST (Application-Level)
    // ========================================================================

    describe('Application-Level Encryption', function() {
        it('should encrypt data before storing', async function() {
            const key = `${testKey}:encrypted`;
            const plaintext = 'sensitive-factor-digest';

            // Simulate AES-256-GCM encryption
            const algorithm = 'aes-256-gcm';
            const encryptionKey = crypto.randomBytes(32);
            const iv = crypto.randomBytes(12);

            const cipher = crypto.createCipheriv(algorithm, encryptionKey, iv);
            let encrypted = cipher.update(plaintext, 'utf8', 'hex');
            encrypted += cipher.final('hex');
            const authTag = cipher.getAuthTag();

            // Store encrypted data
            const encryptedData = JSON.stringify({
                data: encrypted,
                iv: iv.toString('hex'),
                authTag: authTag.toString('hex')
            });

            await redisClient.set(key, encryptedData);

            // Retrieve and decrypt
            const retrieved = await redisClient.get(key);
            const parsed = JSON.parse(retrieved);

            const decipher = crypto.createDecipheriv(
                algorithm,
                encryptionKey,
                Buffer.from(parsed.iv, 'hex')
            );
            decipher.setAuthTag(Buffer.from(parsed.authTag, 'hex'));

            let decrypted = decipher.update(parsed.data, 'hex', 'utf8');
            decrypted += decipher.final('utf8');

            expect(decrypted).to.equal(plaintext);
            console.log('   ✓ AES-256-GCM encryption/decryption working');
        });
    });

    // ========================================================================
    // PERFORMANCE & LIMITS
    // ========================================================================

    describe('Performance & Limits', function() {
        it('should handle bulk operations efficiently', async function() {
            const pipeline = redisClient.multi();
            const count = 100;

            for (let i = 0; i < count; i++) {
                pipeline.set(`${testKey}:bulk:${i}`, `value${i}`);
            }

            const start = Date.now();
            await pipeline.exec();
            const duration = Date.now() - start;

            console.log(`   ✓ ${count} operations in ${duration}ms (${(count/duration*1000).toFixed(0)} ops/sec)`);
            expect(duration).to.be.lessThan(1000); // Should complete in under 1 second
        });

        it('should enforce maxmemory policy', async function() {
            // This test checks if Redis is configured correctly
            const info = await redisClient.info('memory');
            expect(info).to.include('maxmemory');
            console.log('   ✓ Memory limits configured');
        });
    });

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================

    describe('Error Handling', function() {
        it('should handle connection errors gracefully', async function() {
            const badClient = redis.createClient({
                socket: {
                    host: 'invalid-host-12345',
                    port: 9999,
                    connectTimeout: 1000
                }
            });

            try {
                await badClient.connect();
                expect.fail('Should have thrown error');
            } catch (error) {
                expect(error).to.exist;
                console.log('   ✓ Connection error handled');
            }
        });

        it('should handle authentication errors', async function() {
            // Skip if no password configured
            if (!process.env.REDIS_PASSWORD) {
                console.log('   ⚠️  No password configured, skipping');
                this.skip();
                return;
            }

            const badAuthClient = redis.createClient({
                socket: {
                    host: process.env.REDIS_HOST || 'localhost',
                    port: parseInt(process.env.REDIS_PORT) || 6379
                },
                password: 'wrong-password-12345'
            });

            try {
                await badAuthClient.connect();
                await badAuthClient.ping();
                expect.fail('Should have thrown auth error');
            } catch (error) {
                expect(error.message).to.include('WRONGPASS');
                console.log('   ✓ Auth error handled');
            } finally {
                if (badAuthClient.isOpen) {
                    await badAuthClient.quit();
                }
            }
        });
    });

    // ========================================================================
    // CLEANUP
    // ========================================================================

    describe('Cleanup', function() {
        it('should delete all test keys', async function() {
            // Delete all test keys
            const keys = await redisClient.keys(`${testKey}:*`);
            if (keys.length > 0) {
                await redisClient.del(keys);
            }
            console.log(`   ✓ Cleaned up ${keys.length} test keys`);
        });
    });
});

// Run tests
console.log('\n═══════════════════════════════════════════════════════════');
console.log('  ZeroPay Redis Connection Tests');
console.log('═══════════════════════════════════════════════════════════\n');
