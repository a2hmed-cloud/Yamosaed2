package com.yamosaed.assistant.agent

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private fun has(text: String, vararg words: String) = words.any { text.contains(Arabic.norm(it)) }
private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

private val knownCities: Map<String, String> = mapOf(
    "اسماعيليه" to "Ismailia", "قاهره" to "Cairo", "اسكندريه" to "Alexandria",
    "جيزه" to "Giza", "بورسعيد" to "Port Said", "اسوان" to "Aswan", "اقصر" to "Luxor",
    "طنطا" to "Tanta", "منصوره" to "Mansoura", "سويس" to "Suez", "اسيوط" to "Asyut",
    "غردقه" to "Hurghada"
).mapKeys { Arabic.norm(it.key) }

private fun cityIn(text: String): String? =
    knownCities.entries.firstOrNull { text.contains(it.key) }?.value

fun parseClock(text: String): Pair<Int, Int>? {
    val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(text) ?: return null
    var h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    if (has(text, "مساء", "بالليل", "الليل") && h in 1..11) h += 12
    if (has(text, "صباح", "صبح") && h == 12) h = 0
    return if (h in 0..23 && min in 0..59) h to min else null
}

fun parseDuration(rawText: String): Long? {
    val text = Arabic.norm(rawText)
    if (has(text, "نص ساعه")) return 1800L
    val matches = Regex("(\\d+)\\s*(ثانيه|ثواني|دقيقه|دقايق|دقائق|ساعه|ساعات)").findAll(text).toList()
    if (matches.isNotEmpty()) return matches.sumOf { m ->
        m.groupValues[1].toLong() * when (m.groupValues[2]) {
            "ساعه", "ساعات" -> 3600L
            "دقيقه", "دقايق", "دقائق" -> 60L
            else -> 1L
        }
    }
    return when {
        has(text, "ساعه") -> 3600L
        has(text, "دقيقه") -> 60L
        else -> null
    }
}

private fun fmt(sec: Long) = when {
    sec >= 3600 && sec % 3600 == 0L -> "${sec / 3600} ساعه"
    sec >= 60 && sec % 60 == 0L -> "${sec / 60} دقيقه"
    else -> "$sec ثانيه"
}

fun defaultTools(): List<Tool> = listOf(
    AlarmTool(), TimerTool(), TimeTool(), DateTool(), WeatherTool(), ProfileTool(),
    TaskAddTool(), TaskListTool(), TaskClearTool(), PlacesTool(), MusicTool(),
    HelpTool()
)

class AlarmTool : Tool("alarm") {
    override fun score(text: String, ctx: Ctx) = when {
        has(text, "منبه") -> 3
        has(text, "فكرني") && parseClock(text) != null -> 2
        else -> 0
    }
    override fun run(text: String, ctx: Ctx) =
        schedule(text, ctx) ?: Reply("قولي الوقت، زي منبه الساعه 7 الصبح", askSlot = "time")
    override fun fill(slot: String, text: String, ctx: Ctx) =
        schedule(text, ctx) ?: Reply("الوقت مش واضح، قوله تاني زي 7 الصبح", askSlot = slot)

    private fun schedule(text: String, ctx: Ctx): Reply? {
        val (h, m) = parseClock(text) ?: return null
        val ok = ctx.host.setAlarm(h, m, "يا مساعد")
        return Reply(if (ok) "تمام، ضبطت المنبه على ${"%02d:%02d".format(h, m)}" else "مش قادر أضبط المنبه دلوقتي.")
    }
}

class TimerTool : Tool("timer") {
    override fun score(text: String, ctx: Ctx) = when {
        has(text, "تايمر", "مؤقت") -> 3
        has(text, "فكرني") && parseDuration(text) != null && parseClock(text) == null -> 2
        else -> 0
    }
    override fun run(text: String, ctx: Ctx) =
        schedule(text, ctx) ?: Reply("قول المدة، زي تايمر 5 دقايق", askSlot = "duration")
    override fun fill(slot: String, text: String, ctx: Ctx) =
        schedule(text, ctx) ?: Reply("المدة مش واضحة، قولها زي 5 دقايق", askSlot = slot)

