package com.yamosaed.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class TaskReminderReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TEXT = "extra_task_text"
        const val CHANNEL_ID = "yamosaed_reminders"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val taskText = intent?.getStringExtra(EXTRA_TASK_TEXT) ?: "لديك مهمة مسجلة في يا مساعد"
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: System.currentTimeMillis().toString()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تذكيرات المهام - يا مساعد",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات للمهام والتذكيرات المجدولة"
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("يا مساعد — تذكير بمهمتك")
            .setContentText(taskText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(taskId.hashCode(), notification)
    }
}
