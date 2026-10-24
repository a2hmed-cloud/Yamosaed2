package com.yamosaed.assistant.agent.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.yamosaed.assistant.agent.Arabic
import kotlin.math.max

data class DiscoveredApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean = false,
    val aliases: List<String> = emptyList()
)

data class AppMatchResult(
    val app: DiscoveredApp,
    val score: Int,
    val matchType: String
)

class AppDiscoveryService(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    // خريطة أسماء واختصارات التطبيقات الشائعة بالعربية والإنجليزية
    private val commonAliases = mapOf(
        "com.google.android.keep" to listOf("keep", "google keep", "جوجل كيب", "كيب", "ملاحظات جوجل", "ملاحظات", "نوتس", "notes"),
        "com.whatsapp" to listOf("whatsapp", "واتساب", "الواتس", "واتس", "whats"),
        "com.google.android.youtube" to listOf("youtube", "يوتيوب", "اليوتيوب", "فيديوهات"),
        "com.android.chrome" to listOf("chrome", "كروم", "الكروم", "متصفح", "متصفح كروم", "browser"),
        "com.facebook.katana" to listOf("facebook", "فيسبوك", "الفيس", "فيس", "fb"),
        "com.instagram.android" to listOf("instagram", "انستجرام", "الانستا", "انستا", "insta"),
        "org.telegram.messenger" to listOf("telegram", "تليجرام", "التيليجرام", "تليغرام"),
        "com.zhiliaoapp.musically" to listOf("tiktok", "تيك توك", "التيك توك", "تيكتوك"),
        "com.spotify.music" to listOf("spotify", "سبوتيفاي", "السبوتيفاي", "سبوتفاي"),
        "com.soundcloud.android" to listOf("soundcloud", "ساوندكلاود", "ساوند كلاود"),
        "com.google.android.apps.maps" to listOf("maps", "خرائط", "الخرائط", "خرائط جوجل", "google maps"),
        "com.google.android.gm" to listOf("gmail", "جيميل", "البريد", "ايميل", "بريد جوجل"),
        "com.google.android.calculator" to listOf("calculator", "حاسبة", "الحاسبة", "آلة حاسبة", "اله حاسبه"),
        "com.android.settings" to listOf("settings", "إعدادات", "الاعدادات", "الضبط", "ضبط الهاتف")
    )

    /**
     * اكتشاف جميع التطبيقات المثبتة القابلة للتشغيل على الجهاز
     */
    fun getInstalledApps(): List<DiscoveredApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        val discoveredList = mutableListOf<DiscoveredApp>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg.isNullOrBlank() || seenPackages.contains(pkg)) continue
            seenPackages.add(pkg)

            val label = resolveInfo.loadLabel(packageManager).toString().trim()
            val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val aliases = commonAliases[pkg] ?: emptyList()

            discoveredList.add(
                DiscoveredApp(
                    packageName = pkg,
                    label = label,
                    isSystemApp = isSystem,
                    aliases = aliases
                )
            )
        }
        return discoveredList
    }

    /**
     * البحث الذكي عن التطبيقات وترتيب النتائج حسب نسبة التطابق
     * قاعدة إلزامية: عدم فتح Google لمجرد أن المستخدم قال Google Keep!
     */
    fun searchApps(query: String): List<AppMatchResult> {
        val cleanQuery = query.trim().lowercase()
        val normQuery = Arabic.norm(cleanQuery).lowercase().trim()
        if (normQuery.isBlank()) return emptyList()

        val allApps = getInstalledApps()
        val results = mutableListOf<AppMatchResult>()

        for (app in allApps) {
            val cleanLabel = app.label.trim().lowercase()
            val normLabel = Arabic.norm(cleanLabel).lowercase().trim()

            var bestScore = 0
            var matchType = "NONE"

            // 1. التطابق التام مع الاسم
            if (cleanLabel == cleanQuery || normLabel == normQuery) {
                bestScore = 1000
                matchType = "EXACT_LABEL"
            }

            // 2. فحص الأسماء المستعارة والشهيرة
            for (alias in app.aliases) {
                val cleanAlias = alias.lowercase().trim()
                val normAlias = Arabic.norm(cleanAlias).lowercase().trim()

                if (cleanAlias == cleanQuery || normAlias == normQuery) {
                    val score = 950
                    if (score > bestScore) {
                        bestScore = score
                        matchType = "EXACT_ALIAS ($alias)"
                    }
                } else if (normQuery.contains(normAlias) || normAlias.contains(normQuery)) {
                    // حساب دقة احتواء العبارة (الأطول والأكثر خصوصية يحصل على سكور أعلى)
                    val overlapRatio = normAlias.length.toDouble() / max(normQuery.length, normAlias.length)
                    val score = (700 * overlapRatio).toInt()
                    if (score > bestScore) {
                        bestScore = score
                        matchType = "PARTIAL_ALIAS ($alias)"
                    }
                }
            }

            // 3. تطابق احتواء الاسم الظاهر
            if (bestScore < 900) {
                if (normLabel.startsWith(normQuery)) {
                    val score = 800 - (normLabel.length - normQuery.length)
                    if (score > bestScore) {
                        bestScore = score
                        matchType = "LABEL_PREFIX"
                    }
                } else if (normQuery.startsWith(normLabel)) {
                    // المستخدم طلب تطبيقاً أطول (مثال: طلب Google Keep والاسم هو Google فقط)
                    // هنا نمنحها نقاطاً منخفضة جداً لمنع فتح تطبيق Google العام!
                    val score = 200 - (normQuery.length - normLabel.length)
                    if (score > bestScore && !normQuery.contains("keep")) {
                        bestScore = score
                        matchType = "SUBSTRING_PENALTY"
                    }
                } else if (normLabel.contains(normQuery)) {
                    val score = 500 - (normLabel.length - normQuery.length)
                    if (score > bestScore) {
                        bestScore = score
                        matchType = "LABEL_CONTAINS"
                    }
                }
            }

            // 4. تطابق الكلمات الفردية
            if (bestScore < 400) {
                val queryTokens = normQuery.split(" ").filter { it.length > 1 }
                val labelTokens = normLabel.split(" ").filter { it.length > 1 }
                val matchingTokens = queryTokens.filter { labelTokens.contains(it) }

                if (matchingTokens.isNotEmpty()) {
                    val tokenScore = (matchingTokens.size * 300) / max(queryTokens.size, labelTokens.size)
                    if (tokenScore > bestScore) {
                        bestScore = tokenScore
                        matchType = "TOKEN_MATCH"
                    }
                }
            }

            if (bestScore >= 300) {
                results.add(AppMatchResult(app, bestScore, matchType))
            }
        }

        // الترتيب التنازلي حسب السكور
        return results.sortedByDescending { it.score }
    }

    /**
     * إيجاد أفضل تطبيق مطابق بالاسم، مع مراعاة التمييز الدقيق (Disambiguation)
     */
    fun findBestAppMatch(query: String): DiscoveredApp? {
        val matches = searchApps(query)
        val first = matches.firstOrNull() ?: return null

        // في حال وجود تطبيقين قريبين جداً في النقاط، نفضل التطبيق المحدد بدقة
        if (matches.size > 1) {
            val second = matches[1]
            // لو كان الطلب Google Keep، نتأكد بنسبة 100% أننا لن نرجع تطبيق Google العام
            if (query.lowercase().contains("keep") && second.app.packageName.contains("keep")) {
                return second.app
            }
        }
        return first.app
    }

    /**
     * تشغيل التطبيق عبر Intent Launch الرسمي
     */
    fun launchApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
