package com.hermetic.app.data.model

import com.google.gson.annotations.SerializedName

data class Project(
    val id: String,
    val name: String,
    @SerializedName("parent_directory") val parentDirectory: String,
    @SerializedName("provider_id") val providerId: String? = null,
)

data class Session(
    val id: String,
    val title: String,
    @SerializedName("project_id") val projectId: String? = null,
    @SerializedName("context_summary") val contextSummary: String? = null,
    @SerializedName("created") val createdAt: String? = null,
)

data class ToolCall(
    val id: String,
    val tool: String,
    val args: Map<String, Any>,
    val result: String? = null,
    @SerializedName("isRunning") val isRunning: Boolean = false,
)

data class Message(
    val id: String,
    @SerializedName("session_id") val sessionId: String,
    val role: MessageRole,
    val content: String,
    val attachments: List<String>? = null,
    @SerializedName("created") val createdAt: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

enum class MessageRole {
    @SerializedName("user") USER,
    @SerializedName("assistant") ASSISTANT,
    @SerializedName("tool") TOOL,
}

data class Provider(
    val id: String,
    val name: String,
    @SerializedName("provider_type") val providerType: ProviderType,
    @SerializedName("api_key") val apiKey: String,
    @SerializedName("base_url") val baseUrl: String? = null,
    @SerializedName("default_model") val defaultModel: String,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("is_default") val isDefault: Boolean = false,
    @SerializedName("max_context_tokens") val maxContextTokens: Int? = null,
)

enum class ProviderType {
    @SerializedName("gemini") GEMINI,
    @SerializedName("openai") OPENAI,
    @SerializedName("anthropic") ANTHROPIC,
}

data class ProviderWithModels(
    val id: String,
    val name: String,
    @SerializedName("provider_type") val providerType: String,
    @SerializedName("base_url") val baseUrl: String? = null,
    @SerializedName("default_model") val defaultModel: String,
    @SerializedName("is_default") val isDefault: Boolean = false,
    @SerializedName("is_active") val isActive: Boolean = true,
    val models: List<String> = emptyList(),
)

data class MCPServer(
    val id: String,
    val name: String,
    val description: String? = null,
    val transport: MCPTransport,
    val command: String? = null,
    val args: List<String>? = null,
    val url: String? = null,
    val env: Map<String, String>? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
)

enum class MCPTransport {
    @SerializedName("stdio") STDIO,
    @SerializedName("sse") SSE,
}

data class MCPTool(
    val name: String,
    val description: String? = null,
    val inputSchema: Map<String, Any>? = null,
    @SerializedName("mcp_server_id") val mcpServerId: String? = null,
    @SerializedName("mcp_server_name") val mcpServerName: String? = null,
)

data class Skill(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerializedName("skill_type") val skillType: SkillType,
    val source: SkillSource? = null,
    @SerializedName("system_prompt_additions") val systemPromptAdditions: String? = null,
    @SerializedName("tool_defs") val toolDefs: List<Map<String, Any>>? = null,
    @SerializedName("tool_code") val toolCode: String? = null,
    @SerializedName("mcp_server_id") val mcpServerId: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true,
)

enum class SkillType {
    @SerializedName("native") NATIVE,
    @SerializedName("mcp") MCP,
    @SerializedName("python") PYTHON,
    @SerializedName("prompt") PROMPT,
}

enum class SkillSource {
    @SerializedName("generated") GENERATED,
    @SerializedName("llama-hub") LLAMA_HUB,
    @SerializedName("langchain-hub") LANGCHAIN_HUB,
    @SerializedName("custom") CUSTOM,
}

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
    val durationSeconds: Double? = null,
    val model: String? = null,
    val thinking: String? = null,
)
