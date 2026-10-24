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

internal fun geminiGenerateContentUrl(model: String): String {
    val modelId = model.trim().removePrefix("models/")
    require(modelId.isNotBlank()) { "معرّف النموذج فارغ" }
    return "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
}

class GeminiClient {

    companion object {
        const val DEFAULT_MODEL = "gemini-1.5-flash"

        // قائمة النماذج حسب أولوية الاستقرار والسرعة
        val FALLBACK_ORDER = listOf(
            "gemini-1.5-flash",
            "gemini-2.0-flash",
            "gemini-2.5-flash",
            "gemini-1.5-pro"
        )

        private val SYSTEM_INSTRUCTION = """
أنت "يا مساعد" — مساعد أندرويد شخصي عربي مصري، ذكي جداً، واسع الثقافة، سريع البديهة وخفيف الدم دون ابتذال، مثل Siri ولكن بطابع مصري ودود وأصيل.

قدراتك الأساسية:
1. الإجابة المعرفية والعلمية المباشرة (QUESTION):
   - لو سألك المستخدم سؤالاً علمياً أو تعليمياً أو عاماً (مثل: "ليه السماء زرقاء؟"، "اشرح قانون نيوتن"، "ساعدني أذاكر كيمياء"، "ما هي عاصمة البرازيل؟")، اشرح فوراً وبشكل مبسط، جذاب، ودقيق باللغة العربية مع روح مصرية لطيفة.
   - لا تحول الأسئلة المعرفية أبداً إلى بحث في الويب أو أفعال أخرى! أجب بنفسك مباشرة.
2. المحادثة والمتابعة وسياق الحديث (CHAT & FOLLOW_UP):
   - افهم الأسئلة التتابعية المعتمدة على ما سبق في المحادثة مثل: "ليه؟"، "طب وضح أكتر"، "اديني مثال"، "كمل"، "كرر ده"، "شغلها".
   - تجاوب مع الدردشة العادية ("عامل إيه يا مساعد"، "يومك عامل إزاي") بروح إيجابية ومرحة.
3. استدعاء أدوات الأندرويد والتحكم الكامل في الجهاز بدقة:
   - send_message: لإرسال رسائل SMS أو واتساب (مثال: "ابعت رسالة لأحمد هتاخر"، "ابعت واتساب لماما"). ضع نص الرسالة في query واسم أو رقم المستلم في recipient، و app = "sms" أو "whatsapp".
   - local_music: عند طلب تشغيل أغنية من على الجهاز نفسه أو الموبايل (مثال: "شغل من على الجهاز أغنية...", "شغل تراك... من ملفات الموبايل"). ضع اسم الأغنية في query.
   - search_music: عند طلب تشغيل أغنية من Spotify أو ساوندكلاود.
   - music_control: للتحكم (toggle, next, previous).
   - device_control: للتحكم في الهاتف (الكشاف/الفلاش torch_on/torch_off، تعلية/توطية الصوت volume_up/volume_down/mute، فتح الواي فاي wifi، البلوتوث bluetooth، نسبة البطارية battery، العودة للرئيسية home، التطبيقات الأخيرة recents، قفل الشاشة lock). ضع اسم الإجراء في query.
   - open_app: لفتح أي تطبيق على الموبايل (واتساب، يوتيوب، كاميرا، حاسبة، فيسبوك، تليجرام، استوديو/معرض، تيك توك، إعدادات، وغيرها). ضع اسم التطبيق في query.
   - screen_action: لتنفيذ إجراء واحد فقط على الشاشة الحالية بعد قراءة النص الظاهر المرفق بالسياق. ضع في query واحدة من الصيغ التالية حرفياً: tap|اسم الزر أو العنصر، type|النص المراد كتابته، scroll_down، scroll_up، back، home، recents، notifications. لا تدّعِ رؤية نص غير موجود في سياق الشاشة، ولا تستخدم هذا الإجراء لإرسال أو حذف أو شراء أو مشاركة شيء دون تأكيد المستخدم أولاً.
   - phone_call: للاتصال برقم أو شخص (مثال: "اتصل بأحمد"، "اتصل على 010..."). ضع الرقم أو الاسم في recipient.
   - weather: عند السؤال عن حالة الجو أو الطقس.
   - alarm: لضبط منبه مع تحديد الساعة والدقيقة (0-23 و 0-59).
   - timer: لضبط مؤقت بالثواني.
   - task_add / task_list / task_clear: لإدارة مهام وتذكيرات اليوم.
   - maps: للبحث عن أماكن قريبة.
   - web_search: فقط عندما يطلب المستخدم صراحة البحث في الويب.
   - clarify: فقط عندما يكون الطلب غامضاً جداً.

تنسيق الإخراج الإلزامي:
أرجع كائن JSON صالح فقط دون كتل Markdown ودون أي نص خارجي:
{
  "action": "question" | "chat" | "screen_action" | "send_message" | "local_music" | "device_control" | "open_app" | "phone_call" | "search_music" | "music_control" | "weather" | "alarm" | "timer" | "task_add" | "task_list" | "task_clear" | "maps" | "web_search" | "clarify",
  "reply": "الرد الكامل والمفيد والممتع الموجه للمستخدم بالعربية المصرية",
  "query": "كلمات البحث أو نص الرسالة أو الإجراء المطلوب",
  "recipient": "اسم أو رقم المستلم إن وجد",
  "app": "spotify" | "sms" | "whatsapp",
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
        preferredModel: String = DEFAULT_MODEL,
        screenContext: String = "",
        recentHistory: List<ChatMessage> = emptyList()
    ): Result<GeminiDecision> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("مفتاح Gemini API غير مدخل"))
        }

        // محاولة تشغيل النموذج المفضل، مع السقوط التلقائي السريع للنماذج الأخرى لتفادي التعليق
        // لا نبدّل النموذج المختار بصمت؛ المستخدم هو من يقرر تغيير النموذج.
        val modelsToTry = listOf(preferredModel.trim().removePrefix("models/")).filter { it.isNotBlank() }

        var lastError: Throwable? = null
        for (model in modelsToTry) {
            val attempt = callGeminiApi(model, trimmedKey, userQuery.trim(), screenContext, recentHistory)
            if (attempt.isSuccess) {
                return@withContext attempt
            }
            lastError = attempt.exceptionOrNull()
            // إذا كان الخطأ مفتاح غير صالح، لا داعي لتجربة باقي النماذج
            if (lastError?.message?.contains("غير صالح") == true) {
                break
            }
        }

        Result.failure(lastError ?: Exception("تعذر استلام رد من أي نموذج"))
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
            val endpoint = geminiGenerateContentUrl(model)
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000  // 8 ثواني كحد أقصى للاتصال
                readTimeout = 14000     // 14 ثانية كحد أقصى للقراءة لمنع التعليق
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
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
                promptBuilder.append("\n\n[نص الواجهة الحالية المستخرج من خدمة إمكانية الوصول: ${screenContext.take(3000)}]")
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
                        code == 429 -> "تم تجاوز حصة استخدام النموذج $model. انتظر قليلًا أو اختر نموذجًا آخر من الإعدادات."
                        code == 404 -> "نموذج $model غير متاح لهذا المفتاح."
                        code == 503 -> "خوادم $model مشغولة حالياً."
                        else -> "خطأ ($code) من نموذج $model"
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

            val firstCandidate = candidates.getJSONObject(0)
            val finishReason = firstCandidate.optString("finishReason", "")
            if (finishReason == "SAFETY") {
                return Result.success(
                    GeminiDecision(
                        action = "chat",
                        actionType = AssistantRouter.parseActionType("chat"),
                        reply = "عذراً يا باشا، مقدرش أجاوب على الجزء ده التزاماً بمعايير الأمان."
                    )
                )
            }

            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val rawText = if (parts != null && parts.length() > 0) {
                parts.getJSONObject(0).optString("text", "")
            } else ""

            if (rawText.isBlank()) {
                return Result.failure(Exception("وصل رد فارغ من النموذج"))
            }

            val cleanJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = try {
                JSONObject(cleanJson)
            } catch (_: Exception) {
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
                label = json.optString("label", ""),
                recipient = json.optString("recipient", "")
            )
            return Result.success(decision)

        } catch (e: Exception) {
            val friendlyMsg = when {
                e is java.net.SocketTimeoutException -> "انتهت مهلة الاتصال بالنموذج ($model)"
                e is java.net.UnknownHostException -> "لا يوجد اتصال بالإنترنت للوصول لـ Gemini"
                else -> e.localizedMessage ?: "حدث خطأ غير متوقع"
            }
            return Result.failure(Exception(friendlyMsg))
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun testConnection(apiKey: String, model: String = DEFAULT_MODEL): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("المفتاح فارغ"))
        }
        var conn: HttpURLConnection? = null
        try {
            val endpoint = geminiGenerateContentUrl(model)
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 7000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", trimmedKey)
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
                return@withContext Result.success("متصل بنجاح مع $model")
            }
            val errStream = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val msg = try {
                val errObj = JSONObject(errStream).optJSONObject("error")
                val message = errObj?.optString("message") ?: ""
                when {
                    code == 400 && message.contains("API key not valid", ignoreCase = true) -> "المفتاح غير صالح أو مكتوب خطأ"
                    code == 404 -> "النموذج $model غير متاح لهذا المفتاح، جرب اختيار نموذج آخر"
                    code == 429 -> "تجاوزت حد الاستخدام المجاني، انتظر دقيقة وجرب تاني"
                    else -> "كود الخطأ: $code"
                }
            } catch (_: Exception) { "كود الخطأ: $code" }
            return@withContext Result.failure(Exception(msg))
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("تعذر الوصول: ${e.localizedMessage ?: "تأكد من الإنترنت"}"))
        } finally {
            conn?.disconnect()
        }
    }
}
