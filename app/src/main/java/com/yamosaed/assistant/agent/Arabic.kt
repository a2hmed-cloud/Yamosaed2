package com.yamosaed.assistant.agent

object Arabic {
    private val marks = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
    private val nonWord = Regex("[^\\p{L}\\p{N}\\s]")
    private const val INDIC = "٠١٢٣٤٥٦٧٨٩"
    private val fillers = setOf("يا", "مساعد", "ممكن", "لو", "سمحت", "فضلك", "بقى", "كده")

    private val synonyms: Map<String, String> = mapOf(
        "عاوز" to "عايز", "عاوزه" to "عايز", "محتاج" to "عايز", "نفسي" to "عايز",
        "دقايق" to "دقيقه", "دقائق" to "دقيقه", "دقيقتين" to "2 دقيقه",
        "ساعتين" to "2 ساعه", "ساعات" to "ساعه",
        "صحيني" to "منبه",
        "واحد" to "1", "اتنين" to "2", "تلاته" to "3", "اربعه" to "4", "خمسه" to "5",
        "سته" to "6", "سبعه" to "7", "تمنيه" to "8", "تمانيه" to "8", "تسعه" to "9", "عشره" to "10"
    )

    fun tokens(input: String): List<String> {
        var s = input.lowercase().replace(marks, "")
        s = s.map { c -> val i = INDIC.indexOf(c); if (i >= 0) '0' + i else c }.joinToString("")
        s = s.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        s = nonWord.replace(s, " ")
        return s.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .flatMap { synonyms[it]?.split(" ") ?: listOf(it) }
            .filter { it !in fillers }
    }

    fun norm(input: String): String = tokens(input).joinToString(" ")
}