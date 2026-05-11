package com.nkusharora.smsforwarder.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * Required for the default SMS app role (ACTION_SENDTO with sms:/smsto:/mms:/mmsto:).
 * This app is background-only and does not provide a compose UI — the activity
 * informs the user and finishes.
 */
class ComposeSmsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(
            this,
            "SMS Forwarder runs in the background. Use another app to compose messages.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }
}
