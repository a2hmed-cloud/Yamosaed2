package com.yamosaed.assistant.agent.action

import android.accessibilityservice.AccessibilityService
import com.yamosaed.assistant.agent.discovery.AppDiscoveryService
import com.yamosaed.assistant.agent.vision.AccessibilityInteractionService
import com.yamosaed.assistant.agent.vision.ScreenObservationService
import kotlinx.coroutines.delay

class ActionExecutor(
    private val appDiscoveryService: AppDiscoveryService,
    private val accessibilityService: AccessibilityInteractionService,
    private val observationService: ScreenObservationService
) {

    /**
     * تنفيذ الفعل والتحقق من حالة الشاشة قبل وبعد التنفيذ
     */
    suspend fun execute(action: AgentAction): ActionResult {
        val beforeSnapshot = observationService.observeCurrentScreen()
        val initialHash = beforeSnapshot.screenHash

        return when (action) {
            is AgentAction.LaunchApp -> {
                val launched = appDiscoveryService.launchApp(action.packageName)
                if (!launched) {
                    ActionResult(
                        action = action,
                        isAccepted = false,
                        isSuccess = false,
                        screenBefore = beforeSnapshot,
                        errorMessage = "تعذر تشغيل التطبيق ${action.packageName}"
                    )
                } else {
                    val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 2500L)
                    val changed = afterSnapshot.screenHash != initialHash
                    ActionResult(
                        action = action,
                        isAccepted = true,
                        isSuccess = true,
                        screenBefore = beforeSnapshot,
                        screenAfter = afterSnapshot,
                        screenChanged = changed,
                        details = "تم فتح تطبيق ${action.appLabel.ifBlank { action.packageName }}"
                    )
                }
            }

            is AgentAction.Click -> {
                if (!accessibilityService.isAvailable) {
                    return ActionResult(
                        action = action,
                        isAccepted = false,
                        isSuccess = false,
                        screenBefore = beforeSnapshot,
                        errorMessage = "خدمة إمكانية الوصول غير مفعلة للنقر على العناصر"
                    )
                }

                val clicked = accessibilityService.clickElement(
                    text = action.text,
                    viewId = action.viewId,
                    bounds = action.bounds
                )

                if (!clicked) {
                    ActionResult(
                        action = action,
                        isAccepted = true,
                        isSuccess = false,
                        screenBefore = beforeSnapshot,
                        errorMessage = "لم يتم العثور على العنصر المطلوب للنقر: ${action.text ?: action.viewId ?: action.description}"
                    )
                } else {
                    val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 1800L)
                    val changed = afterSnapshot.screenHash != initialHash
                    ActionResult(
                        action = action,
                        isAccepted = true,
                        isSuccess = true,
                        screenBefore = beforeSnapshot,
                        screenAfter = afterSnapshot,
                        screenChanged = changed,
                        details = "تم النقر على ${action.text ?: action.description}"
                    )
                }
            }

            is AgentAction.TypeText -> {
                if (!accessibilityService.isAvailable) {
                    return ActionResult(
                        action = action,
                        isAccepted = false,
                        isSuccess = false,
                        screenBefore = beforeSnapshot,
                        errorMessage = "خدمة إمكانية الوصول غير مفعلة لكتابة النص"
                    )
                }

                val typed = accessibilityService.typeText(
                    targetQuery = action.targetQuery,
                    textToType = action.textToType,
                    clearFirst = action.clearFirst
                )

                val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 1200L)
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = typed,
                    screenBefore = beforeSnapshot,
                    screenAfter = afterSnapshot,
                    screenChanged = afterSnapshot.screenHash != initialHash,
                    errorMessage = if (!typed) "لم يتم العثور على حقل إدخال نشط لكتابة النص" else null,
                    details = "تمت كتابة: \"${action.textToType}\""
                )
            }

            is AgentAction.Scroll -> {
                val scrolled = accessibilityService.scroll(action.forward)
                val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 1200L)
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = scrolled,
                    screenBefore = beforeSnapshot,
                    screenAfter = afterSnapshot,
                    screenChanged = afterSnapshot.screenHash != initialHash,
                    details = if (action.forward) "تم التمرير للأسفل" else "تم التمرير للأعلى"
                )
            }

            is AgentAction.NavigateBack -> {
                val ok = accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 1500L)
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = ok,
                    screenBefore = beforeSnapshot,
                    screenAfter = afterSnapshot,
                    screenChanged = afterSnapshot.screenHash != initialHash,
                    details = "تم الرجوع للخلف"
                )
            }

            is AgentAction.NavigateHome -> {
                val ok = accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                val afterSnapshot = observationService.waitForScreenChange(initialHash, timeoutMs = 1500L)
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = ok,
                    screenBefore = beforeSnapshot,
                    screenAfter = afterSnapshot,
                    screenChanged = afterSnapshot.screenHash != initialHash,
                    details = "تم الرجوع للشاشة الرئيسية"
                )
            }

            is AgentAction.Wait -> {
                delay(action.durationMs)
                val afterSnapshot = observationService.observeCurrentScreen()
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = true,
                    screenBefore = beforeSnapshot,
                    screenAfter = afterSnapshot,
                    details = "تم الانتظار لمدة ${action.durationMs} مللي ثانية"
                )
            }

            is AgentAction.RequestConfirmation -> {
                ActionResult(
                    action = action,
                    isAccepted = true,
                    isSuccess = false,
                    screenBefore = beforeSnapshot,
                    details = "بانتظار تأكيد المستخدم: ${action.message}"
                )
            }
        }
    }
}
