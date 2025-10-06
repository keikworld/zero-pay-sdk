package com.zeropay.sdk.cache

import com.zeropay.sdk.Factor
import com.zeropay.sdk.network.SecureApiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class RedisCacheClientTest {
    
    @Mock
    private lateinit var mockApiClient: SecureApiClient
    
    private lateinit var cacheClient: RedisCacheClient
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        cacheClient = RedisCacheClient(mockApiClient)
    }
    
    @Test
    fun testStoreEnrollment_ValidData_ReturnsSuccess() = runBlocking {
        // Arrange
        val userUuid = "test-uuid-123"
        val digests = mapOf(
            Factor.PIN to ByteArray(32) { it.toByte() },
            Factor.PATTERN to ByteArray(32) { (it * 2).toByte() }
        )
        
        // Mock API response
        `when`(mockApiClient.post(any(), any(), any()))
            .thenReturn(SecureApiClient.ApiResponse(true, null, null))
        
        // Act
        val result = cacheClient.storeEnrollment(userUuid, digests, "device-123")
        
        // Assert
        assertTrue(result.isSuccess)
        verify(mockApiClient).post(any(), any(), any())
    }
    
    @Test
    fun testStoreEnrollment_LessThanTwoFactors_ThrowsException() = runBlocking {
        // Arrange
        val userUuid = "test-uuid-123"
        val digests = mapOf(
            Factor.PIN to ByteArray(32)
        )
        
        // Act & Assert
        val result = cacheClient.storeEnrollment(userUuid, digests, "device-123")
        assertTrue(result.isFailure)
    }
}
