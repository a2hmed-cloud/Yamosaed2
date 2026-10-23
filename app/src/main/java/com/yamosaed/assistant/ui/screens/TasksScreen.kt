package com.yamosaed.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamosaed.assistant.model.TaskItem
import com.yamosaed.assistant.storage.TaskManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TasksScreen(
    taskManager: TaskManager,
    modifier: Modifier = Modifier
) {
    var tasks by remember { mutableStateOf(taskManager.getTasks()) }
    var newTaskText by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    fun refresh() {
        tasks = taskManager.getTasks()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("مسح كل المهام") },
            text = { Text("هل أنت متأكد من مسح جميع المهام المسجلة في يومك؟") },
            confirmButton = {
                TextButton(onClick = {
                    taskManager.clearTasks()
                    refresh()
                    showClearDialog = false
                }) {
                    Text("مسح الكل", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080D18))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "مهامي ويومي",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${tasks.count { !it.isCompleted }} مهام متبقية",
                    fontSize = 13.sp,
                    color = Color(0xFF8FA4CE)
                )
            }
            if (tasks.isNotEmpty()) {
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "مسح الكل",
                        tint = Color(0xFFEF9A9A)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // New Task Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            OutlinedTextField(
                value = newTaskText,
                onValueChange = { newTaskText = it },
                placeholder = { Text("أضف مهمة جديدة...") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        if (newTaskText.isNotBlank()) {
                            focusManager.clearFocus()
                            taskManager.addTask(newTaskText)
                            newTaskText = ""
                            refresh()
                        } else {
                            focusManager.clearFocus()
                        }
                    }
                ),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4F8CFF),
                    unfocusedBorderColor = Color(0xFF22355F)
                ),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (newTaskText.isNotBlank()) {
                        focusManager.clearFocus()
                        taskManager.addTask(newTaskText)
                        newTaskText = ""
                        refresh()
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF3D74EB), RoundedCornerShape(14.dp))
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة", tint = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📅 يومك فاضي ورايق!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "تقدر تقول للمساعد بصوتك:\n\"سجل في يومي اشتري طلبات البيت\"",
                        fontSize = 13.sp,
                        color = Color(0xFF8FA4CE),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = {
                            taskManager.toggleTask(task.id)
                            refresh()
                        },
                        onDelete = {
                            taskManager.deleteTask(task.id)
                            refresh()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: TaskItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) Color(0xFF0F1728) else Color(0xFF14203D)
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = Color(0xFF8FA4CE)
                )
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.text,
                    color = if (task.isCompleted) Color(0xFF7E8EA8) else Color.White,
                    fontSize = 15.sp,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (task.reminderTimeMillis != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Alarm,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        val dateFormatted = SimpleDateFormat("HH:mm - dd/MM", Locale("ar", "EG"))
                            .format(Date(task.reminderTimeMillis))
                        Text(
                            text = dateFormatted,
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "حذف المهمة",
                    tint = Color(0xFF90A4AE),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
