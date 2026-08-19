package com.bankamountreader.util

import android.content.Context
import android.content.SharedPreferences

/**
 * DuplicateGuard — ป้องกันการส่งยอดเงินซ้ำจากการแจ้งเตือนเดิม
 *
 * เก็บ ID ของ notification ที่เคยส่งแล้วใน SharedPreferences
 * ID สร้างจาก packageName + postTime + title + text → SHA-like hash
 *
 * TTL: ลบ entry ที่เกิน 24 ชั่วโมง เพื่อไม่ให้ storage โตไม่หยุด
 */
class DuplicateGuard(context: Context) {

    companion object {
        private const val PREFS_NAME = "duplicate_guard"
        private const val TTL_MS     = 24 * 60 * 60 * 1000L  // 24 ชั่วโมง
        private const val MAX_ENTRIES = 500
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * generateId — สร้าง ID จาก parameter ที่ระบุ
     * ใช้ hashCode ง่าย ๆ — ไม่ต้องการ crypto-grade uniqueness
     */
    fun generateId(
        packageName: String,
        postTime: Long,
        title: String,
        text: String,
    ): String {
        val raw = "$packageName|$postTime|$title|$text"
        return raw.hashCode().toString(16)
    }

    /**
     * isDuplicate — คืน true ถ้า ID นี้เคย markSeen แล้ว และยังไม่หมด TTL
     */
    fun isDuplicate(id: String): Boolean {
        val seenAt = prefs.getLong(id, -1L)
        if (seenAt == -1L) return false
        if (System.currentTimeMillis() - seenAt > TTL_MS) {
            // หมด TTL → ถือว่าไม่ใช่ duplicate แล้ว ลบทิ้ง
            prefs.edit().remove(id).apply()
            return false
        }
        return true
    }

    /**
     * markSeen — บันทึก ID นี้ว่าส่งแล้ว
     * ถ้า entries เต็ม → clear all (simple eviction)
     */
    fun markSeen(id: String) {
        val editor = prefs.edit()
        if (prefs.all.size >= MAX_ENTRIES) {
            editor.clear()
        }
        editor.putLong(id, System.currentTimeMillis()).apply()
    }
}
