package com.yamosaed.assistant.audio

import android.content.Context
import android.speech.SpeechRecognizer
import com.yamosaed.assistant.gemini.GeminiClient
import com.yamosaed.assistant.storage.PreferencesManager
import kotlinx.coroutines.*
import java.io.File

sealed class VoiceInputState {
    object Idle : VoiceInputState()
    data class Listening(val mode: String) : VoiceInputState() // "ON_DEVICE" or "CLOUD_RECORDING"
    object Processing : VoiceInputState()
    data class Success(val text: String) : VoiceInputState()
    data class Error(val message: String, val suggestTextInput: Boolean = false) : VoiceInputState()
}

class VoiceInputManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val geminiClient: GeminiClient,
    private val onStateChange: (VoiceInputState) -> Unit,
    private val onResult: (String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val recorderHelper = AudioRecorderHelper(context)
    private var isUsingCloudRecorder = false

    private val speechManager = SpeechManager(
        context = context,
        onReady = {
            if (!isUsingCloudRecorder) {
                onStateChange(VoiceInputState.Listening("ON_DEVICE"))
            }
        },
        onSpeechStart = {},
        onSpeechEnd = {
            if (!isUsingCloudRecorder) {
                onStateChange(VoiceInputState.Processing)
            }
        },
        onResult = { recognizedText ->
            onStateChange(VoiceInputState.Success(recognizedText))
            onResult(recognizedText)
        },
        onError = { errorMsg ->
            // إذا فشل التعرف المحلي، نحاول السقوط إلى المسجل الصوتي السحابي
            if (!isUsingCloudRecorder && preferencesManager.geminiApiKey.isNotBlank()) {
                fallbackToCloudRecording()
            } else {
                onStateChange(VoiceInputState.Error(errorMsg, suggestTextInput = true))
            }
        }
    )

    fun isLocalSpeechRecognitionAvailable(): Boolean {
        return speechManager.isAvailable()
    }

    /**
     * بدء الاستماع الذكي: يختار التعرف على الصوت المحلي إن كان مدعوماً، أو التسجيل الصوتي المباشر والتحويل الذكي
     */
    fun startListening() {
        if (speechManager.isAvailable()) {
            isUsingCloudRecorder = false
            speechManager.isContinuousWakeMode = false
            speechManager.startListening()
        } else {
            // الجهاز لا يدعم SpeechRecognizer: استخدام حل تحويل الصوت لكلام عبر التسجيل والذكاء الاصطناعي
            startCloudVoiceRecording()
        }
    }

    fun stopListening() {
        if (isUsingCloudRecorder) {
            stopCloudVoiceRecording()
        } else {
            speechManager.stopListening()
        }
    }

    private fun startCloudVoiceRecording() {
        isUsingCloudRecorder = true
        val apiKey = preferencesManager.geminiApiKey

        if (apiKey.isBlank()) {
            onStateChange(
                VoiceInputState.Error(
                    message = "التعرف الصوتي غير مدعوم على جهازك. يمكنك استخدام الكتابة النصية، أو إدخال مفتاح Gemini في الإعدادات لتفعيل التحويل الصوتي السحابي الذكي.",
                    suggestTextInput = true
                )
            )
            return
        }

        val file = recorderHelper.startRecording()
        if (file == null) {
            onStateChange(VoiceInputState.Error("تعذر تشغيل ميكروفون الجهاز للتسجيل.", suggestTextInput = true))
        } else {
            onStateChange(VoiceInputState.Listening("CLOUD_RECORDING"))
        }
    }

    private fun stopCloudVoiceRecording() {
        scope.launch {
            onStateChange(VoiceInputState.Processing)
            val audioFile: File? = recorderHelper.stopRecording()
            isUsingCloudRecorder = false

            if (audioFile == null || !audioFile.exists() || audioFile.length() < 1000) {
                onStateChange(VoiceInputState.Error("لم يتم التقاط صوت واضح، حاول مرة أخرى."))
                return@launch
            }

            val apiKey = preferencesManager.geminiApiKey
            val transcriptionResult = geminiClient.transcribeAudio(audioFile, apiKey)

            if (transcriptionResult.isSuccess) {
                val text = transcriptionResult.getOrThrow()
                onStateChange(VoiceInputState.Success(text))
                onResult(text)
            } else {
                val error = transcriptionResult.exceptionOrNull()?.localizedMessage ?: "فشل تحويل الصوت إلى كلام"
                onStateChange(VoiceInputState.Error(error, suggestTextInput = true))
            }
        }
    }

    private fun fallbackToCloudRecording() {
        speechManager.stopListening()
        startCloudVoiceRecording()
    }

    fun destroy() {
        speechManager.destroy()
        scope.cancel()
    }
}
