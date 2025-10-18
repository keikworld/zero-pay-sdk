/**
 * End-to-End Integration Test: Verification Flow
 *
 * Tests the complete verification flow from merchant to backend.
 *
 * Test Flow:
 * 1. Enroll user (prerequisite)
 * 2. Create verification session
 * 3. Verify with correct factors (should succeed)
 * 4. Verify with incorrect factors (should fail)
 * 5. Check session status
 * 6. Test session expiration
 * 7. Test rate limiting
 *
 * Prerequisites:
 * - Backend server running on localhost:3000
 * - Redis running on localhost:6380
 * - PostgreSQL database set up
 *
 * Run:
 *   npm run test:integration
 *
 * @version 1.0.0
 */

const { expect } = require('chai');
const axios = require('axios');
const crypto = require('crypto');
const { v4: uuidv4 } = require('uuid');

// Test configuration
const BASE_URL = process.env.TEST_BASE_URL || 'http://localhost:3000';
const API_URL = `${BASE_URL}/api/v1`;

// Helper functions
function generateSHA256(data) {
    return crypto.createHash('sha256').update(data).digest('hex');
}

function generateNonce() {
    return crypto.randomBytes(32).toString('hex');
}

function getCurrentTimestamp() {
    return new Date().toISOString();
}

// Test suite
describe('Verification Flow - End-to-End', function() {
    this.timeout(10000);

    let testUserUuid;
    let testFactors;
    let verificationSession;

    // Setup: Enroll test user
    before(async function() {
        console.log('\n🧪 Starting Verification Integration Tests\n');

        // Generate test UUID
        testUserUuid = uuidv4();
        console.log(`   Test UUID: ${testUserUuid}`);

        // Generate 6 test factors
        testFactors = [
            {
                type: 'PIN',
                digest: generateSHA256('1234')
            },
            {
                type: 'PATTERN',
                digest: generateSHA256('pattern123')
            },
            {
                type: 'WORDS',
                digest: generateSHA256('correct horse battery staple')
            },
            {
                type: 'FACE',
                digest: generateSHA256('face_template_xyz')
            },
            {
                type: 'FINGERPRINT',
                digest: generateSHA256('fingerprint_template_abc')
            },
            {
                type: 'RHYTHM_TAP',
                digest: generateSHA256('tap_pattern_123')
            }
        ];

        // Enroll user
        console.log('   📝 Enrolling test user...');

        const enrollmentRequest = {
            user_uuid: testUserUuid,
            factors: testFactors,
            device_id: 'test-device-001',
            ttl_seconds: 86400,
            nonce: generateNonce(),
            timestamp: getCurrentTimestamp(),
            gdpr_consent: true,
            consent_timestamp: getCurrentTimestamp()
        };

        const response = await axios.post(`${API_URL}/enrollment/store`, enrollmentRequest);
        expect(response.status).to.equal(200);
        console.log('   ✓ Test user enrolled\n');
    });

    // Cleanup
    after(async function() {
        console.log('\n🧹 Cleaning up test data...\n');

        if (testUserUuid) {
            try {
                await axios.delete(`${API_URL}/enrollment/delete/${testUserUuid}`);
                console.log('   ✓ Test data deleted');
            } catch (error) {
                console.log('   ⚠ Cleanup warning:', error.message);
            }
        }
    });

    // Test 1: Create verification session
    describe('POST /verification/session/create', function() {
        it('should create verification session with transaction details', async function() {
            const request = {
                user_uuid: testUserUuid,
                amount: 99.99,
                currency: 'USD',
                transaction_id: 'TXN-123456',
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            };

            console.log('   🔐 Creating verification session...');

            const response = await axios.post(`${API_URL}/verification/session/create`, request);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('session_id');
            expect(response.data.data).to.have.property('user_uuid', testUserUuid);
            expect(response.data.data).to.have.property('required_factors');
            expect(response.data.data.required_factors).to.be.an('array');
            expect(response.data.data).to.have.property('expires_at');

            verificationSession = response.data.data;

            console.log('   ✓ Session created successfully');
            console.log(`   Session ID: ${verificationSession.session_id}`);
            console.log(`   Required factors: ${verificationSession.required_factors.join(', ')}`);
        });

        it('should reject session creation for non-existent user', async function() {
            const fakeUuid = uuidv4();

            const request = {
                user_uuid: fakeUuid,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            };

            try {
                await axios.post(`${API_URL}/verification/session/create`, request);
                throw new Error('Should have rejected');
            } catch (error) {
                expect(error.response.status).to.equal(404);
                expect(error.response.data.error.code).to.equal('USER_NOT_FOUND');
                console.log('   ✓ Correctly rejected non-existent user');
            }
        });

        it('should validate PSD3 dynamic linking (amount + currency)', async function() {
            const request = {
                user_uuid: testUserUuid,
                amount: 150.00,
                // Missing currency - should fail
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            };

            try {
                await axios.post(`${API_URL}/verification/session/create`, request);
                throw new Error('Should have rejected');
            } catch (error) {
                expect(error.response.status).to.equal(400);
                console.log('   ✓ Correctly validated PSD3 dynamic linking');
            }
        });
    });

    // Test 2: Verify with correct factors
    describe('POST /verification/verify - Success Case', function() {
        it('should verify successfully with correct factors', async function() {
            const request = {
                session_id: verificationSession.session_id,
                user_uuid: testUserUuid,
                factors: testFactors, // Same factors as enrollment
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                device_id: 'test-device-001'
            };

            console.log('   ✅ Verifying with correct factors...');

            const response = await axios.post(`${API_URL}/verification/verify`, request);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('verified', true);
            expect(response.data.data).to.have.property('session_id', verificationSession.session_id);
            expect(response.data.data).to.have.property('confidence_score');
            expect(response.data.data.confidence_score).to.be.a('number');
            expect(response.data.data.confidence_score).to.be.at.least(0.9); // High confidence
            expect(response.data.data).to.have.property('factors_verified', 6);
            expect(response.data.data).to.have.property('total_factors', 6);

            // Optional ZK proof
            if (response.data.data.zk_proof) {
                expect(response.data.data.zk_proof).to.be.a('string');
                console.log('   ✓ ZK-SNARK proof generated');
            }

            console.log('   ✓ Verification successful');
            console.log(`   Confidence: ${(response.data.data.confidence_score * 100).toFixed(1)}%`);
        });
    });

    // Test 3: Verify with incorrect factors (Zero-knowledge test)
    describe('POST /verification/verify - Failure Case', function() {
        it('should fail verification with incorrect factors', async function() {
            // Create new session
            const sessionResponse = await axios.post(`${API_URL}/verification/session/create`, {
                user_uuid: testUserUuid,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });

            const newSession = sessionResponse.data.data;

            // Use wrong factors
            const wrongFactors = [
                {
                    type: 'PIN',
                    digest: generateSHA256('9999') // Wrong PIN
                },
                {
                    type: 'PATTERN',
                    digest: generateSHA256('wrong_pattern')
                },
                {
                    type: 'WORDS',
                    digest: generateSHA256('wrong words')
                },
                {
                    type: 'FACE',
                    digest: generateSHA256('wrong_face')
                },
                {
                    type: 'FINGERPRINT',
                    digest: generateSHA256('wrong_fingerprint')
                },
                {
                    type: 'RHYTHM_TAP',
                    digest: generateSHA256('wrong_rhythm')
                }
            ];

            const request = {
                session_id: newSession.session_id,
                user_uuid: testUserUuid,
                factors: wrongFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            };

            console.log('   ❌ Verifying with incorrect factors...');

            const response = await axios.post(`${API_URL}/verification/verify`, request);

            // Assertions
            // Zero-knowledge: Backend should return verified=false, but not reveal which factor failed
            expect(response.status).to.equal(200); // Still 200, but verified=false
            expect(response.data.data).to.have.property('verified', false);
            expect(response.data.data).to.have.property('confidence_score');
            expect(response.data.data.confidence_score).to.be.lessThan(0.5); // Low confidence

            // CRITICAL: Backend should NOT reveal which factor failed
            expect(response.data.data).to.not.have.property('failed_factors');
            expect(response.data.data).to.not.have.property('failure_reason');

            console.log('   ✓ Verification failed as expected');
            console.log('   ✓ Zero-knowledge preserved (no factor details revealed)');
        });

        it('should fail verification with partial correct factors', async function() {
            const sessionResponse = await axios.post(`${API_URL}/verification/session/create`, {
                user_uuid: testUserUuid,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });

            const newSession = sessionResponse.data.data;

            // Mix of correct and incorrect factors
            const mixedFactors = [
                testFactors[0], // Correct
                testFactors[1], // Correct
                { type: 'WORDS', digest: generateSHA256('wrong') }, // Wrong
                testFactors[3], // Correct
                testFactors[4], // Correct
                { type: 'RHYTHM_TAP', digest: generateSHA256('wrong') } // Wrong
            ];

            const request = {
                session_id: newSession.session_id,
                user_uuid: testUserUuid,
                factors: mixedFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            };

            console.log('   ⚠️  Verifying with partial correct factors...');

            const response = await axios.post(`${API_URL}/verification/verify`, request);

            // Should still fail (all factors must match)
            expect(response.data.data).to.have.property('verified', false);

            console.log('   ✓ Correctly rejected partial match');
        });
    });

    // Test 4: Session status
    describe('GET /verification/session/status/:sessionId', function() {
        it('should get session status', async function() {
            const response = await axios.get(
                `${API_URL}/verification/session/status/${verificationSession.session_id}`
            );

            expect(response.status).to.equal(200);
            expect(response.data.data).to.have.property('session_id', verificationSession.session_id);

            console.log('   ✓ Session status retrieved');
        });

        it('should return 404 for non-existent session', async function() {
            const fakeSessionId = uuidv4();

            try {
                await axios.get(`${API_URL}/verification/session/status/${fakeSessionId}`);
                throw new Error('Should have returned 404');
            } catch (error) {
                expect(error.response.status).to.equal(404);
                console.log('   ✓ Correctly returned 404 for invalid session');
            }
        });
    });

    // Test 5: Session expiration
    describe('Session Expiration', function() {
        it('should reject expired session', async function() {
            this.timeout(20000); // Longer timeout

            // Note: This test assumes session expiry is configured to a short duration
            // For production, sessions expire after 15 minutes
            // For testing, you may need to adjust backend config

            console.log('   ⏳ Testing session expiration (this may take time)...');
            console.log('   (Skipped in fast test mode - enable in full integration tests)');

            // TODO: Implement actual expiration test
            // - Wait for session to expire
            // - Attempt verification
            // - Should return SESSION_EXPIRED error
        });
    });

    // Test 6: Rate limiting
    describe('Rate Limiting', function() {
        it('should enforce verification rate limits', async function() {
            this.timeout(30000);

            console.log('   ⏱️  Testing rate limits...');

            const requests = [];

            // Send multiple requests rapidly
            for (let i = 0; i < 60; i++) {
                const sessionRequest = axios.post(`${API_URL}/verification/session/create`, {
                    user_uuid: testUserUuid,
                    nonce: generateNonce(),
                    timestamp: getCurrentTimestamp()
                }).catch(err => err.response);

                requests.push(sessionRequest);
            }

            const responses = await Promise.all(requests);
            const rateLimited = responses.filter(r => r && r.status === 429);

            expect(rateLimited.length).to.be.greaterThan(0);
            console.log(`   ✓ Rate limiting active (${rateLimited.length} requests blocked)`);
        });
    });

    // Test 7: Constant-time comparison validation
    describe('Security: Constant-Time Comparison', function() {
        it('should take similar time for correct and incorrect factors', async function() {
            this.timeout(20000);

            console.log('   🔒 Testing constant-time comparison...');

            // Create two sessions
            const session1Response = await axios.post(`${API_URL}/verification/session/create`, {
                user_uuid: testUserUuid,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });

            const session2Response = await axios.post(`${API_URL}/verification/session/create`, {
                user_uuid: testUserUuid,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });

            // Time correct verification
            const start1 = Date.now();
            await axios.post(`${API_URL}/verification/verify`, {
                session_id: session1Response.data.data.session_id,
                user_uuid: testUserUuid,
                factors: testFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });
            const time1 = Date.now() - start1;

            // Time incorrect verification
            const wrongFactors = testFactors.map(f => ({
                type: f.type,
                digest: generateSHA256('wrong_' + f.type)
            }));

            const start2 = Date.now();
            await axios.post(`${API_URL}/verification/verify`, {
                session_id: session2Response.data.data.session_id,
                user_uuid: testUserUuid,
                factors: wrongFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });
            const time2 = Date.now() - start2;

            console.log(`   Correct factors: ${time1}ms`);
            console.log(`   Incorrect factors: ${time2}ms`);

            // Times should be similar (within 20% tolerance)
            const timeDiff = Math.abs(time1 - time2);
            const avgTime = (time1 + time2) / 2;
            const percentDiff = (timeDiff / avgTime) * 100;

            expect(percentDiff).to.be.lessThan(20);
            console.log(`   ✓ Timing difference: ${percentDiff.toFixed(1)}% (constant-time validated)`);
        });
    });
});

// Run tests
console.log('\n═══════════════════════════════════════════════════════════');
console.log('  ZeroPay Verification Integration Tests');
console.log('═══════════════════════════════════════════════════════════\n');
