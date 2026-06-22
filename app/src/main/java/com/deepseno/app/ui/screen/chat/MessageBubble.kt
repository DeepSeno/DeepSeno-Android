package com.enmooy.deepseno.ui.screen.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.data.remote.model.Source
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.DisplayMessage

@Composable
fun MessageBubble(message: DisplayMessage) {
    val t = LocalStrings.current
    val isUser = message.role == "user"

    val shape = if (isUser) {
        RoundedCornerShape(
            topStart = 14.dp,
            topEnd = 14.dp,
            bottomStart = 14.dp,
            bottomEnd = 4.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 14.dp,
            topEnd = 14.dp,
            bottomStart = 4.dp,
            bottomEnd = 14.dp,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            // Message bubble
            Column(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isUser) {
                            Modifier.background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        AccentGreen.copy(alpha = 0.14f),
                                        AccentGreen.copy(alpha = 0.06f),
                                    ),
                                )
                            )
                        } else {
                            Modifier
                                .background(BgSecondary.copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                // Content
                if (message.isStreaming && message.content.isEmpty()) {
                    TypingIndicator()
                } else if (message.isStreaming) {
                    Text(
                        text = message.content + " \u258C",
                        style = TextStyle(fontSize = 14.sp),
                        color = TextPrimary,
                        lineHeight = 20.sp,
                    )
                } else if (isUser) {
                    Text(
                        text = message.content,
                        style = TextStyle(fontSize = 14.sp),
                        color = TextPrimary,
                        lineHeight = 20.sp,
                    )
                } else {
                    // AI response: block-level markdown
                    MarkdownContentView(content = message.content)
                }
            }

            // Sources
            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                SourcesView(
                    sources = message.sources,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}

// MARK: - Sources view with dedup

@Composable
private fun SourcesView(
    sources: List<Source>,
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current
    val uniqueSources = deduplicatedSources(sources, max = 3)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BgSecondary.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text = t.sourcesLabel,
            style = TextStyle(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            color = TextTertiary,
        )
        Spacer(Modifier.height(3.dp))

        uniqueSources.forEach { source ->
            Row(
                modifier = Modifier.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(10.dp),
                )
                source.speaker?.let { speaker ->
                    Text(
                        text = speaker,
                        style = TextStyle(fontSize = 10.sp),
                        color = TextSecondary,
                    )
                }
                // Only show time if meaningful (not 00:00)
                source.time?.let { time ->
                    if (!time.contains("00:00")) {
                        Text(
                            text = time,
                            style = TextStyle(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = TextTertiary,
                        )
                    }
                }
            }
        }

        if (sources.size > 3) {
            Text(
                text = "+${sources.size - 3}",
                style = TextStyle(fontSize = 10.sp),
                color = TextTertiary,
            )
        }
    }
}

private fun deduplicatedSources(sources: List<Source>, max: Int): List<Source> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<Source>()
    for (source in sources) {
        val key = (source.speaker ?: "") + (source.time ?: "")
        if (seen.add(key)) {
            result.add(source)
            if (result.size >= max) break
        }
    }
    return result
}

// MARK: - Markdown Content View

@Composable
private fun MarkdownContentView(content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val blocks = parseMarkdownBlocks(content)
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Text(
                        text = parseInlineBold(block.text),
                        style = TextStyle(
                            fontSize = if (block.level <= 2) 16.sp else 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                }
                is MarkdownBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "\u2022",
                            style = TextStyle(fontSize = 14.sp),
                            color = AccentGreen,
                            modifier = Modifier.width(10.dp),
                        )
                        Text(
                            text = parseInlineBold(block.text),
                            style = TextStyle(fontSize = 14.sp),
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        )
                    }
                }
                is MarkdownBlock.IndentedBullet -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "\u25E6",
                            style = TextStyle(fontSize = 12.sp),
                            color = TextTertiary,
                            modifier = Modifier.width(10.dp),
                        )
                        Text(
                            text = parseInlineBold(block.text),
                            style = TextStyle(fontSize = 13.sp),
                            color = TextSecondary,
                            lineHeight = 18.sp,
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineBold(block.text),
                        style = TextStyle(fontSize = 14.sp),
                        color = TextPrimary,
                        lineHeight = 20.sp,
                    )
                }
                is MarkdownBlock.Empty -> {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class Heading(val text: String, val level: Int) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class IndentedBullet(val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data object Empty : MarkdownBlock()
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val lines = content.split("\n")
    return lines.map { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> MarkdownBlock.Empty
            trimmed.startsWith("### ") -> MarkdownBlock.Heading(trimmed.removePrefix("### "), 3)
            trimmed.startsWith("## ") -> MarkdownBlock.Heading(trimmed.removePrefix("## "), 2)
            trimmed.startsWith("# ") -> MarkdownBlock.Heading(trimmed.removePrefix("# "), 1)
            (line.startsWith("    * ") || line.startsWith("    - ") || line.startsWith("\t* ") || line.startsWith("\t- ")) -> {
                MarkdownBlock.IndentedBullet(trimmed.removePrefix("* ").removePrefix("- ").trim())
            }
            trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
                MarkdownBlock.Bullet(trimmed.removePrefix("* ").removePrefix("- "))
            }
            else -> MarkdownBlock.Paragraph(trimmed)
        }
    }
}

private fun parseInlineBold(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val boldStart = remaining.indexOf("**")
            if (boldStart == -1) {
                append(remaining)
                break
            }
            // Append text before bold
            append(remaining.substring(0, boldStart))
            remaining = remaining.substring(boldStart + 2)

            val boldEnd = remaining.indexOf("**")
            if (boldEnd == -1) {
                // No closing **, put back the opening
                append("**")
                append(remaining)
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(remaining.substring(0, boldEnd))
            }
            remaining = remaining.substring(boldEnd + 2)
        }
    }
}

// MARK: - Typing indicator

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = alpha * 0.5f)),
            )
        }
    }
}
