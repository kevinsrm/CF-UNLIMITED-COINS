/*
 * lang: Kotlin
 * file: KeepAliveService.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: foreground anchor, keeps the lock surface reachable after boot and screen-off
 */
package com.cf.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFY_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Prefs(this).locked) {
            val launch = Intent(this, LockActivity::class.java)
            launch.putExtra(LockActivity.EXTRA_ARM, false)
            launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            runCatching { startActivity(launch) }
        }
        return START_STICKY
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "CF LOCK", NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
        nm.createNotificationChannel(channel)
    }

    private fun notification(): Notification {
        channel()
        val launch = Intent(this, LockActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pending = PendingIntent.getActivity(this, 0, launch, flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("CF LOCK")
            .setContentText("Dispositivo bloqueado.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "cf_lock"
        private const val NOTIFY_ID = 4301

        /** shared channel + notification, used by the overlay service as well */
        fun notificationFor(context: android.content.Context): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "CF LOCK", NotificationManager.IMPORTANCE_LOW)
                channel.setShowBadge(false)
                channel.lockscreenVisibility = Notification.VISIBILITY_SECRET
                nm.createNotificationChannel(channel)
            }
            val launch = Intent(context, LockActivity::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getActivity(context, 0, launch, flags)
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(context, CHANNEL_ID)
            else
                @Suppress("DEPRECATION") Notification.Builder(context)
            return builder
                .setContentTitle("CF LOCK")
                .setContentText("Dispositivo bloqueado.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
        }

        fun start(context: android.content.Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
