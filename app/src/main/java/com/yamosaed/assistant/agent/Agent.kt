package com.yamosaed.assistant.agent

import java.util.Calendar

enum class MusicAction { TOGGLE, NEXT, PREV }

interface Host {
    fun now(): Calendar
    fun setAlarm(hour: Int, minute: Int, label: String): Boolean
    fun setTimer(seconds: Long, label: String): Boolean
    fun openUrl(url: String): Boolean
    fun weather(city: String, done: (String) -> Unit)
    fun musicControl(action: MusicAction): Boolean
    fun openMusicSearch(app: String, query: String): Boolean
    /** بيفعّل الضغط التلقائي على أول نتيجة مطابقة. يرجع false لو الخدمة مش مفعّلة. */
    fun armClick(app: String, query: String, done: (Boolean) -> Unit): Boolean
    fun load(key: String): String?
    fun save(key: String, value: String)
}

class Ctx(val host: Host, val emit: (String) -> Unit) {
    var lastTool: String? = null
    var lastCity: String? = null
    val homeCity: String? get() = host.load("home_city")
}

data class Reply(val text: String, val askSlot: String? = null)

abstract class Tool(val name: String) {
    /** 0 = مش الأداة دي. الأعلى يفوز. */
    abstract fun score(text: String, ctx: Ctx): Int
    abstract fun run(text: String, ctx: Ctx): Reply
    open fun fill(slot: String, text: String, ctx: Ctx): Reply =
        Reply("مش فاهم، قولها تاني.", askSlot = slot)
}

object Splitter {
    private val sep = Regex("[،,؛]|\\s+(?:وبعدين|وبعد كده|ثم)\\s+|\\s+و\\s+")
    fun split(input: String): List<String> =
        input.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
}

class Agent(
    private val tools: List<Tool>,
    private val fallback: Tool,
    private val ctx: Ctx
) {
    private data class Match(val tool: Tool, val score: Int)
    private var pending: Pair<Tool, String>? = null

    fun handle(input: String): String {
        val waiting = pending
        if (waiting != null) {
            val text = Arabic.norm(input)
            val fresh = best(text)
            pending = null
            if (fresh == null || fresh.score < 3) {
                return apply(waiting.first, waiting.first.fill(waiting.second, text, ctx))
            }
        }

        val parts = Splitter.split(input)
        val matches = parts.map { best(Arabic.norm(it)) }
        if (parts.size > 1 && matches.all { it != null }) {
            return parts.indices.joinToString(" ") { i ->
                val m = matches[i]!!
                apply(m.tool, m.tool.run(Arabic.norm(parts[i]), ctx))
            }
        }

        val text = Arabic.norm(input)
        if (text.isBlank()) return "قولي حاجة أساعدك فيها."
        val m = best(text)
        return if (m != null) apply(m.tool, m.tool.run(text, ctx))
        else apply(fallback, fallback.run(text, ctx))
    }

    private fun best(text: String): Match? {
        var found: Match? = null
        for (t in tools) {
            val s = t.score(text, ctx)
            if (s > 0 && (found == null || s > found.score)) found = Match(t, s)
        }
        return found
    }

    private fun apply(tool: Tool, r: Reply): String {
        ctx.lastTool = tool.name
        pending = r.askSlot?.let { tool to it }
        return r.text
    }
}