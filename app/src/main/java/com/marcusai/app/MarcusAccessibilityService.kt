package com.marcusai.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Optional, user-enabled navigation bridge.
 * Marcus only invokes explicit global navigation actions (Home, Back, Recents).
 * It does not inspect screen contents or click arbitrary controls.
 */
class MarcusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var instance: MarcusAccessibilityService? = null

        fun perform(action: Int): Boolean {
            val service = instance ?: return false
            return runCatching { service.performGlobalAction(action) }.getOrDefault(false)
        }
    }
}
