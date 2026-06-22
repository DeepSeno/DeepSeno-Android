package com.enmooy.deepseno.ui.screen.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.AccentAmber
import com.enmooy.deepseno.ui.theme.AccentGreen
import com.enmooy.deepseno.ui.theme.AccentRed
import com.enmooy.deepseno.ui.theme.TextSecondary

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current

    val color: Color = when (status.lowercase()) {
        "completed" -> AccentGreen
        "processing", "uploading" -> AccentAmber
        "failed" -> AccentRed
        else -> TextSecondary
    }

    val label: String = when (status.lowercase()) {
        "completed" -> t.statusCompleted
        "processing", "uploading" -> t.statusProcessing
        "failed" -> t.statusFailed
        else -> status.uppercase()
    }

    val shape = RoundedCornerShape(5.dp)

    Text(
        text = label,
        style = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
        ),
        color = color,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.15f), shape)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
