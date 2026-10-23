package com.yamosaed.assistant.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yamosaed.assistant.AssistantEyes
import com.yamosaed.assistant.model.AssistantState

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
    onStopSpeaking: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateOf("") }
    val verticalScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F1B35), Color(0xFF080D18))
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(verticalScroll),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "يا مساعد",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "مساعدك الصوتي المصري الذكي",
                    fontSize = 12.sp,
                    color = Color(0xFF8FA4CE)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onOpenSettings,
                    label = {
                        Text(
                            if (isGeminiConnected) "✓ Gemini" else "محلي",
                            fontSize = 11.sp,
                            color = if (isGeminiConnected) Color(0xFF90CAF9) else Color(0xFFFFCC80)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (isGeminiConnected) Icons.Filled.AutoAwesome else Icons.Filled.CloudOff,
                            contentDescription = null,
                            tint = if (isGeminiConnected) Color(0xFF90CAF9) else Color(0xFFFFCC80),
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0xFF142244)
                    )
                )

                IconButton(onClick = onOpenHistory) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = "سجل المحادثة",
                        tint = Color(0xFFB0BDD8)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- Center Stage: Animated Eyes ---
        AssistantEyes(state = state)

        Spacer(Modifier.height(16.dp))

        // Status Indicator
        Text(
            text = statusText,
            color = when (state) {
                AssistantState.ERROR -> Color(0xFFEF9A9A)
                AssistantState.SUCCESS -> Color(0xFFA5D6A7)
                AssistantState.LISTENING -> Color(0xFF90CAF9)
                AssistantState.SPEAKING -> Color(0xFF80DEEA)
                else -> Color.White
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(14.dp))

        // --- Dialogue Cards ---
        if (recognizedText.isNotBlank()) {
            Surface(
                color = Color(0xFF162544),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color(0xFF8FA4CE),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = recognizedText,
                        color = Color(0xFFE3EDFF),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (replyText.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Color(0xFF1A2A4C),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (!actionDetail.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = actionDetail,
                                color = Color(0xFF81C784),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = replyText,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )

                    if (state == AssistantState.SPEAKING) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إيقاف الصوت", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // --- Quick Actions Row ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val chips = listOf(
                "🌤 الطقس في الإسماعيلية",
                "🕒 الساعة كام",
                "📅 التاريخ النهارده",
                "⏱ تايمر 1 دقيقة",
                "🎵 شغل عمرو دياب",
                "📋 وريني مهامي",
                "📍 صيدلية قريبة",
                "💡 تقدر تعمل إيه"
            )
            for (chip in chips) {
                OutlinedButton(
                    onClick = { onSubmitTextQuery(chip.substringAfter(" ")) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFC6D7F8)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(chip, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- Bottom Interaction Controls ---
        // Big Voice Mic Button
        Button(
            onClick = onMicClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state == AssistantState.LISTENING) Color(0xFFE53935) else Color(0xFF3D74EB)
            ),
            modifier = Modifier
                .size(76.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                if (state == AssistantState.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = "الميكروفون",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state == AssistantState.LISTENING) "اضغط لإيقاف الاستماع" else "اضغط للتحدث",
            color = Color(0xFF8FA4CE),
            fontSize = 12.sp
        )

        Spacer(Modifier.height(16.dp))

        // Optional Text Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("اكتب طلبك لو مش حابب تتكلم...") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            focusManager.clearFocus()
                            onSubmitTextQuery(textInput)
                            textInput = ""
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
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        focusManager.clearFocus()
                        onSubmitTextQuery(textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF223660), CircleShape)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "إرسال", tint = Color(0xFF64B5F6))
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
