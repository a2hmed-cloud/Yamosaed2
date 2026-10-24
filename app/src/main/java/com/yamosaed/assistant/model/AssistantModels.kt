package com.yamosaed.assistant.model

import com.yamosaed.assistant.agent.ActionType

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    EXECUTING,
    SUCCESS,
    ERROR
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val actionDetail: String? = null,
    val actionType: ActionType? = null
)

data class TaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reminderTimeMillis: Long? = null,
    val isCompleted: Boolean = false
)

data class GeminiDecision(
    val action: String,
    val actionType: ActionType = ActionType.CHAT,
    val query: String = "",
    val reply: String = "",
    val hour: Int = -1,
    val minute: Int = -1,
    val seconds: Long = 0L,
    val app: String = "spotify",
    val label: String = ""
)
