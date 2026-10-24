package com.yamosaed.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yamosaed.assistant.model.AssistantState
import kotlin.math.min

/**
 * شكل نصف دائري / قوس علوي ناعم للعين الإلكترونية
 */
val CyberEyeShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    // قوس علوي ناعم ومستقيم من الأسفل
    moveTo(0f, h)
    arcTo(
        rect = Rect(0f, 0f, w, h * 1.85f),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false
    )
    lineTo(w, h)
    close()
}

/**
 * عينين إلكترونيتين لروبوت الذكاء الاصطناعي بلون أزرق سايبراني موحد
 * متمركزتان في منتصف الشاشة وتملآن العرض عند وضع التليفون landscape
 */
@Composable
fun AssistantEyes(
    state: AssistantState,
    modifier: Modifier = Modifier,
    onEyeClick: (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robot_twin_eyes_motion")

    // 1. نبض التنفس الحيوي المتزامن للعينين
    val breathScale by infiniteTransition.animateFloat(
        initialValue = when (state) {
            AssistantState.LISTENING -> 1.0f
            AssistantState.THINKING -> 0.98f
            AssistantState.SPEAKING -> 0.97f
            AssistantState.EXECUTING -> 1.0f
            else -> 0.985f
        },
        targetValue = when (state) {
            AssistantState.LISTENING -> 1.08f
            AssistantState.THINKING -> 1.03f
            AssistantState.SPEAKING -> 1.05f
            AssistantState.EXECUTING -> 1.06f
            else -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 420
                    AssistantState.SPEAKING -> 320
                    AssistantState.THINKING -> 650
                    AssistantState.EXECUTING -> 360
                    else -> 1700
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    // 2. حركة مسح بصرية أفقية متزامنة أثناء التفكير (Thinking Gaze / Scan)
    val gazeShiftX by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gaze_shift"
    )

    // 3. تذبذب نبض الصوت أثناء الكلام (Audio Ripple Flutter)
    val audioFlutter by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "audio_flutter"
    )

    // 4. ترميشة روبوت خاطفة (Robotic Blink) متزامنة بين العينين
    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blink_progress"
    )
    val eyelidOpen = if (state == AssistantState.IDLE && blinkProgress < 0.12f) 0.15f else 1.0f

    // 5. درجات اللون الأزرق السايبراني الموحد (Unified Cyber Blue Palette)
    val electricBlue = Color(0xFF00E5FF)       // أزرق سماوي نيون مشع
    val deepCyberBlue = Color(0xFF0091EA)      // أزرق كهربائي عميق
    val ambientBlue = Color(0xFF0288D1).copy(alpha = 0.40f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight

        // تحديد أبعاد العينين لملء الشاشة بشكل متناسق في الوضعين
        val singleEyeWidth = if (isLandscape) {
            min((maxWidth.value - 64f) / 2.3f, maxHeight.value * 0.95f).coerceIn(150f, 260f).dp
        } else {
            ((maxWidth.value - 48f) / 2f).coerceIn(130f, 190f).dp
        }
        val singleEyeHeight = (singleEyeWidth.value * 0.65f).dp
        val spacingBetweenEyes = if (isLandscape) 32.dp else 20.dp

        // صف العينين المزدوجتين (عين يمين وعين شمال)
        Row(
            modifier = Modifier
                .scale(breathScale)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = onEyeClick != null
                ) {
                    onEyeClick?.invoke()
                },
            horizontalArrangement = Arrangement.spacedBy(spacingBetweenEyes),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // العين اليسرى
            SingleRoboticEye(
                width = singleEyeWidth,
                height = singleEyeHeight,
                state = state,
                eyelidOpen = eyelidOpen,
                gazeShiftX = gazeShiftX,
                audioFlutter = audioFlutter,
                electricBlue = electricBlue,
                deepCyberBlue = deepCyberBlue,
                ambientBlue = ambientBlue,
                isLeft = true
            )

            // العين اليمنى
            SingleRoboticEye(
                width = singleEyeWidth,
                height = singleEyeHeight,
                state = state,
                eyelidOpen = eyelidOpen,
                gazeShiftX = gazeShiftX,
                audioFlutter = audioFlutter,
                electricBlue = electricBlue,
                deepCyberBlue = deepCyberBlue,
                ambientBlue = ambientBlue,
                isLeft = false
            )
        }
    }
}

/**
 * رسم العين الإلكترونية الواحدة بنصف دائرة بيضاء مصقولة وبؤبؤ أزرق إلكتروني متوهج
 */
