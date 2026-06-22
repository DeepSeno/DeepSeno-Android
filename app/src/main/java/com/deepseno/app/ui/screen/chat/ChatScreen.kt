package com.enmooy.deepseno.ui.screen.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enmooy.deepseno.i18n.LocalStrings
import com.enmooy.deepseno.ui.theme.*
import com.enmooy.deepseno.ui.viewmodel.AppState
import com.enmooy.deepseno.ui.viewmodel.ChatViewModel

@Composable
fun ChatScreen(
    appState: AppState = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
) {
    val t = LocalStrings.current

    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val sessions by chatViewModel.sessions.collectAsStateWithLifecycle()
    val currentSession by chatViewModel.currentSession.collectAsStateWithLifecycle()
    val inputText by chatViewModel.inputText.collectAsStateWithLifecycle()
    val isStreaming by chatViewModel.isStreaming.collectAsStateWithLifecycle()
    val showSessions by chatViewModel.showSessions.collectAsStateWithLifecycle()
    val errorMessage by chatViewModel.errorMessage.collectAsStateWithLifecycle()
    val isConnected by appState.connectionActive.collectAsStateWithLifecycle()

    val canSend = inputText.trim().isNotEmpty() && !isStreaming && isConnected

    // Load sessions on appear
    LaunchedEffect(isConnected) {
        if (isConnected) chatViewModel.loadSessions()
    }

    // Consume pending chat prompt set by Briefing's "Ask AI about this".
    val pendingPrompt by appState.pendingChatPrompt.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPrompt) {
        if (!pendingPrompt.isNullOrEmpty()) {
            chatViewModel.inputText.value = pendingPrompt!!
            appState.setPendingChatPrompt(null)
        }
    }

    // Error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Auto-scroll to bottom
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, messages.lastOrNull()?.content) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPrimary),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            HeaderBar(
                sessionTitle = currentSession?.title,
                onSessionsClick = { chatViewModel.showSessions.value = true },
            )

            // Content
            if (messages.isEmpty() && !isStreaming) {
                // Empty state
                EmptyState(
                    onSuggestionClick = { suggestion ->
                        chatViewModel.inputText.value = suggestion
                        chatViewModel.sendMessage(appState)
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }

            // Input bar
            InputBar(
                text = inputText,
                onTextChange = { chatViewModel.inputText.value = it },
                canSend = canSend,
                onSend = { chatViewModel.sendMessage(appState) },
            )
        }

        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Session list sheet
    if (showSessions) {
        SessionListSheet(
            sessions = sessions,
            currentSessionId = currentSession?.id,
            onNewSession = { chatViewModel.createSession() },
            onSelectSession = { id ->
                chatViewModel.switchSession(id)
                chatViewModel.showSessions.value = false
            },
            onDeleteSession = { id -> chatViewModel.deleteSession(id) },
            onDismiss = { chatViewModel.showSessions.value = false },
        )
    }
}

@Composable
private fun HeaderBar(
    sessionTitle: String?,
    onSessionsClick: () -> Unit,
) {
    val t = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // AI avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, BgTertiary, RoundedCornerShape(8.dp))
                .background(BgTertiary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(22.dp),
            )
        }

        // Title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = t.aiAssistant,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = TextPrimary,
            )
            if (sessionTitle != null) {
                Text(
                    text = sessionTitle,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }

        // Sessions button
        IconButton(onClick = onSessionsClick) {
            Icon(
                imageVector = Icons.Default.FormatListBulleted,
                contentDescription = t.sessions,
                tint = TextSecondary,
            )
        }
    }
}

@Composable
private fun EmptyState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalStrings.current
    val suggestions = listOf(
        t.suggestToday,
        t.suggestMeetings,
        t.suggestTasks,
        t.suggestPeople,
        t.suggestDecisions,
        t.suggestSearch,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = t.askAnything,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            color = TextSecondary,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Suggestion chips
        suggestions.forEach { suggestion ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgSecondary)
                    .border(1.dp, BgTertiary, RoundedCornerShape(8.dp))
                    .clickable { onSuggestionClick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = suggestion,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
) {
    val t = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSecondary)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    text = t.askPlaceholder,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    color = TextSecondary,
                )
            },
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = TextPrimary,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgTertiary,
                unfocusedContainerColor = BgTertiary,
                cursorColor = AccentGreen,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = false,
            maxLines = 4,
        )

        IconButton(
            onClick = onSend,
            enabled = canSend,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = t.send,
                tint = if (canSend) AccentGreen else TextSecondary.copy(alpha = 0.4f),
            )
        }
    }
}
