/*
 * lang: Kotlin
 * file: Prefs.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: small persisted state table for CF Lock; survives reboot and app update
 */
package com.cf.kiosk

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("cf_state", Context.MODE_PRIVATE)

    var locked: Boolean
        get() = sp.getBoolean(KEY_LOCKED, false)
        set(value) { sp.edit().putBoolean(KEY_LOCKED, value).commit() }

    var fails: Int
        get() = sp.getInt(KEY_FAILS, 0)
        set(value) { sp.edit().putInt(KEY_FAILS, value).commit() }

    var bootCount: Int
        get() = sp.getInt(KEY_BOOTS, 0)
        set(value) { sp.edit().putInt(KEY_BOOTS, value).commit() }

    var cooldownUntil: Long
        get() = sp.getLong(KEY_COOLDOWN, 0L)
        set(value) { sp.edit().putLong(KEY_COOLDOWN, value).commit() }

    companion object {
        const val KEY_LOCKED = "locked"
        const val KEY_FAILS = "fails"
        const val KEY_BOOTS = "boots"
        const val KEY_COOLDOWN = "cool"
    }
}
