package com.nkusharora.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nkusharora.smsforwarder.forward.SmsForwarder
import com.nkusharora.smsforwarder.util.Dedup
import com.nkusharora.smsforwarder.util.Logr

/**
 * Receives SMS_DELIVER (only delivered to the default SMS app). This broadcast
 * cannot be aborted by other apps, which is why bank/OTP messages that some
 * forwarders miss still arrive here.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val first = messages[0]
        val sender = first.displayOriginatingAddress ?: first.originatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { msg ->
            msg.displayMessageBody ?: msg.messageBody.orEmpty()
        }
        val timestamp = first.timestampMillis
        val subId = intent.getIntExtra("subscription", -1)

        Logr.i("SMS_DELIVER from=$sender chars=${body.length} subId=$subId parts=${messages.size}")

        // We're the default SMS app — write the message to the system inbox so the
        // user's regular SMS reader (if any) still sees it.
        writeToInbox(context, sender, body, timestamp, subId)

        if (Dedup.shouldProcess(sender, body, timestamp)) {
            SmsForwarder.forward(context, sender, body, subId)
        }
    }

    private fun writeToInbox(ctx: Context, address: String, body: String, ts: Long, subId: Int) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, ts)
                put(Telephony.Sms.DATE_SENT, ts)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (subId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        } catch (t: Throwable) {
            Logr.w("writeToInbox failed", t)
        }
    }
}
