package com.yamosaed.assistant

import com.yamosaed.assistant.agent.Arabic
import com.yamosaed.assistant.agent.parseClock
import com.yamosaed.assistant.agent.parseDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun testArabicNormalization() {
        val tokens = Arabic.tokens("عَاوِزْ أَعْرَفْ السَّاعَة كام")
        // "عاوز" normalized to "عايز"
        assertTrue(tokens.contains("عايز"))
        // Diacritics removed
        assertTrue(tokens.contains("الساعه") || tokens.contains("ساعه"))
    }

    @Test
    fun testIndicNumbers() {
        val tokens = Arabic.tokens("منبه الساعة ٧ الصبح")
        val norm = Arabic.norm("منبه الساعة ٧ الصبح")
        assertTrue(norm.contains("7"))
    }

    @Test
    fun testParseClock() {
        val clockMorning = parseClock("منبه 7 الصبح")
        assertNotNull(clockMorning)
        assertEquals(7, clockMorning!!.first)
        assertEquals(0, clockMorning.second)

        val clockNight = parseClock("منبه 9 بالليل")
        assertNotNull(clockNight)
        assertEquals(21, clockNight!!.first)
        assertEquals(0, clockNight.second)

        val clockColon = parseClock("الساعة 14:45")
        assertNotNull(clockColon)
        assertEquals(14, clockColon!!.first)
        assertEquals(45, clockColon.second)
    }

    @Test
    fun testParseDuration() {
        val fiveMins = parseDuration("تايمر 5 دقايق")
        assertEquals(300L, fiveMins)

        val halfHour = parseDuration("نص ساعه")
        assertEquals(1800L, halfHour)

        val oneHour = parseDuration("ساعه")
        assertEquals(3600L, oneHour)
    }
}
