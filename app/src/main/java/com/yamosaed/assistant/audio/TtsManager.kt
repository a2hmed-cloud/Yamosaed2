package com.yamosaed.assistant.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TtsManager(
    context: Context,
    private val onReadyStateChanged: (Boolean) -> Unit = {}
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    var isReady: Boolean = false
        private set
    var isSpeaking: Boolean = false
        private set

    private var onSpeechDoneCallback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val arLocale = Locale("ar", "EG")
            val langResult = tts?.setLanguage(arLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try generic Arabic
                tts?.setLanguage(Locale("ar"))
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }
                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    onSpeechDoneCallback?.invoke()
                    onSpeechDoneCallback = null
                }
                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    onSpeechDoneCallback?.invoke()
                    onSpeechDoneCallback = null
                }
            })
            isReady = true
            onReadyStateChanged(true)
        } else {
            isReady = false
            onReadyStateChanged(false)
        }
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) return
        stop()
        if (isReady && tts != null) {
            isSpeaking = true
            onSpeechDoneCallback = onDone
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "yamosaed_utterance_${System.currentTimeMillis()}")
        } else {
            onDone?.invoke()
        }
    }

    fun stop() {
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            onSpeechDoneCallback = null
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
