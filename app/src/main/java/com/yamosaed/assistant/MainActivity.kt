package com.yamosaed.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yamosaed.assistant.agent.*
import com.yamosaed.assistant.audio.SpeechManager
import com.yamosaed.assistant.audio.TtsManager
import com.yamosaed.assistant.gemini.GeminiClient
import com.yamosaed.assistant.model.AssistantState
import com.yamosaed.assistant.model.ChatMessage
import com.yamosaed.assistant.storage.PreferencesManager
import com.yamosaed.assistant.storage.TaskManager
import com.yamosaed.assistant.ui.screens.AssistantScreen
import com.yamosaed.assistant.ui.screens.HistoryDialog
import com.yamosaed.assistant.ui.screens.SettingsScreen
import com.yamosaed.assistant.ui.screens.TasksScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var taskManager: TaskManager
    private lateinit var geminiClient: GeminiClient
    private lateinit var host: AndroidHost
    private lateinit var localAgent: Agent
    private lateinit var coordinator: AssistantCoordinator
    private lateinit var speechManager: SpeechManager
    private lateinit var ttsManager: TtsManager

    // UI state
    private var assistantState by mutableStateOf(AssistantState.IDLE)
    private var statusText by mutableStateOf("جاهز يا باشا! دوس على العين أو اطلب أمر صوتي.")
    private var recognizedText by mutableStateOf("")
    private var replyText by mutableStateOf("أهلاً بيك يا فندم! أنا يا مساعد، جاهز لأوامرك.")
    private var currentActionDetail by mutableStateOf<String?>(null)
    private val conversationHistory = mutableStateListOf<ChatMessage>()
    private var showHistoryDialog by mutableStateOf(false)
    private var showTasksDialog by mutableStateOf(false)
    private var showSettingsDialog by mutableStateOf(false)

    // Job tracking for safe cancellation and debouncing
    private var currentQueryJob: Job? = null
    private var lastQueryTimestamp = 0L

    // Permission launcher
    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startVoiceListening()
        } else {
            assistantState = AssistantState.ERROR
            statusText = "محتاج إذن الميكروفون عشان أقدر أسمعك يا فندم."
        }
    }

    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        preferencesManager = PreferencesManager(this)
        taskManager = TaskManager(this)
        geminiClient = GeminiClient()

        val sharedPrefs = getSharedPreferences("yamosaed_raw", MODE_PRIVATE)
        host = AndroidHost(this, sharedPrefs)

        val ctx = Ctx(host) { msg ->
            runOnUiThread {
                replyText = msg
                ttsManager.speak(msg)
            }
        }
        localAgent = Agent(defaultTools(), WebSearchTool(), ctx)

        coordinator = AssistantCoordinator(
            host = host,
            preferencesManager = preferencesManager,
            taskManager = taskManager,
            geminiClient = geminiClient,
            localAgent = localAgent
        ) { reply, action, actionType ->
            runOnUiThread {
                replyText = reply
                currentActionDetail = action
                statusText = when (actionType) {
                    ActionType.QUESTION -> "إجابة معرفية"
                    ActionType.CHAT -> "جاهز"
                    ActionType.MUSIC_SEARCH -> "تشغيل الموسيقى"
                    ActionType.WEATHER -> "حالة الطقس"
                    ActionType.ALARM -> "ضبط المنبه"
                    ActionType.TIMER -> "تشغيل المؤقت"
                    ActionType.OPEN_TASKS -> {
                        showTasksDialog = true
                        showSettingsDialog = false
                        "فتح المهام"
                    }
                    ActionType.OPEN_SETTINGS -> {
                        showSettingsDialog = true
                        showTasksDialog = false
                        "فتح الإعدادات"
                    }
                    ActionType.CLOSE_OVERLAYS -> {
                        showTasksDialog = false
                        showSettingsDialog = false
                        showHistoryDialog = false
                        "الرئيسية"
                    }
                    else -> "تم التنفيذ"
                }
                assistantState = AssistantState.SPEAKING

                conversationHistory.add(
                    ChatMessage(
                        text = reply,
                        isUser = false,
                        actionDetail = action,
                        actionType = actionType
                    )
                )

                ttsManager.speak(reply) {
                    runOnUiThread {
                        assistantState = AssistantState.IDLE
                        statusText = "جاهز لأمر جديد"
                    }
                }
            }
        }

        ttsManager = TtsManager(this) { ready ->
            if (ready) {
                ttsManager.setSpeechRate(preferencesManager.speechRate)
            }
        }

        speechManager = SpeechManager(
            context = this,
            onReady = {
                assistantState = AssistantState.LISTENING
                statusText = "سامعك كويس... اتكلم دلوقتي"
            },
            onSpeechStart = {
                assistantState = AssistantState.LISTENING
                statusText = "بسمعك..."
            },
            onSpeechEnd = {
                assistantState = AssistantState.THINKING
                statusText = "ثواني، بفهم طلبك..."
            },
            onResult = { query ->
                handleUserQuery(query)
            },
            onError = { error ->
                assistantState = AssistantState.ERROR
                statusText = error
            }
        )

        // Request notification permission if Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            MaterialTheme {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = Color(0xFF030712)
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // الشاشة الرئيسية الكاملة: العين الإلكترونية الآلية في المنتصف بدون فوضى
                        AssistantScreen(
                            state = assistantState,
                            statusText = statusText,
                            recognizedText = recognizedText,
                            replyText = replyText,
                            actionDetail = currentActionDetail,
                            isGeminiConnected = preferencesManager.geminiApiKey.isNotBlank(),
                            onMicClick = {
                                if (assistantState == AssistantState.LISTENING) {
                                    speechManager.stopListening()
                                    assistantState = AssistantState.IDLE
                                    statusText = "تم إيقاف الاستماع"
                                } else {
                                    cancelCurrentOperation()
                                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startVoiceListening()
                                    } else {
                                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            onSubmitTextQuery = { query ->
                                handleUserQuery(query)
                            },
                            onCancelOperation = {
                                cancelCurrentOperation()
                            },
                            onStopSpeaking = {
                                ttsManager.stop()
                                assistantState = AssistantState.IDLE
                                statusText = "تم إيقاف الصوت"
                            },
                            onOpenSettings = {
                                focusManager.clearFocus()
                                showSettingsDialog = true
                            },
                            onOpenTasks = {
                                focusManager.clearFocus()
                                showTasksDialog = true
                            },
                            onOpenHistory = {
                                focusManager.clearFocus()
                                showHistoryDialog = true
                            }
                        )

                        // نافذة الإعدادات كـ Dialog عصري أنيق (تفتح بضغطة زر علوي أو بالأمر الصوتي "الإعدادات")
                        if (showSettingsDialog) {
                            Dialog(
                                onDismissRequest = { showSettingsDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF0A1224)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        SettingsScreen(
                                            preferencesManager = preferencesManager,
                                            geminiClient = geminiClient,
                                            onTestTts = {
                                                ttsManager.speak("أهلاً بيك يا باشا! أنا يا مساعد، مساعدك الشخصي الذكي.")
                                            }
                                        )
                                        IconButton(
                                            onClick = { showSettingsDialog = false },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp)
                                                .background(Color(0xFF162544), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // نافذة المهام كـ Dialog عصري أنيق (تفتح بضغطة زر علوي أو بالأمر الصوتي "افتح مهامي")
                        if (showTasksDialog) {
                            Dialog(
                                onDismissRequest = { showTasksDialog = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF0A1224)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        TasksScreen(taskManager = taskManager)
                                        IconButton(
                                            onClick = { showTasksDialog = false },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(12.dp)
                                                .background(Color(0xFF162544), CircleShape)
                                        ) {
                                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        // نافذة سجل المحادثات
                        if (showHistoryDialog) {
                            HistoryDialog(
                                history = conversationHistory,
                                onClearHistory = { conversationHistory.clear() },
                                onDismiss = {
                                    focusManager.clearFocus()
                                    showHistoryDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startVoiceListening() {
        ttsManager.stop()
        assistantState = AssistantState.LISTENING
        statusText = "بجهز الميكروفون..."
        speechManager.startListening()
    }

    private fun handleUserQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        // منع إرسال طلبين في جزء من الثانية (Debounce)
        val now = System.currentTimeMillis()
        if (now - lastQueryTimestamp < 400L && assistantState == AssistantState.THINKING) {
            return
        }
        lastQueryTimestamp = now

        // إلغاء أي مهمة سابقة أو نطق جاري فوراً
        cancelCurrentOperation()

        recognizedText = trimmed
        conversationHistory.add(ChatMessage(text = trimmed, isUser = true))

        assistantState = AssistantState.THINKING
        statusText = "ثواني، بشوف طلبك..."

        currentQueryJob = lifecycleScope.launch {
            try {
                withTimeout(18000L) {
                    coordinator.processQuery(trimmed, conversationHistory.toList())
                }
            } catch (_: TimeoutCancellationException) {
                assistantState = AssistantState.IDLE
                val timeoutMsg = "الطلب أخد وقت زيادة يا باشا، جرب تسأل تاني أو اختار موديل أسرع زي flash من الإعدادات."
                replyText = timeoutMsg
                statusText = "انتهى الوقت المحدد للرد"
                conversationHistory.add(ChatMessage(text = timeoutMsg, isUser = false))
                ttsManager.speak(timeoutMsg)
            } catch (_: CancellationException) {
                assistantState = AssistantState.IDLE
                statusText = "تم إلغاء الطلب"
            } catch (e: Exception) {
                assistantState = AssistantState.ERROR
                statusText = e.localizedMessage ?: "حدث خطأ أثناء معالجة الطلب"
            }
        }
    }

    private fun cancelCurrentOperation() {
        currentQueryJob?.cancel()
        currentQueryJob = null
        ttsManager.stop()
        speechManager.stopListening()
        SearchClickService.instance?.disarm()
        if (assistantState != AssistantState.IDLE) {
            assistantState = AssistantState.IDLE
            statusText = "جاهز يا باشا"
        }
    }

    override fun onDestroy() {
        cancelCurrentOperation()
        speechManager.destroy()
        ttsManager.shutdown()
        super.onDestroy()
    }
}