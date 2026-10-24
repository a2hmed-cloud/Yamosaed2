package com.yamosaed.assistant

import com.yamosaed.assistant.agent.ActionType
import com.yamosaed.assistant.agent.AssistantRouter
import com.yamosaed.assistant.model.GeminiDecision
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiDecisionTest {

    @Test
    fun testScientificQuestionParsing() {
        val jsonStr = """
            {
                "action": "question",
                "reply": "السماء زرقاء بسبب تشتت رايلي، حيث تتشتت موجات الضوء الأزرق القصيرة في الغلاف الجوي أكثر من بقية الألوان."
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val rawAction = json.optString("action")
        val actionType = AssistantRouter.parseActionType(rawAction)

        val decision = GeminiDecision(
            action = rawAction,
            actionType = actionType,
            reply = json.optString("reply")
        )

        assertEquals(ActionType.QUESTION, decision.actionType)
        assertTrue(decision.reply.contains("تشتت رايلي"))
    }

    @Test
    fun testFollowUpDetection() {
        assertTrue(AssistantRouter.isFollowUp("ليه؟"))
        assertTrue(AssistantRouter.isFollowUp("وضح اكتر"))
        assertTrue(AssistantRouter.isFollowUp("اديني مثال"))
        assertTrue(AssistantRouter.isFollowUp("شغلها"))
        assertTrue(AssistantRouter.isFollowUp("كمل"))
    }

    @Test
    fun testKnowledgeQuestionDetection() {
        assertTrue(AssistantRouter.isKnowledgeQuestion("ليه السماء زرقاء؟"))
        assertTrue(AssistantRouter.isKnowledgeQuestion("اشرح قانون نيوتن التاني"))
        assertTrue(AssistantRouter.isKnowledgeQuestion("ساعدني اذاكر كيمياء"))
        assertFalse(AssistantRouter.isKnowledgeQuestion("اضبط منبه الساعة 7"))
        assertFalse(AssistantRouter.isKnowledgeQuestion("شغل اغنية لعمرو دياب"))
    }

    @Test
    fun testMusicDecisionWithArtistParsing() {
        val jsonStr = """
            {
                "action": "search_music",
                "query": "عمرو دياب يا انا يا لا",
                "app": "spotify",
                "reply": "عيوني، بدورلك على أغنية يا أنا يا لأ لعمرو دياب"
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val rawAction = json.optString("action")
        val decision = GeminiDecision(
            action = rawAction,
            actionType = AssistantRouter.parseActionType(rawAction),
            query = json.optString("query"),
            reply = json.optString("reply"),
            app = json.optString("app")
        )

        assertEquals(ActionType.MUSIC_SEARCH, decision.actionType)
        assertEquals("عمرو دياب يا انا يا لا", decision.query)
        assertEquals("spotify", decision.app)
    }

    @Test
    fun testMediaControlNextTrack() {
        val jsonStr = """
            {
                "action": "music_control",
                "query": "next",
                "reply": "تمام، الأغنية اللي بعدها."
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val rawAction = json.optString("action")
        val decision = GeminiDecision(
            action = rawAction,
            actionType = AssistantRouter.parseActionType(rawAction),
            query = json.optString("query"),
            reply = json.optString("reply")
        )

        assertEquals(ActionType.MUSIC_CONTROL, decision.actionType)
        assertEquals("next", decision.query)
    }

    @Test
    fun testExplicitWebSearch() {
        val jsonStr = """
            {
                "action": "web_search",
                "query": "أحدث أخبار التكنولوجيا",
                "reply": "فتحت بحث جوجل عن أحدث أخبار التكنولوجيا"
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val rawAction = json.optString("action")
        val decision = GeminiDecision(
            action = rawAction,
            actionType = AssistantRouter.parseActionType(rawAction),
            query = json.optString("query"),
            reply = json.optString("reply")
        )

        assertEquals(ActionType.WEB_SEARCH, decision.actionType)
        assertEquals("أحدث أخبار التكنولوجيا", decision.query)
    }

    @Test
    fun testAlarmDecisionParsing() {
        val jsonStr = """
            {
                "action": "alarm",
                "hour": 8,
                "minute": 30,
                "label": "الشغل",
                "reply": "تمام، ضبطت منبه الشغل على 08:30"
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val rawAction = json.optString("action")
        val decision = GeminiDecision(
            action = rawAction,
            actionType = AssistantRouter.parseActionType(rawAction),
            hour = json.optInt("hour"),
            minute = json.optInt("minute"),
            label = json.optString("label"),
            reply = json.optString("reply")
        )

        assertEquals(ActionType.ALARM, decision.actionType)
        assertEquals(8, decision.hour)
        assertEquals(30, decision.minute)
        assertEquals("الشغل", decision.label)
    }

    @Test
    fun testSearchClickTokensNormalization() {
        val tokens = SearchClickService.normalizeAndTokenize("شغل اغنية تملي معاك لعمرو دياب على سبوتيفاي")
        // "شغل", "اغنية", "على", "سبوتيفاي" should be filtered out
        assertTrue(tokens.contains("تملي"))
        assertTrue(tokens.contains("معاك"))
        assertTrue(tokens.contains("عمرو") || tokens.contains("لعمرو"))
        assertFalse(tokens.contains("شغل"))
        assertFalse(tokens.contains("سبوتيفاي"))
    }

    @Test
    fun testDeviceControlActionsParsing() {
        assertEquals(ActionType.DEVICE_CONTROL, AssistantRouter.parseActionType("device_control"))
        assertEquals(ActionType.LOCAL_MUSIC, AssistantRouter.parseActionType("local_music"))
        assertEquals(ActionType.SEND_MESSAGE, AssistantRouter.parseActionType("send_message"))
        assertEquals(ActionType.OPEN_APP, AssistantRouter.parseActionType("open_app"))
        assertEquals(ActionType.PHONE_CALL, AssistantRouter.parseActionType("phone_call"))
    }

    @Test
    fun testWakeWordPrefixExtraction() {
        val regex = Regex("^(يا\\s*مساعد|يا\\s*مسعد|مساعد|يا\\s*عم\\s*مساعد)[\\s,:]*", RegexOption.IGNORE_CASE)

        val wakeOnly = "يا مساعد"
        val m1 = regex.find(wakeOnly)
        assertTrue(m1 != null)
        val cmd1 = wakeOnly.substring(m1!!.range.last + 1).trim()
        assertEquals("", cmd1)

        val wakeWithCommand = "يا مساعد شغل الكشاف"
        val m2 = regex.find(wakeWithCommand)
        assertTrue(m2 != null)
        val cmd2 = wakeWithCommand.substring(m2!!.range.last + 1).trim()
        assertEquals("شغل الكشاف", cmd2)

        val wakeMessage = "يا مساعد ابعت رسالة لأحمد"
        val m3 = regex.find(wakeMessage)
        assertTrue(m3 != null)
        val cmd3 = wakeMessage.substring(m3!!.range.last + 1).trim()
        assertEquals("ابعت رسالة لأحمد", cmd3)
    }
}
