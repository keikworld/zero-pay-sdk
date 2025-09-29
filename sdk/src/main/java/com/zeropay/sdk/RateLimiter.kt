package com.zeropay.sdk

import java.util.concurrent.TimeUnit

object RateLimiter {

    private val attempts = mutableMapOf<String, Int>()
    private val startTime = System.currentTimeMillis()

    fun check(uidHash: String): RateResult {
        val key = "attempts:$uidHash:${day()}"
        val count = attempts.getOrDefault(key, 0)
        if (count >= 20) return RateResult.BLOCKED_24H

        val fails = attempts.getOrDefault("fails:$uidHash", 0)
        return when {
            fails >= 10 -> RateResult.FROZEN_FRAUD
            fails >= 8  -> RateResult.COOL_DOWN_4H
            fails >= 5  -> RateResult.COOL_DOWN_15M
            else        -> RateResult.OK
        }
    }

    fun recordFail(uidHash: String) {
        attempts["fails:$uidHash"] = attempts.getOrDefault("fails:$uidHash", 0) + 1
    }

    fun resetFails(uidHash: String) {
        attempts.remove("fails:$uidHash")
    }

    private fun day() = (System.currentTimeMillis() / TimeUnit.DAYS.toMillis(1)).toString()

    enum class RateResult { OK, COOL_DOWN_15M, COOL_DOWN_4H, FROZEN_FRAUD, BLOCKED_24H }
}