package com.nkusharora.smsforwarder.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Deduplicates messages seen by more than one capture path (SMS_DELIVER, SMS_RECEIVED,
 * and the ContentObserver). Keyed by sender + body hash + second-precision timestamp.
 */
object Dedup {
    private const val WINDOW_MS = 120_000L
    private val seen = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun shouldProcess(sender: String, body: String, timestampMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value > WINDOW_MS) it.remove()
        }
        val key = "${sender.trim().lowercase()}|${timestampMs / 1000}|${body.hashCode()}"
        return seen.putIfAbsent(key, now) == null
    }
}
