package com.yamosaed.assistant.agent.vision

import android.graphics.Rect

/**
 * تمثيل منظم لعنصر تفاعلي في واجهة الشاشة مستخرج من شجرة إمكانية الوصول
 */
data class UiElementNode(
    val id: String = "",
    val viewIdResourceName: String? = null,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val bounds: Rect = Rect(),
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isCheckable: Boolean = false,
    val isChecked: Boolean = false,
    val isFocused: Boolean = false,
    val isEnabled: Boolean = true,
    val childCount: Int = 0
) {
    val displayIdentifier: String
        get() = when {
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            viewIdResourceName?.isNotBlank() == true -> viewIdResourceName.substringAfterLast("/")
            else -> className.substringAfterLast(".")
        }

    fun isInteractive(): Boolean = isClickable || isEditable || isScrollable || isCheckable
}

/**
 * لقطة حالة موحدة للشاشة الحالية تجمع الشجرة والنصوص المكتشفة والوقت
 */
data class ScreenSnapshot(
    val packageName: String = "",
    val activityName: String? = null,
    val elements: List<UiElementNode> = emptyList(),
    val visibleTexts: List<String> = emptyList(),
    val summary: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 1.0f,
    val isKeyboardVisible: Boolean = false
) {
    /**
     * حساب بصمة الشاشة لاكتشاف التغير بعد تنفيذ الأفعال
     */
    val screenHash: Int
        get() = (packageName + elements.map { "${it.id}:${it.text}:${it.isFocused}" }.joinToString(";")).hashCode()

    fun findElementByText(query: String, ignoreCase: Boolean = true): UiElementNode? {
        val q = query.trim()
        return elements.firstOrNull {
            it.text.equals(q, ignoreCase) || it.contentDescription.equals(q, ignoreCase)
        } ?: elements.firstOrNull {
            it.text.contains(q, ignoreCase) || it.contentDescription.contains(q, ignoreCase)
        }
    }

    fun findEditableField(): UiElementNode? {
        return elements.firstOrNull { it.isEditable && it.isFocused }
            ?: elements.firstOrNull { it.isEditable }
    }

    fun findClickableByViewId(viewIdSuffix: String): UiElementNode? {
        return elements.firstOrNull {
            it.viewIdResourceName?.endsWith(viewIdSuffix, ignoreCase = true) == true
        }
    }
}
