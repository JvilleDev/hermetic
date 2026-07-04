package com.hermetic.app.data.repository

import com.hermetic.app.data.model.SSEEvent
import com.hermetic.app.data.remote.HermeticApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.sse.EventSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val api: HermeticApi,
) {
    private var currentEventSource: EventSource? = null

    fun streamChat(
        sessionId: String,
        message: String,
        projectId: String?,
        projectName: String?,
        parentDirectory: String?,
    ): Flow<SSEEvent> {
        cancelStream()
        val channel = Channel<SSEEvent>(Channel.BUFFERED)

        currentEventSource = api.streamChat(
            sessionId = sessionId,
            message = message,
            projectId = projectId,
            projectName = projectName,
            parentDirectory = parentDirectory,
            onSession = { id -> channel.trySend(SSEEvent.Session(id)) },
            onProvider = { model, provider -> channel.trySend(SSEEvent.Provider(model, provider)) },
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
}
