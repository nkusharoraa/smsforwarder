package com.nkusharora.smsforwarder.forward

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.nkusharora.smsforwarder.data.Prefs
import com.nkusharora.smsforwarder.util.Logr

object SmsForwarder {

    private const val FORWARD_PREFIX = "[FWD]"
    private val DIGITS = Regex("\\D")

    fun forward(ctx: Context, sender: String, body: String, subId: Int = -1) {
        val dest = Prefs.destination(ctx) ?: run {
            Logr.w("No destination set; SMS from $sender not forwarded.")
            return
        }
        if (!Prefs.isEnabled(ctx)) {
            Logr.d("Forwarding disabled; SMS from $sender not forwarded.")
            return
        }
        if (sender.startsWith(FORWARD_PREFIX) || body.startsWith(FORWARD_PREFIX)) {
            Logr.d("Loop guard tripped on message from $sender")
            return
        }
        val senderDigits = sender.replace(DIGITS, "")
        val destDigits = dest.replace(DIGITS, "")
        if (senderDigits.isNotEmpty() && destDigits.isNotEmpty() &&
            (senderDigits.endsWith(destDigits) || destDigits.endsWith(senderDigits))
        ) {
            Logr.d("Sender equals destination; suppressing to avoid loop.")
            return
        }
        for (b in Prefs.blocked(ctx)) {
            if (sender.contains(b, ignoreCase = true)) {
                Logr.d("Sender '$sender' matches block entry '$b'; skipping.")
                return
            }
        }

        val header = buildString {
            append(FORWARD_PREFIX).append(' ')
            if (Prefs.includeSender(ctx)) append("From ").append(sender)
            if (Prefs.includeSim(ctx) && subId >= 0) append(" (subId=").append(subId).append(')')
            append('\n')
        }
        val payload = header + body

        val mgr = smsManager(ctx, subId)
        val parts = try {
            mgr.divideMessage(payload)
        } catch (t: Throwable) {
            Logr.e("divideMessage failed", t)
            return
        }
        try {
            mgr.sendMultipartTextMessage(dest, null, parts, null, null)
            Logr.i("Forwarded ${parts.size} part(s) (${payload.length} chars) from $sender -> $dest")
        } catch (t: Throwable) {
            Logr.e("sendMultipartTextMessage failed", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(ctx: Context, subId: Int): SmsManager {
        if (Build.VERSION.SDK_INT >= 31) {
            val base = ctx.getSystemService(SmsManager::class.java)
            return if (subId >= 0) base.createForSubscriptionId(subId) else base
        }
        return if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId)
        else SmsManager.getDefault()
    }

    // Reserved for future delivery-report tracking; unused for now.
    @Suppress("unused")
    private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0, Intent("com.nkusharora.smsforwarder.SENT"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
