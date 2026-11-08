/*
 * lang: Kotlin
 * file: LockActivity.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: single visible surface while the device is armed
 */
package com.cf.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class LockActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var field: EditText
    private lateinit var note: TextView
    private val ui = Handler(Looper.getMainLooper())

    private val rearm = object : Runnable {
        override fun run() {
            if (prefs.locked) {
                Shield.hideBars(this@LockActivity)
                ui.postDelayed(this, 1500L)
            }
        }
    }

    private val cooling = object : Runnable {
        override fun run() {
            val left = prefs.cooldownUntil - System.currentTimeMillis()
            if (left > 0L) {
                note.text = "Too many attempts. Next try in " + ((left + 999L) / 1000L) + "s"
                ui.postDelayed(this, 500L)
            } else {
                note.text = ""
                ui.removeCallbacks(this)
            }
        }
    }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        prefs = Prefs(this)
        alive = this
        if (intent.getBooleanExtra(EXTRA_ARM, false)) prefs.locked = true
        if (!prefs.locked) {
            showUnlocked()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Shield.applyKiosk(this)
        buildUi()
        OverlayGuard.start(this)
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if (newIntent.getBooleanExtra(EXTRA_ARM, false)) prefs.locked = true
        if (!prefs.locked) showUnlocked()
    }

    override fun onResume() {
        super.onResume()
        if (!prefs.locked) return
        Shield.applyKiosk(this)
        Shield.wake(this)
        Shield.lockTask(this)
        ui.removeCallbacks(rearm)
        ui.postDelayed(rearm, 200L)
        ui.removeCallbacks(cooling)
        ui.post(cooling)
        field.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        if (prefs.locked) ui.postDelayed({ bringForward() }, 60L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && prefs.locked) Shield.hideBars(this)
    }

    @Deprecated("older builds still route back through this")
    override fun onBackPressed() {
        if (prefs.locked) bringForward() else super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (prefs.locked) bringForward()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        if (alive === this) alive = null
        super.onDestroy()
    }

    private fun bringForward() {
        if (!prefs.locked) return
        val again = Intent(this, LockActivity::class.java)
        again.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        runCatching { startActivity(again) }
        Shield.hideBars(this)
        Shield.lockTask(this)
    }

    private fun shell(titleText: String, bodyText: String, accent: Int): LinearLayout {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad * 2, pad, pad * 2, pad)
        root.setBackgroundColor(0xFF0B0B0B.toInt())

        val title = TextView(this)
        title.text = titleText
        title.setTextColor(accent)
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        root.addView(title)

        val body = TextView(this)
        body.text = bodyText
        body.setTextColor(0xFFEDEDED.toInt())
        body.textSize = 17f
        body.gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = (pad).toInt()
        root.addView(body, lp)
        return root
    }

    private fun buildUi() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = shell(
            "CF LOCK",
            "O dispositivo esta bloqueado.\nDigite a senha correta para desbloquear.",
            0xFFFF3B30.toInt()
        )

        field = EditText(this)
        field.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        field.transformationMethod = PasswordTransformationMethod()
        field.setTextColor(0xFFFFFFFF.toInt())
        field.setHintTextColor(0xFF777777.toInt())
        field.hint = "senha"
        field.gravity = Gravity.CENTER
        field.textSize = 22f
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        field.setSingleLine(true)
        field.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { tryCode(); true } else false
        }
        val fParams = LinearLayout.LayoutParams(pad * 10, ViewGroup.LayoutParams.WRAP_CONTENT)
        fParams.topMargin = pad
        root.addView(field, fParams)

        val button = Button(this)
        button.text = "Desbloquear"
        button.setOnClickListener { tryCode() }
        val bParams = LinearLayout.LayoutParams(pad * 10, ViewGroup.LayoutParams.WRAP_CONTENT)
        bParams.topMargin = pad
        root.addView(button, bParams)

        note = TextView(this)
        note.setTextColor(0xFFFFB020.toInt())
        note.textSize = 15f
        note.gravity = Gravity.CENTER
        val nParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        nParams.topMargin = pad
        root.addView(note, nParams)

        setContentView(root)
    }

    private fun showUnlocked() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = shell(
            "CF LOCK",
            "Dispositivo liberado.",
            0xFF35C759.toInt()
        )
        val home = Button(this)
        home.text = "Restaurar tela inicial"
        home.setOnClickListener {
            runCatching { packageManager.clearPackagePreferredActivities(packageName) }
            runCatching { startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
        }
        val hp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        hp.topMargin = pad
        root.addView(home, hp)

        val arm = Button(this)
        arm.text = "Bloquear de novo"
        arm.setOnClickListener {
            prefs.locked = true
            prefs.fails = 0
            prefs.cooldownUntil = 0L
            val again = Intent(this, LockActivity::class.java)
            again.putExtra(EXTRA_ARM, true)
            again.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(again)
        }
        val ap = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        ap.topMargin = pad
        root.addView(arm, ap)

        setContentView(root)
        ui.removeCallbacksAndMessages(null)
        Shield.stopLockTask(this)
        stopService(Intent(this, KeepAliveService::class.java))
    }

    private fun tryCode() {
        val now = System.currentTimeMillis()
        if (prefs.cooldownUntil > now) {
            note.text = "Too many attempts."
            return
        }
        if (Gate.accepts(field.text.toString())) {
            unlock()
        } else {
            prefs.fails = prefs.fails + 1
            if (prefs.fails >= 5) {
                prefs.fails = 0
                prefs.cooldownUntil = now + 60000L
                note.text = "Too many attempts. Wait 60s."
                ui.post(cooling)
            } else {
                note.text = "Senha incorreta."
            }
            field.setText("")
        }
    }

    private fun unlock() {
        prefs.locked = false
        prefs.fails = 0
        prefs.cooldownUntil = 0L
        ui.removeCallbacksAndMessages(null)
        Shield.stopLockTask(this)
        Shield.hideBars(this)
        OverlayGuard.stop(this)
        stopService(Intent(this, KeepAliveService::class.java))
        // give back the home slot when the system lets us, otherwise the panel offers the picker
        runCatching { packageManager.clearPackagePreferredActivities(packageName) }
        if (Shield.isHome(this)) {
            showUnlocked()
        } else {
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            runCatching { startActivity(home) }
            finishAndRemoveTask()
        }
    }

    companion object {
        const val EXTRA_ARM = "com.cf.kiosk.ARM"
        private var alive: LockActivity? = null

        /** called by the overlay once the correct key is entered */
        fun release(context: Context) {
            val prefs = Prefs(context)
            prefs.locked = false
            prefs.fails = 0
            prefs.cooldownUntil = 0L
            OverlayGuard.stop(context)
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
            runCatching { context.packageManager.clearPackagePreferredActivities(context.packageName) }
            alive?.let { panel ->
                alive = null
                runCatching { panel.finishAndRemoveTask() }
            }
            val home = Intent(Intent.ACTION_MAIN)
            home.addCategory(Intent.CATEGORY_HOME)
            home.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            runCatching { context.startActivity(home) }
        }
    }
}
