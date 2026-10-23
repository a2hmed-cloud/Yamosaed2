package com.yamosaed.assistant

import com.yamosaed.assistant.model.GeminiDecision
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiDecisionTest {

    @Test
    fun testMusicDecisionParsing() {
        val jsonStr = """
            {
                "action": "search_music",
                "query": "عمرو دياب",
                "app": "spotify",
                "reply": "عيوني، بدورلك على عمرو دياب في سبوتيفاي"
            }
        """.trimIndent()

        val json = JSONObject(jsonStr)
        val decision = GeminiDecision(
            action = json.optString("action"),
            query = json.optString("query"),
            reply = json.optString("reply"),
            app = json.optString("app")
        )

        assertEquals("search_music", decision.action)
        assertEquals("عمرو دياب", decision.query)
        assertEquals("spotify", decision.app)
        assertEquals("عيوني، بدورلك على عمرو دياب في سبوتيفاي", decision.reply)
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
        val decision = GeminiDecision(
            action = json.optString("action"),
            hour = json.optInt("hour"),
            minute = json.optInt("minute"),
            label = json.optString("label"),
            reply = json.optString("reply")
        )

        assertEquals("alarm", decision.action)
        assertEquals(8, decision.hour)
        assertEquals(30, decision.minute)
        assertEquals("الشغل", decision.label)
    }
}
