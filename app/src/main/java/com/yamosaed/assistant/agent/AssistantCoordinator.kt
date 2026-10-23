package com.yamosaed.assistant.agent

import com.yamosaed.assistant.AndroidHost
import com.yamosaed.assistant.SearchClickService
import com.yamosaed.assistant.gemini.GeminiClient
import com.yamosaed.assistant.model.ChatMessage
import com.yamosaed.assistant.model.GeminiDecision
import com.yamosaed.assistant.storage.PreferencesManager
import com.yamosaed.assistant.storage.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssistantCoordinator(
    private val host: AndroidHost,
    private val preferencesManager: PreferencesManager,
    private val taskManager: TaskManager,
    private val geminiClient: GeminiClient,
    private val localAgent: Agent,
    private val onFeedback: (reply: String, actionName: String?) -> Unit
) {

    suspend fun processQuery(
        rawQuery: String,
        recentHistory: List<ChatMessage>
    ) = withContext(Dispatchers.Main) {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            onFeedback("قولي حاجة أقدر أساعدك فيها يا باشا.", null)
            return@withContext
        }

        val apiKey = preferencesManager.geminiApiKey
        val screenContext = SearchClickService.visibleScreenText

        if (apiKey.isNotBlank()) {
            val geminiResult = withContext(Dispatchers.IO) {
                geminiClient.decide(
                    apiKey = apiKey,
                    userQuery = trimmed,
                    screenContext = screenContext,
                    recentHistory = recentHistory
                )
            }

            if (geminiResult.isSuccess) {
                val decision = geminiResult.getOrThrow()
                executeGeminiDecision(decision, trimmed)
                return@withContext
            }
        }

        // Offline or fallback to local Egyptian rule-based agent
        val localReply = withContext(Dispatchers.Default) {
            localAgent.handle(trimmed)
        }
        onFeedback(localReply, "محلي")
    }

    private fun executeGeminiDecision(decision: GeminiDecision, originalQuery: String) {
        when (decision.action) {
            "search_music" -> {
                val app = if (decision.app.isNotBlank()) decision.app else preferencesManager.defaultMusicApp
                val query = decision.query.ifBlank { originalQuery }
                val armed = host.armClick(app, query) { ok ->
                    if (ok) {
                        onFeedback("لقيت $query في $app وشغلتها.", "تشغيل موسيقى")
                    } else {
                        onFeedback("فتحت $app للبحث عن $query. اختار الأغنية من الشاشة.", "بحث موسيقى")
                    }
                }
                val opened = host.openMusicSearch(app, query)
                val reply = if (!opened) {
                    "مش قادر أفتح تطبيق الموسيقى ($app) دلوقتي."
                } else if (armed) {
                    decision.reply.ifBlank { "بدور على $query في $app وهختارها تلقائيًا..." }
                } else {
                    decision.reply.ifBlank { "فتحت البحث عن $query في $app. فعّل إمكانية الوصول للاختيار التلقائي." }
                }
                onFeedback(reply, "موسيقى: $query")
            }

            "music_control" -> {
                val action = when (decision.query.lowercase()) {
                    "next" -> MusicAction.NEXT
                    "previous", "prev" -> MusicAction.PREV
                    else -> MusicAction.TOGGLE
                }
                val success = host.musicControl(action)
                val currentTrack = host.getNowPlayingInfo()
                val reply = if (success) {
                    val trackSuffix = if (!currentTrack.isNullOrBlank()) " ($currentTrack)" else ""
                    decision.reply.ifBlank { "تمام يا فندم$trackSuffix." }
                } else {
                    "مافيش تطبيق موسيقى نشط حاليًا. شغّل Spotify أو فعّل وصول الإشعارات."
                }
                onFeedback(reply, "تحكم بالوسائط")
            }

            "weather" -> {
                val city = decision.query.ifBlank { preferencesManager.homeCity }
                onFeedback(decision.reply.ifBlank { "ثواني بجيب طقس $city..." }, "جلب الطقس")
                host.weather(city) { resultText ->
                    onFeedback(resultText, "طقس: $city")
                }
            }

            "alarm" -> {
                val hour = if (decision.hour in 0..23) decision.hour else 7
                val minute = if (decision.minute in 0..59) decision.minute else 0
                val label = decision.label.ifBlank { "يا مساعد" }
                val ok = host.setAlarm(hour, minute, label)
                val timeStr = "%02d:%02d".format(hour, minute)
                val reply = if (ok) {
                    decision.reply.ifBlank { "عيوني، ضبطت المنبه على الساعة $timeStr." }
                } else {
                    "مش قادر أفتح تطبيق الساعة لضبط المنبه."
                }
                onFeedback(reply, "منبه $timeStr")
            }

            "timer" -> {
                val sec = if (decision.seconds > 0) decision.seconds else 60L
                val ok = host.setTimer(sec, decision.label.ifBlank { "تايمر" })
                val durationText = when {
                    sec >= 3600 && sec % 3600 == 0L -> "${sec / 3600} ساعة"
                    sec >= 60 && sec % 60 == 0L -> "${sec / 60} دقيقة"
                    else -> "$sec ثانية"
                }
                val reply = if (ok) {
                    decision.reply.ifBlank { "تمام، بدأت تايمر لمدة $durationText وهنبهك لما يخلص." }
                } else {
                    "محتاج تسمح بالمنبهات الدقيقة من إعدادات النظام الأول."
                }
                onFeedback(reply, "تايمر $durationText")
            }

            "task_add" -> {
                val taskText = decision.query.ifBlank { originalQuery }
                taskManager.addTask(taskText)
                val reply = decision.reply.ifBlank { "عيوني، سجلت في مهامك: $taskText" }
                onFeedback(reply, "إضافة مهمة")
            }

            "task_list" -> {
                val tasks = taskManager.getTasks()
                val reply = if (tasks.isEmpty()) {
                    "لسه ما سجلتش أي مهام في يومك يا باشا."
                } else {
                    val formatted = tasks.joinToString("\n") { (if (it.isCompleted) "✓ " else "• ") + it.text }
                    decision.reply.ifBlank { "مهامك المسجلة:\n$formatted" }
                }
                onFeedback(reply, "عرض المهام")
            }

            "task_clear" -> {
                taskManager.clearTasks()
                val reply = decision.reply.ifBlank { "مسحت كل المهام من يومك." }
                onFeedback(reply, "مسح المهام")
            }

            "places" -> {
                val placeQuery = decision.query.ifBlank { originalQuery }
                val url = "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(placeQuery, "UTF-8")
                val ok = host.openUrl(url)
                val reply = if (ok) {
                    decision.reply.ifBlank { "فتحت خرائط Google للبحث عن $placeQuery." }
                } else {
                    "تعذر فتح تطبيق الخرائط."
                }
                onFeedback(reply, "خرائط: $placeQuery")
            }

            "clarify", "chat" -> {
                val reply = decision.reply.ifBlank { "أنا معاك يا فندم، تحب أساعدك بإيه؟" }
                onFeedback(reply, "محادثة")
            }

            else -> {
                val localReply = localAgent.handle(originalQuery)
                onFeedback(localReply, "محلي")
            }
        }
    }
}