    private fun schedule(text: String, ctx: Ctx): Reply? {
        val sec = parseDuration(text) ?: return null
        val ok = ctx.host.setTimer(sec, "تايمر")
        return Reply(if (ok) "تمام، التايمر ${fmt(sec)} وهينبهك لما يخلص." else "محتاج تسمح بالمنبهات الدقيقة الأول.")
    }
}

class TimeTool : Tool("time") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "الساعه", "الوقت") && has(text, "كام", "دلوقتي", "الان")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        val c = ctx.host.now()
        return Reply("الساعه دلوقتي ${"%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))}")
    }
}

class DateTool : Tool("date") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "تاريخ", "النهارده")) 2 else 0
    override fun run(text: String, ctx: Ctx) =
        Reply("النهارده " + SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar", "EG")).format(ctx.host.now().time))
}

class WeatherTool : Tool("weather") {
    override fun score(text: String, ctx: Ctx) = when {
        has(text, "طقس", "الجو", "حراره") -> 3
        ctx.lastTool == name && cityIn(text) != null -> 3
        else -> 0
    }
    override fun run(text: String, ctx: Ctx): Reply {
        val city = cityIn(text)
            ?: Regex("\\sفي\\s+(.+)$").find(text)?.groupValues?.get(1)?.trim()
            ?: ctx.homeCity
            ?: ctx.lastCity
            ?: "Ismailia"
        ctx.lastCity = city
        ctx.host.weather(city) { ctx.emit(it) }
        return Reply("بدور على الطقس في $city...")
    }
}

class ProfileTool : Tool("profile") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "انا ساكن", "مدينتي", "خلي المدينه", "غير المدينه")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        val city = cityFrom(text) ?: return Reply("قولي اسم المدينه اللي ساكن فيها", askSlot = "city")
        return save(city, ctx)
    }
    override fun fill(slot: String, text: String, ctx: Ctx) =
        (cityIn(text) ?: text.takeIf { it.isNotBlank() })?.let { save(it, ctx) }
            ?: Reply("مش لاقي المدينه دي، قولها تاني", askSlot = slot)

    private fun cityFrom(text: String): String? =
        cityIn(text) ?: Regex("(?:ساكن في|مدينتي|المدينه|في)\\s+(.+)$")
            .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun save(city: String, ctx: Ctx): Reply {
        ctx.host.save("home_city", city)
        return Reply("تمام، هفتكر إنك ساكن في $city، والطقس هيكون عنها.")
    }
}

class TaskAddTool : Tool("task_add") {
    private val prefix = Regex("(سجل|ضيف) (في )?(يومي|لمهامي|مهامي)")
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "سجل", "ضيف") && has(text, "يومي", "لمهامي", "مهامي")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        val task = text.replace(prefix, "").trim()
        return if (task.isBlank()) Reply("قول المهمة اللي عايز تسجلها", askSlot = "task") else add(task, ctx)
    }
    override fun fill(slot: String, text: String, ctx: Ctx) = add(text, ctx)

    private fun add(task: String, ctx: Ctx): Reply {
        val old = ctx.host.load("tasks").orEmpty()
        ctx.host.save("tasks", if (old.isBlank()) "• $task" else "$old\n• $task")
        return Reply("تمام، سجلت في يومك: $task")
    }
}

class TaskListTool : Tool("task_list") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "اعرض", "وريني", "جدولي", "مهامي", "يومي") && !has(text, "سجل", "ضيف", "امسح")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        val t = ctx.host.load("tasks").orEmpty()
        return Reply(if (t.isBlank()) "لسه مفيش مهام، قول سجل في يومي وبعدين المهمة." else "المهام المسجلة:\n$t")
    }
}

class TaskClearTool : Tool("task_clear") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "امسح", "شيل") && has(text, "مهام", "مهامي", "يومي")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        ctx.host.save("tasks", "")
        return Reply("مسحت كل المهام.")
    }
}

