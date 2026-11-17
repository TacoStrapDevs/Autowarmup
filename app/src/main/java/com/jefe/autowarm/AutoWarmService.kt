package com.jefe.autowarm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class AutoWarmService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoWarmService? = null
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // passive — no event handling required
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
    }

    /**
     * Swipe up to advance YouTube feed / Shorts.
     * Gesture runs on the Main dispatcher — required by AccessibilityService API.
     * Path: 75% → 25% of screen height, centre X, 300ms stroke.
     */
    fun doScroll(onDone: () -> Unit) {
        val m = resources.displayMetrics
        val cx = m.widthPixels / 2f
        val startY = m.heightPixels * 0.75f
        val endY   = m.heightPixels * 0.25f

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 300L))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gd: GestureDescription?) {
                serviceScope.launch { delay(250L); onDone() }
            }
            override fun onCancelled(gd: GestureDescription?) { onDone() }
        }, null)
    }

    /**
     * Find the YouTube like button and tap it.
     *
     * YouTube uses content-desc to label the like button.
     * Known desc values across versions:
     *   "like this video" / "Like" / "like video along with..."
     * Already-liked state uses:
     *   "unlike this video" / "Unlike"
     *
     * Strategy:
     *  1. Walk the full a11y tree.
     *  2. Find first clickable node whose desc contains "like" (case-insensitive)
     *     and does NOT contain "dislike" or start with "unlike".
     *  3. Tap it.
     */
    fun doLike(onDone: () -> Unit) {
        val root = rootInActiveWindow ?: run { onDone(); return }
        try {
            val node = findLikeButton(root)
            if (node != null && !isAlreadyLiked(node)) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
            }
        } finally {
            root.recycle()
        }
        serviceScope.launch { delay(200L); onDone() }
    }

    private fun findLikeButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (node.isClickable
            && desc.contains("like")
            && !desc.contains("dislike")
            && !desc.startsWith("unlike")
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findLikeButton(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    // "unlike…" prefix = already liked; isChecked covers some YouTube builds
    private fun isAlreadyLiked(node: AccessibilityNodeInfo): Boolean {
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return desc.startsWith("unlike") || node.isChecked
    }
}
