package com.yamosaed.assistant.agent.orchestrator

import com.yamosaed.assistant.agent.action.ActionExecutor
import com.yamosaed.assistant.agent.action.ActionResult
import com.yamosaed.assistant.agent.action.AgentAction
import com.yamosaed.assistant.agent.planner.AgentPlanner
import com.yamosaed.assistant.agent.planner.ExecutionPlan
import com.yamosaed.assistant.agent.safety.AgentSafetyPolicy
import com.yamosaed.assistant.agent.vision.ScreenObservationService
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

enum class OrchestratorState {
    IDLE,
    UNDERSTANDING,
    PLANNING,
    EXECUTING,
    OBSERVING,
    VERIFYING,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class OrchestratorFeedback(
    val state: OrchestratorState,
    val currentStepNumber: Int,
    val totalSteps: Int,
    val message: String,
    val confirmationRequired: Boolean = false,
    val confirmationPrompt: String? = null
)

class AgentOrchestrator(
    private val planner: AgentPlanner,
    private val actionExecutor: ActionExecutor,
    private val observationService: ScreenObservationService,
    private val safetyPolicy: AgentSafetyPolicy,
    private val onFeedback: (OrchestratorFeedback) -> Unit
) {

    private val isCancelled = AtomicBoolean(false)
    private var currentTaskJob: Job? = null
    private var pendingConfirmationAction: AgentAction? = null

    var currentState: OrchestratorState = OrchestratorState.IDLE
        private set

    /**
     * إلغاء المهمة الجارية فوراً
     */
    fun cancelCurrentTask() {
        isCancelled.set(true)
        currentTaskJob?.cancel()
        currentTaskJob = null
        pendingConfirmationAction = null
        currentState = OrchestratorState.CANCELLED
        onFeedback(
            OrchestratorFeedback(
                state = OrchestratorState.CANCELLED,
                currentStepNumber = 0,
                totalSteps = 0,
                message = "تم إلغاء المهمة بواسطة المستخدم."
            )
        )
    }

    /**
     * موافقة المستخدم على إجراء حساس معلق (مثل إرسال الرسالة)
     */
    suspend fun confirmPendingAction(): Boolean = withContext(Dispatchers.Main) {
        val action = pendingConfirmationAction ?: return@withContext false
        pendingConfirmationAction = null
        currentState = OrchestratorState.EXECUTING
        onFeedback(
            OrchestratorFeedback(
                state = OrchestratorState.EXECUTING,
                currentStepNumber = 1,
                totalSteps = 1,
                message = "جاري تنفيذ الإجراء بعد موافقتك..."
            )
        )
        val result = actionExecutor.execute(action)
        if (result.isSuccess) {
            currentState = OrchestratorState.COMPLETED
            onFeedback(
                OrchestratorFeedback(
                    state = OrchestratorState.COMPLETED,
                    currentStepNumber = 1,
                    totalSteps = 1,
                    message = "تم تنفيذ الإجراء بنجاح: ${result.details}"
                )
            )
            true
        } else {
            currentState = OrchestratorState.FAILED
            onFeedback(
                OrchestratorFeedback(
                    state = OrchestratorState.FAILED,
                    currentStepNumber = 1,
                    totalSteps = 1,
                    message = "تعذر إتمام الإجراء: ${result.errorMessage ?: "خطأ غير متوقع"}"
                )
            )
            false
        }
    }

    /**
     * تشغيل حلقة الوكيل المتكاملة: Observe → Plan → Act → Verify
     */
    suspend fun executeCompoundCommand(query: String): Boolean = withContext(Dispatchers.Main) {
        isCancelled.set(false)
        currentState = OrchestratorState.UNDERSTANDING
        onFeedback(
            OrchestratorFeedback(
                state = OrchestratorState.UNDERSTANDING,
                currentStepNumber = 0,
                totalSteps = 0,
                message = "جاري فهم أبعاد المهمة وتحليل الشاشة..."
            )
        )

        // 1. الملاحظة الأولية (Observe)
        val initialScreen = observationService.observeCurrentScreen()

        // 2. التخطيط (Plan)
        currentState = OrchestratorState.PLANNING
        val plan = planner.createPlan(query, initialScreen)

        if (plan == null || plan.steps.isEmpty()) {
            currentState = OrchestratorState.FAILED
            onFeedback(
                OrchestratorFeedback(
                    state = OrchestratorState.FAILED,
                    currentStepNumber = 0,
                    totalSteps = 0,
                    message = "لم أستطع تكوين خطة مناسبة لهذا الأمر. يمكنك توجيه أمر مباشر."
                )
            )
            return@withContext false
        }

        val totalSteps = plan.steps.size
        onFeedback(
            OrchestratorFeedback(
                state = OrchestratorState.PLANNING,
                currentStepNumber = 0,
                totalSteps = totalSteps,
                message = "الهدف: ${plan.goal} (${totalSteps} خطوات)"
            )
        )

        // 3. حلقة التنفيذ والمراقبة والتحقق (Act → Observe → Verify)
        var stepIndex = 0
        val maxStepsLimit = 12

        while (stepIndex < totalSteps && stepIndex < maxStepsLimit) {
            if (isCancelled.get()) {
                currentState = OrchestratorState.CANCELLED
                return@withContext false
            }

            val step = plan.steps[stepIndex]
            val stepNumber = stepIndex + 1

            // فحص الشاشة الحالية
            currentState = OrchestratorState.OBSERVING
            val currentScreen = observationService.observeCurrentScreen()

            // توليد الفعل المناسب للشاشة
            val action = step.actionBuilder(currentScreen)
            if (action == null) {
                currentState = OrchestratorState.FAILED
                onFeedback(
                    OrchestratorFeedback(
                        state = OrchestratorState.FAILED,
                        currentStepNumber = stepNumber,
                        totalSteps = totalSteps,
                        message = "تعذر تحديد الإجراء المناسب في الخطوة: ${step.description}"
                    )
                )
                return@withContext false
            }

            // الفحص الأمني (Safety Policy)
            val safetyResult = safetyPolicy.evaluateAction(action, query)
            if (safetyResult.requiresConfirmation) {
                currentState = OrchestratorState.WAITING_FOR_USER
                pendingConfirmationAction = when (action) {
                    is AgentAction.RequestConfirmation -> action.pendingAction
                    else -> action
                }
                onFeedback(
                    OrchestratorFeedback(
                        state = OrchestratorState.WAITING_FOR_USER,
                        currentStepNumber = stepNumber,
                        totalSteps = totalSteps,
                        message = "مطلوب تأكيدك للمتابعة",
                        confirmationRequired = true,
                        confirmationPrompt = safetyResult.confirmationPrompt ?: "هل تؤكد تنفيذ هذا الإجراء؟"
                    )
                )
                return@withContext true
            }

            // تنفيذ الفعل (Act)
            currentState = OrchestratorState.EXECUTING
            onFeedback(
                OrchestratorFeedback(
                    state = OrchestratorState.EXECUTING,
                    currentStepNumber = stepNumber,
                    totalSteps = totalSteps,
                    message = "الخطوة $stepNumber/$totalSteps: ${step.description}..."
                )
            )

            val result: ActionResult = actionExecutor.execute(action)

            // التحقق من النتيجة (Verify)
            currentState = OrchestratorState.VERIFYING
            if (!result.isSuccess) {
                // محاولة إعادة المحاولة لمرة واحدة مع انتظار
                delay(800L)
                val retryScreen = observationService.observeCurrentScreen()
                val retryAction = step.actionBuilder(retryScreen)
                val retryResult = if (retryAction != null) actionExecutor.execute(retryAction) else result

                if (!retryResult.isSuccess) {
                    currentState = OrchestratorState.FAILED
                    onFeedback(
                        OrchestratorFeedback(
                            state = OrchestratorState.FAILED,
                            currentStepNumber = stepNumber,
                            totalSteps = totalSteps,
                            message = "تعذر إكمال: ${step.description}. ${retryResult.errorMessage ?: ""}"
                        )
                    )
                    return@withContext false
                }
            }

            delay(400L)
            stepIndex++
        }

        currentState = OrchestratorState.COMPLETED
        onFeedback(
            OrchestratorFeedback(
                state = OrchestratorState.COMPLETED,
                currentStepNumber = totalSteps,
                totalSteps = totalSteps,
                message = "تمت المهمة بنجاح: ${plan.goal}"
            )
        )
        return@withContext true
    }
}
