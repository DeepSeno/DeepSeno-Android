package com.enmooy.deepseno.ui.screen.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.data.remote.model.Recording
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.screen.common.StatusBadge
import com.enmooy.deepseno.ui.theme.*

@Composable
fun SourceCard(
    recording: Recording,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current
    val (icon, iconColor) = mediaIconInfo(recording.mediaType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSecondary.copy(alpha = 0.6f))
            .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Colored left-edge accent strip
        Box(
            modifier = Modifier
                .padding(start = 2.dp, top = 8.dp, bottom = 8.dp)
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(iconColor),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Media type icon in colored bg
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = recording.mediaType,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.fileName,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recording.formattedDuration?.let { duration ->
                        Text(
                            text = duration,
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = TextSecondary,
                        )
                    } ?: recording.pageCount?.let { pages ->
                        if (pages > 0) {
                            Text(
                                text = "${pages}p",
                                style = TextStyle(fontSize = 11.sp),
                                color = TextSecondary,
                            )
                        }
                    }
                    recording.recordedAt?.let { date ->
                        Text(
                            text = "\u00B7",
                            style = TextStyle(fontSize = 11.sp),
                            color = TextSecondary,
                        )
                        val parts = date.take(10).split("-")
                        val formatted = if (parts.size == 3) "${parts[1]}/${parts[2]}" else date.take(10)
                        Text(
                            text = formatted,
                            style = TextStyle(fontSize = 11.sp),
                            color = TextSecondary,
                        )
                    }
                    if (recording.status.isNotEmpty()) {
                        StatusBadge(status = recording.status)
                    }
                }

                // Meta tags
                val tags = buildCompactTags(recording)
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { (iconText, value) ->
                            MetaTag(icon = iconText, text = value)
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun MetaTag(icon: String, text: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BgTertiary.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = icon,
            style = TextStyle(fontSize = 8.sp),
            color = TextTertiary,
        )
        Text(
            text = text,
            style = TextStyle(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = TextTertiary,
        )
    }
}

private fun mediaIconInfo(mediaType: String): Pair<ImageVector, Color> {
    return when (mediaType) {
        "audio" -> Icons.Default.Mic to AccentGreen
        "video" -> Icons.Default.Videocam to AccentBlue
        "document", "pdf", "docx", "text" -> Icons.Default.Description to AccentAmber
        "image" -> Icons.Default.Image to Color(0xFFA855F7) // purple
        else -> Icons.Default.InsertDriveFile to TextSecondary
    }
}

private fun buildCompactTags(recording: Recording): List<Pair<String, String>> {
    val tags = mutableListOf<Pair<String, String>>()
    recording.extractedCount?.let { if (it > 0) tags.add("\uD83C\uDFF7" to "$it") }
    recording.speakerCount?.let { if (it > 0) tags.add("\uD83D\uDC65" to "$it") }
    recording.wordCount?.let { if (it > 0) tags.add("\uD83D\uDCDD" to "$it") }
    return tags
}
