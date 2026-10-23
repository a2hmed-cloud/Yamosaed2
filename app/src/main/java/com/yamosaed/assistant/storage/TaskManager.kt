package com.yamosaed.assistant.storage

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.yamosaed.assistant.TaskReminderReceiver
import com.yamosaed.assistant.model.TaskItem
import org.json.JSONArray
import org.json.JSONObject

class TaskManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("yamosaed_tasks", Context.MODE_PRIVATE)

    fun getTasks(): List<TaskItem> {
        val json = prefs.getString("task_list", "[]") ?: "[]"
        val list = mutableListOf<TaskItem>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TaskItem(
                        id = obj.getString("id"),
                        text = obj.getString("text"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        reminderTimeMillis = if (obj.has("reminderTime") && !obj.isNull("reminderTime")) obj.getLong("reminderTime") else null,
                        isCompleted = obj.optBoolean("isCompleted", false)
                    )
                )
            }
        } catch (_: Exception) { }
        return list.sortedBy { it.isCompleted }
    }

    fun addTask(text: String, reminderTimeMillis: Long? = null): TaskItem {
        val task = TaskItem(
            text = text.trim(),
            reminderTimeMillis = reminderTimeMillis
        )
        val current = getTasks().toMutableList()
        current.add(0, task)
        saveTasks(current)

        if (reminderTimeMillis != null && reminderTimeMillis > System.currentTimeMillis()) {
            scheduleReminder(task)
        }
        return task
    }

    fun toggleTask(id: String): Boolean {
        val current = getTasks().map {
            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
        }
        saveTasks(current)
        return true
    }

    fun deleteTask(id: String): Boolean {
        val current = getTasks().filter { it.id != id }
        saveTasks(current)
        cancelReminder(id)
        return true
    }

    fun clearTasks() {
        val tasks = getTasks()
        tasks.forEach { cancelReminder(it.id) }
        saveTasks(emptyList())
    }

    private fun saveTasks(tasks: List<TaskItem>) {
        val array = JSONArray()
        for (task in tasks) {
            val obj = JSONObject().apply {
                put("id", task.id)
                put("text", task.text)
                put("createdAt", task.createdAt)
                if (task.reminderTimeMillis != null) put("reminderTime", task.reminderTimeMillis)
                put("isCompleted", task.isCompleted)
            }
            array.put(obj)
        }
        prefs.edit().putString("task_list", array.toString()).apply()
    }

    fun scheduleReminder(task: TaskItem): Boolean {
        val time = task.reminderTimeMillis ?: return false
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) {
            return false
        }
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskReminderReceiver.EXTRA_TASK_TEXT, task.text)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pendingIntent)
        return true
    }

    fun cancelReminder(taskId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
