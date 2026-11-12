package com.marcusai.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility bridge for explicit Marcus navigation commands.
 * It deliberately does not automate payments, purchases, security prompts or message sending.
 */
class MarcusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("command")) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "scroll_down" -> scrollScreen(true)
            "scroll_up" -> scrollScreen(false)
            "type" -> {
                val text = intent.getStringExtra("text").orEmpty()
                if (text.isNotBlank() && text.length <= 500) {
                    val node = rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                    node?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply { putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) })
                }
            }
            "launch" -> {
                val packageName = intent.getStringExtra("package").orEmpty()
                if (packageName.isNotBlank()) {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun scrollScreen(down: Boolean) {
        val path = Path().apply {
            val x = resources.displayMetrics.widthPixels * 0.5f
            val start = resources.displayMetrics.heightPixels * if (down) 0.75f else 0.30f
            val end = resources.displayMetrics.heightPixels * if (down) 0.30f else 0.75f
            moveTo(x, start); lineTo(x, end)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 380))
            .build()
        dispatchGesture(gesture, null, null)
    }
}