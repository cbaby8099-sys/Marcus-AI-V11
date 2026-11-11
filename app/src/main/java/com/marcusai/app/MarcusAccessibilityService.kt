package com.marcusai.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

/**
 * User-enabled shortcut for Marcus only.
 * The bubble opens Marcus and activates only Marcus's own microphone button.
 * It deliberately does not automate other apps or perform arbitrary screen actions.
 */
class MarcusAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var voiceLaunchArmed = false
    private var micClickAttempted = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!voiceLaunchArmed || event == null) return
        if (event.packageName?.toString() != packageName) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        if (!micClickAttempted) {
            handler.postDelayed({
                if (voiceLaunchArmed && !micClickAttempted) {
                    val root = rootInActiveWindow
                    if (root != null && clickMarcusMic(root)) {
                        micClickAttempted = true
                        voiceLaunchArmed = false
                    }
                    root?.recycle()
                }
            }, 350L)
        }
    }

    private fun clickMarcusMic(node: AccessibilityNodeInfo): Boolean {
        val label = node.text?.toString().orEmpty()
        if (label.contains("◉") && label.contains("M")) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            val parent = node.parent
            val clicked = parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            parent?.recycle()
            if (clicked) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val clicked = clickMarcusMic(child)
            child.recycle()
            if (clicked) return true
        }
        return false
    }

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(58)
        val view = TextView(this).apply {
            text = "M"
            setTextColor(Color.rgb(190, 245, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(8, 24, 40))
                setStroke(dp(2), Color.rgb(56, 200, 255))
            }
            contentDescription = "Tap to speak to Marcus"
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(180)
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(8)) dragged = true
                    params.x = (startX - dx).coerceAtLeast(0)
                    params.y = (startY + dy).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) openMarcusVoice()
                    true
                }
                else -> true
            }
        }
        bubble = view
        windowManager.addView(view, params)
    }

    private fun openMarcusVoice() {
        voiceLaunchArmed = true
        micClickAttempted = false
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            voiceLaunchArmed = false
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(launch)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onInterrupt() {
        voiceLaunchArmed = false
    }

    override fun onDestroy() {
        bubble?.let { windowManager.removeView(it) }
        bubble = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}