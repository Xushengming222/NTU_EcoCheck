package com.ntu.electricity.payment

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class LoginAttemptGateTest {
    @Test fun queryPaymentRefreshAndRestartShareThePersistedBudget() {
        val store = mutableMapOf<String, Long>()
        fun newGate() = LoginAttemptGate({ store[it] ?: 0L }, { key, value -> store[key] = value; true }, { 1000L })
        val first = newGate()
        assertTrue(first.reserve("student"))
        repeat(50) { assertFalse(first.reserve("student")) }
        val recreated = newGate()
        assertFalse(recreated.canAttempt("student"))
        assertFalse(recreated.reserve("student"))
        assertEquals(1, store.size)
        assertFalse(store.keys.any { it.contains("student") })
    }

    @Test fun challengeOrErrorRequiresManualLoginAcrossNewBrowsers() {
        val store = mutableMapOf<String, Long>()
        val gate = LoginAttemptGate({ store[it] ?: 0L }, { key, value -> store[key] = value; true }, { 1000L })
        gate.requireManual("student")
        assertFalse(gate.reserve("student"))
        assertFalse(gate.canAttempt("student"))
    }

    @Test fun expiredCooldownDoesNotUnlockAnotherAccountOrConsumeItsBudget() {
        var now = 1000L
        val store = mutableMapOf<String, Long>()
        val gate = LoginAttemptGate({ store[it] ?: 0L }, { key, value -> store[key] = value; true }, { now })
        assertTrue(gate.reserve("A")); assertFalse(gate.reserve("A"))
        assertTrue(gate.reserve("B"))
        now += LoginAttemptGate.COOLDOWN_MS - 1
        assertFalse(gate.reserve("A"))
        now++
        assertTrue(gate.reserve("A")); assertFalse(gate.reserve("A"))
    }

    @Test fun failedPersistenceNeverAuthorizesAnAutomaticClick() {
        val gate = LoginAttemptGate({ 0L }, { _, _ -> false }, { 1000L })
        assertFalse(gate.reserve("student"))
        assertFalse(gate.canAttempt("student"))
    }

    @Test fun simultaneousQueryAndPaymentMayReserveOnlyOnce() {
        val store = mutableMapOf<String, Long>()
        val gate = LoginAttemptGate({ store[it] ?: 0L }, { key, value -> store[key] = value; true }, { 1000L })
        val start = CountDownLatch(1)
        val winners = AtomicInteger()
        val threads = List(20) { Thread { start.await(); if (gate.reserve("student")) winners.incrementAndGet() } }
        threads.forEach { it.start() }; start.countDown(); threads.forEach { it.join() }
        assertEquals(1, winners.get())
    }

    @Test fun backwardsClockDoesNotCauseImmediateAutomaticRetry() {
        var now = 1000L
        val store = mutableMapOf<String, Long>()
        val gate = LoginAttemptGate({ store[it] ?: 0L }, { key, value -> store[key] = value; true }, { now })
        assertTrue(gate.reserve("student")); now = 500L
        assertFalse(gate.reserve("student"))
    }
}
