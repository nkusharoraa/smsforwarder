package com.nkusharora.smsforwarder.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "smsforwarder"

    const val KEY_DEST = "destination_number"
    const val KEY_ENABLED = "forwarding_enabled"
    const val KEY_INCLUDE_SENDER = "include_sender"
    const val KEY_INCLUDE_SIM = "include_sim"
    const val KEY_BLOCKED = "blocked_senders"

    fun get(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun destination(ctx: Context): String? =
        get(ctx).getString(KEY_DEST, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun isEnabled(ctx: Context): Boolean = get(ctx).getBoolean(KEY_ENABLED, true)
    fun includeSender(ctx: Context): Boolean = get(ctx).getBoolean(KEY_INCLUDE_SENDER, true)
    fun includeSim(ctx: Context): Boolean = get(ctx).getBoolean(KEY_INCLUDE_SIM, true)

    fun blocked(ctx: Context): List<String> =
        get(ctx).getString(KEY_BLOCKED, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}
