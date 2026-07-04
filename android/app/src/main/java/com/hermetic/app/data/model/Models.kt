package com.hermetic.app.data.model

data class Project(
    val id: String,
    val name: String,
    val parentDirectory: String,
    val providerId: String? = null,
)

data class Session(
    val id: String,
    val title: String,
    val projectId: String? = null,
    val contextSummary: String? = null,
    val createdAt: String? = null,
)

data class Message(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val attachments: List<String>? = null,
    val createdAt: String? = null,
)

enum class MessageRole { USER, ASSISTANT, TOOL }

data class Provider(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val apiKey: String,
    val baseUrl: String? = null,
    val defaultModel: String,
    val isActive: Boolean = true,
    val isDefault: Boolean = false,
    val maxContextTokens: Int? = null,
)

enum class ProviderType { GEMINI, OPENAI, ANTHROPIC }

data class MCPServer(
    val id: String,
    val name: String,
    val description: String? = null,
    val transport: MCPTransport,
    val command: String? = null,
    val args: List<String>? = null,
    val url: String? = null,
    val env: Map<String, String>? = null,
    val isActive: Boolean = true,
)

enum class MCPTransport { STDIO, SSE }

data class MCPTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any>? = null,
    val mcpServerId: String? = null,
    val mcpServerName: String? = null,
)

data class Skill(
    val id: String,
    val name: String,
    val description: String? = null,
    val skillType: SkillType,
    val source: SkillSource? = null,
    val systemPromptAdditions: String? = null,
    val toolDefs: List<Map<String, Any>>? = null,
    val toolCode: String? = null,
    val mcpServerId: String? = null,
    val isActive: Boolean = true,
)

enum class SkillType { NATIVE, MCP, PYTHON, PROMPT }
enum class SkillSource { GENERATED, LLAMA_HUB, LANGCHAIN_HUB, CUSTOM }

sealed class SSEEvent {
    data class Session(val sessionId: String) : SSEEvent()
    data class Provider(val model: String, val provider: String) : SSEEvent()
    data class Thinking(val text: String) : SSEEvent()
    data class Token(val text: String) : SSEEvent()
    data class ToolStart(val tool: String, val args: Map<String, Any>) : SSEEvent()
    data class ToolResult(val tool: String, val result: String) : SSEEvent()
    data object Done : SSEEvent()
}

data class ToolCallState(
    val tool: String,
    val args: Map<String, Any>,
    val result: String? = null,
    val isRunning: Boolean = true,
)

data class ChatMessage(
    val role: MessageRole,
    val content: String?,
    val toolCalls: List<ToolCallState>? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
