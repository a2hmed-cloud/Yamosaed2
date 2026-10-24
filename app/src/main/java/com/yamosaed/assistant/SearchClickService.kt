package com.yamosaed.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yamosaed.assistant.agent.Arabic

class SearchClickService : AccessibilityService() {

    private var queryTokens: List<String> = emptyList()
    private var rawQueryString: String = ""
    private var targetPackage: String? = null
    private var deadlineTime = 0L
    private var callback: ((Boolean) -> Unit)? = null
    private var hasClicked = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val timeoutRunnable = Runnable {
        if (!hasClicked && callback != null) {
            finish(false)
        }
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        disarm()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onInterrupt() {
        disarm()
    }

    /**
     * تجهيز خدمة إمكانية الوصول للبحث عن أغنية ونقر أول نتيجة مطابقة
     */
    fun arm(pkg: String, rawQuery: String, done: (Boolean) -> Unit): Boolean {
        // 1. إلغاء أي مهمة بحث سابقة
        disarm()

        val tokens = normalizeAndTokenize(rawQuery)
        if (tokens.isEmpty()) return false

        queryTokens = tokens
        rawQueryString = rawQuery
        targetPackage = pkg
        callback = done
        hasClicked = false
        deadlineTime = System.currentTimeMillis() + TIMEOUT_MS

        // جدولة مؤقت المهلة (13 ثانية)
        mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MS)
        return true
    }

    /**
     * تنظيف الحالة وإلغاء أي مهمة بحث نشطة
     */
    fun disarm() {
        mainHandler.removeCallbacks(timeoutRunnable)
        queryTokens = emptyList()
        rawQueryString = ""
        targetPackage = null
        callback = null
        hasClicked = false
        visibleScreenText = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // قراءة فقط تطبيقات الموسيقى المصرح بها
        if (packageName != "com.spotify.music" && packageName != "com.soundcloud.android") return

        val root = rootInActiveWindow ?: return
        val currentScreenText = normalizeText(subtreeText(root))
        visibleScreenText = currentScreenText.take(3000)

        if (queryTokens.isEmpty() || hasClicked) return
        if (packageName != targetPackage) return

        if (System.currentTimeMillis() > deadlineTime) {
            finish(false)
            return
        }

        // جمع كافة العناصر القابلة للنقر وحساب درجات التطابق
        val candidates = mutableListOf<CandidateNode>()
        collectClickableNodes(root, candidates)

        if (candidates.isEmpty()) return

        // ترتيب المرشحين حسب أعلى نسبة تطابق
        val best = candidates.maxByOrNull { it.score } ?: return

        // عتبة التطابق الإلزامية: لا ننقر إلا إذا تجاوزت النسبة 0.65
        if (best.score >= 0.65) {
            hasClicked = true
            val success = best.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            finish(success)
        }
    }

    private fun finish(ok: Boolean) {
        mainHandler.removeCallbacks(timeoutRunnable)
        val cb = callback
        callback = null
        queryTokens = emptyList()
        targetPackage = null
        cb?.invoke(ok)
    }

    private data class CandidateNode(
        val node: AccessibilityNodeInfo,
        val text: String,
        val score: Double
    )

    private fun collectClickableNodes(
        node: AccessibilityNodeInfo,
        outList: MutableList<CandidateNode>
    ) {
        if (node.isClickable) {
            val nodeText = normalizeText(subtreeText(node))
            if (nodeText.isNotBlank()) {
                val score = calculateMatchScore(nodeText, queryTokens)
                if (score > 0.0) {
                    outList.add(CandidateNode(node, nodeText, score))
                }
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectClickableNodes(it, outList) }
        }
    }

    private fun calculateMatchScore(nodeText: String, tokens: List<String>): Double {
        var matchCount = 0
        for (t in tokens) {
            if (nodeText.contains(t)) {
                matchCount++
            }
        }
        val baseRatio = matchCount.toDouble() / tokens.size

        // حوافز إضافية بناءً على نوع العنصر (أغنية مقابل ألبوم أو قائمة تشغيل)
        val bonus = when {
            nodeText.contains("اغنيه") || nodeText.contains("أغنية") || nodeText.contains("song") || nodeText.contains("track") -> 0.15
            nodeText.contains("البوم") || nodeText.contains("ألبوم") || nodeText.contains("album") -> -0.15
            nodeText.contains("قائمه") || nodeText.contains("playlist") -> -0.10
            else -> 0.0
        }
        return (baseRatio + bonus).coerceAtLeast(0.0)
    }

    private fun subtreeText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { sb.append(subtreeText(it)) }
        }
        return sb.toString()
    }

    companion object {
        const val TIMEOUT_MS = 13_000L

        @Volatile
        var instance: SearchClickService? = null

        @Volatile
        var visibleScreenText: String = ""

        fun normalizeText(input: String): String {
            return Arabic.norm(input).lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun normalizeAndTokenize(input: String): List<String> {
            val junk = setOf("شغل", "اسمع", "اغنية", "اغنيه", "مزيكا", "موسيقى", "على", "في", "سبوتيفاي", "spotify", "soundcloud")
            return Arabic.tokens(input)
                .map { normalizeText(it) }
                .filter { it.length > 1 && it !in junk }
        }
    }
}