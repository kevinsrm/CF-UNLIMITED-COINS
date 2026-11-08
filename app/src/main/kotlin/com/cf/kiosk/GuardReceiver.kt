/*
 * lang: Kotlin
 * file: GuardReceiver.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: device-admin surface, uninstall shield while armed
 */
package com.cf.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class GuardReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Prefs(context).locked = true
        KeepAliveService.start(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // the admin bit is gone, the lock state is not: keep the panel honest
        Prefs(context).locked = true
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "CF LOCK permanece armado neste aparelho."
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        if (Prefs(context).locked) KeepAliveService.start(context)
    }
}
