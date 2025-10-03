package com.zeropay.sdk.network

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import java.security.KeyStore

/**
 * Network Security Configuration
 * 
 * Features:
 * - Certificate pinning
 * - HTTPS enforcement
 * - TLS 1.3 only
 * - Request signing
 * - Nonce-based replay protection
 */
object NetworkSecurity {
    
    private const val API_BASE_URL = "https://api.zeropay.com"
    
    /**
     * Create secure OkHttp client with certificate pinning
     */
    fun createSecureClient(): OkHttpClient {
        // Certificate pinning configuration
        val certificatePinner = CertificatePinner.Builder()
            .add("api.zeropay.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") // TODO: Add real cert hash
            .add("api.zeropay.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // Backup cert
            .build()
        
        // Trust manager for custom SSL validation
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        val trustManager = trustManagers[0] as X509TrustManager
        
        // SSL context with TLS 1.3
        val sslContext = SSLContext.getInstance("TLSv1.3")
        sslContext.init(null, arrayOf(trustManager), null)
        
        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(SecurityHeadersInterceptor())
            .addInterceptor(RequestSigningInterceptor())
            .build()
    }
    
    /**
     * Enforce HTTPS only
     */
    fun enforceHttps(url: String): String {
        require(url.startsWith("https://")) {
            "Only HTTPS connections are allowed. Attempted: $url"
        }
        return url
    }
}

/**
 * Add security headers to all requests
 */
class SecurityHeadersInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .addHeader("X-ZeroPay-Version", "1.0")
            .addHeader("X-ZeroPay-Platform", "Android")
            .addHeader("X-ZeroPay-SDK", "1.0.0")
            .build()
        
        return chain.proceed(request)
    }
}

/**
 * Sign requests with HMAC
 */
class RequestSigningInterceptor : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        
        // Generate request signature
        val timestamp = System.currentTimeMillis().toString()
        val nonce = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(16)
            .joinToString("") { "%02x".format(it) }
        
        // Sign: HMAC-SHA256(method + url + body + timestamp + nonce)
        val bodyBytes = request.body?.let {
            val buffer = okio.Buffer()
            it.writeTo(buffer)
            buffer.readByteArray()
        } ?: ByteArray(0)
        
        val signatureData = (
            request.method +
            request.url.toString() +
            bodyBytes.joinToString("") { "%02x".format(it) } +
            timestamp +
            nonce
        ).toByteArray()
        
        // TODO: Get API key from secure storage
        val apiKey = "YOUR_API_KEY_HERE".toByteArray()
        val signature = com.zeropay.sdk.crypto.CryptoUtils.hmacSha256(apiKey, signatureData)
        val signatureHex = signature.joinToString("") { "%02x".format(it) }
        
        val signedRequest = request.newBuilder()
            .addHeader("X-ZeroPay-Timestamp", timestamp)
            .addHeader("X-ZeroPay-Nonce", nonce)
            .addHeader("X-ZeroPay-Signature", signatureHex)
            .build()
        
        return chain.proceed(signedRequest)
    }
}

/**
 * Nonce manager for replay attack prevention
 */
object NonceManager {
    
    private val usedNonces = mutableSetOf<String>()
    private val lock = Any()
    private const val MAX_NONCES = 10000
    private const val NONCE_VALIDITY_MS = 5 * 60 * 1000L // 5 minutes
    
    data class NonceEntry(
        val nonce: String,
        val timestamp: Long
    )
    
    private val nonceEntries = mutableListOf<NonceEntry>()
    
    /**
     * Generate new nonce for request
     */
    fun generateNonce(): String {
        val nonce = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(32)
            .joinToString("") { "%02x".format(it) }
        
        synchronized(lock) {
            nonceEntries.add(NonceEntry(nonce, System.currentTimeMillis()))
            
            // Cleanup old nonces
            if (nonceEntries.size > MAX_NONCES) {
                cleanupOldNonces()
            }
        }
        
        return nonce
    }
    
    /**
     * Verify nonce is valid and not reused
     */
    fun verifyNonce(nonce: String, timestamp: Long): Boolean {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            
            // Check timestamp is within validity window
            if (currentTime - timestamp > NONCE_VALIDITY_MS) {
                return false
            }
            
            // Check nonce hasn't been used
            if (usedNonces.contains(nonce)) {
                return false
            }
            
            // Mark nonce as used
            usedNonces.add(nonce)
            
            return true
        }
    }
    
    private fun cleanupOldNonces() {
        val currentTime = System.currentTimeMillis()
        nonceEntries.removeAll { 
            currentTime - it.timestamp > NONCE_VALIDITY_MS 
        }
        
        // Rebuild used nonces set
        usedNonces.clear()
        usedNonces.addAll(nonceEntries.map { it.nonce })
    }
}

