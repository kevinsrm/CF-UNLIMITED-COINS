/*
 * The full-screen overlay has been disabled so it cannot obstruct the keyboard
 * or the device's recovery controls. Keep the service class for compatibility
 * with previously installed versions and persisted service intents.
 */
package com.cf.kiosk

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class OverlayGuard : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        fun start(context: Context) = stop(context)

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OverlayGuard::class.java)) }
        }
    }
}
