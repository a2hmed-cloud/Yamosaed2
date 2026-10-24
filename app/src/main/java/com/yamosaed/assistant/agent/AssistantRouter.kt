package com.yamosaed.assistant.agent

enum class ActionType {
    CHAT,          // الدردشة والترحيب والحديث العام
    QUESTION,      // الأسئلة العلمية والتعليمية والمعرفية والشرح
    WEB_SEARCH,    // البحث الصريح في الويب أو الأخبار
    MUSIC_SEARCH,  // البحث عن أغنية أو فنان وتشغيلها
    MUSIC_CONTROL, // التحكم بالموسيقى (وقف، كمل، التالي، السابق)
    WEATHER,       // حالة الطقس
    ALARM,         // ضبط المنبه
    TIMER,         // ضبط المؤقت
    TASK_ADD,      // إضافة مهمة
    TASK_LIST,     // عرض المهام
    TASK_CLEAR,    // مسح المهام
    MAPS,          // الأماكن والخرائط
    CLARIFY,       // طلب توضيح عند الغموض التام
    LOCAL_MUSIC,   // تشغيل أغاني من على الجهاز نفسه
    SEND_MESSAGE,  // إرسال رسائل SMS أو WhatsApp
    PHONE_CALL,    // الاتصال برقم أو شخص
    DEVICE_CONTROL,// التحكم في الجهاز (كشاف، صوت، بطارية، واي فاي، هوم)
    OPEN_APP,      // فتح أي تطبيق على الجهاز
    OPEN_SETTINGS, // فتح شاشة الإعدادات
    OPEN_TASKS,    // فتح شاشة المهام
    CLOSE_OVERLAYS // العودة لشاشة العين
}

data class LocalIntent(
    val type: ActionType,
    val query: String = "",
    val recipient: String = "",
    val app: String = "",
    val label: String = ""
)

object AssistantRouter {

    fun parseActionType(actionStr: String): ActionType = when (actionStr.lowercase().trim()) {
        "question", "qa", "explain" -> ActionType.QUESTION
        "search_music", "music_search", "music_play" -> ActionType.MUSIC_SEARCH
        "music_control", "media_control" -> ActionType.MUSIC_CONTROL
        "local_music", "device_music", "play_local" -> ActionType.LOCAL_MUSIC
        "send_message", "message", "sms", "whatsapp" -> ActionType.SEND_MESSAGE
        "phone_call", "call", "dial" -> ActionType.PHONE_CALL
        "device_control", "torch", "flashlight", "volume", "wifi", "battery" -> ActionType.DEVICE_CONTROL
        "open_app", "launch_app", "app" -> ActionType.OPEN_APP
        "weather" -> ActionType.WEATHER
        "alarm" -> ActionType.ALARM
        "timer" -> ActionType.TIMER
        "task_add" -> ActionType.TASK_ADD
        "task_list" -> ActionType.TASK_LIST
        "task_clear" -> ActionType.TASK_CLEAR
        "maps", "places" -> ActionType.MAPS
        "web_search", "web" -> ActionType.WEB_SEARCH
        "open_settings", "settings" -> ActionType.OPEN_SETTINGS
        "open_tasks", "tasks" -> ActionType.OPEN_TASKS
        "close", "back" -> ActionType.CLOSE_OVERLAYS
        "clarify" -> ActionType.CLARIFY
        else -> ActionType.CHAT
    }

    fun isOpenTasksCommand(text: String): Boolean {
        val norm = Arabic.norm(text).trim()
        return norm.contains("افتح مهامي") || norm.contains("وريني مهامي") ||
                norm == "مهامي" || norm == "المهام" || norm == "افتح المهام" || norm.contains("قائمه المهام")
    }

    fun isOpenSettingsCommand(text: String): Boolean {
        val norm = Arabic.norm(text).trim()
        return norm.contains("افتح الاعدادات") || norm.contains("افتح الإعدادات") ||
                norm == "اعدادات" || norm == "الاعدادات" || norm == "الإعدادات" || norm == "ضبط"
    }

    fun isCloseCommand(text: String): Boolean {
        val norm = Arabic.norm(text).trim()
        return norm == "اقفل" || norm == "ارجع" || norm == "اغلق" || norm == "خروج"
    }