@Composable
private fun SingleRoboticEye(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    state: AssistantState,
    eyelidOpen: Float,
    gazeShiftX: Float,
    audioFlutter: Float,
    electricBlue: Color,
    deepCyberBlue: Color,
    ambientBlue: Color,
    isLeft: Boolean
) {
    Box(
        modifier = Modifier
            .size(width = width, height = height),
        contentAlignment = Alignment.BottomCenter
    ) {
        // الهالة المحيطة الضوئية الزرقاء (Ambient Blue Volumetric Glow)
        Canvas(modifier = Modifier.fillMaxSize().scale(1.25f)) {
            val center = Offset(size.width / 2f, size.height * 0.9f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ambientBlue,
                        ambientBlue.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = center
            )
        }

        // الهيكل الخارجي الأبيض للعين (نصف دائرة بيضاء مصقولة)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 20.dp,
                    shape = CyberEyeShape,
                    spotColor = electricBlue,
                    ambientColor = Color.White
                )
                .clip(CyberEyeShape)
                .drawBehind {
                    // هيكل سيراميكي أبيض لامع
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White,
                                Color(0xFFF0F6FF),
                                Color(0xFFDCE8F8)
                            )
                        )
                    )
                    // خط تحديد أزرق كهربائي ناعم على الحافة الخارجية
                    drawArc(
                        color = electricBlue.copy(alpha = 0.65f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(3f, 3f),
                        size = Size(size.width - 6f, size.height * 1.85f - 6f),
                        style = Stroke(width = 4f)
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // التجويف الداخلي للعين (حجرة البؤبؤ السايبرانية الزرقاء)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.85f)
                    .clip(CyberEyeShape)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. خلفية الحجرة الزجاجية الداكنة
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF030A18),
                                Color(0xFF06142E),
                                Color(0xFF020712)
                            )
                        )
                    )

                    // 2. شبكة خطوط تقنية زرقاء نانوية (Cyber Blue Grid)
                    val gridPaint = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            electricBlue.copy(alpha = 0.20f),
                            Color.Transparent
                        )
                    )
                    drawLine(
                        brush = gridPaint,
                        start = Offset(0f, h * 0.45f),
                        end = Offset(w, h * 0.45f),
                        strokeWidth = 1.5f
                    )
                    drawLine(
                        brush = gridPaint,
                        start = Offset(0f, h * 0.72f),
                        end = Offset(w, h * 0.72f),
                        strokeWidth = 1.5f
                    )

                    // 3. مركز البؤبؤ الآلي الأزرق مع إمكانية تحريك النظر (Gaze Shift)
                    val pupilOffsetX = if (state == AssistantState.THINKING) gazeShiftX else 0f
                    val pupilCenter = Offset((w / 2f) + pupilOffsetX, h * 0.95f)
                    val baseRadius = min(w, h) * 0.62f

                    // حلقة رقمية خارجية زرقاء
                    drawCircle(
                        color = electricBlue.copy(alpha = 0.40f),
                        radius = baseRadius * 0.82f * eyelidOpen,
                        center = pupilCenter,
                        style = Stroke(width = 2.5f)
                    )

                    // حلقة وسيطة متوهجة
                    drawCircle(
                        color = deepCyberBlue.copy(alpha = 0.65f),
                        radius = baseRadius * 0.60f * eyelidOpen,
                        center = pupilCenter,
                        style = Stroke(width = 3.5f)
                    )

                    // 4. البؤبؤ الأزرق المركزي المشع (Luminous Electric Blue Iris)
                    val coreRadius = baseRadius * 0.38f * eyelidOpen * (if (state == AssistantState.SPEAKING) audioFlutter else 1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                electricBlue,
                                deepCyberBlue,
                                Color.Transparent
                            ),
                            center = pupilCenter,
                            radius = coreRadius.coerceAtLeast(6f)
                        ),
                        radius = coreRadius.coerceAtLeast(6f),
                        center = pupilCenter
                    )

                    // 5. موجة استشعار صوتية عند الاستماع (Listening Audio Ring)
                    if (state == AssistantState.LISTENING) {
                        drawCircle(
                            color = electricBlue.copy(alpha = 0.35f),
                            radius = baseRadius * 0.96f,
                            center = pupilCenter,
                            style = Stroke(width = 2f)
                        )
                    }

                    // نقطة بريق الضوء الكريستالي
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = 4.5f,
                        center = Offset((w / 2f) - baseRadius * 0.22f + pupilOffsetX, h * 0.52f)
                    )
                }
            }
        }
    }
}
