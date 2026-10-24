package com.yamosaed.assistant.agent.safety

import com.yamosaed.assistant.agent.action.AgentAction

data class SafetyCheckResult(
    val isAllowed: Boolean,
    val requiresConfirmation: Boolean,
    val confirmationPrompt: String? = null,
    val warning: String? = null
)

class AgentSafetyPolicy {

    /**
     * التحقق الأمني من الفعل قبل تنفيذه لحماية خصوصية وأمان المستخدم
     */
    fun evaluateAction(action: AgentAction, contextQuery: String = ""): SafetyCheckResult {
        return when (action) {
            is AgentAction.Click -> {
                val label = (action.text ?: action.description).lowercase()
                // تأكيد أزرار الإرسال والحذف النهائي
                if (label.contains("إرسال") || label.contains("ارسال") || label.contains("send") || label == "send") {
                    SafetyCheckResult(
                        isAllowed = true,
                        requiresConfirmation = true,
                        confirmationPrompt = "هل أنت متأكد من رغبتك في إرسال هذه الرسالة؟"
                    )
                } else if (label.contains("حذف") || label.contains("delete") || label.contains("مسح")) {
                    SafetyCheckResult(
                        isAllowed = true,
                        requiresConfirmation = true,
                        confirmationPrompt = "هل أنت متأكد من رغبتك في الحذف؟"
                    )
                } else {
                    SafetyCheckResult(isAllowed = true, requiresConfirmation = false)
                }
            }

            is AgentAction.RequestConfirmation -> {
                SafetyCheckResult(
                    isAllowed = true,
                    requiresConfirmation = true,
                    confirmationPrompt = action.message
                )
            }

            else -> SafetyCheckResult(isAllowed = true, requiresConfirmation = false)
        }
    }
}
