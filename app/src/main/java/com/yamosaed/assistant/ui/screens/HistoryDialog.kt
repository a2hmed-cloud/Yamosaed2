package com.yamosaed.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yamosaed.assistant.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryDialog(
    history: List<ChatMessage>,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF101C35),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل المحادثة",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        if (history.isNotEmpty()) {
                            IconButton(onClick = onClearHistory) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = "مسح السجل",
                                    tint = Color(0xFFEF9A9A)
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.LightGray)
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E325C), modifier = Modifier.padding(vertical = 8.dp))

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا يوجد محادثات سابقة.",
                            color = Color(0xFF8FA4CE),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(history) { msg ->
                            val timeStr = SimpleDateFormat("HH:mm", Locale("ar", "EG")).format(Date(msg.timestamp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (msg.isUser) Color(0xFF1A3060) else Color(0xFF122244),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (msg.isUser) "أنت" else "يا مساعد",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (msg.isUser) Color(0xFF90CAF9) else Color(0xFF80DEEA)
                                    )
                                    Text(
                                        text = timeStr,
                                        fontSize = 10.sp,
                                        color = Color(0xFF7E8FA8)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                                if (!msg.actionDetail.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "الإجراء: ${msg.actionDetail}",
                                        color = Color(0xFF81C784),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
