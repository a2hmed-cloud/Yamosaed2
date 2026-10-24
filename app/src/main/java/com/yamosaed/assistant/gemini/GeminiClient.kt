package com.yamosaed.assistant.gemini

import com.yamosaed.assistant.agent.AssistantRouter
import com.yamosaed.assistant.model.ChatMessage
import com.yamosaed.assistant.model.GeminiDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient {

    companion object {
        private const val PRIMARY_MODEL = "gemini-2.5-flash"
        private const val FALLBACK_MODEL = "gemini-1.5-flash"

        private val SYSTEM_INSTRUCTION = """
أنت "يا مساعد" — مساعد أندرويد شخصي عربي مصري، ذكي جداً، واسع الثقافة، سريع البديهة وخفيف الدم دون ابتذال، مثل Siri ولكن بطابع مصري ودود وأصيل.

قدراتك الأساسية:
1. الإجابة المعرفية والعلمية المباشرة (QUESTION):
   - لو سألك المستخدم سؤالاً علمياً أو تعليمياً أو عاماً (مثل: "ليه السماء زرقاء؟"، "اشرح قانون نيوتن"، "ساعدني أذاكر كيمياء"، "ما هي عاصمة البرازيل؟")، اشرح فوراً وبشكل مبسط، جذاب، ودقيق باللغة العربية مع روح مصرية لطيفة.
   - لا تحول الأسئلة المعرفية أبداً إلى بحث في الويب أو أفعال أخرى! أجب بنفسك مباشرة.
2. المحادثة والمتابعة وسياق الحديث (CHAT & FOLLOW_UP):
   - افهم الأسئلة التتابعية المعتمدة على ما سبق في المحادثة مثل: "ليه؟"، "طب وضح أكتر"، "اديني مثال"، "كمل"، "كرر ده"، "شغلها".
   - تجاوب مع الدردشة العادية ("عامل إيه يا مساعد"، "يومك عامل إزاي") بروح إيجابية ومرحة.
3. استدعاء أدوات الأندرويد بدقة:
   - search_music: عند طلب تشغيل أو سماع أغنية أو فنان أو مزيكا (مثال: "شغل عمرو دياب"، "اسمع تامر حسني").
   - music_control: للتحكم (toggle, next, previous).
   - weather: عند السؤال عن حالة الجو أو الطقس أو الحرارة.
   - alarm: لضبط منبه مع تحديد الساعة والدقيقة (0-23 و 0-59).
   - timer: لضبط مؤقت بالثواني.
   - task_add / task_list / task_clear: لإدارة مهام وتذكيرات اليوم.
   - maps: للبحث عن أماكن قريبة (صيدلية، مطعم، بنك).
   - web_search: فقط عندما يطلب المستخدم صراحة البحث في الويب أو عن أحدث الأخبار ("ابحث في جوجل عن...", "دور على أخبار التكنولوجيا").
   - clarify: فقط عندما يكون الطلب غامضاً جداً ولا يمكن تخمين مقصده، اسأل سؤالاً توضيحياً قصيراً.

تنسيق الإخراج الإلزامي:
أرجع كائن JSON صالح فقط دون كتل Markdown ودون أي نص خارجي:
{
  "action": "question" | "chat" | "search_music" | "music_control" | "weather" | "alarm" | "timer" | "task_add" | "task_list" | "task_clear" | "maps" | "web_search" | "clarify",
  "reply": "الرد الكامل والمفيد والممتع الموجه للمستخدم بالعربية المصرية",
  "query": "كلمات البحث أو نص المهمة أو المدينة أو التحكم عند الحاجة",
  "app": "spotify" | "soundcloud",
  "hour": -1,
  "minute": -1,
  "seconds": 0,
  "label": ""
}
""".trimIndent()
    }

    suspend fun decide(
        apiKey: String,
        userQuery: String,
        screenContext: String = "",
        recentHistory: List<ChatMessage> = emptyList()
    ): Result<GeminiDecision> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مدخل"))
        }

        tryPrimaryThenFallback(trimmedKey, userQuery.trim(), screenContext, recentHistory)
    }

    private fun tryPrimaryThenFallback(
        apiKey: String,
        userQuery: String,
        screenContext: String,
        recentHistory: List<ChatMessage>
    ): Result<GeminiDecision> {
        val firstAttempt = callGeminiApi(PRIMARY_MODEL, apiKey, userQuery, screenContext, recentHistory)
        if (firstAttempt.isSuccess) {
            return firstAttempt
        }

        val err = firstAttempt.exceptionOrNull()
        // If 404 or model unavailable, try fallback model
        if (err?.message?.contains("404") == true || err?.message?.contains("not found", ignoreCase = true) == true) {
            return callGeminiApi(FALLBACK_MODEL, apiKey, userQuery, screenContext, recentHistory)
        }
        return firstAttempt
    }

    private fun callGeminiApi(
        model: String,
        apiKey: String,
        userQuery: String,
        screenContext: String,
        recentHistory: List<ChatMessage>
    ): Result<GeminiDecision> {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 25000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val root = JSONObject()

            // System Instructions
            root.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION)))
            })

            // Conversation history (up to last 8 messages)
            val contentsArray = JSONArray()
            val historySlice = recentHistory.takeLast(8)
            for (msg in historySlice) {
                val role = if (msg.isUser) "user" else "model"
                contentsArray.put(
                    JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", msg.text)))
                    }
                )
            }

            // Current prompt with optional screen context
            val promptBuilder = StringBuilder(userQuery)
            if (screenContext.isNotBlank()) {
                promptBuilder.append("\n\n[معلومات استرشادية من شاشة الموسيقى: ${screenContext.take(1500)}]")
            }

            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", promptBuilder.toString())))
                }
            )
            root.put("contents", contentsArray)

            // Generation config
            root.put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            })

            conn.outputStream.use { os ->
                os.write(root.toString().toByteArray(Charsets.UTF_8))
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val respText = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            if (code !in 200..299) {
                val errMsg = try {
                    val errJson = JSONObject(respText)
                    val rawMsg = errJson.optJSONObject("error")?.optString("message") ?: ""
                    when {
                        code == 400 && rawMsg.contains("API key not valid", ignoreCase = true) -> "مفتاح Gemini API غير صالح، تأكد منه في الإعدادات."
                        code == 429 -> "تم تجاوز حد الاستخدام المسموح لـ Gemini حالياً، انتظر دقيقة وجرب تاني."
                        code == 404 -> "النموذج غير متاح ($model)"
                        else -> "خطأ في الاتصال بالذكاء الاصطناعي ($code)"
                    }
                } catch (_: Exception) {
                    "خطأ اتصال برقم $code"
                }
                return Result.failure(Exception(errMsg))
            }

            val respObj = JSONObject(respText)
            val candidates = respObj.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return Result.failure(Exception("لم يصل رد من نموذج الذكاء الاصطناعي"))
            }

            val parts = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val rawText = parts.getJSONObject(0).getString("text")

            val cleanJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = try {
                JSONObject(cleanJson)
            } catch (_: Exception) {
                // If model returned plain text instead of JSON, treat as friendly answer
                JSONObject().apply {
                    put("action", "question")
                    put("reply", cleanJson)
                }
            }

            val rawAction = json.optString("action", "chat")
            val actionType = AssistantRouter.parseActionType(rawAction)

            val decision = GeminiDecision(
                action = rawAction,
                actionType = actionType,
                query = json.optString("query", ""),
                reply = json.optString("reply", ""),
                hour = json.optInt("hour", -1),
                minute = json.optInt("minute", -1),
                seconds = json.optLong("seconds", 0L),
                app = json.optString("app", "spotify"),
                label = json.optString("label", "")
            )
            return Result.success(decision)

        } catch (e: Exception) {
            val friendlyMsg = when {
                e is java.net.SocketTimeoutException -> "انتهت مهلة الاتصال بـ Gemini، تأكد من سرعة الإنترنت."
                e is java.net.UnknownHostException -> "لا يوجد اتصال بالإنترنت للوصول لـ Gemini."
                else -> e.localizedMessage ?: "حدث خطأ غير متوقع أثناء الاتصال."
            }
            return Result.failure(Exception(friendlyMsg))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("المفتاح فارغ"))
        }
        var conn: HttpURLConnection? = null
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$PRIMARY_MODEL:generateContent?key=$apiKey"
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = JSONObject().apply {
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", "رد بكلمة: متصل")))
                    }
                ))
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 200..299) {
                return@withContext Result.success("متصل بنجاح مع Gemini API ($PRIMARY_MODEL)")
            }
            val errStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = try {
                val errObj = JSONObject(errStream).optJSONObject("error")
                errObj?.optString("message") ?: "كود الخطأ: $code"
            } catch (_: Exception) { "كود الخطأ: $code" }
            return@withContext Result.failure(Exception(msg))
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("تعذر الوصول لـ Gemini: ${e.localizedMessage ?: "تأكد من الإنترنت"}"))
        } finally {
            conn?.disconnect()
        }
    }
}
