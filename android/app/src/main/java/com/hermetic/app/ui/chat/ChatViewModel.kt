package com.hermetic.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermetic.app.data.model.ChatMessage
import com.hermetic.app.data.model.MessageRole
import com.hermetic.app.data.model.SSEEvent
import com.hermetic.app.data.model.ToolCallState
import com.hermetic.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val inputText: String = "",
    val sessionId: String = "new",
    val currentModel: String? = null,
    val currentProvider: String? = null,
    val thinkingText: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun sendMessage(
        projectId: String?,
        projectName: String?,
        parentDirectory: String?,
    ) {
        val message = _uiState.value.inputText.trim()
        if (message.isEmpty()) return

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            content = message,
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMsg,
            inputText = "",
            isStreaming = true,
            error = null,
            thinkingText = null,
        )

        val assistantMsg = ChatMessage(
            role = MessageRole.ASSISTANT,
            content = "",
            toolCalls = emptyList(),
        )

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + assistantMsg,
        )

        val currentSessionId = _uiState.value.sessionId

        viewModelScope.launch {
            try {
                chatRepository.streamChat(
                    sessionId = currentSessionId,
                    message = message,
                    projectId = projectId,
                    projectName = projectName,
                    parentDirectory = parentDirectory,
                )
                    .catch { e ->
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            error = "Error de conexión: ${e.message}",
                        )
                    }
                    .collect { event ->
                        val msgs = _uiState.value.messages.toMutableList()
                        val last = msgs.lastOrNull() ?: return@collect

                        when (event) {
                            is SSEEvent.Session -> {
                                _uiState.value = _uiState.value.copy(sessionId = event.sessionId)
                            }
                            is SSEEvent.Provider -> {
                                _uiState.value = _uiState.value.copy(
                                    currentModel = event.model,
                                    currentProvider = event.provider,
                                )
                            }
                            is SSEEvent.Thinking -> {
                                val prev = _uiState.value.thinkingText ?: ""
                                _uiState.value = _uiState.value.copy(
                                    thinkingText = prev + event.text,
                                )
                            }
                            is SSEEvent.Token -> {
                                msgs[msgs.lastIndex] = last.copy(
                                    content = (last.content ?: "") + event.text,
                                )
                                _uiState.value = _uiState.value.copy(messages = msgs)
                            }
                            is SSEEvent.ToolStart -> {
                                val toolCall = ToolCallState(
                                    tool = event.tool,
                                    args = event.args,
                                )
                                val existing = last.toolCalls?.toMutableList() ?: mutableListOf()
                                existing.add(toolCall)
                                msgs[msgs.lastIndex] = last.copy(toolCalls = existing)
                                _uiState.value = _uiState.value.copy(messages = msgs)
                            }
                            is SSEEvent.ToolResult -> {
                                val existing = last.toolCalls?.toMutableList() ?: return@collect
                                val idx = existing.indexOfLast { it.tool == event.tool && it.isRunning }
                                if (idx >= 0) {
                                    existing[idx] = existing[idx].copy(
                                        result = event.result,
                                        isRunning = false,
                                    )
                                }
                                msgs[msgs.lastIndex] = last.copy(toolCalls = existing)
                                _uiState.value = _uiState.value.copy(messages = msgs)
                            }
                            is SSEEvent.Done -> {
                                _uiState.value = _uiState.value.copy(
                                    isStreaming = false,
                                    thinkingText = null,
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isStreaming = false,
                    error = "Error: ${e.message}",
                )
            }
        }
    }

    fun cancelStream() {
        chatRepository.cancelStream()
        _uiState.value = _uiState.value.copy(isStreaming = false)
    }
}