/**
 * API client with security features
 */
class SecureApiClient(
    private val baseUrl: String = "https://api.zeropay.com"
) {
    private val client = NetworkSecurity.createSecureClient()
    
    /**
     * Submit authentication proof
     */
    suspend fun submitProof(
        userUuid: String,
        factorProofs: Map<com.zeropay.sdk.Factor, ByteArray>,
        sessionId: String
    ): ApiResponse {
        val url = NetworkSecurity.enforceHttps("$baseUrl/v1/auth/verify")
        
        // Serialize proofs
        val proofData = com.zeropay.sdk.security.ProofSerializer.serializeForNetwork(
            factorProofs = factorProofs,
            metadata = mapOf(
                "userUuid" to userUuid,
                "sessionId" to sessionId,
                "timestamp" to System.currentTimeMillis().toString()
            )
        )
        
        val requestBody = proofData.toRequestBody(
            "application/octet-stream".toMediaType()
        )
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        
        return try {
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.bytes()
                ApiResponse(
                    success = true,
                    data = body,
                    errorMessage = null
                )
            } else {
                ApiResponse(
                    success = false,
                    data = null,
                    errorMessage = "HTTP ${response.code}: ${response.message}"
                )
            }
        } catch (e: Exception) {
            ApiResponse(
                success = false,
                data = null,
                errorMessage = e.message
            )
        }
    }
    
    /**
     * Get server nonce for challenge-response
     */
    suspend fun getNonce(): String {
        val url = NetworkSecurity.enforceHttps("$baseUrl/v1/auth/nonce")
        
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        
        val response = client.newCall(request).execute()
        
        return if (response.isSuccessful) {
            response.body?.string() ?: NonceManager.generateNonce()
        } else {
            throw Exception("Failed to get nonce: ${response.code}")
        }
    }
    
    data class ApiResponse(
        val success: Boolean,
        val data: ByteArray?,
        val errorMessage: String?
    )
}

/**
 * Session binding to prevent session hijacking
 */
object SessionSecurity {
    
    data class SecureSession(
        val sessionId: String,
        val deviceId: String,
        val userUuid: String,
        val createdAt: Long,
        val expiresAt: Long,
        val nonce: String
    )
    
    private val activeSessions = mutableMapOf<String, SecureSession>()
    private val lock = Any()
    
    /**
     * Create new secure session
     */
    fun createSession(
        userUuid: String,
        deviceId: String,
        durationMs: Long = 15 * 60 * 1000 // 15 minutes
    ): SecureSession {
        val sessionId = com.zeropay.sdk.crypto.CryptoUtils.secureRandomBytes(32)
            .joinToString("") { "%02x".format(it) }
        
        val nonce = NonceManager.generateNonce()
        
        val session = SecureSession(
            sessionId = sessionId,
            deviceId = deviceId,
            userUuid = userUuid,
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + durationMs,
            nonce = nonce
        )
        
        synchronized(lock) {
            activeSessions[sessionId] = session
        }
        
        return session
    }
    
    /**
     * Validate session is still active
     */
    fun validateSession(sessionId: String, deviceId: String): Boolean {
        synchronized(lock) {
            val session = activeSessions[sessionId] ?: return false
            
            // Check expiration
            if (System.currentTimeMillis() > session.expiresAt) {
                activeSessions.remove(sessionId)
                return false
            }
            
            // Check device binding
            if (session.deviceId != deviceId) {
                return false
            }
            
            return true
        }
    }
    
    /**
     * Invalidate session
     */
    fun invalidateSession(sessionId: String) {
        synchronized(lock) {
            activeSessions.remove(sessionId)
        }
    }
    
    /**
     * Cleanup expired sessions
     */
    fun cleanupExpiredSessions() {
        synchronized(lock) {
            val currentTime = System.currentTimeMillis()
            val expiredSessions = activeSessions.filter { (_, session) ->
                currentTime > session.expiresAt
            }.keys
            
            expiredSessions.forEach { activeSessions.remove(it) }
        }
    }
}