class PlacesTool : Tool("places") {
    override fun score(text: String, ctx: Ctx) =
        if (has(text, "مطاعم", "صيدليه", "كافيه", "مخبز", "محلات", "اماكن قريبه", "قريب مني")) 3 else 0
    override fun run(text: String, ctx: Ctx): Reply {
        val ok = ctx.host.openUrl("https://www.google.com/maps/search/?api=1&query=" + enc(text))
        return Reply(if (ok) "فتحت الخرائط للبحث." else "مش قادر أفتح الخرائط.")
    }
}

class MusicTool : Tool("music") {
    private val controls = listOf("وقف", "كمل", "ايقاف", "التالي", "الجايه", "قدام", "اسكب", "فاتت", "ارجع")
    private val apps = mapOf(
        "spotify" to listOf("سبوتيفاي", "spotify"),
        "soundcloud" to listOf("ساوند كلاود", "ساوندكلاود", "soundcloud"),
        "twist" to listOf("تويست", "twist")
    )
    private val junk = setOf(
        "شغل", "اغنيه", "اغاني", "موسيقي", "على", "ميوزك", "music", "ابحث", "دور", "افتح",
        "spotify", "سبوتيفاي", "soundcloud", "ساوند", "كلاود", "ساوندكلاود", "twist", "تويست"
    )
    private fun appOf(text: String): String? =
        apps.entries.firstOrNull { has(text, *it.value.toTypedArray()) }?.key

    override fun score(text: String, ctx: Ctx) = when {
        appOf(text) != null -> 3
        has(text, *controls.toTypedArray()) -> 3
        has(text, "شغل", "اغنيه", "اغاني", "موسيقي") -> 2
        else -> 0
    }

    override fun run(text: String, ctx: Ctx): Reply {
        val h = ctx.host
        val app = appOf(text)
        if (app == null && has(text, *controls.toTypedArray())) {
            val action = when {
                has(text, "التالي", "الجايه", "قدام", "اسكب") -> MusicAction.NEXT
                has(text, "فاتت", "ارجع") -> MusicAction.PREV
                else -> MusicAction.TOGGLE
            }
            return Reply(if (h.musicControl(action)) "تم" else "مفيش موسيقى شغالة، افتح تطبيق موسيقى الأول، أو فعّل وصول الإشعارات.")
        }
        val target = app ?: ctx.host.load("music_app") ?: "spotify"
        val q = text.split(" ").filter { it !in junk }.joinToString(" ").ifBlank { "موسيقى" }
        val clickable = target != "twist"
        val armed = clickable && h.armClick(target, q) { ok ->
            ctx.emit(if (ok) "شغّلتلك $q" else "ما لقيتش $q في النتايج، جرب اسم تاني")
        }
        return if (!h.openMusicSearch(target, q)) Reply("مش قادر أفتح التطبيق دلوقتي.")
        else Reply(
            when {
                armed -> "بدور على $q..."
                clickable -> "فتحت البحث عن $q، اختار الأغنية. فعّل إمكانية الوصول عشان أختارها بنفسي."
                else -> "فتحت بحث عن $q في تويست ميوزك."
            }
        )
    }
}

class HelpTool : Tool("help") {
    override fun score(text: String, ctx: Ctx) = when {
        has(text, "تقدر تعمل", "ساعدني", "اوامر", "تعرف تعمل") -> 3
        has(text, "شكرا", "تسلم") -> 3
        else -> 0
    }
    override fun run(text: String, ctx: Ctx): Reply =
        if (has(text, "شكرا", "تسلم")) Reply("العفو، تحت أمرك.")
        else Reply("أقدر أقولك الوقت والتاريخ، أضبط منبه أو تايمر، أقولك الطقس، أشغلك موسيقى من سبوتيفاي أو ساوند كلاود وأتحكم فيها، أفتحلك الخرائط، وأسجل مهامك في يومك. ولو قلتلي مدينتك مرة، هفتكرها.")
}

class WebSearchTool : Tool("web") {
    override fun score(text: String, ctx: Ctx) = 0
    override fun run(text: String, ctx: Ctx): Reply {
        val ok = ctx.host.openUrl("https://www.google.com/search?q=" + enc(text))
        return Reply(if (ok) "معنديش إجابة مباشرة، فتحت لك بحث عن الموضوع." else "مش قادر أفتح البحث دلوقتي.")
    }
}