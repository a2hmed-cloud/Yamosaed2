package com.yamosaed.assistant.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Validated, app-owned representation of an assistant action plan.
 * Model output is untrusted: this parser only accepts registered tools/actions
 * and never grants permissions or executes actions itself.
 */
enum class ActionRisk { LOW, REVIEW, SENSITIVE }

data class PlannedAction(
    val tool: String,
    val action: String,
    val arguments: Map<String, String>,
    val requiresConfirmation: Boolean,
    val risk: ActionRisk
)

data class ActionPlan(
    val intent: String,
    val goal: String,
    val steps: List<PlannedAction>,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null
)

sealed class PlanParseResult {
    data class Valid(val plan: ActionPlan) : PlanParseResult()
    data class Invalid(val reason: String) : PlanParseResult()
}

/** Allow-list and risk policy live in the app, never in model-generated JSON. */
object ActionPlanParser {
    private data class Rule(val risk: ActionRisk, val alwaysConfirm: Boolean = false)

    private val rules: Map<Pair<String, String>, Rule> = mapOf(
        ("ContactsTool" to "search") to Rule(ActionRisk.LOW),
        ("PhoneTool" to "open_dialer") to Rule(ActionRisk.REVIEW, true),
        ("MessagingTool" to "compose") to Rule(ActionRisk.SENSITIVE, true),
        ("AppLaunchTool" to "open") to Rule(ActionRisk.LOW),
        ("ScreenInteractionTool" to "inspect") to Rule(ActionRisk.LOW),
        ("ScreenInteractionTool" to "click") to Rule(ActionRisk.REVIEW, true),
        ("NotificationTool" to "notify") to Rule(ActionRisk.LOW),
        ("AlarmTimerTool" to "create") to Rule(ActionRisk.REVIEW, true),
        ("TasksCalendarTool" to "create_task") to Rule(ActionRisk.REVIEW, true),
        ("MediaTool" to "search") to Rule(ActionRisk.LOW),
        ("AutomationTool" to "save") to Rule(ActionRisk.REVIEW, true),
        ("SettingsTool" to "open") to Rule(ActionRisk.LOW),
        ("WebSearchTool" to "search") to Rule(ActionRisk.LOW)
    )

    fun parse(json: String): PlanParseResult = try {
        val root = JSONObject(json)
        val intent = root.optString("intent").trim()
        val goal = root.optString("goal").trim()
        val clarification = root.optBoolean("needsClarification", false)
        val question = root.optString("clarificationQuestion").trim().ifBlank { null }
        if (intent.isBlank()) return PlanParseResult.Invalid("حقل intent مطلوب")
        if (goal.isBlank() && !clarification) return PlanParseResult.Invalid("حقل goal مطلوب")
        if (clarification) {
            if (question.isNullOrBlank()) return PlanParseResult.Invalid("سؤال التوضيح مطلوب")
            return PlanParseResult.Valid(ActionPlan(intent, goal, emptyList(), true, question))
        }

        val rawSteps = root.optJSONArray("steps")
            ?: return PlanParseResult.Invalid("قائمة steps مطلوبة")
        if (rawSteps.length() == 0) return PlanParseResult.Invalid("الخطة لا تحتوي على خطوات")
        if (rawSteps.length() > MAX_STEPS) return PlanParseResult.Invalid("عدد الخطوات أكبر من الحد المسموح")

        val steps = mutableListOf<PlannedAction>()
        for (i in 0 until rawSteps.length()) {
            val step = rawSteps.optJSONObject(i)
                ?: return PlanParseResult.Invalid("الخطوة رقم ${i + 1} ليست كائنًا صالحًا")
            val tool = step.optString("tool").trim()
            val action = step.optString("action").trim()
            val rule = rules[tool to action]
                ?: return PlanParseResult.Invalid("الأداة أو الإجراء غير مسموح: $tool.$action")
            val argsObject = step.optJSONObject("arguments") ?: JSONObject()
            val args = linkedMapOf<String, String>()
            val keys = argsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = argsObject.opt(key)
                if (key.isBlank() || value !is String) {
                    return PlanParseResult.Invalid("مدخلات الخطوة يجب أن تكون نصوصًا")
                }
                if (value.length > MAX_ARGUMENT_LENGTH) {
                    return PlanParseResult.Invalid("أحد المدخلات أطول من الحد المسموح")
                }
                args[key] = value
            }
            val modelRequestedConfirmation = step.optBoolean("requiresConfirmation", false)
            steps += PlannedAction(
                tool = tool,
                action = action,
                arguments = args,
                // Model can request more confirmation, but cannot bypass app policy.
                requiresConfirmation = rule.alwaysConfirm || modelRequestedConfirmation,
                risk = rule.risk
            )
        }
        PlanParseResult.Valid(ActionPlan(intent, goal, steps))
    } catch (_: Exception) {
        PlanParseResult.Invalid("تعذر قراءة خطة الأوامر بصيغة JSON صحيحة")
    }

    private const val MAX_STEPS = 12
    private const val MAX_ARGUMENT_LENGTH = 1000
}

/** Basic, offline intent fallback for a small set of safe, explicit commands. */
object LocalIntentPlanner {
    fun plan(text: String): ActionPlan? {
        val query = text.trim()
        if (query.isBlank()) return null
        val normalized = Arabic.norm(query).lowercase()
        return when {
            normalized.startsWith("افتح ") || normalized.startsWith("open ") -> {
                val app = query.substringAfter(' ', "").trim()
                if (app.isBlank()) ActionPlan("clarify_app", query, emptyList(), true, "تحب أفتح أنهي تطبيق؟")
                else ActionPlan("open_app", "فتح التطبيق: $app", listOf(
                    PlannedAction("AppLaunchTool", "open", mapOf("appName" to app), false, ActionRisk.LOW)
                ))
            }
            normalized.contains("ابعت") || normalized.contains("ارسل") ||
                normalized.contains("send a message") -> ActionPlan(
                    "compose_message", "تجهيز رسالة للمراجعة", listOf(
                        PlannedAction("MessagingTool", "compose", mapOf("rawRequest" to query), true, ActionRisk.SENSITIVE)
                    )
                )
            normalized.startsWith("اتصل") || normalized.startsWith("كلم ") ||
                normalized.startsWith("call ") -> ActionPlan(
                    "phone_call", "تحديد جهة الاتصال قبل الاتصال", listOf(
                        PlannedAction("PhoneTool", "open_dialer", mapOf("rawRequest" to query), true, ActionRisk.REVIEW)
                    )
                )
            else -> null
        }
    }
}
