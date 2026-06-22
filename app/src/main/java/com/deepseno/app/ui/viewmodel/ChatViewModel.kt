package com.enmooy.deepseno.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enmooy.deepseno.data.remote.model.ChatMessage
import com.enmooy.deepseno.data.remote.model.ChatSession
import com.enmooy.deepseno.data.remote.model.QueryRequest
import com.enmooy.deepseno.data.remote.model.Source
import com.enmooy.deepseno.service.ApiClient
import com.enmooy.deepseno.service.SseClient
import com.enmooy.deepseno.ui.util.Haptics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

data class DisplayMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val sources: List<Source> = emptyList(),
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromChatMessage(msg: ChatMessage): DisplayMessage {
            val sources = msg.sourcesJson?.let {
                try {
                    json.decodeFromString<List<Source>>(it)
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()
            return DisplayMessage(
                id = msg.id.toString(),
                role = msg.role,
                content = msg.content,
                isStreaming = false,
                sources = sources,
            )
        }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val apiClient: ApiClient,
    private val sseClient: SseClient,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<DisplayMessage>>(emptyList())
    val messages: StateFlow<List<DisplayMessage>> = _messages

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession

    val inputText = MutableStateFlow("")

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    val showSessions = MutableStateFlow(false)

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun loadSessions() {
        viewModelScope.launch {
            try {
                val api = apiClient.api ?: return@launch
                val result = api.getChatSessions()
                _sessions.value = result
                if (_currentSession.value == null && result.isNotEmpty()) {
                    _currentSession.value = result.first()
                    loadMessages()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun createSession() {
        viewModelScope.launch {
            try {
                val api = apiClient.api ?: return@launch
                val session = api.createChatSession()
                _sessions.value = listOf(session) + _sessions.value
                _currentSession.value = session
                _messages.value = emptyList()
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun switchSession(id: Int) {
        val session = _sessions.value.find { it.id == id } ?: return
        _currentSession.value = session
        loadMessages()
    }

    fun deleteSession(id: Int) {
        val updated = _sessions.value.filter { it.id != id }
        _sessions.value = updated
        if (_currentSession.value?.id == id) {
            _currentSession.value = updated.firstOrNull()
            if (_currentSession.value != null) {
                loadMessages()
            } else {
                _messages.value = emptyList()
            }
        }
    }

    fun loadMessages() {
        val sessionId = _currentSession.value?.id ?: return
        viewModelScope.launch {
            try {
                val api = apiClient.api ?: return@launch
                val result = api.getSessionMessages(sessionId)
                _messages.value = result.map { DisplayMessage.fromChatMessage(it) }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun sendMessage(appState: AppState) {
        val text = inputText.value.trim()
        if (text.isEmpty() || _isStreaming.value) return

        val isRelay = appState.relayTransportMode.value != "none"
        if (!isRelay) {
            if (appState.connectionHost.value == null || appState.connectionPort.value == null) return
        }

        Haptics.light(appContext)
        inputText.value = ""

        val userMsg = DisplayMessage(role = "user", content = text)
        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            val api = apiClient.api ?: return@launch
            try {
                if (_currentSession.value == null) {
                    val session = api.createChatSession()
                    _sessions.value = listOf(session) + _sessions.value
                    _currentSession.value = session
                }

                val sessionId = _currentSession.value?.id
                val assistantId = UUID.randomUUID().toString()
                val assistantMsg = DisplayMessage(id = assistantId, role = "assistant", content = "", isStreaming = true)
                _messages.value = _messages.value + assistantMsg
                _isStreaming.value = true

                // Check relay mode inside coroutine — pairing may complete asynchronously
                val useRelay = appState.relayTransportMode.value != "none"
                if (useRelay) {
                    val response = api.query(com.enmooy.deepseno.data.remote.model.QueryRequest(text))
                    val answer = response.answer ?: "No response"
                    updateMessage(assistantId, answer)
                } else {
                    // LAN mode: use SSE streaming
                    val host = appState.connectionHost.value ?: return@launch
                    val port = appState.connectionPort.value ?: return@launch
                    val token = appState.connectionToken.value ?: return@launch
                    sseClient.queryStream(
                        host, port, token, text, sessionId,
                        onChunk = { chunk -> updateMessage(assistantId, _messages.value.findLast { it.id == assistantId }?.content + chunk) },
                        onStatus = { /* ignore */ },
                    )
                }
                _isStreaming.value = false
            } catch (e: Exception) {
                _isStreaming.value = false
                _errorMessage.value = e.message
            }
        }
    }

    private fun updateMessage(id: String, content: String) {
        val list = _messages.value
        val idx = list.indexOfLast { it.id == id }
        if (idx >= 0) {
            val updated = list.toMutableList()
            updated[idx] = updated[idx].copy(content = content, isStreaming = false)
            _messages.value = updated
        }
    }
}

// @Serializable data classes for chat (inline)
