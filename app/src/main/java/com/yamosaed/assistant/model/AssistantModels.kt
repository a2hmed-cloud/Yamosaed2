package com.yamosaed.assistant.model

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
    val actionDetail: String? = null
)

data class TaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val reminderTimeMillis: Long? = null,
    val isCompleted: Boolean = false
)

data class GeminiDecision(
    val action: String, // "chat", "search_music", "music_control", "weather", "alarm", "timer", "task_add", "task_list", "task_clear", "places", "clarify", "none"
    val query: String = "",
    val reply: String = "",
    val hour: Int = -1,
    val minute: Int = -1,
    val seconds: Long = 0L,
    val app: String = "spotify",
    val label: String = ""
)
