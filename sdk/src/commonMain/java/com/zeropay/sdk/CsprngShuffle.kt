package com.zeropay.sdk

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CsprngShuffle {

    fun shuffle(list: List<Any>): List<Any> {
        val seed = SecureRandom.getSeed(32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed, "HmacSHA256"))
        val hash = mac.doFinal(System.currentTimeMillis().toString().toByteArray())
        val random = SecureRandom(hash)
        return list.shuffled(random)
    }
}