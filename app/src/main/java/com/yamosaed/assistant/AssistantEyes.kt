package com.yamosaed.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yamosaed.assistant.model.AssistantState

@Composable
fun AssistantEyes(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eyes_motion")

    // Pulse animation based on state
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = when (state) {
            AssistantState.LISTENING -> 1.0f
            AssistantState.SPEAKING -> 0.95f
            AssistantState.THINKING -> 0.96f
            AssistantState.EXECUTING -> 0.98f
            else -> 0.97f
        },
        targetValue = when (state) {
            AssistantState.LISTENING -> 1.15f
            AssistantState.SPEAKING -> 1.08f
            AssistantState.THINKING -> 1.04f
            AssistantState.EXECUTING -> 1.12f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 400
                    AssistantState.SPEAKING -> 350
                    AssistantState.THINKING -> 700
                    AssistantState.EXECUTING -> 300
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Thinking pupil horizontal sway
    val thinkingShift by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_shift"
    )

    // Speaking vertical height flutter
    val speakingFlutter by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking_flutter"
    )

    // Blinking animation for idle state
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink_progress"
    )
    val eyeOpenRatio = if (state == AssistantState.IDLE && blinkProgress < 0.15f) 0.15f else 1.0f

    // Dynamic gradient colors
    val auraColorOuter by animateColorAsState(
        targetValue = when (state) {
            AssistantState.LISTENING -> Color(0xFF0D47A1)
            AssistantState.THINKING -> Color(0xFF4A148C)
            AssistantState.SPEAKING -> Color(0xFF006064)
            AssistantState.EXECUTING -> Color(0xFFE65100)
            AssistantState.SUCCESS -> Color(0xFF1B5E20)
            AssistantState.ERROR -> Color(0xFFB71C1C)
            AssistantState.IDLE -> Color(0xFF14203D)
        },
        label = "aura_color_outer"
    )

    val auraColorCenter by animateColorAsState(
        targetValue = when (state) {
            AssistantState.LISTENING -> Color(0xFF448AFF)
            AssistantState.THINKING -> Color(0xFFAB47BC)
            AssistantState.SPEAKING -> Color(0xFF26C6DA)
            AssistantState.EXECUTING -> Color(0xFFFF9800)
            AssistantState.SUCCESS -> Color(0xFF4CAF50)
            AssistantState.ERROR -> Color(0xFFEF5350)
            AssistantState.IDLE -> Color(0xFF304FFE)
        },
        label = "aura_color_center"
    )

    val pupilColor by animateColorAsState(
        targetValue = when (state) {
            AssistantState.ERROR -> Color(0xFF880E4F)
            AssistantState.SUCCESS -> Color(0xFF1B5E20)
            else -> Color(0xFF0B1938)
        },
        label = "pupil_color"
    )

    Box(
        modifier = modifier
            .size(160.dp)
            .scale(pulseScale)
            .background(
                brush = Brush.radialGradient(
                    listOf(auraColorCenter.copy(alpha = 0.85f), auraColorOuter, Color(0xFF080D18))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(2) { eyeIndex ->
                val eyeHeight = when (state) {
                    AssistantState.IDLE -> 56.dp * eyeOpenRatio
                    AssistantState.SPEAKING -> 56.dp * speakingFlutter
                    AssistantState.SUCCESS -> 42.dp
                    AssistantState.ERROR -> 48.dp
                    else -> 56.dp
                }

                // White sclera
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = eyeHeight)
                        .clip(if (state == AssistantState.SUCCESS) RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp) else CircleShape)
                        .background(Color(0xFFF0F5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    // Pupil
                    val pupilOffsetX = if (state == AssistantState.THINKING) thinkingShift.dp else 0.dp
                    Box(
                        modifier = Modifier
                            .offset(x = pupilOffsetX)
                            .size(
                                width = if (state == AssistantState.LISTENING) 26.dp else 22.dp,
                                height = if (state == AssistantState.LISTENING) 32.dp else 26.dp
                            )
                            .background(pupilColor, CircleShape)
                    ) {
                        // Light reflection catchlight
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = (-3).dp, y = 3.dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }

                if (eyeIndex == 0) {
                    Box(modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
