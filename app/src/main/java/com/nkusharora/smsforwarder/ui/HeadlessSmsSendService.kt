package com.nkusharora.smsforwarder.ui

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required for the default SMS app role (RESPOND_VIA_MESSAGE). This app does not
 * implement quick-reply, so we accept the intent and stop immediately.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
