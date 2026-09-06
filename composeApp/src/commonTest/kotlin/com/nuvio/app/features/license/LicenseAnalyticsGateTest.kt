package com.nuvio.app.features.license

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicenseAnalyticsGateTest {
    private val key = "KHAYIN-TEST-0001"
    private val heartbeat = "heartbeat"

    @Test
    fun `a rejected heartbeat pauses further heartbeat posts for the same license`() {
        val gate = LicenseAnalyticsGate()
        assertTrue(gate.shouldPost(key, heartbeat))

        val outcome = gate.onResult(key, heartbeat, 400)

        assertEquals(LicenseAnalyticsGate.Outcome.REJECTED_PERMANENT, outcome)
        assertFalse(gate.shouldPost(key, heartbeat))
    }

    @Test
    fun `a different license key is still posted after a rejection`() {
        val gate = LicenseAnalyticsGate()
        gate.onResult(key, heartbeat, 400)

        assertTrue(gate.shouldPost("KHAYIN-TEST-0002", heartbeat))
    }

    @Test
    fun `a rejection of one event does not mute another event`() {
        val gate = LicenseAnalyticsGate()
        gate.onResult(key, "activation", 400)

        assertTrue(gate.shouldPost(key, heartbeat))
    }

    @Test
    fun `a successful post re-enables a paused license`() {
        val gate = LicenseAnalyticsGate()
        gate.onResult(key, heartbeat, 400)
        assertFalse(gate.shouldPost(key, heartbeat))

        gate.onResult(key, heartbeat, 200)

        assertTrue(gate.shouldPost(key, heartbeat))
    }

    @Test
    fun `reset re-enables posting after a rejection`() {
        val gate = LicenseAnalyticsGate()
        gate.onResult(key, heartbeat, 400)

        gate.reset()

        assertTrue(gate.shouldPost(key, heartbeat))
    }

    @Test
    fun `transient failures keep the license eligible for retry`() {
        val gate = LicenseAnalyticsGate()

        assertEquals(LicenseAnalyticsGate.Outcome.RETRYABLE, gate.onResult(key, heartbeat, 500))
        assertTrue(gate.shouldPost(key, heartbeat))
        assertEquals(LicenseAnalyticsGate.Outcome.RETRYABLE, gate.onResult(key, heartbeat, 429))
        assertTrue(gate.shouldPost(key, heartbeat))
    }

    @Test
    fun `status classification separates permanent rejections from retryable failures`() {
        assertEquals(LicenseAnalyticsGate.Outcome.ACCEPTED, LicenseAnalyticsGate.classify(201))
        assertEquals(LicenseAnalyticsGate.Outcome.REJECTED_PERMANENT, LicenseAnalyticsGate.classify(400))
        assertEquals(LicenseAnalyticsGate.Outcome.REJECTED_PERMANENT, LicenseAnalyticsGate.classify(403))
        assertEquals(LicenseAnalyticsGate.Outcome.RETRYABLE, LicenseAnalyticsGate.classify(408))
        assertEquals(LicenseAnalyticsGate.Outcome.RETRYABLE, LicenseAnalyticsGate.classify(429))
        assertEquals(LicenseAnalyticsGate.Outcome.RETRYABLE, LicenseAnalyticsGate.classify(503))
    }
}
