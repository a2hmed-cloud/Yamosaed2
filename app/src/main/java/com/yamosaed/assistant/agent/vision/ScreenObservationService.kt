package com.yamosaed.assistant.agent.vision

import kotlinx.coroutines.delay

class ScreenObservationService(
    private val accessibilityService: AccessibilityInteractionService
) {

    /**
     * فحص الشاشة الحالية مع معلومات موحدة
     */
    fun observeCurrentScreen(): ScreenSnapshot {
        return accessibilityService.captureCurrentScreen()
    }

    /**
     * انتظار حدوث تغيير في الشاشة بعد تنفيذ فعل (مع مهلة زمنية)
     */
    suspend fun waitForScreenChange(initialHash: Int, timeoutMs: Long = 2500L, pollIntervalMs: Long = 200L): ScreenSnapshot {
        val deadline = System.currentTimeMillis() + timeoutMs
        var latestSnapshot = observeCurrentScreen()

        while (System.currentTimeMillis() < deadline) {
            delay(pollIntervalMs)
            latestSnapshot = observeCurrentScreen()
            if (latestSnapshot.screenHash != initialHash) {
                return latestSnapshot
            }
        }
        return latestSnapshot
    }

    /**
     * التحقق مما إذا كان النص المطلوب قد ظهر فعلياً في الشاشة
     */
    fun verifyTextAppeared(text: String, ignoreCase: Boolean = true): Boolean {
        val snapshot = observeCurrentScreen()
        val q = text.trim()
        return snapshot.visibleTexts.any { it.contains(q, ignoreCase) } ||
                snapshot.elements.any { it.text.contains(q, ignoreCase) || it.contentDescription.contains(q, ignoreCase) }
    }
}
