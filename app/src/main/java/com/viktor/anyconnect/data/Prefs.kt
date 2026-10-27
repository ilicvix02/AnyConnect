package com.viktor.anyconnect.data

import android.content.Context

/** Stores the API key and model on the device only. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("anyconnect", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("key", "") ?: ""
        set(v) { sp.edit().putString("key", v).apply() }

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { sp.edit().putString("model", v).apply() }
}
