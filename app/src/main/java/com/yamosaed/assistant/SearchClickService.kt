package com.yamosaed.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yamosaed.assistant.agent.Arabic

class SearchClickService : AccessibilityService() {
    private var query: List<String> = emptyList()
    private var targetPackage: String? = null
    private var deadline = 0L
    private var callback: ((Boolean) -> Unit)? = null

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }
    override fun onInterrupt() {}

    fun arm(pkg: String, rawQuery: String, done: (Boolean) -> Unit): Boolean {
        val tokens = Arabic.tokens(rawQuery)
        if (tokens.isEmpty()) return false
        query = tokens
        targetPackage = pkg
        callback = done
        deadline = System.currentTimeMillis() + 12_000
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        // Read only supported music apps; never collect text from unrelated apps.
        if (packageName != "com.spotify.music" && packageName != "com.soundcloud.android") return
        val root = rootInActiveWindow ?: return
        visibleScreenText = Arabic.norm(subtreeText(root)).take(5000)
        if (query.isEmpty()) return
        if (packageName != targetPackage) return
        if (System.currentTimeMillis() > deadline) return finish(false)
        val rows = mutableListOf<Triple<AccessibilityNodeInfo, Double, Double>>()
        collect(root, rows)
        val best = rows.maxByOrNull { it.second + it.third } ?: return
        if (best.second < 0.6) return
        finish(best.first.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun finish(ok: Boolean) {
        query = emptyList()
        val cb = callback
        callback = null
        cb?.invoke(ok)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<Triple<AccessibilityNodeInfo, Double, Double>>) {
        if (node.isClickable) {
            val text = Arabic.norm(subtreeText(node))
            if (text.isNotBlank()) {
                val hits = query.count { text.contains(it) }
                val ratio = hits.toDouble() / query.size
                val songBonus = when {
                    text.contains("اغنيه") || text.contains("song") -> 0.05
                    text.contains("البوم") || text.contains("album") || text.contains("فنان") || text.contains("artist") -> -0.05
                    else -> 0.0
                }
                out += Triple(node, ratio, songBonus)
            }
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, out) }
    }

    private fun subtreeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) node.getChild(i)?.let { sb.append(subtreeText(it)) }
        return sb.toString()
    }

    companion object { @Volatile var instance: SearchClickService? = null; @Volatile var visibleScreenText: String = "" }
}