/*
 * lang: Kotlin
 * file: Shield.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: home role, standalone power, uninterruptible idle, immersive kiosk surface
 */
package com.cf.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager

object Shield {

    const val FLAG_KIOSK: Int = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

    fun admin(context: Context): ComponentName =
        ComponentName(context.applicationContext, GuardReceiver::class.java)

    fun isAdmin(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return runCatching { dpm.isAdminActive(admin(context)) }.getOrDefault(false)
    }

    fun applyKiosk(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = FLAG_KIOSK
    }

    fun hideBars(activity: Activity) {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = FLAG_KIOSK
    }

    /** start task pinning without a chooser; older builds require a permission the app cannot hold */
    fun pin(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) return
        runCatching { activity.startLockTask() }
    }

    /** lock-task set from the admin surface, so the system stops honouring home and recents */
    fun lockTask(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (isAdmin(activity)) {
            val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            runCatching { dpm.setLockTaskPackages(admin(activity), arrayOf(activity.packageName)) }
            runCatching { activity.startLockTask() }
        } else {
            pin(activity)
        }
    }

    fun stopLockTask(activity: Activity) {
        runCatching { activity.stopLockTask() }
    }

    fun wake(activity: Activity) {
        val power = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "cf:kiosk"
        )
        runCatching { wl.acquire(2500L) }
    }

    fun kioskHomeIntent(): Intent {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        return intent
    }

    fun isHome(context: Context): Boolean {
        val intent = kioskHomeIntent()
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }
}
