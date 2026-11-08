/*
 * lang: Kotlin
 * file: OverlayGuard.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: window type 2038 surface, the only user-space layer that sits above the shade
 */
package com.cf.kiosk

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class OverlayGuard : Service() {

    private var wm: WindowManager? = null
    private var panel: View? = null
    private var note: TextView? = null
    private var field: EditText? = null
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFY_ID, KeepAliveService.notificationFor(this))
        if (Prefs(this).locked) attach()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Prefs(this).locked) attach()
        return START_STICKY
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun attach() {
        if (panel != null) return
        if (!allowed(this)) return
        val window = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = window

        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad * 2, pad, pad * 2, pad)
        root.setBackgroundColor(0xF2111111.toInt())
        root.isClickable = true
        root.isFocusable = true

        val title = TextView(this)
        title.text = "CF LOCK"
        title.setTextColor(0xFFFF3B30.toInt())
        title.textSize = 30f
        title.gravity = Gravity.CENTER
        root.addView(title)

        val body = TextView(this)
        body.text = "O dispositivo esta bloqueado.\nDigite a senha correta para desbloquear."
        body.setTextColor(0xFFEDEDED.toInt())
        body.textSize = 17f
        body.gravity = Gravity.CENTER
        val bp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bp.topMargin = pad
        root.addView(body, bp)

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        input.transformationMethod = PasswordTransformationMethod()
        input.setTextColor(Color.WHITE)
        input.setHintTextColor(0xFF777777.toInt())
        input.hint = "senha"
        input.gravity = Gravity.CENTER
        input.textSize = 22f
        input.setSingleLine(true)
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { attempt(); true } else false
        }
        val ip = LinearLayout.LayoutParams(pad * 10, ViewGroup.LayoutParams.WRAP_CONTENT)
        ip.topMargin = pad
        root.addView(input, ip)
        field = input

        val button = Button(this)
        button.text = "Desbloquear"
        button.setOnClickListener { attempt() }
        val gp = LinearLayout.LayoutParams(pad * 10, ViewGroup.LayoutParams.WRAP_CONTENT)
        gp.topMargin = pad
        root.addView(button, gp)

        val status = TextView(this)
        status.setTextColor(0xFFFFB020.toInt())
        status.textSize = 15f
        status.gravity = Gravity.CENTER
        val sp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        sp.topMargin = pad
        root.addView(status, sp)
        note = status

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SECURE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        params.screenBrightness = 0.4f

        runCatching { window.addView(root, params) }
            .onSuccess {
                panel = root
                input.requestFocus()
            }
    }

    private fun detach() {
        val view = panel ?: return
        runCatching { wm?.removeView(view) }
        panel = null
        ui.removeCallbacksAndMessages(null)
    }

    private fun attempt() {
        val prefs = Prefs(this)
        val now = System.currentTimeMillis()
        if (prefs.cooldownUntil > now) {
            note?.text = "Too many attempts."
            return
        }
        val input = field ?: return
        if (Gate.accepts(input.text.toString())) {
            prefs.locked = false
            prefs.fails = 0
            prefs.cooldownUntil = 0L
            detach()
            stopSelf()
            LockActivity.release(this)
        } else {
            prefs.fails = prefs.fails + 1
            if (prefs.fails >= 5) {
                prefs.fails = 0
                prefs.cooldownUntil = now + 60000L
                note?.text = "Too many attempts. Wait 60s."
            } else {
                note?.text = "Senha incorreta."
            }
            input.setText("")
        }
    }

    companion object {
        private const val NOTIFY_ID = 4302

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
            val intent = Intent(context, OverlayGuard::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OverlayGuard::class.java)) }
        }
    }
}