    /**
     * استخراج وتحديد أوامر الهاتف المباشرة (الرسائل، المكالمات، التطبيقات، التحكم بالجهاز، أغاني الجهاز)
     * لتنفيذها فوراً بسرعة فائقة وموثوقية 100% دون انتظار
     */
    fun detectDeviceIntent(rawQuery: String): LocalIntent? {
        val norm = Arabic.norm(rawQuery).lowercase().trim()
        if (norm.isBlank()) return null

        // 1. أوامر شاشات الواجهة
        if (isOpenTasksCommand(norm)) return LocalIntent(ActionType.OPEN_TASKS)
        if (isOpenSettingsCommand(norm)) return LocalIntent(ActionType.OPEN_SETTINGS)
        if (isCloseCommand(norm)) return LocalIntent(ActionType.CLOSE_OVERLAYS)

        // 2. تشغيل الأغاني من على الجهاز نفسه
        if (norm.contains("من علي الجهاز") || norm.contains("من على الجهاز") || norm.contains("من الجهاز") ||
            norm.contains("من علي الموبايل") || norm.contains("من على الموبايل") || norm.contains("من الموبايل") ||
            norm.contains("ملفات الجهاز") || norm.contains("اغاني الجهاز") ||
            norm.contains("اغاني الموبايل") || norm.contains("من عندي") ||
            norm.contains("تراك محلي") || (norm.startsWith("شغل") && norm.contains("محلي"))
        ) {
            val songName = rawQuery.trim()
                .replace(Regex("(من على الجهاز|من على الموبايل|من الجهاز|من الموبايل|ملفات الجهاز|اغاني الجهاز|اغاني الموبايل|من عندي|محلي|شغل|اغنية|أغنية|تراك)", RegexOption.IGNORE_CASE), "")
                .trim()
            return LocalIntent(ActionType.LOCAL_MUSIC, query = songName, label = "مشغل الجهاز")
        }

        // 3. إرسال الرسائل (SMS و WhatsApp)
        val isMessageCmd = norm.contains("ابعت رسال") || norm.contains("ارسل رسال") ||
                norm.contains("ابعت مسدج") || norm.contains("ارسل مسدج") ||
                norm.contains("ابعت واتساب") || norm.contains("ابعت واتس") ||
                norm.contains("رساله واتساب") || norm.contains("مسدج واتساب") ||
                norm.startsWith("مسدج ل") || norm.startsWith("رساله ل")

        if (isMessageCmd) {
            val isWhatsApp = norm.contains("واتساب") || norm.contains("واتس") || norm.contains("whatsapp")
            val app = if (isWhatsApp) "whatsapp" else "sms"

            val rawClean = rawQuery.trim()
                .replace(Regex("^(ابعت|ارسل|ابعث)\\s*(رساله|رسالة|مسدج|واتساب|واتس)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^(رساله|رسالة|مسدج)\\s*(واتساب|واتس)?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()

            val targetPart = rawClean.replace(Regex("^(لـ|ل|الي|إلى|علي|على)\\s*", RegexOption.IGNORE_CASE), "").trim()
            val tokens = targetPart.split(Regex("\\s+"), limit = 2)
            val recipient = tokens.firstOrNull().orEmpty()
            val messageBody = if (tokens.size > 1) tokens[1].trim() else ""

            return LocalIntent(
                type = ActionType.SEND_MESSAGE,
                recipient = recipient,
                query = messageBody,
                app = app,
                label = if (isWhatsApp) "واتساب" else "رسالة SMS"
            )
        }

        // 4. المكالمات الهاتفية
        val isCallCmd = norm.startsWith("اتصل") || norm.startsWith("كلم ") || norm.startsWith("رن على") ||
                norm.startsWith("رن علي") || norm.startsWith("مكالمه ل") || norm.startsWith("مكالمة ل")
        if (isCallCmd) {
            val target = rawQuery.trim()
                .replace(Regex("^(اتصل\\s*(بـ|ب|على|علي|لي)?|كلم|رن\\s*(على|علي)|مكالمه\\s*(لـ|ل)?|مكالمة\\s*(لـ|ل)?)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (target.isNotBlank()) {
                return LocalIntent(
                    type = ActionType.PHONE_CALL,
                    recipient = target,
                    query = target,
                    label = "اتصال هاتفي"
                )
            }
        }

        // 5. التحكم في عتاد وخصائص الجهاز
        if (norm.contains("كشاف") || norm.contains("فلاش") ||
            norm.contains("ولع النور") || norm.contains("شغل النور") || norm.contains("اطفي النور") || norm.contains("طفي النور") ||
            norm.contains("علي الصوت") || norm.contains("ارفع الصوت") || norm.contains("زود الصوت") ||
            norm.contains("وطي الصوت") || norm.contains("اخفض الصوت") || norm.contains("نزل الصوت") ||
            norm.contains("اكتم الصوت") || norm.contains("كتم الصوت") || norm.contains("ميوت") || norm.contains("صامت") ||
            norm.contains("بطاريه") || norm.contains("بطارية") || norm.contains("شحن الموبايل") || norm.contains("شحن البطاريه") || norm.contains("نسبه البطاريه") ||
            norm.contains("واي فاي") || norm.contains("وايفاي") || norm.contains("wifi") ||
            norm.contains("بلوتوث") || norm.contains("bluetooth") ||
            norm.contains("الرئيسيه") || norm.contains("الهوم") ||
            norm.contains("التطبيقات الاخيره") || norm.contains("البرامج المفتوحه") ||
            norm.contains("شريط الاشعارات") || norm.contains("الاشعارات") ||
            norm.contains("اقفل الشاشه") || norm.contains("قفل الشاشه") || norm.contains("اطفي الشاشه")
        ) {
            return LocalIntent(
                type = ActionType.DEVICE_CONTROL,
                query = norm,
                label = "تحكم بالجهاز"
            )
        }

        // 6. فتح التطبيقات على الجهاز
        val isOpenAppCmd = (norm.startsWith("افتح ") || norm.startsWith("افتحلي ") || norm.startsWith("شغل تطبيق ") || norm.startsWith("افتح تطبيق ")) &&
                !norm.contains("مهامي") && !norm.contains("اعدادات") && !norm.contains("الإعدادات")

        if (isOpenAppCmd) {
            val appTarget = rawQuery.trim()
                .replace(Regex("^(افتحلي|افتح|شغل تطبيق|افتح تطبيق)\\s*(تطبيق|برنامج)?\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (appTarget.isNotBlank()) {
                return LocalIntent(
                    type = ActionType.OPEN_APP,
                    query = appTarget,
                    label = "فتح تطبيق"
                )
            }
        }

        // التطبيقات المباشرة بدون كلمة "افتح"
        val directApps = listOf("يوتيوب", "واتساب", "الواتس", "واتس", "كاميرا", "الكاميرا", "فيسبوك", "انستجرام", "تليجرام", "تيك توك", "حاسبه", "معرض الصور", "الاستوديو")
        if (norm.startsWith("شغل ") && directApps.any { norm.contains(it) }) {
            val target = rawQuery.trim().replace(Regex("^شغل\\s*", RegexOption.IGNORE_CASE), "").trim()
            return LocalIntent(ActionType.OPEN_APP, query = target, label = "فتح تطبيق")
        }

        return null
    }

    /**
     * التحقق مما إذا كان الطلب سؤالاً معرفياً أو علمياً لا يجوز توجيهه لفتح البحث تلقائياً عند غياب الإنترنت
     */
    fun isKnowledgeQuestion(text: String): Boolean {
        val norm = Arabic.norm(text)
        val knowledgePrefixes = listOf(
            "ليه", "ازاي", "كيف", "اشرح", "ما هو", "ما هي", "ماهو", "ماهي",
            "قانون", "نظريه", "مين هو", "مين هي", "عرف", "يعني ايه", "ساعدني اذاكر",
            "فهمني", "وضح", "اديني مثال", "كمل"
        )
        return knowledgePrefixes.any { norm.startsWith(it) || norm.contains(" $it ") }
    }

    /**
     * التحقق مما إذا كان الطلب متابعة لسياق محادثة سابقة (Follow-up)
     */
    fun isFollowUp(text: String): Boolean {
        val norm = Arabic.norm(text).trim()
        val followUps = listOf(
            "ليه", "وضح اكتر", "اديني مثال", "كمل", "طب ليه", "وايه كمان",
            "فهمني اكتر", "مش فاهم", "شغلها", "شغلها تاني", "كرر ده", "اللي بعدها"
        )
        return followUps.any { norm == it || norm.startsWith("$it ") }
    }
}
