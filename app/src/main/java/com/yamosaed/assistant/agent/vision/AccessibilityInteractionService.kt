package com.yamosaed.assistant.agent.vision

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.yamosaed.assistant.SearchClickService

class AccessibilityInteractionService {

    val isAvailable: Boolean
        get() = SearchClickService.instance != null

    /**
     * التقاط تمثيل كامل لعناصر الشاشة الحالية
     */
    fun captureCurrentScreen(): ScreenSnapshot {
        val service = SearchClickService.instance ?: return ScreenSnapshot(
            summary = "خدمة إمكانية الوصول (Accessibility) غير مفعلة في إعدادات النظام"
        )
        val root = service.rootInActiveWindow ?: return ScreenSnapshot(
            summary = "لا توجد نافذة نشطة قابلة للقراءة حالياً"
        )

        val packageName = root.packageName?.toString().orEmpty()
        val elements = mutableListOf<UiElementNode>()
        val visibleTexts = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 30) return
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val text = node.text?.toString().orEmpty().trim()
            val desc = node.contentDescription?.toString().orEmpty().trim()
            val viewId = node.viewIdResourceName
            val className = node.className?.toString().orEmpty()

            if (text.isNotBlank() && !visibleTexts.contains(text)) {
                visibleTexts.add(text)
            } else if (desc.isNotBlank() && !visibleTexts.contains(desc)) {
                visibleTexts.add(desc)
            }

            elements.add(
                UiElementNode(
                    id = "$depth:${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}",
                    viewIdResourceName = viewId,
                    text = text,
                    contentDescription = desc,
                    className = className,
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isCheckable = node.isCheckable,
                    isChecked = node.isChecked,
                    isFocused = node.isFocused,
                    isEnabled = node.isEnabled,
                    childCount = node.childCount
                )
            )

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(root, 0)

        val isKeyboard = elements.any { it.className.contains("InputMethod", ignoreCase = true) || it.className.contains("Keyboard", ignoreCase = true) }

        val summary = "التطبيق: $packageName، العناصر: ${elements.size} (تفاعلي: ${elements.count { it.isInteractive() }})، نصوص: ${visibleTexts.take(6).joinToString(" | ")}"

        return ScreenSnapshot(
            packageName = packageName,
            elements = elements,
            visibleTexts = visibleTexts,
            summary = summary,
            isKeyboardVisible = isKeyboard
        )
    }

    /**
     * النقر على عنصر عبر النص أو معرّف الواجهة ViewId أو الإحداثيات
     */
    fun clickElement(text: String? = null, viewId: String? = null, bounds: Rect? = null): Boolean {
        val service = SearchClickService.instance ?: return false
        val root = service.rootInActiveWindow ?: return false

        // 1. البحث عبر المعرّف
        if (!viewId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val clickable = nodes?.firstOrNull { it.isClickable } ?: nodes?.firstOrNull()
            if (clickable != null && performClickOnNode(clickable)) return true
        }

        // 2. البحث عبر النص
        if (!text.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val clickable = nodes?.firstOrNull { it.isClickable } ?: nodes?.firstOrNull()
            if (clickable != null && performClickOnNode(clickable)) return true
        }

        // 3. البحث بالمرور على جميع العناصر إذا كان هناك وصف موازي أو تطابق جزئي
        if (!text.isNullOrBlank()) {
            val q = text.trim()
            var matchedNode: AccessibilityNodeInfo? = null
            fun findDeep(node: AccessibilityNodeInfo?) {
                if (node == null || matchedNode != null) return
                val t = node.text?.toString().orEmpty()
                val d = node.contentDescription?.toString().orEmpty()
                if (t.contains(q, ignoreCase = true) || d.contains(q, ignoreCase = true)) {
                    matchedNode = node
                    return
                }
                for (i in 0 until node.childCount) findDeep(node.getChild(i))
            }
            findDeep(root)
            if (matchedNode != null && performClickOnNode(matchedNode!!)) return true
        }

        return false
    }

    /**
     * إدخال نص في حقل محدد أو الحقل النشط حالياً
     */
    fun typeText(targetQuery: String? = null, textToType: String, clearFirst: Boolean = false): Boolean {
        val service = SearchClickService.instance ?: return false
        val root = service.rootInActiveWindow ?: return false

        var editNode: AccessibilityNodeInfo? = null

        // البحث عن حقل نصي مطابق
        if (!targetQuery.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(targetQuery)
            editNode = nodes?.firstOrNull { it.isEditable }
        }

        // إذا لم نجد، نبحث عن الحقل المفعل أو أول حقل إدخال متاح
        if (editNode == null) {
            fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.isEditable) return node
                for (i in 0 until node.childCount) {
                    val found = findEditable(node.getChild(i))
                    if (found != null) return found
                }
                return null
            }
            editNode = findEditable(root)
        }

        if (editNode != null) {
            editNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle()
            val existing = if (clearFirst) "" else editNode.text?.toString().orEmpty()
            val finalContent = if (existing.isBlank()) textToType else "$existing $textToType"
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, finalContent)
            return editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        return false
    }

    /**
     * التمرير للأعلى أو للأسفل
     */
    fun scroll(forward: Boolean): Boolean {
        val service = SearchClickService.instance ?: return false
        val root = service.rootInActiveWindow ?: return false

        fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                val found = findScrollable(node.getChild(i))
                if (found != null) return found
            }
            return null
        }

        val scrollable = findScrollable(root) ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollable.performAction(action)
    }

    /**
     * الرجوع للخلف أو للشاشة الرئيسية
     */
    fun performGlobalAction(action: Int): Boolean {
        val service = SearchClickService.instance ?: return false
        return service.performGlobalAction(action)
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
