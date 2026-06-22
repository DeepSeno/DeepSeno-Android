package com.enmooy.deepseno.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enmooy.deepseno.data.remote.model.ChatSession
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListSheet(
    sessions: List<ChatSession>,
    currentSessionId: Int?,
    onNewSession: () -> Unit,
    onSelectSession: (Int) -> Unit,
    onDeleteSession: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalStrings.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgSecondary,
        contentColor = TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            // Top bar: Sessions title + Done button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t.sessions,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary,
                )
                TextButton(onClick = onDismiss) {
                    Text(
                        text = t.done,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = AccentGreen,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // New Session button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNewSession() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = t.newSession,
                    tint = AccentGreen,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = t.newSession,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = AccentGreen,
                )
            }

            HorizontalDivider(color = BgTertiary, thickness = 1.dp)

            // Session list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isCurrent = session.id == currentSessionId

                    SwipeToDismissBox(
                        state = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    onDeleteSession(session.id)
                                    true
                                } else false
                            },
                        ),
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AccentRed)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isCurrent) BgTertiary else BgSecondary)
                                .clickable { onSelectSession(session.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = session.title,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = relativeDate(session.createdAt),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                ),
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun relativeDate(dateStr: String): String {
    return try {
        val instant = Instant.parse(dateStr)
        val now = Instant.now()
        val days = ChronoUnit.DAYS.between(instant, now)
        when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7L -> "${days}d ago"
            else -> {
                val ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                ldt.format(DateTimeFormatter.ofPattern("MMM d"))
            }
        }
    } catch (_: Exception) {
        dateStr.take(10)
    }
}
