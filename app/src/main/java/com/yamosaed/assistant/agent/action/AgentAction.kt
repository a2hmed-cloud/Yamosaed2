package com.yamosaed.assistant.agent.action

import android.graphics.Rect
import com.yamosaed.assistant.agent.vision.ScreenSnapshot

/**
 * الأفعال الحقيقية التي يدعمها وكيل أندرويد
 */
sealed class AgentAction {
    data class LaunchApp(val packageName: String, val appLabel: String = "") : AgentAction()
    data class Click(val text: String? = null, val viewId: String? = null, val bounds: Rect? = null, val description: String = "") : AgentAction()
    data class TypeText(val textToType: String, val targetQuery: String? = null, val clearFirst: Boolean = false) : AgentAction()
    data class Scroll(val forward: Boolean = true) : AgentAction()
    object NavigateBack : AgentAction()
    object NavigateHome : AgentAction()
    data class Wait(val durationMs: Long = 1000L) : AgentAction()
    data class RequestConfirmation(val message: String, val pendingAction: AgentAction) : AgentAction()
}

/**
 * نتيجة تنفيذ الفعل مع أدلة التحقق وتغير الشاشة
 */
data class ActionResult(
    val action: AgentAction,
    val isAccepted: Boolean,
    val isSuccess: Boolean,
    val screenBefore: ScreenSnapshot? = null,
    val screenAfter: ScreenSnapshot? = null,
    val screenChanged: Boolean = false,
    val errorMessage: String? = null,
    val details: String = ""
)
