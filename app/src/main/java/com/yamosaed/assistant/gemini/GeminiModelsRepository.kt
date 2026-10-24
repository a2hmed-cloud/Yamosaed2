package com.yamosaed.assistant.gemini

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GeminiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val supportsGenerateContent: Boolean
)

class GeminiModelsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("gemini_models_cache", Context.MODE_PRIVATE)

    fun cachedModels(): List<GeminiModel> = runCatching {
        val saved = prefs.getString(CACHE_KEY, null) ?: return emptyList()
        parseModels(saved)
    }.getOrDefault(emptyList())

    suspend fun fetchModels(apiKey: String): List<GeminiModel> = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "اكتب مفتاح Gemini API أولاً." }
        val result = mutableListOf<GeminiModel>()
        var pageToken: String? = null
        do {
            var connection: HttpURLConnection? = null
            try {
                val query = pageToken?.let { "?pageToken=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
                connection = (URL("https://generativelanguage.googleapis.com/v1beta/models$query")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("x-goog-api-key", apiKey.trim())
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException(when {
                        code == 400 || code == 401 || code == 403 -> "مفتاح API غير صالح أو ليس لديه صلاحية (HTTP $code)."
                        code == 429 -> "تم تجاوز حصة Gemini API. انتظر قليلًا ثم أعد المحاولة."
                        else -> "تعذر جلب النماذج (HTTP $code)."
                    })
                }
                val root = JSONObject(body)
                result += parseModels(root.optJSONArray("models") ?: JSONArray())
                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() && it != "null" }
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                throw IllegalStateException(
                    if (e is java.net.UnknownHostException) "لا يوجد اتصال بالإنترنت."
                    else if (e is java.net.SocketTimeoutException) "انتهت مهلة الاتصال. حاول مرة أخرى."
                    else "تعذر جلب النماذج. تحقق من الاتصال ثم أعد المحاولة."
                )
            } finally {
                connection?.disconnect()
            }
        } while (pageToken != null && result.size < 1000)

        if (result.isEmpty()) throw IllegalStateException("لم يُرجع API أي نموذج يدعم توليد المحتوى.")
        val unique = result.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
        prefs.edit().putString(CACHE_KEY, modelsToJson(unique).toString())
            .putLong(UPDATED_KEY, System.currentTimeMillis()).apply()
        unique
    }

    fun cacheTimestamp(): Long = prefs.getLong(UPDATED_KEY, 0L)

    companion object {
        private const val CACHE_KEY = "cached_models_json"
        private const val UPDATED_KEY = "cached_models_updated"

        fun parseModels(json: String): List<GeminiModel> =
            parseModels(JSONObject(json).optJSONArray("models") ?: JSONArray())

        fun parseModels(array: JSONArray): List<GeminiModel> {
            val parsed = mutableListOf<GeminiModel>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val rawName = item.optString("name").trim()
                if (rawName.isBlank() || rawName == "null") continue
                val methods = item.optJSONArray("supportedGenerationMethods")
                val supports = methods != null && (0 until methods.length()).any {
                    methods.optString(it) == "generateContent"
                }
                if (!supports) continue
                parsed += GeminiModel(
                    id = rawName.removePrefix("models/"),
                    displayName = item.optString("displayName")
                        .takeIf { it.isNotBlank() && it != "null" } ?: rawName.removePrefix("models/"),
                    description = item.optString("description")
                        .takeIf { it.isNotBlank() && it != "null" } ?: "لا يوجد وصف من Google.",
                    supportsGenerateContent = true
                )
            }
            return parsed.distinctBy { it.id }
        }

        private fun modelsToJson(models: List<GeminiModel>) = JSONObject().put("models", JSONArray().apply {
            models.forEach { model ->
                put(JSONObject().put("name", "models/${model.id}")
                    .put("displayName", model.displayName)
                    .put("description", model.description)
                    .put("supportedGenerationMethods", JSONArray().put("generateContent")))
            }
        })
    }
}
