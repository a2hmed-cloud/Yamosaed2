package com.yamosaed.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yamosaed.assistant.audio.SpeechManager
import java.util.Locale

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "yamosaed_wake_service"
        const val NOTIFICATION_ID = 4040
        const val ACTION_START = "com.yamosaed.assistant.action.START_WAKE"
        const val ACTION_STOP = "com.yamosaed.assistant.action.STOP_WAKE"
        const val ACTION_WAKE_DETECTED = "com.yamosaed.assistant.action.WAKE_DETECTED"
        const val EXTRA_WAKE_PHRASE = "com.yamosaed.assistant.extra.WAKE_PHRASE"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WakeWordService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechManager: SpeechManager? = null
    private var restartPending = false

    private val restartListener = Runnable {
        restartPending = false
        if (isRunning && speechManager != null && speechManager?.isListening != true) {
            speechManager?.startListening()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopListener()
            stopForeground(STOP_FOREGROUND_REMOVE)
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification("جاري تهيئة الاستماع...")
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            isRunning = true
        } catch (_: Exception) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("الميكروفون غير مسموح. افتح التطبيق وامنح صلاحية الميكروفون.")
            stopListener()
            return START_NOT_STICKY
        }

        startListenerIfAvailable()
        return START_STICKY
    }

    private fun startListenerIfAvailable() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("التعرف على الصوت غير متوفر على هذا الهاتف؛ الاستماع غير نشط.")
            stopListener()
            return
        }

        if (speechManager == null) {
            speechManager = SpeechManager(
                context = this,
                onReady = { updateNotification("جاهز لسماع نداء «يا مساعد».") },
                onSpeechStart = {},
                onSpeechEnd = {},
                onResult = { spokenText ->
                    if (containsWakePhrase(spokenText)) {
                        updateNotification("تم سماع نداء «يا مساعد». اضغط الإشعار لفتح التطبيق.")
                        sendBroadcast(
                            Intent(ACTION_WAKE_DETECTED)
                                .setPackage(packageName)
                                .putExtra(EXTRA_WAKE_PHRASE, spokenText)
                        )
                    }
                    scheduleRestart(if (containsWakePhrase(spokenText)) 700L else 250L)
                },
                onError = { message ->
                    if (message.contains("غير مدعوم") || message.contains("غير موجود")) {
                        updateNotification("التعرف على الصوت غير متاح؛ الاستماع غير نشط.")
                        stopListener()
                    } else {
                        updateNotification("تعذر الاستماع مؤقتًا: $message")
                        scheduleRestart(900L)
                    }
                }
            ).apply { isContinuousWakeMode = false }
        }
        scheduleRestart(100L)
    }

    private fun containsWakePhrase(text: String): Boolean {
        val normalized = text.lowercase(Locale("ar", "EG"))
            .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
            .replace("ـ", "")
            .replace(Regex("[\\p{Punct}\\s]+"), "")
        return normalized.contains("يامساعد") || normalized.contains("ياامساعد")
    }

    private fun scheduleRestart(delayMs: Long) {
        if (restartPending || !isRunning) return
        restartPending = true
        mainHandler.postDelayed(restartListener, delayMs)
    }

    private fun stopListener() {
        mainHandler.removeCallbacks(restartListener)
        restartPending = false
        speechManager?.destroy()
        speechManager = null
    }

    override fun onDestroy() {
        stopListener()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "خدمة الاستماع - يا مساعد", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار واضح عند تفعيل الاستماع لنداء يا مساعد"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = ACTION_WAKE_DETECTED
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPending = PendingIntent.getService(
            this, 1,
            Intent(this, WakeWordService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("يا مساعد")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف الاستماع", stopPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildForegroundNotification(status))
    }
}
