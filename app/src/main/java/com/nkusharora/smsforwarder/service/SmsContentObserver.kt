package com.nkusharora.smsforwarder.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.provider.Telephony
import com.nkusharora.smsforwarder.forward.SmsForwarder
import com.nkusharora.smsforwarder.util.Dedup
import com.nkusharora.smsforwarder.util.Logr

/**
 * Watches content://sms for new rows. This is the safety net that catches
 * messages which other apps may have aborted via the SMS_RECEIVED broadcast,
 * or which arrive via OEM-specific channels that bypass standard receivers.
 *
 * When this app is the default SMS app, SMS_DELIVER already captures everything
 * and Dedup prevents double-forwarding here.
 */
class SmsContentObserver(private val ctx: Context) {

    private val thread = HandlerThread("sms-observer").apply { start() }
    private val handler = Handler(thread.looper)
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scanInbox()
        }
    }

    @Volatile
    private var lastDate: Long = System.currentTimeMillis()

    fun register() {
        ctx.contentResolver.registerContentObserver(
            Uri.parse("content://sms"),
            true,
            observer
        )
        Logr.d("ContentObserver registered on content://sms")
    }

    fun unregister() {
        try {
            ctx.contentResolver.unregisterContentObserver(observer)
        } catch (_: Throwable) {
        }
        thread.quitSafely()
    }

    private fun scanInbox() {
        try {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.SUBSCRIPTION_ID
                ),
                "${Telephony.Sms.DATE} > ?",
                arrayOf(lastDate.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { c ->
                val ai = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bi = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val di = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val si = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
                while (c.moveToNext()) {
                    val sender = c.getString(ai) ?: "unknown"
                    val body = c.getString(bi).orEmpty()
                    val ts = c.getLong(di)
                    val sub = if (si >= 0) c.getInt(si) else -1
                    if (ts > lastDate) lastDate = ts
                    if (Dedup.shouldProcess(sender, body, ts)) {
                        Logr.i("Observer caught SMS from=$sender (subId=$sub)")
                        SmsForwarder.forward(ctx, sender, body, sub)
                    }
                }
            }
        } catch (t: Throwable) {
            Logr.w("Observer query failed", t)
        }
    }
}
