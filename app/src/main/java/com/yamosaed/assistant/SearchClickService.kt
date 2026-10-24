package com.yamosaed.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yamosaed.assistant.agent.Arabic
import kotlin.math.max

class SearchClickService : AccessibilityService() {

    private var queryTokens: List<String> = emptyList()
    private var rawQueryString: String = ""
    private var targetPackage: String? = null
    private var deadlineTime = 0L
    private var callback: ((Boolean) -> Unit)? = null
    private var hasClicked = false

    // تتبع مراحل معالجة البحث والكيبورد
    private var searchSubmitted = false
    private var keyboardDismissed = false
    private var lastActionTime = 0L

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
     * تجهيز خدمة إمكانية الوصول للبحث عن أغنية ونقر أعلى نتيجة مطابقة في Spotify / SoundCloud
     */
    fun arm(pkg: String, rawQuery: String, done: (Boolean) -> Unit): Boolean {
        disarm()

        val tokens = normalizeAndTokenize(rawQuery)
        if (tokens.isEmpty()) return false

        queryTokens = tokens
        rawQueryString = normalizeText(rawQuery)
        targetPackage = pkg
        callback = done
        hasClicked = false
        searchSubmitted = false
        keyboardDismissed = false
        lastActionTime = 0L
        deadlineTime = System.currentTimeMillis() + TIMEOUT_MS

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
        searchSubmitted = false
        keyboardDismissed = false
        lastActionTime = 0L
        visibleScreenText = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // قراءة النص الظاهر من أي تطبيق؛ البحث التلقائي عن الأغاني يظل محصوراً في تطبيقات الموسيقى.
        val root = rootInActiveWindow ?: return
        visibleScreenText = normalizeText(subtreeText(root)).take(5000)

        if (packageName != "com.spotify.music" && packageName != "com.soundcloud.android") return
        if (queryTokens.isEmpty() || hasClicked) return
        if (packageName != targetPackage) return

        if (System.currentTimeMillis() > deadlineTime) {
            finish(false)
            return
        }

        val now = System.currentTimeMillis()

        // 1. التعامل مع الكيبورد والضغط على إنتر أو أيقونة البحث
        if (!searchSubmitted) {
            val searchInput = findSearchInput(root)
            if (searchInput != null) {
                // تنفيذ إجراء البحث (Enter / Search IME Action)
                var imeSuccess = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    imeSuccess = searchInput.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                }
                if (!imeSuccess) {
                    // محاولة النقر على أيقونة البحث إذا وجدت
                    val searchButton = findSearchButton(root)
                    searchButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                searchSubmitted = true
                lastActionTime = now
            }
        }

        // 2. إخفاء الكيبورد ومسح الشاشة لرؤية النتائج كاملة دون حجب
        if (searchSubmitted && !keyboardDismissed && (now - lastActionTime >= 200L)) {
            dismissSoftKeyboard()
            keyboardDismissed = true
            lastActionTime = now
            return // إعطاء النظام فرصة لتحديث واجهة النتائج بعد إخفاء الكيبورد
        }

        // 3. فحص الشاشة بالكامل وتحليل نتائج الأغاني واختيار أعلى نسبة تطابق
        val candidates = mutableListOf<CandidateNode>()
        collectSongCandidates(root, candidates)

        if (candidates.isEmpty()) return

        // ترتيب النتائج حسب أعلى نسبة تطابق
        val best = candidates.maxByOrNull { it.score } ?: return

        // إذا تجاوزت أعلى نتيجة عتبة المطابقة (0.50)، يتم تشغيلها فوراً
        if (best.score >= 0.50) {
            hasClicked = true
            val clickTarget = findClickableTarget(best.node) ?: best.node
            val success = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            finish(success)
        }
    }

