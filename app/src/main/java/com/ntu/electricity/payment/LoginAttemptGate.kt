package com.ntu.electricity.payment

import java.security.MessageDigest

/** One automatic attempt per account in ten minutes, shared and persisted before clicking. */
class LoginAttemptGate(
    private val read: (String) -> Long,
    private val write: (String, Long) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val attemptsInThisProcess = mutableMapOf<String, Long>()
    private fun key(account: String): String = MessageDigest.getInstance("SHA-256")
        .digest(account.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Synchronized fun canAttempt(account: String): Boolean {
        val accountKey = key(account)
        val previous = maxOf(read(accountKey), attemptsInThisProcess[accountKey] ?: 0L)
        return previous == 0L || now() - previous >= COOLDOWN_MS
    }

    @Synchronized fun reserve(account: String): Boolean {
        if (!canAttempt(account)) return false
        val timestamp = now()
        val accountKey = key(account)
        attemptsInThisProcess[accountKey] = timestamp
        return write(accountKey, timestamp)
    }
    @Synchronized fun requireManual(account: String) {
        val accountKey = key(account)
        val timestamp = now()
        attemptsInThisProcess[accountKey] = timestamp
        write(accountKey, timestamp)
    }

    companion object { const val COOLDOWN_MS = 10 * 60 * 1000L }
}
