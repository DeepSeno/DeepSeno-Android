package com.enmooy.deepseno.ui.screen.capture

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.enmooy.deepseno.ui.theme.AccentRed

@Composable
fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pulse animation for recording state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulse1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse1Scale",
    )

    val pulse1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOut),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse1Alpha",
    )

    val pulse2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOut, delayMillis = 400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse2Scale",
    )

    val pulse2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseOut, delayMillis = 400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse2Alpha",
    )

    // Spring-animated inner shape size
    val innerSize by animateDpAsState(
        targetValue = if (isRecording) 36.dp else 80.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "innerSize",
    )

    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 10.dp else 40.dp,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "innerCorner",
    )

    // Gradient ring colors
    val ringGradient = Brush.linearGradient(
        colors = listOf(AccentRed, AccentRed.copy(alpha = 0.7f)),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    // Glow opacity
    val glowAlpha by animateFloatAsState(
        targetValue = if (isRecording) 0.25f else 0.1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "glowAlpha",
    )

    val outerGlowAlpha by animateFloatAsState(
        targetValue = if (isRecording) 0.08f else 0.03f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "outerGlowAlpha",
    )

    Box(
        modifier = modifier
            .size(120.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .drawBehind {
                val center = Offset(size.width / 2, size.height / 2)

                // Outer glow (always visible)
                drawCircle(
                    color = AccentRed.copy(alpha = outerGlowAlpha),
                    radius = 60.dp.toPx(),
                    center = center,
                )

                // Red glow shadow
                drawCircle(
                    color = AccentRed.copy(alpha = glowAlpha),
                    radius = 40.dp.toPx(),
                    center = center.copy(y = center.y + 4.dp.toPx()),
                    style = Stroke(width = 0f),
                )

                // Pulse rings (recording only)
                if (isRecording) {
                    // First pulse ring
                    drawCircle(
                        color = AccentRed.copy(alpha = pulse1Alpha),
                        radius = 55.dp.toPx() * pulse1Scale,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )

                    // Second pulse ring (delayed)
                    drawCircle(
                        color = AccentRed.copy(alpha = pulse2Alpha),
                        radius = 55.dp.toPx() * pulse2Scale * 0.95f,
                        center = center,
                    )
                }

                // Gradient ring stroke (100dp diameter = 50dp radius)
                drawCircle(
                    brush = ringGradient,
                    radius = 50.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Inner shape
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(AccentRed),
        )
    }
}
