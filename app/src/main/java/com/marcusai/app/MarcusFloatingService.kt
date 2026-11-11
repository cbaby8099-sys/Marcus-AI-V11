package com.marcusai.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * Visible, user-started floating shortcut. It does not observe other apps,
 * read screen content, or automate taps. Android overlay permission is required.
 */
class MarcusFloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification())
        if (bubble == null) showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "M"
            textSize = 25f
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(7, 15, 29), Color.rgb(0, 205, 255), 100f, 3)
            elevation = 12f
            contentDescription = "Marcus floating robot. Tap to open voice assistant."
        }
        bubble = view
        val params = WindowManager.LayoutParams(
            dp(62), dp(62),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
            y = 0
        }
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) moved = true
                    params.x = (startX - dx).coerceAtLeast(0)
                    params.y = startY + dy
                    runCatching { windowManager.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        showPanel()
                        openMarcusVoice()
                    }
                    true
                }
                else -> true
            }
        }
        runCatching { windowManager.addView(view, params) }
    }

    private fun showPanel() {
        if (panel != null) {
            panel?.visibility = android.view.View.VISIBLE
            return
        }
        var panelView: LinearLayout? = null
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(10, 17, 30), Color.rgb(0, 190, 240), 22f, 2)
            elevation = 16f
        }
        root.addView(TextView(this).apply {
            text = "M A R C U S   //   ONLINE"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(90, 225, 255))
        })
        root.addView(TextView(this).apply {
            text = "Floating companion active"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(5), 0, dp(10))
        })
        root.addView(actionButton("VOICE  •  OPEN MARCUS") { openMarcusVoice() })
        root.addView(actionButton("OPEN CHAT") { openMarcusChat() })
        root.addView(actionButton("HIDE PANEL") { panelView?.visibility = android.view.View.GONE })
        panelView = root
        panel = root
        val params = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dp(12)
            y = dp(80)
        }
        runCatching { windowManager.addView(root, params) }
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(20, 39, 60), Color.rgb(0, 145, 195), 12f, 1)
            setOnClickListener { action() }
        }

    private fun openMarcusVoice() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_START_VOICE, true)
        }
        startActivity(intent)
    }

    private fun openMarcusChat() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun rounded(fill: Int, stroke: Int, radius: Float, width: Int) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            setStroke(dp(width), stroke)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Marcus floating companion", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Shows when the user has enabled the Marcus floating robot bubble"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val stop = android.app.PendingIntent.getService(
            this, 19, Intent(this, MarcusFloatingService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Marcus floating companion is active")
            .setContentText("Tap the robot bubble to open Marcus voice mode.")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Turn off", stop)
            .build()
    }

    override fun onDestroy() {
        runCatching { bubble?.let { windowManager.removeView(it) } }
        runCatching { panel?.let { windowManager.removeView(it) } }
        bubble = null
        panel = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.marcusai.app.STOP_FLOATING"
        const val EXTRA_START_VOICE = "com.marcusai.app.START_VOICE"
        private const val CHANNEL_ID = "marcus_floating_companion"
        private const val NOTIFICATION_ID = 1901
    }
}
