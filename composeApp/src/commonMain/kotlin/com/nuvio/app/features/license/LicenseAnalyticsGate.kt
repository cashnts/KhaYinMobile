package com.nuvio.app.features.license

/**
 * Decides whether the client should keep posting telemetry to the `license_analytics` endpoint.
 *
 * A permanent client rejection (HTTP 4xx) pauses further posts for that license key and event, so
 * the app does not repeat a request the server keeps refusing on every 15-second heartbeat. A
 * successful post, a different license key or event, or a transient failure keeps the endpoint
 * eligible. The pause is tracked per (license key, event), so a rejection of one telemetry event
 * does not mute another.
 */
internal class LicenseAnalyticsGate {
    private val paused = mutableSetOf<String>()

    /** Returns true when a telemetry post for [licenseKey] and [event] should be attempted. */
    fun shouldPost(licenseKey: String, event: String): Boolean = token(licenseKey, event) !in paused

    /**
     * Records the HTTP [status] of a completed post for [licenseKey] and [event] and returns how it
     * was classified. A permanent rejection pauses posts for this pair; a success re-enables them.
     */
    fun onResult(licenseKey: String, event: String, status: Int): Outcome {
        val outcome = classify(status)
        val token = token(licenseKey, event)
        when (outcome) {
            Outcome.ACCEPTED -> paused.remove(token)
            Outcome.REJECTED_PERMANENT -> paused.add(token)
            Outcome.RETRYABLE -> Unit
        }
        return outcome
    }

    /** Clears every pause so the next post is attempted, for example after a fresh activation. */
    fun reset() {
        paused.clear()
    }

    private fun token(licenseKey: String, event: String): String = "$licenseKey $event"

    enum class Outcome { ACCEPTED, REJECTED_PERMANENT, RETRYABLE }

    companion object {
        /**
         * Classifies an HTTP status. A 4xx (except 408 timeout and 429 too-many-requests) is a
         * permanent rejection the client cannot fix by resending the same request. Everything else
         * is accepted (2xx) or worth a later retry.
         */
        fun classify(status: Int): Outcome = when {
            status in 200..299 -> Outcome.ACCEPTED
            status == 408 || status == 429 -> Outcome.RETRYABLE
            status in 400..499 -> Outcome.REJECTED_PERMANENT
            else -> Outcome.RETRYABLE
        }
    }
}
