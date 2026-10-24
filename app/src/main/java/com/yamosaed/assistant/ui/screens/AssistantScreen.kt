package com.yamosaed.assistant.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamosaed.assistant.AssistantEyes
import com.yamosaed.assistant.model.AssistantState

/**
 * واجهة العين الإلكترونية الذكية لمساعد يا مساعد (Robotic AI Eye Interface)
 * مخصصة للعمل بكفاءة في الوضع الأفقي (Landscape) والوضع الرأسي بدون فوضى أو عناصر مزدحمة
 */
@Composable
fun AssistantScreen(
    state: AssistantState,
    statusText: String,
    recognizedText: String,
    replyText: String,
    actionDetail: String?,
    isGeminiConnected: Boolean,
    onMicClick: () -> Unit,
    onSubmitTextQuery: (String) -> Unit,
    onCancelOperation: () -> Unit,
    onStopSpeaking: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTextInput by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0A1428),
                        Color(0xFF050B18),
                        Color(0xFF02040A)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // --- 1. الشريط العلوي الهادئ (Top Cyber Bar) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // مؤشر الهوية السايبرانية والاتصال
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "يا مساعد",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Surface(
                    color = Color(0xFF142442),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "AI ROBOT",
                        color = Color(0xFF64B5F6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // أزرار التحكم العلوية (الإعدادات، المهام، السجل) كما طلب المستخدم
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // زر مهامي من فوق
                IconButton(
                    onClick = onOpenTasks,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF101C36), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Checklist,
                        contentDescription = "افتح مهامي",
                        tint = Color(0xFF90CAF9),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // زر الإعدادات في الشاشة من فوق (بناءً على طلب المستخدم صراحة)
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF101C36), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "الإعدادات",
                        tint = Color(0xFF90CAF9),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // سجل المحادثات
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF101C36), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "سجل الأوامر",
                        tint = Color(0xFF78909C),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // --- 2. قلب الشاشة: العين الإلكترونية الآلية في المنتصف تماماً ---
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // العين الإلكترونية الآلية (نصف دائرة بيضاء تملأ الشاشة بالعرض)
            AssistantEyes(
                state = state,
                onEyeClick = {
                    if (state == AssistantState.LISTENING) {
                        onCancelOperation()
                    } else if (state == AssistantState.SPEAKING) {
                        onStopSpeaking()
                    } else {
                        onMicClick()
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            // شريط الحالة السايبراني المتوهج (Floating Status Pill)
            Surface(
                color = Color(0xFF0B172E).copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (state == AssistantState.LISTENING || state == AssistantState.THINKING || state == AssistantState.SPEAKING) {
                        Color(0xFF00E5FF)
                    } else {
                        Color(0xFF1E3A70)
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusDotColor = if (state == AssistantState.LISTENING || state == AssistantState.THINKING) Color(0xFF00E5FF) else Color(0xFF40C4FF)

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(statusDotColor, CircleShape)
                    )

                    Text(
                        text = when (state) {
                            AssistantState.LISTENING -> "بيسمعك دلوقتي... اتكلم يا فندم"
                            AssistantState.THINKING -> "بفكر وبحلل طلبك..."
                            AssistantState.SPEAKING -> "بيتكلم..."
                            AssistantState.ERROR -> "حدث تعذر: $statusText"
                            else -> if (statusText.isNotBlank()) statusText else "اضغط على العين أو اطلب أمر صوتي"
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // نص الرد أو التعرف الصوتي الأخير بشكل هادئ غير مزعج
            AnimatedVisibility(
                visible = replyText.isNotBlank() && state != AssistantState.LISTENING,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = replyText,
                    color = Color(0xFFB0C4DE),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 32.dp, vertical = 6.dp)
                        .widthIn(max = 520.dp)
                )
            }

            // زر الإلغاء الفوري الصغير إذا كان مشغولاً
            AnimatedVisibility(
                visible = state == AssistantState.THINKING || state == AssistantState.SPEAKING,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                OutlinedButton(
                    onClick = onCancelOperation,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF8A80)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FF5252)),
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "إلغاء",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("إلغاء", fontSize = 11.sp)
                }
            }
        }

        // --- 3. الشريط السفلي السريع (Minimal Floating Bottom Bar) ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // زر لوحة المفاتيح الهادئ لكتابة أمر صامت إذا رغب المستخدم
            IconButton(
                onClick = { showTextInput = !showTextInput },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF0E1A33), CircleShape)
            ) {
                Icon(
                    if (showTextInput) Icons.Filled.Close else Icons.Filled.Keyboard,
                    contentDescription = "كتابة أمر نصي",
                    tint = Color(0xFF7E97C2),
                    modifier = Modifier.size(18.dp)
                )
            }

            // الزر الرئيسي للاستماع الصوتي
            FloatingActionButton(
                onClick = onMicClick,
                containerColor = if (state == AssistantState.LISTENING) Color(0xFF00E5FF) else Color(0xFF0091EA),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = when (state) {
                        AssistantState.LISTENING -> Icons.Filled.Mic
                        AssistantState.THINKING -> Icons.Filled.HourglassTop
                        AssistantState.SPEAKING -> Icons.Filled.VolumeUp
                        else -> Icons.Filled.Mic
                    },
                    contentDescription = "الميكروفون",
                    modifier = Modifier.size(26.dp)
                )
            }

            // زر إيقاف الصوت إن كان يتحدث
            if (state == AssistantState.SPEAKING) {
                IconButton(
                    onClick = onStopSpeaking,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF2C151A), CircleShape)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "إيقاف الصوت",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // حقل إدخال نصي منبثق عند الحاجة
        AnimatedVisibility(
            visible = showTextInput,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .widthIn(max = 480.dp)
        ) {
            Surface(
                color = Color(0xFF0F1E3A),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E4B82)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = {
                            Text("اكتب أمرك أو سؤالك هنا...", color = Color(0xFF6B87B5), fontSize = 13.sp)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00E5FF),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    onSubmitTextQuery(textInput)
                                    textInput = ""
                                    showTextInput = false
                                    focusManager.clearFocus()
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSubmitTextQuery(textInput)
                                textInput = ""
                                showTextInput = false
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "إرسال",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }
        }
    }
}
