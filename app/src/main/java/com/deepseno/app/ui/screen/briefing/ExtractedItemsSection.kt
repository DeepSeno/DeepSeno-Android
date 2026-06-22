package com.enmooy.deepseno.ui.screen.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.enmooy.deepseno.data.remote.model.ExtractedItem
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState

private val typeOrder = listOf("decision", "contact", "meeting", "number", "memo")

private fun typeColor(type: String): Color = when (type) {
    "decision" -> AccentAmber
    "contact" -> AccentBlue
    "meeting" -> Color(0xFFA855F7) // purple
    "number" -> TextSecondary
    "memo" -> Color(0xFF14B8A6) // teal
    else -> TextSecondary
}

@Composable
fun ExtractedItemsSection(
    items: List<ExtractedItem>,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit = { _, _ -> },
    onAskAI: (prompt: String) -> Unit = {},
) {
    val t = LocalStrings.current
    var quoteItem by remember { mutableStateOf<ExtractedItem?>(null) }
    val appState: AppState = hiltViewModel()

    // Group by type in defined order
    val grouped = items.groupBy { it.type }
    val orderedTypes = typeOrder.filter { grouped.containsKey(it) } +
        grouped.keys.filter { it !in typeOrder }

    Column {
        // Section header
        Text(
            text = t.extractedHeader,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Surface(
            color = BgSecondary,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                orderedTypes.forEach { type ->
                    val typeItems = grouped[type] ?: return@forEach
                    TypeGroup(
                        type = type,
                        items = typeItems,
                        onSourceClick = onSourceClick,
                        onShowQuote = { quoteItem = it },
                        onAskAI = onAskAI,
                    )
                }
            }
        }
    }

    quoteItem?.let { item ->
        BriefingQuoteSheet(
            item = item,
            apiClient = appState.apiClient,
            onDismiss = { quoteItem = null },
            onJumpToSource = { rid, sid -> onSourceClick(rid, sid) },
        )
    }
}

@Composable
private fun TypeGroup(
    type: String,
    items: List<ExtractedItem>,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
    onShowQuote: (ExtractedItem) -> Unit,
    onAskAI: (prompt: String) -> Unit,
) {
    val color = typeColor(type)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Type badge
        Text(
            text = type.uppercase(),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = color,
            modifier = Modifier
                .background(
                    color = color.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        // Items
        items.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Colored dot
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(color),
                    )

                    // Content text
                    Text(
                        text = item.content,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        color = TextPrimary,
                        modifier = Modifier.weight(1f),
                    )

                    BriefingItemMenu(
                        item = item,
                        onShowQuote = { onShowQuote(item) },
                        onAskAI = onAskAI,
                    )
                }

                // Source attribution — tap to jump to the originating recording at segment
                if (item.hasSource && item.recordingId != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(modifier = Modifier.padding(start = 14.dp)) {
                        SourceLinkRow(
                            item = item,
                            onClick = { onSourceClick(item.recordingId!!, item.segmentId) },
                        )
                    }
                }
            }
        }
    }
}
