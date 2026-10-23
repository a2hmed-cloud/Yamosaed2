package com.yamosaed.assistant.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamosaed.assistant.WakeWordService
import com.yamosaed.assistant.gemini.GeminiClient
import com.yamosaed.assistant.storage.PreferencesManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    preferencesManager: PreferencesManager,
    geminiClient: GeminiClient,
    onTestTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var apiKeyInput by remember { mutableStateOf(preferencesManager.geminiApiKey) }
    var isKeyHidden by remember { mutableStateOf(true) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    var homeCityInput by remember { mutableStateOf(preferencesManager.homeCity) }
    var bgWakeEnabled by remember { mutableStateOf(preferencesManager.isBackgroundWakeEnabled) }
    var speechRate by remember { mutableFloatStateOf(preferencesManager.speechRate) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080D18))
            .verticalScroll(scrollState)
            .padding(20.dp)
    ) {
        Text(
            text = "الإعدادات والصلاحيات",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "تخصيص مساعدك الصوتي وضبط خدمات النظام",
            fontSize = 13.sp,
            color = Color(0xFF8FA4CE)
        )

        Spacer(Modifier.height(20.dp))

        // --- SECTION 1: Gemini API Configuration ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14203D)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF64B5F6))
                    Spacer(Modifier.width(8.dp))
                    Text("إعداد Gemini API", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "أدخل مفتاحك من Google AI Studio لتفعيل فهم اللغة الطبيعية وسياق المحادثة. يُحفظ المفتاح محليًا على جهازك.",
                    color = Color(0xFFBAC7E2),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    visualTransformation = if (isKeyHidden) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            focusManager.clearFocus()
                            isKeyHidden = !isKeyHidden
                        }) {
                            Icon(
                                if (isKeyHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "إظهار/إخفاء المفتاح",
                                tint = Color.LightGray
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF4F8CFF),
                        unfocusedBorderColor = Color(0xFF334B79)
                    )
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            preferencesManager.geminiApiKey = apiKeyInput
                            testStatus = "تم حفظ المفتاح بنجاح!"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D74EB)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حفظ المفتاح")
                    }

                    OutlinedButton(
                        onClick = {
                            if (apiKeyInput.isBlank()) {
                                testStatus = "يرجى كتابة المفتاح أولاً"
                                return@OutlinedButton
                            }
                            isTestingConnection = true
                            testStatus = "جاري اختبار الاتصال مع Gemini..."
                            coroutineScope.launch {
                                val result = geminiClient.testConnection(apiKeyInput.trim())
                                isTestingConnection = false
                                testStatus = if (result.isSuccess) {
                                    "✓ ${result.getOrNull()}"
                                } else {
                                    "✗ ${result.exceptionOrNull()?.message ?: "فشل الاتصال"}"
                                }
                            }
                        },
                        enabled = !isTestingConnection,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("اختبار الاتصال")
                        }
                    }
                }

                if (apiKeyInput.isNotBlank()) {
                    TextButton(
                        onClick = {
                            preferencesManager.clearApiKey()
                            apiKeyInput = ""
                            testStatus = "تم مسح المفتاح"
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("مسح المفتاح من الجهاز", color = Color(0xFFEF9A9A), fontSize = 12.sp)
                    }
                }

                testStatus?.let { status ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        color = if (status.startsWith("✓") || status.contains("نجاح")) Color(0xFF81C784) else Color(0xFFFFB74D)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- SECTION 2: System Permissions & Services Dashboard ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14203D)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFFFFB74D))
                    Spacer(Modifier.width(8.dp))
                    Text("الصلاحيات والخدمات المطلوبة", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "يحتاج المساعد إلى صلاحيات محددة لتنفيذ المهام دون ادعاءات وهمية:",
                    color = Color(0xFFBAC7E2),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))

                PermissionRow(
                    title = "إمكانية الوصول (Accessibility)",
                    subtitle = "لاختيار الأغنية تلقائيًا في Spotify وSoundCloud",
                    actionLabel = "فتح الإعدادات",
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                )

                HorizontalDivider(color = Color(0xFF22355F), modifier = Modifier.padding(vertical = 8.dp))

                PermissionRow(
                    title = "الوصول للإشعارات (Notification Listener)",
                    subtitle = "للتحكم في الموسيقى (تشغيل، إيقاف، التالي)",
                    actionLabel = "تفعيل الوصول",
                    onClick = {
                        try {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                )

                HorizontalDivider(color = Color(0xFF22355F), modifier = Modifier.padding(vertical = 8.dp))

                PermissionRow(
                    title = "المنبهات الدقيقة (Exact Alarms)",
                    subtitle = "لتشغيل التايمر وتنبيهات المهام في الوقت المحدد",
                    actionLabel = "إذن المنبه",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {}
                        }
                    }
                )

                HorizontalDivider(color = Color(0xFF22355F), modifier = Modifier.padding(vertical = 8.dp))

                PermissionRow(
                    title = "إشعارات التطبيق وصوت التنبيه",
                    subtitle = "لإظهار التنبيهات وإشعار الخدمة عند الانتهاء",
                    actionLabel = "ضبط الإشعارات",
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- SECTION 3: Background Wake Word Service ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14203D)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MicNone, contentDescription = null, tint = Color(0xFF81C784))
                        Spacer(Modifier.width(8.dp))
                        Text("الاستماع في الخلفية (تجريبي)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                    }
                    Switch(
                        checked = bgWakeEnabled,
                        onCheckedChange = { isChecked ->
                            bgWakeEnabled = isChecked
                            preferencesManager.isBackgroundWakeEnabled = isChecked
                            if (isChecked) {
                                WakeWordService.start(context)
                            } else {
                                WakeWordService.stop(context)
                            }
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "تنويه أمان: تشغيل الخدمة يعرض إشعارًا دائمًا بأن الميكروفون مفعّل. قيود أندرويد الحديثة وتحسين استهلاك البطارية قد توقف الاستماع في الخلفية، لذا يفضّل استخدام زر الميكروفون داخل التطبيق للاستجابة المضمونة.",
                    color = Color(0xFFB0BDD8),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- SECTION 4: Preferences & Voice Settings ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14203D)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("تفضيلات المساعد", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = homeCityInput,
                    onValueChange = {
                        homeCityInput = it
                        preferencesManager.homeCity = it
                    },
                    label = { Text("مدينتك الافتراضية (للطقس)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(Modifier.height(16.dp))

                Text("سرعة النطق الصوتي (${"%.1f".format(speechRate)}x)", color = Color(0xFFC0CEEB), fontSize = 13.sp)
                Slider(
                    value = speechRate,
                    onValueChange = {
                        speechRate = it
                        preferencesManager.speechRate = it
                    },
                    valueRange = 0.7f..1.4f,
                    steps = 6
                )

                Button(
                    onClick = onTestTts,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF233660)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("تجربة الصوت المصري")
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF8FA4CE), fontSize = 11.sp)
        }
        TextButton(onClick = onClick) {
            Text(actionLabel, color = Color(0xFF64B5F6), fontSize = 12.sp)
        }
    }
}
