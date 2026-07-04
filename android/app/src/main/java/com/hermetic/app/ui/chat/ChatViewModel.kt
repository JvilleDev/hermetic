package com.hermetic.app.ui.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermetic.app.data.model.ChatMessage
import com.hermetic.app.data.model.MessageRole
import com.hermetic.app.data.model.SSEEvent
import com.hermetic.app.data.model.ToolCallState
import com.hermetic.app.data.remote.HermeticApi
import com.hermetic.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val isLoading: Boolean = false,
    val inputText: String = "",
    val sessionId: String = "new",
    val currentModel: String? = null,
    val currentProvider: String? = null,
    val thinkingText: String? = null,
    val error: String? = null,
    val parentDirectory: String? = null,
    val selectedProviderId: String? = null,
    val selectedModel: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val api: HermeticApi,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val activeModel = chatRepository.activeModel
    val providers = chatRepository.providers

    fun selectProvider(providerId: String, model: String) {
        _uiState.value = _uiState.value.copy(
            selectedProviderId = providerId,
            selectedModel = model,
        )
    }

    init {
        Log.d("HermeticChat", "ChatViewModel inicializado")
        viewModelScope.launch {
            savedStateHandle.getStateFlow("sessionId", "new").collect { newSessionId ->
                val parentDir = savedStateHandle.get<String>("parentDirectory")
                Log.d("HermeticChat", "Navegación detectada: sessionId=$newSessionId, parentDir=$parentDir")
                _uiState.value = _uiState.value.copy(
                    sessionId = newSessionId,
                    parentDirectory = parentDir,
                    messages = emptyList(),
                    error = null
                )
                if (newSessionId != "new") {
                    loadSessionMessages(newSessionId)
                } else {
                    Log.d("HermeticChat", "Abriendo nuevo chat vacío")
                }
            }
        }
    }

    private fun loadSessionMessages(sessionId: String) {
        Log.d("HermeticChat", "Iniciando carga de mensajes para sesión: $sessionId")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.IO) {
                api.getMessages(sessionId)
            }
            result.onSuccess { msgs ->
                Log.d("HermeticChat", "Mensajes cargados con éxito de la API: ${msgs.size} mensajes")
                val chatMsgs = msgs.map { msg ->
                    Log.d("HermeticChat", "Mapeando mensaje [${msg.id}] - Rol: ${msg.role}, Contenido: ${msg.content?.take(30) ?: ""}...")
                    ChatMessage(
                        role = msg.role,
                        content = msg.content,
                        toolCalls = msg.toolCalls?.map { tc ->
                            ToolCallState(
                                tool = tc.tool,
                                args = tc.args,
                                result = tc.result,
                                isRunning = tc.isRunning
                            )
                        },
                        timestamp = System.currentTimeMillis()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    messages = chatMsgs,
                    isLoading = false
                )
                Log.d("HermeticChat", "Estado de UI actualizado con los mensajes mapeados")
            }
            result.onFailure { e ->
                Log.e("HermeticChat", "Error al obtener mensajes de la sesión $sessionId", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar mensajes: ${e.message}"
                )
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun sendMessage(
        projectId: String? = null,
        projectName: String? = null,
        parentDirectory: String? = null,
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
        val resolvedParentDir = parentDirectory ?: savedStateHandle.get<String>("parentDirectory")
        val resolvedProjId = projectId ?: savedStateHandle.get<String>("projectId")
        val resolvedProjName = projectName ?: savedStateHandle.get<String>("projectName")

        viewModelScope.launch {
            try {
                chatRepository.streamChat(
                    sessionId = currentSessionId,
                    message = message,
                    projectId = resolvedProjId,
                    projectName = resolvedProjName,
                    parentDirectory = resolvedParentDir,
                    providerId = _uiState.value.selectedProviderId,
                    model = _uiState.value.selectedModel,
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
                                val lastAssistantIdx = msgs.indexOfLast { it.role == MessageRole.ASSISTANT }
                                if (lastAssistantIdx >= 0) {
                                    msgs[lastAssistantIdx] = msgs[lastAssistantIdx].copy(model = event.model)
                                }
                                _uiState.value = _uiState.value.copy(
                                    currentModel = event.model,
                                    currentProvider = event.provider,
                                    messages = msgs,
                                )
                            }
                            is SSEEvent.Thinking -> {
                                val lastAssistantIdx = msgs.indexOfLast { it.role == MessageRole.ASSISTANT }
                                if (lastAssistantIdx >= 0) {
                                    val lastMsg = msgs[lastAssistantIdx]
                                    val prevThinking = lastMsg.thinking ?: ""
                                    msgs[lastAssistantIdx] = lastMsg.copy(thinking = prevThinking + event.text)
                                } else {
                                    val newMsg = ChatMessage(
                                        role = MessageRole.ASSISTANT,
                                        content = "",
                                        toolCalls = emptyList(),
                                        thinking = event.text
                                    )
                                    msgs.add(newMsg)
                                }
                                val prev = _uiState.value.thinkingText ?: ""
                                _uiState.value = _uiState.value.copy(
                                    thinkingText = prev + event.text,
                                    messages = msgs,
                                )
                            }
                            is SSEEvent.Token -> {
                                if (last.role == MessageRole.ASSISTANT && last.toolCalls.isNullOrEmpty()) {
                                    msgs[msgs.lastIndex] = last.copy(
                                        content = (last.content ?: "") + event.text,
                                    )
                                } else {
                                    val newMsg = ChatMessage(
                                        role = MessageRole.ASSISTANT,
                                        content = event.text,
                                        toolCalls = emptyList()
                                    )
                                    msgs.add(newMsg)
                                }
                                _uiState.value = _uiState.value.copy(messages = msgs)
                            }
                            is SSEEvent.ToolStart -> {
                                val toolCall = ToolCallState(
                                    tool = event.tool,
                                    args = event.args,
                                )
                                if (last.role == MessageRole.ASSISTANT) {
                                    val existing = last.toolCalls?.toMutableList() ?: mutableListOf()
                                    existing.add(toolCall)
                                    msgs[msgs.lastIndex] = last.copy(toolCalls = existing)
                                } else {
                                    val newMsg = ChatMessage(
                                        role = MessageRole.ASSISTANT,
                                        content = "",
                                        toolCalls = listOf(toolCall)
                                    )
                                    msgs.add(newMsg)
                                }
                                _uiState.value = _uiState.value.copy(messages = msgs)
                            }
                            is SSEEvent.ToolResult -> {
                                val lastAssistantIdx = msgs.indexOfLast { it.role == MessageRole.ASSISTANT }
                                if (lastAssistantIdx >= 0) {
                                    val assistantMsg = msgs[lastAssistantIdx]
                                    val existing = assistantMsg.toolCalls?.toMutableList() ?: mutableListOf()
                                    val idx = existing.indexOfLast { it.tool == event.tool && it.isRunning }
                                    if (idx >= 0) {
                                        existing[idx] = existing[idx].copy(
                                            result = event.result,
                                            isRunning = false,
                                        )
                                        msgs[lastAssistantIdx] = assistantMsg.copy(toolCalls = existing)
                                        _uiState.value = _uiState.value.copy(messages = msgs)
                                    }
                                }
                            }
                            is SSEEvent.Done -> {
                                val lastAssistantIdx = msgs.indexOfLast { it.role == MessageRole.ASSISTANT }
                                if (lastAssistantIdx >= 0) {
                                    val assistantMsg = msgs[lastAssistantIdx]
                                    val elapsedMs = System.currentTimeMillis() - assistantMsg.timestamp
                                    val seconds = elapsedMs / 1000.0
                                    val rounded = Math.round(seconds * 10.0) / 10.0
                                    msgs[lastAssistantIdx] = assistantMsg.copy(durationSeconds = rounded)
                                }
                                _uiState.value = _uiState.value.copy(
                                    isStreaming = false,
                                    thinkingText = null,
                                    messages = msgs,
                                )
                                viewModelScope.launch(Dispatchers.IO) {
                                    chatRepository.refreshSessions()
                                    // Wait a bit for the backend to finish generating the automatic title, then refresh again
                                    kotlinx.coroutines.delay(1500)
                                    chatRepository.refreshSessions()
                                }
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
