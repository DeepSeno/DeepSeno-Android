package com.enmooy.deepseno.ui.screen.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.enmooy.deepseno.data.remote.model.ExtractedItem
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState

@Composable
fun TodoListSection(
    todos: List<ExtractedItem>,
    onToggle: (id: Int, currentStatus: String) -> Unit,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit = { _, _ -> },
    onAskAI: (prompt: String) -> Unit = {},
) {
    val t = LocalStrings.current
    var quoteItem by remember { mutableStateOf<ExtractedItem?>(null) }
    val appState: AppState = hiltViewModel()

    Column {
        // Section header
        Text(
            text = t.todosHeader,
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                todos.forEach { todo ->
                    TodoItem(
                        item = todo,
                        onToggle = { onToggle(todo.id, todo.status) },
                        onSourceClick = onSourceClick,
                        onShowQuote = { quoteItem = todo },
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
private fun TodoItem(
    item: ExtractedItem,
    onToggle: () -> Unit,
    onSourceClick: (recordingId: Int, segmentId: Int?) -> Unit,
    onShowQuote: () -> Unit,
    onAskAI: (prompt: String) -> Unit,
) {
    val t = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Checkbox
        Icon(
            imageVector = if (item.isCompleted) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (item.isCompleted) AccentGreen else TextSecondary,
            modifier = Modifier.size(20.dp),
        )

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.content,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    ),
                    color = if (item.isCompleted) TextSecondary else TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )

                // Priority badge
                if (item.priority == "urgent") {
                    Text(
                        text = t.priorityUrgent,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = AccentRed,
                        modifier = Modifier
                            .background(
                                color = AccentRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                } else if (item.priority == "low") {
                    Text(
                        text = t.priorityLow,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextSecondary,
                        modifier = Modifier
                            .background(
                                color = TextSecondary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            // Assignee
            item.assignee?.let { assignee ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = assignee,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = TextSecondary,
                    )
                }
            }

            // Due date
            item.dueDate?.let { dueDate ->
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = dueDate,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                        color = AccentAmber,
                    )
                }
            }

            // Source attribution — tap to jump to the originating recording at segment
            if (item.hasSource && item.recordingId != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SourceLinkRow(
                    item = item,
                    onClick = { onSourceClick(item.recordingId!!, item.segmentId) },
                )
            }
        }

        BriefingItemMenu(
            item = item,
            onShowQuote = onShowQuote,
            onAskAI = onAskAI,
        )
    }
}

@Composable
internal fun SourceLinkRow(
    item: ExtractedItem,
    onClick: () -> Unit,
) {
    val t = LocalStrings.current
    val label = run {
        val title = item.recordingTitle?.takeIf { it.isNotEmpty() } ?: t.briefingViewSource
        item.segmentStartTime?.let { sec ->
            val m = sec.toInt() / 60
            val s = sec.toInt() % 60
            "$title · %d:%02d".format(m, s)
        } ?: title
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            ),
            color = AccentGreen,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(12.dp),
        )
    }
}
