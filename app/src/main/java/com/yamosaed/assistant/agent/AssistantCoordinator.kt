package com.yamosaed.assistant.agent

import com.yamosaed.assistant.AndroidHost
import com.yamosaed.assistant.SearchClickService
import com.yamosaed.assistant.gemini.GeminiClient
import com.yamosaed.assistant.model.ChatMessage
import com.yamosaed.assistant.model.GeminiDecision
import com.yamosaed.assistant.storage.PreferencesManager
import com.yamosaed.assistant.storage.TaskManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.URLEncoder

class AssistantCoordinator(
    private val host: AndroidHost,
    private val preferencesManager: PreferencesManager,
    private val taskManager: TaskManager,
    private val geminiClient: GeminiClient,
    private val localAgent: Agent,
    private val onFeedback: (reply: String, actionName: String?, actionType: ActionType) -> Unit
) {

    suspend fun processQuery(
        rawQuery: String,
        recentHistory: List<ChatMessage>
    ) = withContext(Dispatchers.Main) {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            onFeedback("قولي حاجة أقدر أساعدك فيها يا باشا.", null, ActionType.CHAT)
            return@withContext
        }

        // المهام المركبة تمر عبر حلقة وكيل: يخطط، ينفذ إجراء واحداً، ثم يعيد قراءة الشاشة.
        if (isMultiStepScreenTask(trimmed) && preferencesManager.geminiApiKey.isNotBlank()) {
            runScreenAgentLoop(trimmed, recentHistory)
            return@withContext
        }

        // فحص الأوامر المباشرة للتحكم بالهاتف (رسائل، مكالمات، كشاف، صوت، بطارية، تطبيقات، أغاني الجهاز، الشاشات)
        val localIntent = AssistantRouter.detectDeviceIntent(trimmed)
        if (localIntent != null) {
            when (localIntent.type) {
                ActionType.OPEN_TASKS -> {
                    onFeedback("فتحت لك مهامك يا باشا.", "شاشة المهام", ActionType.OPEN_TASKS)
                    return@withContext
                }
                ActionType.OPEN_SETTINGS -> {
                    onFeedback("فتحت لك الإعدادات يا باشا.", "شاشة الإعدادات", ActionType.OPEN_SETTINGS)
                    return@withContext
                }
                ActionType.CLOSE_OVERLAYS -> {
                    onFeedback("تمام يا فندم، رجعت لشاشة المساعد.", "الرئيسية", ActionType.CLOSE_OVERLAYS)
                    return@withContext
                }
                ActionType.LOCAL_MUSIC -> {
                    val (ok, reply) = host.playLocalSong(localIntent.query)
                    onFeedback(reply, "مشغل الجهاز", ActionType.LOCAL_MUSIC)
                    return@withContext
                }
                ActionType.SEND_MESSAGE -> {
                    val (ok, reply) = host.sendMessage(localIntent.recipient, localIntent.query, localIntent.app)
                    onFeedback(reply, localIntent.label, ActionType.SEND_MESSAGE)
                    return@withContext
                }
                ActionType.PHONE_CALL -> {
                    val (ok, reply) = host.makePhoneCall(localIntent.recipient)
                    onFeedback(reply, "اتصال هاتفي", ActionType.PHONE_CALL)
                    return@withContext
                }
                ActionType.DEVICE_CONTROL -> {
                    val (ok, reply) = host.controlDevice(localIntent.query)
                    onFeedback(reply, "تحكم بالجهاز", ActionType.DEVICE_CONTROL)
                    return@withContext
                }
                ActionType.CREATE_NOTE -> {
                    val (ok, reply) = host.createKeepNote(localIntent.query)
                    onFeedback(reply, localIntent.label, ActionType.CREATE_NOTE)
                    return@withContext
                }
                ActionType.OPEN_APP -> {
                    val (ok, reply) = host.openApp(localIntent.query)
                    onFeedback(reply, "فتح تطبيق", ActionType.OPEN_APP)
                    return@withContext
                }
                else -> { /* متابعة لباقي المعالجات */ }
            }
        }

        val apiKey = preferencesManager.geminiApiKey
        val screenContext = SearchClickService.visibleScreenText

        // 1. المحاولة عبر Gemini الذكي عند توفر المفتاح
        if (apiKey.isNotBlank()) {
            try {
                val geminiResult = withContext(Dispatchers.IO) {
                    geminiClient.decide(
                        apiKey = apiKey,
                        userQuery = trimmed,
                        preferredModel = preferencesManager.selectedGeminiModel,
                        screenContext = screenContext,
                        recentHistory = recentHistory
                    )
                }

                if (geminiResult.isSuccess) {
                    val decision = geminiResult.getOrThrow()
                    executeDecision(decision, trimmed)
                    return@withContext
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // فشل الاتصال - الانتقال الذكي للبديل المحلي أدناه
            }
        }

        // 2. البديل المحلي الذكي عند عدم توفر المفتاح أو انقطاع الإنترنت
        handleLocalFallback(trimmed)
    }

    private fun isMultiStepScreenTask(query: String): Boolean {
        val n = Arabic.norm(query).lowercase()
        val mentionsApp = n.contains("keep") || n.contains("كيب") || n.contains("واتساب") || n.contains("whatsapp") || n.contains("كروم") || n.contains("chrome")
        val asksForAction = n.contains("اكتب") || n.contains("سجل") || n.contains("اعمل") || n.contains("انشئ") || n.contains("ابحث") || n.contains("اضغط") || n.contains("افتح")
        val compound = n.contains(" وبعد") || n.contains(" واكتب") || n.contains(" وسجل") || n.contains(" ثم") || n.contains(" فيها") || n.contains("واعمل")
        return mentionsApp && asksForAction && compound
    }

    private suspend fun runScreenAgentLoop(task: String, recentHistory: List<ChatMessage>) {
        val apiKey = preferencesManager.geminiApiKey
        var lastAction = "لم يبدأ التنفيذ بعد"
        var consecutiveFailures = 0
        for (step in 1..8) {
            val screen = SearchClickService.visibleScreenText
            val instruction = "أنت وكيل ينفذ مهمة حقيقية على هاتف أندرويد. المهمة الأصلية: $task\n" +
                "الخطوة $step من 8. آخر نتيجة: $lastAction\n" +
                "نص الشاشة الحالي: ${screen.take(3500).ifBlank { "لا يوجد نص شاشة متاح؛ لا تخمّن عناصر غير ظاهرة." }}\n" +
                "اختر إجراء واحداً فقط: open_app باسم تطبيق محدد، أو screen_action بصيغة tap|النص أو type|النص أو scroll_down أو scroll_up أو back. " +
                "لا تدّعِ اكتمال المهمة إلا إذا أثبت نص الشاشة ذلك. إذا فشل إجراء، اختر مساراً مختلفاً. عند تعذر المتابعة، أرجع chat واشرح العائق."
            val result = try {
                withContext(Dispatchers.IO) { geminiClient.decide(apiKey, instruction, preferencesManager.selectedGeminiModel, screen, recentHistory.takeLast(4)) }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val decision = result?.getOrNull()
            if (decision == null) {
                onFeedback("وقفت المهمة لأن الاتصال بالذكاء الاصطناعي فشل. لا أقدر أؤكد تنفيذ الطلب.", "الوكيل", ActionType.SCREEN_ACTION)
                return
            }
            when (decision.actionType) {
                ActionType.OPEN_APP -> {
                    val (ok, reply) = host.openApp(decision.query)
                    lastAction = if (ok) "تم طلب فتح ${decision.query}" else "فشل فتح ${decision.query}: $reply"
                    consecutiveFailures = if (ok) 0 else consecutiveFailures + 1
                }
                ActionType.SCREEN_ACTION -> {
                    val ok = SearchClickService.instance?.performAgentAction(decision.query) == true
                    lastAction = if (ok) "نجح الإجراء ${decision.query}" else "فشل الإجراء ${decision.query}؛ اختر إجراء مختلفاً"
                    consecutiveFailures = if (ok) 0 else consecutiveFailures + 1
                }
                ActionType.CHAT, ActionType.QUESTION -> {
                    val screenNow = SearchClickService.visibleScreenText
                    val reply = if (screenNow.isBlank()) "وصلت لنقطة لا أستطيع التحقق منها لأن نص الشاشة غير متاح. آخر خطوة: $lastAction" else decision.reply.ifBlank { "انتهت محاولة التنفيذ؛ راجع الشاشة للتأكد من النتيجة." }
                    onFeedback(reply, "الوكيل", ActionType.SCREEN_ACTION)
                    return
                }
                else -> { lastAction = "الإجراء ${decision.actionType} خارج أدوات التحكم بالشاشة المتاحة"; consecutiveFailures++ }
            }
            if (consecutiveFailures >= 3) {
                onFeedback("وقفت بعد 3 محاولات غير ناجحة. آخر حالة: $lastAction. لم أعتبر المهمة مكتملة.", "الوكيل", ActionType.SCREEN_ACTION)
                return
            }
            delay(900)
        }
        onFeedback("وصلت للحد الآمن البالغ 8 خطوات. آخر نتيجة: $lastAction. راجع الشاشة؛ لم أؤكد اكتمال المهمة.", "الوكيل", ActionType.SCREEN_ACTION)
    }
    private fun handleLocalFallback(query: String) {
        // التحقق أولاً: هل السؤال علمي أو معرفي أو دردشة عادية؟
        if (AssistantRouter.isKnowledgeQuestion(query) || AssistantRouter.isFollowUp(query)) {
            val msg = "معلش يا باشا، الأسئلة المعرفية والشرح محتاجة اتصال بالإنترنت ومفتاح Gemini شغال في الإعدادات. تقدر تسألني عن الساعة، المنبه، التايمر، أو مهام يومك محلياً."
            onFeedback(msg, "معرفة (أوفلاين)", ActionType.QUESTION)
            return
        }

        // لو كان أمراً محلياً مدعوماً (منبه، مؤقت، طقس، مهام، وقت)
        val localReply = localAgent.handle(query)
        val actionType = when {
            localReply.contains("المنبه") -> ActionType.ALARM
            localReply.contains("التايمر") -> ActionType.TIMER
            localReply.contains("الساعه") || localReply.contains("النهارده") -> ActionType.CHAT
            localReply.contains("المهام") || localReply.contains("يومك") -> ActionType.TASK_LIST
            localReply.contains("الطقس") -> ActionType.WEATHER
            else -> ActionType.CHAT
        }
        onFeedback(localReply, "محلي", actionType)
    }

    private fun executeDecision(decision: GeminiDecision, originalQuery: String) {
        when (decision.actionType) {
            ActionType.QUESTION, ActionType.CHAT -> {
                val reply = decision.reply.ifBlank { "أنا معاك يا فندم، تحب أساعدك بإيه كمان؟" }
                val label = if (decision.actionType == ActionType.QUESTION) "إجابة علمية" else "محادثة"
                onFeedback(reply, label, decision.actionType)
            }

            ActionType.MUSIC_SEARCH -> {
                val app = if (decision.app.isNotBlank()) decision.app else preferencesManager.defaultMusicApp
                val query = decision.query.ifBlank { originalQuery }

                // تفعيل خدمة إمكانية الوصول لاختيار الأغنية بنسبة تطابق دقيقة
                val armed = host.armClick(app, query) { ok ->
                    if (ok) {
                        onFeedback("لقيت الأغنية في $app وشغّلتها بنجاح يا فندم.", "تم التشغيل", ActionType.MUSIC_SEARCH)
                    } else {
                        onFeedback("فتحت $app للبحث عن: $query. اضغط على النتيجة لتشغيلها.", "بحث موسيقى", ActionType.MUSIC_SEARCH)
                    }
                }

                val opened = host.openMusicSearch(app, query)
                val reply = if (!opened) {
                    "مش قادر أفتح تطبيق $app على الجهاز دلوقتي."
                } else if (armed) {
                    decision.reply.ifBlank { "بدور على $query في $app وهختارها تلقائيًا..." }
                } else {
                    decision.reply.ifBlank { "فتحت البحث عن $query في $app. تقدر تفعّل إمكانية الوصول للاختيار التلقائي." }
                }
                onFeedback(reply, "موسيقى: $query", ActionType.MUSIC_SEARCH)
            }

            ActionType.MUSIC_CONTROL -> {
                val action = when (decision.query.lowercase().trim()) {
                    "next", "skip" -> MusicAction.NEXT
                    "previous", "prev", "back" -> MusicAction.PREV
                    else -> MusicAction.TOGGLE
                }
                val success = host.musicControl(action)
                val currentTrack = host.getNowPlayingInfo()
                val reply = if (success) {
                    val trackSuffix = if (!currentTrack.isNullOrBlank()) " ($currentTrack)" else ""
                    decision.reply.ifBlank { "تمام يا فندم$trackSuffix." }
                } else {
                    "مافيش تطبيق موسيقى نشط حالياً. شغّل Spotify الأول أو فعّل وصول الإشعارات من الإعدادات."
                }
                onFeedback(reply, "تحكم بالوسائط", ActionType.MUSIC_CONTROL)
            }

            ActionType.WEATHER -> {
                val city = decision.query.ifBlank { preferencesManager.homeCity }
                onFeedback(decision.reply.ifBlank { "ثواني بجيبلك طقس $city..." }, "جلب الطقس", ActionType.WEATHER)
                host.weather(city) { resultText ->
                    onFeedback(resultText, "طقس: $city", ActionType.WEATHER)
                }
            }

            ActionType.ALARM -> {
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
                onFeedback(reply, "منبه $timeStr", ActionType.ALARM)
            }

            ActionType.TIMER -> {
                val sec = if (decision.seconds > 0) decision.seconds else 60L
                val ok = host.setTimer(sec, decision.label.ifBlank { "مؤقت" })
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
                onFeedback(reply, "تايمر $durationText", ActionType.TIMER)
            }

            ActionType.TASK_ADD -> {
                val taskText = decision.query.ifBlank { originalQuery }
                taskManager.addTask(taskText)
                val reply = decision.reply.ifBlank { "عيوني، سجلت في مهامك: $taskText" }
                onFeedback(reply, "إضافة مهمة", ActionType.TASK_ADD)
            }

            ActionType.TASK_LIST -> {
                val tasks = taskManager.getTasks()
                val reply = if (tasks.isEmpty()) {
                    "لسه ما سجلتش أي مهام في يومك يا باشا."
                } else {
                    val formatted = tasks.joinToString("\n") { (if (it.isCompleted) "✓ " else "• ") + it.text }
                    decision.reply.ifBlank { "مهامك المسجلة:\n$formatted" }
                }
                onFeedback(reply, "عرض المهام", ActionType.TASK_LIST)
            }

            ActionType.TASK_CLEAR -> {
                taskManager.clearTasks()
                val reply = decision.reply.ifBlank { "مسحت كل المهام من يومك." }
                onFeedback(reply, "مسح المهام", ActionType.TASK_CLEAR)
            }

            ActionType.MAPS -> {
                val placeQuery = decision.query.ifBlank { originalQuery }
                val url = "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(placeQuery, "UTF-8")
                val ok = host.openUrl(url)
                val reply = if (ok) {
                    decision.reply.ifBlank { "فتحت خرائط Google للبحث عن $placeQuery." }
                } else {
                    "تعذر فتح تطبيق الخرائط."
                }
                onFeedback(reply, "خرائط: $placeQuery", ActionType.MAPS)
            }

            ActionType.WEB_SEARCH -> {
                val searchQuery = decision.query.ifBlank { originalQuery }
                val url = "https://www.google.com/search?q=" + URLEncoder.encode(searchQuery, "UTF-8")
                val ok = host.openUrl(url)
                val reply = if (ok) {
                    decision.reply.ifBlank { "فتحت بحث Google عن: $searchQuery" }
                } else {
                    "تعذر فتح المتصفح للبحث."
                }
                onFeedback(reply, "بحث ويب", ActionType.WEB_SEARCH)
            }

            ActionType.CLARIFY -> {
                val reply = decision.reply.ifBlank { "مش متأكد من قصدك يا باشا، تحب أساعدك في إيه بالظبط؟" }
                onFeedback(reply, "توضيح", ActionType.CLARIFY)
            }

            ActionType.LOCAL_MUSIC -> {
                val songQuery = decision.query.ifBlank { originalQuery }
                val (ok, reply) = host.playLocalSong(songQuery)
                onFeedback(decision.reply.ifBlank { reply }, "مشغل الجهاز", ActionType.LOCAL_MUSIC)
            }

            ActionType.SEND_MESSAGE -> {
                val recipient = decision.recipient.ifBlank { "جهة الاتصال" }
                val msg = decision.query.ifBlank { originalQuery }
                val app = if (decision.app.isNotBlank()) decision.app else "sms"
                val (ok, reply) = host.sendMessage(recipient, msg, app)
                onFeedback(decision.reply.ifBlank { reply }, "إرسال رسالة", ActionType.SEND_MESSAGE)
            }

            ActionType.DEVICE_CONTROL -> {
                val act = decision.query.ifBlank { originalQuery }
                val (ok, reply) = host.controlDevice(act)
                onFeedback(decision.reply.ifBlank { reply }, "تحكم بالجهاز", ActionType.DEVICE_CONTROL)
            }

            ActionType.CREATE_NOTE -> {
                val noteText = decision.query.ifBlank { originalQuery }
                val (ok, reply) = host.createKeepNote(noteText)
                onFeedback(reply, "إنشاء ملاحظة", ActionType.CREATE_NOTE)
            }

            ActionType.OPEN_APP -> {
                val app = decision.query.ifBlank { originalQuery }
                val (ok, reply) = host.openApp(app)
                onFeedback(decision.reply.ifBlank { reply }, "فتح تطبيق", ActionType.OPEN_APP)
            }

            ActionType.PHONE_CALL -> {
                val recipient = decision.recipient.ifBlank { decision.query.ifBlank { originalQuery } }
                val (ok, reply) = host.makePhoneCall(recipient)
                onFeedback(decision.reply.ifBlank { reply }, "اتصال هاتفي", ActionType.PHONE_CALL)
            }

            ActionType.OPEN_TASKS -> {
                val reply = decision.reply.ifBlank { "فتحت لك مهامك يا باشا." }
                onFeedback(reply, "شاشة المهام", ActionType.OPEN_TASKS)
            }

            ActionType.OPEN_SETTINGS -> {
                val reply = decision.reply.ifBlank { "فتحت لك الإعدادات يا باشا." }
                onFeedback(reply, "شاشة الإعدادات", ActionType.OPEN_SETTINGS)
            }

            ActionType.CLOSE_OVERLAYS -> {
                val reply = decision.reply.ifBlank { "تمام يا فندم، رجعت لشاشة المساعد." }
                onFeedback(reply, "الرئيسية", ActionType.CLOSE_OVERLAYS)
            }

            ActionType.SCREEN_ACTION -> {
                val command = decision.query.ifBlank { originalQuery }
                val success = SearchClickService.instance?.performAgentAction(command) == true
                val reply = if (success) {
                    decision.reply.ifBlank { "نفذت الإجراء على الشاشة الحالية." }
                } else {
                    "مش قادر أنفذ الإجراء ده على الشاشة الحالية. اتأكد إن خدمة إمكانية الوصول مفعّلة، وإن العنصر ظاهر وقابل للتحكم."
                }
                onFeedback(reply, "تحكم بالشاشة", ActionType.SCREEN_ACTION)
            }
        }
    }
}
