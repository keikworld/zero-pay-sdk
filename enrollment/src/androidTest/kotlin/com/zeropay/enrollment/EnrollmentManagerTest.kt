// Path: enrollment/src/androidTest/kotlin/com/zeropay/enrollment/EnrollmentManagerTest.kt

package com.zeropay.enrollment

import com.zeropay.enrollment.config.EnrollmentConfig
import com.zeropay.enrollment.consent.ConsentManager
import com.zeropay.enrollment.models.*
import com.zeropay.enrollment.payment.PaymentProviderManager
import com.zeropay.sdk.Factor
import com.zeropay.sdk.cache.RedisCacheClient
import com.zeropay.sdk.security.CryptoUtils
import com.zeropay.sdk.storage.KeyStoreManager
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * EnrollmentManager Unit Tests
 * 
 * Test Coverage:
 * - ✅ Successful enrollment
 * - ✅ Factor validation
 * - ✅ Consent validation
 * - ✅ Rate limiting
 * - ✅ Rollback on failure
 * - ✅ Session validation
 * - ✅ Category validation
 * 
 * @version 1.0.0
 * @date 2025-10-08
 */
class EnrollmentManagerTest {
    
    private lateinit var enrollmentManager: EnrollmentManager
    private lateinit var keyStoreManager: KeyStoreManager
    private lateinit var redisCacheClient: RedisCacheClient
    private lateinit var consentManager: ConsentManager
    private lateinit var paymentProviderManager: PaymentProviderManager
    
    @Before
    fun setup() {
        // Mock dependencies
        keyStoreManager = mockk(relaxed = true)
        redisCacheClient = mockk(relaxed = true)
        consentManager = mockk(relaxed = true)
        paymentProviderManager = mockk(relaxed = true)
        
        // Create manager
        enrollmentManager = EnrollmentManager(
            keyStoreManager = keyStoreManager,
            redisCacheClient = redisCacheClient,
            consentManager = consentManager,
            paymentProviderManager = paymentProviderManager
        )
    }
    
    @After
    fun tearDown() {
        clearAllMocks()
    }
    
    // ==================== SUCCESS CASES ====================
    
    @Test
    fun `enrollWithSession should succeed with valid session`() = runBlocking {
        // Arrange
        val session = createValidSession()
        
        coEvery { redisCacheClient.storeEnrollment(any(), any(), any()) } returns Result.success(Unit)
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Success)
        val success = result as EnrollmentResult.Success
        assertEquals(session.userId, success.user.uuid)
        assertEquals(session.capturedFactors.size, success.factorCount)
        
        // Verify KeyStore storage
        verify(exactly = session.capturedFactors.size) {
            keyStoreManager.storeEnrollment(any(), any(), any())
        }
        
