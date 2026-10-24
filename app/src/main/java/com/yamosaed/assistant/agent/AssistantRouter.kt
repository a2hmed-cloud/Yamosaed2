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
    CLARIFY        // طلب توضيح عند الغموض التام
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
        "clarify" -> ActionType.CLARIFY
        else -> ActionType.CHAT
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
