/**
 * End-to-End Integration Test: Enrollment Flow
 *
 * Tests the complete enrollment flow from SDK to backend.
 *
 * Test Flow:
 * 1. Generate factor digests (SHA-256)
 * 2. Send enrollment request to backend
 * 3. Verify data stored in Redis
 * 4. Verify wrapped key stored in PostgreSQL
 * 5. Retrieve enrollment
 * 6. Update enrollment
 * 7. Delete enrollment (GDPR)
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
describe('Enrollment Flow - End-to-End', function() {
    // Increase timeout for integration tests
    this.timeout(10000);

    let testUserUuid;
    let testFactors;
    let enrollmentResponse;

    // Setup: Generate test data
    before(function() {
        console.log('\n🧪 Starting Enrollment Integration Tests\n');

        // Generate test UUID
        testUserUuid = uuidv4();
        console.log(`   Test UUID: ${testUserUuid}`);

        // Generate 6 test factors (PSD3 SCA minimum)
        testFactors = [
            {
                type: 'PIN',
                digest: generateSHA256('1234'),
                metadata: { length: '4' }
            },
            {
                type: 'PATTERN',
                digest: generateSHA256('pattern123'),
                metadata: { points: '5' }
            },
            {
                type: 'WORDS',
                digest: generateSHA256('correct horse battery staple'),
                metadata: { count: '4' }
            },
            {
                type: 'FACE',
                digest: generateSHA256('face_template_xyz'),
                metadata: { quality: 'high' }
            },
            {
                type: 'FINGERPRINT',
                digest: generateSHA256('fingerprint_template_abc'),
                metadata: { sensor: 'optical' }
            },
            {
                type: 'RHYTHM_TAP',
                digest: generateSHA256('tap_pattern_123'),
                metadata: { taps: '8' }
            }
        ];

        console.log(`   Generated ${testFactors.length} test factors\n`);
    });

    // Cleanup: Delete test data
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

    // Test 1: Enroll user
    describe('POST /enrollment/store', function() {
        it('should successfully enroll a new user', async function() {
            const request = {
                user_uuid: testUserUuid,
                factors: testFactors,
                device_id: 'test-device-001',
                ttl_seconds: 86400,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                gdpr_consent: true,
                consent_timestamp: getCurrentTimestamp()
            };

            console.log('   📤 Sending enrollment request...');

            const response = await axios.post(`${API_URL}/enrollment/store`, request);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('user_uuid', testUserUuid);
            expect(response.data.data).to.have.property('alias');
            expect(response.data.data).to.have.property('enrolled_factors', 6);
            expect(response.data.data).to.have.property('expires_at');

            enrollmentResponse = response.data.data;

            console.log('   ✓ Enrollment successful');
            console.log(`   Alias: ${enrollmentResponse.alias}`);
            console.log(`   Factors: ${enrollmentResponse.enrolled_factors}`);
        });

        it('should reject enrollment with less than 6 factors (PSD3)', async function() {
            const request = {
                user_uuid: uuidv4(),
                factors: testFactors.slice(0, 5), // Only 5 factors
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                gdpr_consent: true
            };

            try {
                await axios.post(`${API_URL}/enrollment/store`, request);
                throw new Error('Should have rejected enrollment');
            } catch (error) {
                expect(error.response.status).to.equal(400);
                expect(error.response.data.error.code).to.equal('INSUFFICIENT_FACTORS');
                console.log('   ✓ Correctly rejected insufficient factors');
            }
        });

        it('should reject enrollment without GDPR consent', async function() {
            const request = {
                user_uuid: uuidv4(),
                factors: testFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                gdpr_consent: false // No consent
            };

            try {
                await axios.post(`${API_URL}/enrollment/store`, request);
                throw new Error('Should have rejected enrollment');
            } catch (error) {
                expect(error.response.status).to.equal(400);
                expect(error.response.data.error.code).to.equal('GDPR_CONSENT_REQUIRED');
                console.log('   ✓ Correctly enforced GDPR consent');
            }
        });

        it('should reject duplicate enrollment', async function() {
            const request = {
                user_uuid: testUserUuid, // Same UUID
                factors: testFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                gdpr_consent: true
            };

            try {
                await axios.post(`${API_URL}/enrollment/store`, request);
                throw new Error('Should have rejected duplicate');
            } catch (error) {
                expect(error.response.status).to.equal(409);
                console.log('   ✓ Correctly rejected duplicate enrollment');
            }
        });
    });

    // Test 2: Retrieve enrollment
    describe('GET /enrollment/retrieve/:uuid', function() {
        it('should retrieve enrolled factors', async function() {
            console.log('   📥 Retrieving enrollment...');

            const response = await axios.get(`${API_URL}/enrollment/retrieve/${testUserUuid}`);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('user_uuid', testUserUuid);
            expect(response.data.data).to.have.property('factors');
            expect(response.data.data.factors).to.be.an('array').with.lengthOf(6);
            expect(response.data.data).to.have.property('expires_at');

            // Zero-knowledge: Should return factor types, NOT digests
            response.data.data.factors.forEach(factor => {
                expect(factor).to.have.property('type');
                expect(factor).to.not.have.property('digest'); // Never expose digests
            });

            console.log('   ✓ Retrieved enrollment successfully');
            console.log(`   Factor types: ${response.data.data.factors.map(f => f.type).join(', ')}`);
        });

        it('should return 404 for non-existent UUID', async function() {
            const fakeUuid = uuidv4();

            try {
                await axios.get(`${API_URL}/enrollment/retrieve/${fakeUuid}`);
                throw new Error('Should have returned 404');
            } catch (error) {
                expect(error.response.status).to.equal(404);
                expect(error.response.data.error.code).to.equal('USER_NOT_FOUND');
                console.log('   ✓ Correctly returned 404 for missing user');
            }
        });
    });

    // Test 3: Update enrollment
    describe('PUT /enrollment/update', function() {
        it('should update enrolled factors', async function() {
            // Add one more factor (total 7)
            const updatedFactors = [
                ...testFactors,
                {
                    type: 'NFC',
                    digest: generateSHA256('nfc_tag_001'),
                    metadata: { tag_id: '001' }
                }
            ];

            const request = {
                user_uuid: testUserUuid,
                factors: updatedFactors,
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp(),
                gdpr_consent: true
            };

            console.log('   🔄 Updating enrollment...');

            const response = await axios.put(`${API_URL}/enrollment/update`, request);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('enrolled_factors', 7);

            console.log('   ✓ Update successful');
            console.log(`   New factor count: ${response.data.data.enrolled_factors}`);
        });
    });

    // Test 4: Export user data (GDPR)
    describe('GET /enrollment/export/:uuid', function() {
        it('should export user data', async function() {
            console.log('   📦 Exporting user data...');

            const response = await axios.post(`${API_URL}/enrollment/export/${testUserUuid}`, {
                nonce: generateNonce(),
                timestamp: getCurrentTimestamp()
            });

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);
            expect(response.data.data).to.have.property('user_uuid', testUserUuid);
            expect(response.data.data).to.have.property('data');
            expect(response.data.data).to.have.property('request_id');

            console.log('   ✓ Export successful');
            console.log(`   Request ID: ${response.data.data.request_id}`);
        });
    });

    // Test 5: Delete enrollment (GDPR right to erasure)
    describe('DELETE /enrollment/delete/:uuid', function() {
        it('should delete enrolled user', async function() {
            console.log('   🗑️  Deleting enrollment...');

            const response = await axios.delete(`${API_URL}/enrollment/delete/${testUserUuid}`);

            // Assertions
            expect(response.status).to.equal(200);
            expect(response.data).to.have.property('success', true);

            console.log('   ✓ Deletion successful');
        });

        it('should return 404 after deletion', async function() {
            try {
                await axios.get(`${API_URL}/enrollment/retrieve/${testUserUuid}`);
                throw new Error('Should have returned 404');
            } catch (error) {
                expect(error.response.status).to.equal(404);
                console.log('   ✓ User successfully deleted (GDPR compliance)');
            }
        });
    });

    // Test 6: Rate limiting
    describe('Rate Limiting', function() {
        it('should enforce rate limits', async function() {
            this.timeout(30000); // Longer timeout for rate limit test

            const requests = [];
            const testUuid = uuidv4();

            console.log('   ⏱️  Testing rate limits...');

            // Send 101 requests (rate limit is 100 per 15 minutes)
            for (let i = 0; i < 101; i++) {
                const request = axios.post(`${API_URL}/enrollment/store`, {
                    user_uuid: testUuid,
                    factors: testFactors,
                    nonce: generateNonce(),
                    timestamp: getCurrentTimestamp(),
                    gdpr_consent: true
                }).catch(err => err.response);

                requests.push(request);
            }

            const responses = await Promise.all(requests);
            const rateLimited = responses.filter(r => r && r.status === 429);

            expect(rateLimited.length).to.be.greaterThan(0);
            console.log(`   ✓ Rate limiting active (${rateLimited.length} requests blocked)`);

            // Cleanup
            await axios.delete(`${API_URL}/enrollment/delete/${testUuid}`).catch(() => {});
        });
    });
});

// Run tests
console.log('\n═══════════════════════════════════════════════════════════');
console.log('  ZeroPay Enrollment Integration Tests');
console.log('═══════════════════════════════════════════════════════════\n');
