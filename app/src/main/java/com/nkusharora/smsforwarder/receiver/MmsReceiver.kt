package com.nkusharora.smsforwarder.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nkusharora.smsforwarder.util.Logr

/**
 * Receives WAP_PUSH_DELIVER. Required for the default SMS app role but MMS
 * forwarding is intentionally not implemented — the use case is SMS forwarding.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Logr.d("WAP_PUSH_DELIVER action=${intent.action} type=${intent.type}")
    }
}
