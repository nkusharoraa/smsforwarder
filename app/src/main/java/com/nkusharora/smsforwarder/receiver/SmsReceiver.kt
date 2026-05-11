package com.nkusharora.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nkusharora.smsforwarder.forward.SmsForwarder
import com.nkusharora.smsforwarder.util.Dedup
import com.nkusharora.smsforwarder.util.Logr

/**
 * Fallback receiver for when this app is NOT the default SMS app.
 * Dedup ensures we don't double-forward when we *are* default (and would receive
 * both SMS_DELIVER and SMS_RECEIVED for the same message).
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != "android.intent.action.DATA_SMS_RECEIVED"
        ) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val first = messages[0]
        val sender = first.displayOriginatingAddress ?: first.originatingAddress ?: "unknown"
        val body = messages.joinToString(separator = "") { msg ->
            msg.displayMessageBody
                ?: msg.messageBody
                ?: runCatching { String(msg.userData ?: ByteArray(0)) }.getOrDefault("")
        }
        val ts = first.timestampMillis
        val subId = intent.getIntExtra("subscription", -1)

        Logr.i("SMS_RECEIVED from=$sender chars=${body.length} action=$action")

        if (Dedup.shouldProcess(sender, body, ts)) {
            SmsForwarder.forward(context, sender, body, subId)
        }
    }
}
