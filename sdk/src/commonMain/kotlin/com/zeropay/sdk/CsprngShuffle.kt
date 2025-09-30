package com.zeropay.sdk

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CsprngShuffle {

    fun <T> shuffle(list: List<T>): List<T> { // Made generic
        val seed = SecureRandom.getSeed(32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        val hash = mac.doFinal(System.currentTimeMillis().toString().toByteArray())
        val random = SecureRandom(hash)
        // The .shuffled(random) extension function on List will work correctly
        // with a List<T> and return a List<T>.
        return list.shuffled(random)
    }
}