    /**
     * تنفيذ إجراء واحد واضح على الواجهة الحالية.
     * الصيغ: tap|نص، type|نص، scroll_down، scroll_up، back، home، recents، notifications.
     */
    fun performAgentAction(command: String): Boolean {
        val parts = command.split("|", limit = 2)
        val action = parts[0].trim().lowercase()
        val value = parts.getOrNull(1)?.trim().orEmpty()
        val root = rootInActiveWindow

        return when (action) {
            "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "scroll_down" -> root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
            "scroll_up" -> root?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
            "tap" -> {
                if (value.isBlank() || root == null) false
                else findAgentTarget(root, value)?.let { target ->
                    (findClickableTarget(target) ?: target).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } ?: false
            }
            "type" -> {
                if (value.isBlank() || root == null) false
                else {
                    val input = findFocusedEditable(root) ?: findFirstEditable(root)
                    input?.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        android.os.Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                value
                            )
                        }
                    ) == true
                }
            }
            else -> false
        }
    }

    private fun findAgentTarget(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        val wanted = normalizeText(label)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = normalizeText(
                listOfNotNull(node.text?.toString(), node.contentDescription?.toString()).joinToString(" ")
            )
            if (text == wanted || (wanted.length >= 3 && text.contains(wanted))) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun findFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isFocused) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun findSearchInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val cls = node.className?.toString() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (node.isEditable || cls.contains("EditText") || viewId.contains("query") || viewId.contains("search_field")) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findSearchButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isSearchTrigger = desc.contains("search") || desc.contains("بحث") ||
                    text.contains("بحث") || text.contains("search") ||
                    viewId.contains("search_button") || viewId.contains("search_icon")

            if (node.isClickable && isSearchTrigger) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun dismissSoftKeyboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                softKeyboardController.showMode = SHOW_MODE_HIDDEN
            }
        } catch (_: Exception) {}
        // إغلاق الكيبورد عبر إجراء العودة في النظام
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
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

    /**
     * مسح الشاشة واستخراج عناصر الأغاني مع استبعاد شريط البحث والعناوين الجانبية
     */
    private fun collectSongCandidates(
        node: AccessibilityNodeInfo,
        outList: MutableList<CandidateNode>
    ) {
        val cls = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        // استبعاد حقل البحث وشريط الإدخال حتى لا يعتبر أغنية
        if (node.isEditable || cls.contains("EditText") || viewId.contains("search_field") || viewId.contains("query")) {
            return
        }

        val nodeText = normalizeText(subtreeText(node))
        val isClickable = node.isClickable || (findClickableTarget(node) != null)

        // التحقق من أن العنصر يحتوي على نص ذو معنى ويمثل أغنية أو نتيجة
        if (isClickable && nodeText.isNotBlank()) {
            val isHeaderOrFilter = nodeText.equals("بحث") || nodeText.equals("search") ||
                    nodeText.equals("الكل") || nodeText.equals("all") ||
                    nodeText.equals("أفضل نتيجة") || nodeText.equals("top result") ||
                    nodeText.equals("أغان") || nodeText.equals("songs")

            if (!isHeaderOrFilter) {
                val score = calculateMatchPercentage(nodeText, rawQueryString, queryTokens)
                if (score > 0.0) {
                    outList.add(CandidateNode(node, nodeText, score))
                }
            }
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectSongCandidates(it, outList) }
        }
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
        const val TIMEOUT_MS = 14_000L

        @Volatile
        var instance: SearchClickService? = null

        @Volatile
        var visibleScreenText: String = ""

        /**
         * حساب نسبة التطابق الدقيقة بين الأغنية المطلوبة والنص الظاهر على شاشة Spotify
         */
        fun calculateMatchPercentage(nodeText: String, rawQuery: String, tokens: List<String>): Double {
            if (tokens.isEmpty()) return 0.0

            // 1. نسبة الكلمات المشتركة (Token Overlap)
            var matchCount = 0
            for (t in tokens) {
                if (nodeText.contains(t)) {
                    matchCount++
                }
            }
            val tokenRatio = matchCount.toDouble() / tokens.size

            // 2. نسبة الاحتواء للعبارة كاملة (Phrase Substring)
            val phraseBonus = if (rawQuery.isNotBlank() && nodeText.contains(rawQuery)) 0.25 else 0.0

            // 3. تقييم نوع العنصر (أغنية / تراك مقابل ألبوم أو قائمة تشغيل)
            val typeBonus = when {
                nodeText.contains("اغنيه") || nodeText.contains("أغنية") ||
                        nodeText.contains("song") || nodeText.contains("track") -> 0.20
                nodeText.contains("البوم") || nodeText.contains("ألبوم") ||
                        nodeText.contains("album") -> -0.20
                nodeText.contains("قائمه") || nodeText.contains("playlist") -> -0.15
                nodeText.contains("فنان") || nodeText.contains("artist") -> -0.10
                else -> 0.0
            }

            val totalScore = (tokenRatio * 0.75 + phraseBonus + typeBonus).coerceIn(0.0, 1.0)
            return totalScore
        }

        fun normalizeText(input: String): String {
            return Arabic.norm(input).lowercase()
                .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        fun normalizeAndTokenize(input: String): List<String> {
            val junk = setOf("شغل", "اسمع", "اغنية", "اغنيه", "تراك", "مزيكا", "موسيقى", "على", "في", "سبوتيفاي", "spotify", "soundcloud")
            return Arabic.tokens(input)
                .map { normalizeText(it) }
                .filter { it.length > 1 && it !in junk }
        }
    }
}