package com.jefe.autowarm

import android.content.Context

/**
 * Lightweight SharedPreferences store for lifetime stats.
 * Keys are stable — don't rename them or old data is lost.
 */
class StatsStore(context: Context) {

    private val prefs = context.getSharedPreferences("jefe_stats", Context.MODE_PRIVATE)

    var totalScrolled: Int
        get() = prefs.getInt("total_scrolled", 0)
        set(v) = prefs.edit().putInt("total_scrolled", v).apply()

    var totalLiked: Int
        get() = prefs.getInt("total_liked", 0)
        set(v) = prefs.edit().putInt("total_liked", v).apply()

    var sessions: Int
        get() = prefs.getInt("sessions", 0)
        set(v) = prefs.edit().putInt("sessions", v).apply()

    fun addScrolled(n: Int) { totalScrolled += n }
    fun addLiked(n: Int)    { totalLiked    += n }
    fun addSession()        { sessions      += 1 }

    fun reset() {
        prefs.edit()
            .putInt("total_scrolled", 0)
            .putInt("total_liked", 0)
            .putInt("sessions", 0)
            .apply()
    }
}
