/*
 * lang: Kotlin
 * file: BootReceiver.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: reboot anchor, package-replace anchor, daily re-arm
 */
package com.cf.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val p = Prefs(context)
                p.bootCount = p.bootCount + 1
                if (!p.locked) return
                KeepAliveService.start(context)
                OverlayGuard.start(context)
                schedule(context)
                val launch = Intent(context, LockActivity::class.java)
                launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                runCatching { context.startActivity(launch) }
            }
            ACTION_TICK -> {
                val p = Prefs(context)
                if (!p.locked) return
                KeepAliveService.start(context)
                OverlayGuard.start(context)
                val launch = Intent(context, LockActivity::class.java)
                launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                runCatching { context.startActivity(launch) }
            }
        }
    }

    companion object {
        const val ACTION_TICK = "com.cf.kiosk.TICK"
        private const val TICK_MS = 15L * 60L * 1000L

        /** inexact repeating wake-up, the surface gets re-asserted even if nothing else fires */
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val tick = Intent(context, BootReceiver::class.java)
            tick.action = ACTION_TICK
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getBroadcast(context, 9101, tick, flags)
            runCatching {
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + TICK_MS,
                    TICK_MS,
                    pending
                )
            }
        }
    }
}

/** re-surfaces the kiosk whenever the panel wakes or the device is unlocked */
class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).locked) return
        val launch = Intent(context, LockActivity::class.java)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        runCatching { context.startActivity(launch) }
    }
}
