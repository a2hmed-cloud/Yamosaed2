package com.yamosaed.assistant.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechManager(
    private val context: Context,
    private val onReady: () -> Unit,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    var isListening: Boolean = false
        private set

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        if (!isAvailable()) {
            onError("التعرف على الصوت غير مدعوم على جهازك حاليًا.")
            return
        }

        stopListening()

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onReady()
                }

                override fun onBeginningOfSpeech() {
                    onSpeechStart()
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    onSpeechEnd()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "خطأ في تسجيل الصوت"
                        SpeechRecognizer.ERROR_CLIENT -> "خطأ في تطبيق التعرف الصوتي"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "صلاحية الميكروفون مطلوبة"
                        SpeechRecognizer.ERROR_NETWORK -> "مشكلة في الاتصال بالإنترنت للتعرف على الصوت"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "انتهت مهلة اتصال التعرف الصوتي"
                        SpeechRecognizer.ERROR_NO_MATCH -> "ما سمعتش كلام واضح، جرّب تاني"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "خدمة التعرف الصوتي مشغولة"
                        SpeechRecognizer.ERROR_SERVER -> "خطأ من خادم التعرف الصوتي"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ما سمعتش صوت، اتكلم بعد ما تضغط"
                        else -> "ما قدرتش أسمعك كويس، جرّب تاني"
                    }
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestText = matches?.firstOrNull()?.trim().orEmpty()
                    if (bestText.isNotBlank()) {
                        onResult(bestText)
                    } else {
                        onError("ما وصلنيش كلام واضح، جرّب تاني.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-EG")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            onError("تعذر بدء الميكروفون: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        isListening = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun destroy() {
        stopListening()
    }
}
