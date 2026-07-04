package com.hermetic.app.data.repository

import com.hermetic.app.data.model.ProviderWithModels
import com.hermetic.app.data.model.Session
import com.hermetic.app.data.model.SSEEvent
import com.hermetic.app.data.remote.HermeticApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.sse.EventSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: HermeticApi,
) {
    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeModel = MutableStateFlow<String>("Cargando...")
    val activeModel: StateFlow<String> = _activeModel.asStateFlow()

    private val _providers = MutableStateFlow<List<ProviderWithModels>>(emptyList())
    val providers: StateFlow<List<ProviderWithModels>> = _providers.asStateFlow()

    private var currentEventSource: EventSource? = null

    suspend fun refreshActiveModel() {
        try {
            api.getProvidersWithModels().onSuccess { list ->
                _providers.value = list
                val defaultProvider = list.find { it.isDefault } ?: list.firstOrNull { it.isActive }
                defaultProvider?.let {
                    _activeModel.value = it.defaultModel
                }
            }
        } catch (_: Exception) {}
    }

    fun streamChat(
        sessionId: String,
        message: String,
        projectId: String?,
        projectName: String?,
        parentDirectory: String?,
        providerId: String? = null,
        model: String? = null,
    ): Flow<SSEEvent> {
        cancelStream()
        val channel = Channel<SSEEvent>(Channel.BUFFERED)

        currentEventSource = api.streamChat(
            sessionId = sessionId,
            message = message,
            projectId = projectId,
            projectName = projectName,
            parentDirectory = parentDirectory,
            providerId = providerId,
            model = model,
            onSession = { id -> channel.trySend(SSEEvent.Session(id)) },
            onProvider = { m, provider ->
                _activeModel.value = m
                channel.trySend(SSEEvent.Provider(m, provider))
            },
            onThinking = { text -> channel.trySend(SSEEvent.Thinking(text)) },
            onToken = { text -> channel.trySend(SSEEvent.Token(text)) },
            onToolStart = { tool, args -> channel.trySend(SSEEvent.ToolStart(tool, args)) },
            onToolResult = { tool, result -> channel.trySend(SSEEvent.ToolResult(tool, result)) },
            onDone = { channel.trySend(SSEEvent.Done); channel.close() },
            onError = { msg -> channel.close(Exception(msg)) },
        )

        return channel.receiveAsFlow()
    }

    fun cancelStream() {
        currentEventSource?.cancel()
        currentEventSource = null
    }

    suspend fun refreshSessions(): Result<List<Session>> {
        val result = api.getSessions()
        result.onSuccess {
            _sessions.value = it
        }
        return result
    }
}
