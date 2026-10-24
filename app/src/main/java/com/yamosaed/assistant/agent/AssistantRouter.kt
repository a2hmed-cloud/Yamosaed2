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
    OPEN_SETTINGS, // فتح شاشة الإعدادات
    OPEN_TASKS,    // فتح شاشة المهام
    CLOSE_OVERLAYS // العودة لشاشة العين
}

object AssistantRouter {

    fun parseActionType(actionStr: String): ActionType = when (actionStr.lowercase().trim()) {
        "question", "qa", "explain" -> ActionType.QUESTION
        "search_music", "music_search", "music_play" -> ActionType.MUSIC_SEARCH
        "music_control", "media_control" -> ActionType.MUSIC_CONTROL
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
