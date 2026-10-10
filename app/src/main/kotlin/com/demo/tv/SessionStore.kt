package com.demo.tv

import android.content.Context
import com.demo.xtream.Credentials

/** Remembers the last successful login so the app opens straight to the channel list. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Credentials? {
        val url = prefs.getString("url", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = prefs.getString("pass", null) ?: return null
        return Credentials(url, user, pass)
    }

    fun save(c: Credentials) {
        prefs.edit().putString("url", c.baseUrl).putString("user", c.username).putString("pass", c.password).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    /** Last-watched positions for movies/episodes, keyed by stream URL. */
    fun resumeMs(key: String): Long = prefs.getLong("pos_$key", 0L)

    fun saveResumeMs(key: String, ms: Long) {
        prefs.edit().apply { if (ms > 10_000) putLong("pos_$key", ms) else remove("pos_$key") }.apply()
    }
}