        // Verify Redis caching
        coVerify(exactly = 1) {
            redisCacheClient.storeEnrollment(any(), any(), any())
        }
    }
    
    @Test
    fun `enrollment should succeed even if Redis fails`() = runBlocking {
        // Arrange
        val session = createValidSession()
        
        coEvery { redisCacheClient.storeEnrollment(any(), any(), any()) } returns 
            Result.failure(Exception("Redis connection failed"))
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue("Should succeed despite Redis failure", result is EnrollmentResult.Success)
    }
    
    // ==================== VALIDATION TESTS ====================
    
    @Test
    fun `enrollment should fail with insufficient factors`() = runBlocking {
        // Arrange
        val session = createValidSession().copy(
            selectedFactors = listOf(Factor.PIN),
            capturedFactors = mapOf(
                Factor.PIN to CryptoUtils.sha256("1234".toByteArray())
            )
        )
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.INVALID_FACTOR, failure.error)
        assertTrue(failure.message.contains("At least"))
    }
    
    @Test
    fun `enrollment should fail with too many factors`() = runBlocking {
        // Arrange
        val factors = mutableMapOf<Factor, ByteArray>()
        val factorList = Factor.values().take(11) // More than MAX_FACTORS
        
        factorList.forEach { factor ->
            factors[factor] = CryptoUtils.sha256("test".toByteArray())
        }
        
        val session = createValidSession().copy(
            selectedFactors = factorList,
            capturedFactors = factors
        )
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.INVALID_FACTOR, failure.error)
    }
    
    @Test
    fun `enrollment should fail with single category factors`() = runBlocking {
        // Arrange - All KNOWLEDGE factors
        val session = createValidSession().copy(
            selectedFactors = listOf(
                Factor.PIN,
                Factor.COLOUR,
                Factor.EMOJI,
                Factor.WORDS,
                Factor.PIN, // Duplicate category
                Factor.COLOUR
            ),
            capturedFactors = mapOf(
                Factor.PIN to CryptoUtils.sha256("1234".toByteArray()),
                Factor.COLOUR to CryptoUtils.sha256("red,blue".toByteArray()),
                Factor.EMOJI to CryptoUtils.sha256("😀,😂".toByteArray()),
                Factor.WORDS to CryptoUtils.sha256("cat,dog".toByteArray()),
                Factor.PIN to CryptoUtils.sha256("5678".toByteArray()),
                Factor.COLOUR to CryptoUtils.sha256("green,yellow".toByteArray())
            )
        )
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.INVALID_FACTOR, failure.error)
        assertTrue(failure.message.contains("categories"))
    }
    
    @Test
    fun `enrollment should fail without required consents`() = runBlocking {
        // Arrange
        val session = createValidSession().copy(
            consents = mapOf(
                EnrollmentConfig.ConsentType.DATA_PROCESSING to true,
                EnrollmentConfig.ConsentType.DATA_STORAGE to false, // Missing consent
                EnrollmentConfig.ConsentType.TERMS_OF_SERVICE to true
            )
        )
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.NO_CONSENT, failure.error)
    }
    
    @Test
    fun `enrollment should fail with invalid digest size`() = runBlocking {
        // Arrange
        val session = createValidSession().copy(
            capturedFactors = mapOf(
                Factor.PIN to ByteArray(16), // Wrong size (should be 32)
                Factor.PATTERN_NORMAL to CryptoUtils.sha256("valid".toByteArray()),
                Factor.COLOUR to CryptoUtils.sha256("valid".toByteArray()),
                Factor.EMOJI to CryptoUtils.sha256("valid".toByteArray()),
                Factor.WORDS to CryptoUtils.sha256("valid".toByteArray()),
                Factor.VOICE to CryptoUtils.sha256("valid".toByteArray())
            )
        )
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.INVALID_FACTOR, failure.error)
        assertTrue(failure.message.contains("32 bytes"))
    }
    
    // ==================== RATE LIMITING TESTS ====================
    
    @Test
    fun `enrollment should fail after rate limit exceeded`() = runBlocking {
        // Arrange
        val session = createValidSession()
        
        // Enroll 10 times (max per hour)
        repeat(10) {
            enrollmentManager.enrollWithSession(session)
        }
        
        // Act - 11th attempt
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        val failure = result as EnrollmentResult.Failure
        assertEquals(EnrollmentError.RATE_LIMIT_EXCEEDED, failure.error)
    }
    
    // ==================== ROLLBACK TESTS ====================
    
    @Test
    fun `enrollment should rollback on KeyStore failure`() = runBlocking {
        // Arrange
        val session = createValidSession()
        
        every { keyStoreManager.storeEnrollment(any(), any(), any()) } throws 
            Exception("KeyStore write failed")
        
        // Act
        val result = enrollmentManager.enrollWithSession(session)
        
        // Assert
        assertTrue(result is EnrollmentResult.Failure)
        
        // Verify rollback was attempted
        verify { keyStoreManager.deleteEnrollment(any()) }
        coVerify { redisCacheClient.deleteEnrollment(any()) }
    }
    
    // ==================== METRICS TESTS ====================
    
    @Test
    fun `metrics should track successes and failures`() = runBlocking {
        // Arrange
        val validSession = createValidSession()
        val invalidSession = createValidSession().copy(
            capturedFactors = emptyMap()
        )
        
        coEvery { redisCacheClient.storeEnrollment(any(), any(), any()) } returns Result.success(Unit)
        
        // Act
        enrollmentManager.enrollWithSession(validSession) // Success
        enrollmentManager.enrollWithSession(invalidSession) // Failure
        enrollmentManager.enrollWithSession(validSession) // Success
        
        val metrics = enrollmentManager.getMetrics()
        
        // Assert
        assertEquals(2, metrics.successCount)
        assertEquals(1, metrics.failureCount)
        assertEquals(66.67, metrics.successRate, 0.01)
    }
    
    // ==================== HELPER METHODS ====================
    
    private fun createValidSession(): EnrollmentSession {
        val uuid = UUID.randomUUID().toString()
        
        return EnrollmentSession(
            sessionId = UUID.randomUUID().toString(),
            userId = uuid,
            selectedFactors = listOf(
                Factor.PIN,
                Factor.PATTERN_NORMAL,
                Factor.COLOUR,
                Factor.EMOJI,
                Factor.WORDS,
                Factor.VOICE
            ),
            capturedFactors = mapOf(
                Factor.PIN to CryptoUtils.sha256("1234".toByteArray()),
                Factor.PATTERN_NORMAL to CryptoUtils.sha256("pattern".toByteArray()),
                Factor.COLOUR to CryptoUtils.sha256("red,blue,green".toByteArray()),
                Factor.EMOJI to CryptoUtils.sha256("😀,😂,😍".toByteArray()),
                Factor.WORDS to CryptoUtils.sha256("cat,dog,bird".toByteArray()),
                Factor.VOICE to CryptoUtils.sha256("voice_sample".toByteArray())
            ),
            linkedPaymentProviders = emptyList(),
            consents = EnrollmentConfig.ConsentType.values().associateWith { true },
            currentStep = EnrollmentStep.CONFIRMATION
        )
    }
}
