package com.zeropay.sdk.network

import com.zeropay.sdk.api.ApiConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ZeroPay HTTP Client Interface
 *
 * Multiplatform HTTP client for all API communication.
 *
 * Platform-specific implementations:
 * - Android: OkHttp (see androidMain)
 * - iOS: URLSession (future)
 * - Web: Fetch API (future)
 *
 * Security Features:
 * - TLS 1.3+ enforcement
 * - Certificate pinning
 * - Request signing
 * - Automatic retry with exponential backoff
 * - Request/response logging (redacted)
 * - Timeout configuration
 *
 * Zero-Knowledge:
 * - All sensitive data encrypted in transit (HTTPS)
 * - Never logs request bodies
 * - Nonce-based replay protection
 *
 * @version 1.0.0
 */
interface ZeroPayHttpClient {

    /**
     * Execute HTTP POST request
     *
     * @param endpoint API endpoint (relative to baseUrl)
     * @param body Request body (will be JSON serialized)
     * @param headers Additional headers
     * @return HttpResponse with deserialized body
     * @throws NetworkException on failure
     */
    suspend fun <T> post(
        endpoint: String,
        body: Any,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<T>

    /**
     * Execute HTTP GET request
     *
     * @param endpoint API endpoint (relative to baseUrl)
     * @param queryParams Query parameters
     * @param headers Additional headers
     * @return HttpResponse with deserialized body
     * @throws NetworkException on failure
     */
    suspend fun <T> get(
        endpoint: String,
        queryParams: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<T>

    /**
     * Execute HTTP PUT request
     */
    suspend fun <T> put(
        endpoint: String,
        body: Any,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<T>

    /**
     * Execute HTTP DELETE request
     */
    suspend fun <T> delete(
        endpoint: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<T>

    /**
     * Close client and release resources
     */
    fun close()
}

/**
 * HTTP Response wrapper
 */
data class HttpResponse<T>(
    val statusCode: Int,
    val body: T?,
    val headers: Map<String, String>,
    val requestId: String? = null
) {
    val isSuccessful: Boolean
        get() = statusCode in 200..299

    val isClientError: Boolean
        get() = statusCode in 400..499

    val isServerError: Boolean
        get() = statusCode in 500..599
}

/**
 * Network exception types
 */
sealed class NetworkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Network connectivity error
     */
    class ConnectivityException(
        message: String = "No internet connection",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * Timeout exception
     */
    class TimeoutException(
        message: String = "Request timed out",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * HTTP error (4xx, 5xx)
     */
    class HttpException(
        val statusCode: Int,
        message: String,
        val errorBody: String? = null,
        cause: Throwable? = null
    ) : NetworkException("HTTP $statusCode: $message", cause)

    /**
     * JSON parsing error
     */
    class SerializationException(
        message: String = "Failed to parse response",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * SSL/TLS error (certificate validation failure)
     */
    class SslException(
        message: String = "SSL/TLS error",
        cause: Throwable? = null
    ) : NetworkException(message, cause)

    /**
     * Rate limit exceeded
     */
    class RateLimitException(
        message: String = "Rate limit exceeded",
        val retryAfterSeconds: Int? = null
    ) : NetworkException(message)

    /**
     * Unknown error
     */
    class UnknownException(
        message: String = "Unknown network error",
        cause: Throwable? = null
    ) : NetworkException(message, cause)
}

/**
 * JSON configuration
 * Used across all network operations
 */
internal val json = Json {
    ignoreUnknownKeys = true // Forward compatibility
    prettyPrint = false
    isLenient = false // Strict parsing
    encodeDefaults = true
    coerceInputValues = false // Fail on invalid types
}

/**
 * Helper extension for safe JSON encoding
 */
internal inline fun <reified T> T.toJsonString(): String {
    return json.encodeToString(this)
}

/**
 * Helper extension for safe JSON decoding
 */
internal inline fun <reified T> String.fromJsonString(): T {
    return json.decodeFromString(this)
}
