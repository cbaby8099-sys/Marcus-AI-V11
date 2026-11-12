package com.marcusai.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.view.View

/** Small, user-started floating shortcut. It only opens Marcus; it does not secretly control other apps. */
class FloatingAssistantService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble != null) return START_NOT_STICKY
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val view = TextView(this).apply {
            text = "M"
            textSize = 23f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            elevation = 16f
            contentDescription = "Open Marcus assistant"
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(android.graphics.Color.rgb(36, 215, 245), android.graphics.Color.rgb(12, 82, 126))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke((2 * density).toInt(), android.graphics.Color.WHITE)
            }
            setOnClickListener {
                val launch = Intent(this@FloatingAssistantService, MainActivity::class.java)
                    .putExtra("start_voice", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launch)
            }
            setOnLongClickListener { stopSelf(); true }
        }
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                   else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL; x = 18; y = 0 }
        runCatching { windowManager?.addView(view, params); bubble = view }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
