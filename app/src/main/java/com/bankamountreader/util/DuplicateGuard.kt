package com.bankamountreader.util

import android.content.Context
import android.content.SharedPreferences

/**
 * DuplicateGuard — ป้องกันการส่งยอดเงินซ้ำจาก notification เดิม
 * TTL 24 ชั่วโมง, max 500 entries
 */
class DuplicateGuard(context: Context) {

    companion object {
        private const val PREFS_NAME  = "duplicate_guard"
        private const val TTL_MS      = 24 * 60 * 60 * 1000L
        private const val MAX_ENTRIES = 500
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun generateId(packageName: String, postTime: Long, title: String, text: String): String =
        "$packageName|$postTime|$title|$text".hashCode().toString(16)

    fun isDuplicate(id: String): Boolean {
        val seenAt = prefs.getLong(id, -1L)
        if (seenAt == -1L) return false
        if (System.currentTimeMillis() - seenAt > TTL_MS) {
            prefs.edit().remove(id).apply()
            return false
        }
        return true
    }

    fun markSeen(id: String) {
        val editor = prefs.edit()
        if (prefs.all.size >= MAX_ENTRIES) editor.clear()
        editor.putLong(id, System.currentTimeMillis()).apply()
    }
}
