package com.marcusai.app

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * User-enabled navigation bridge.
 * Screen text is collected only when the user explicitly asks Marcus to read it.
 * Password and editable fields are excluded; text is kept in memory only.
 */
class MarcusAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun typeIntoFocusedField(value: String): Boolean {
        if (value.isBlank() || value.length > 2000) return false
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return false
        val focused = runCatching { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?: return false
        if (!focused.isEditable || focused.isPassword) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return runCatching {
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }

    private fun collectVisibleText(): String {
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return ""
        val output = linkedSetOf<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 18 || output.size >= 80) return
            if (node.isPassword || node.isEditable) return
            val value = node.text?.toString()?.trim().orEmpty()
            if (value.length in 2..220) output.add(value)
            for (i in 0 until minOf(node.childCount, 30)) {
                walk(runCatching { node.getChild(i) }.getOrNull(), depth + 1)
                if (output.size >= 80) break
            }
        }
        walk(root, 0)
        return output.joinToString("\n").take(1600)
    }

    companion object {
        @Volatile private var instance: MarcusAccessibilityService? = null

        fun perform(action: Int): Boolean {
            val service = instance ?: return false
            return runCatching { service.performGlobalAction(action) }.getOrDefault(false)
        }

        fun typeTextIntoFocusedField(value: String): Boolean {
            val service = instance ?: return false
            return runCatching { service.typeIntoFocusedField(value) }.getOrDefault(false)
        }

        fun readVisibleScreen(): String {
            val service = instance ?: return ""
            return runCatching { service.collectVisibleText() }.getOrDefault("")
        }
    }
}
