package com.zeropay.sdk.crypto

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object CryptoUtils {
    
    actual fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data)
    }
    
    actual fun secureRandomBytes(size: Int): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
    
    actual fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }
    
    actual fun floatToBytes(value: Float): ByteArray {
        return ByteBuffer.allocate(4).putFloat(value).array()
    }
    
    actual fun longToBytes(value: Long): ByteArray {
        return ByteBuffer.allocate(8).putLong(value).array()
    }
}
