package com.hermetic.app.data.remote

import com.google.gson.Gson
import com.hermetic.app.data.model.Session
import com.hermetic.app.data.model.Project
import com.hermetic.app.data.model.Provider
import com.hermetic.app.data.model.ProviderWithModels
import com.hermetic.app.data.model.Message
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HermeticApi @Inject constructor(
    private val gson: Gson,
) {
    private var baseUrl: String = ""
    private var authToken: String = ""

    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val restClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun setAuthToken(token: String) {
        authToken = token
    }

    fun getBaseUrl(): String = baseUrl

    private fun Request.Builder.withAuth(): Request.Builder {
        if (authToken.isNotBlank()) {
            header("Authorization", authToken)
        }
        return this
    }

    suspend fun login(email: String, password: String): Map<String, Any> {
        val jsonBody = gson.toJson(mapOf("email" to email, "password" to password))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post(jsonBody)
            .build()
        val response = restClient.newCall(request).execute()
        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            @Suppress("UNCHECKED_CAST")
            return gson.fromJson(body, Map::class.java) as Map<String, Any>
        }
        throw Exception(body ?: "Error HTTP ${response.code}")
    }

    suspend fun register(email: String, password: String, name: String = ""): Map<String, Any> {
        val jsonBody = gson.toJson(mapOf("email" to email, "password" to password, "name" to name))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post(jsonBody)
            .build()
        val response = restClient.newCall(request).execute()
        val body = response.body?.string()
        if (response.isSuccessful && body != null) {
            @Suppress("UNCHECKED_CAST")
            return gson.fromJson(body, Map::class.java) as Map<String, Any>
        }
        throw Exception(body ?: "Error HTTP ${response.code}")
    }

    suspend fun registerFcmToken(fcmToken: String, jwt: String) {
        val jsonBody = gson.toJson(mapOf("token" to fcmToken, "platform" to "android"))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/api/push/register-token")
            .post(jsonBody)
            .header("Authorization", jwt)
            .build()
        restClient.newCall(request).execute()
    }

    suspend fun healthCheck(): Result<Boolean> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun streamChat(
        sessionId: String,
        message: String,
        projectId: String?,
        projectName: String?,
        parentDirectory: String?,
        providerId: String? = null,
        model: String? = null,
        onSession: (String) -> Unit,
        onProvider: (String, String) -> Unit,
        onThinking: (String) -> Unit,
        onToken: (String) -> Unit,
        onToolStart: (String, Map<String, Any>) -> Unit,
        onToolResult: (String, String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ): EventSource {
        val body = mapOf(
            "session_id" to sessionId,
            "message" to message,
            "project_id" to projectId,
            "project_name" to projectName,
            "parent_directory" to parentDirectory,
            "provider_id" to providerId,
            "model" to model,
        )
        val jsonBody = gson.toJson(body)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/chat/stream")
            .post(jsonBody)
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                when (type) {
                    "session" -> {
                        try {
                            val d = gson.fromJson(data, Map::class.java)
                            onSession(d["session_id"] as? String ?: "")
                        } catch (_: Exception) {}
                    }
                    "provider" -> {
                        try {
                            val d = gson.fromJson(data, Map::class.java)
                            onProvider(d["model"] as? String ?: "", d["provider"] as? String ?: "")
                        } catch (_: Exception) {}
                    }
                    "thinking" -> {
                        try {
                            val d = gson.fromJson(data, Map::class.java)
                            onThinking(d["text"] as? String ?: "")
                        } catch (_: Exception) {}
                    }
                    "token" -> {
                        try {
                            val token = gson.fromJson(data, Map::class.java)
                            onToken(token["text"] as? String ?: "")
                        } catch (_: Exception) {
                            onToken(data)
                        }
                    }
                    "tool_start" -> {
                        val toolData = gson.fromJson(data, Map::class.java)
                        @Suppress("UNCHECKED_CAST")
                        val args = toolData["args"] as? Map<String, Any> ?: emptyMap()
                        onToolStart(toolData["tool"] as? String ?: "", args)
                    }
                    "tool_result" -> {
                        val toolData = gson.fromJson(data, Map::class.java)
                        onToolResult(
                            toolData["tool"] as? String ?: "",
                            toolData["result"] as? String ?: "",
                        )
                    }
                    "error" -> {
                        try {
                            val d = gson.fromJson(data, Map::class.java)
                            onError(d["message"] as? String ?: "Error desconocido")
                        } catch (_: Exception) {
                            onError(data)
                        }
                    }
                    "done" -> onDone()
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?,
            ) {
                val msg = when {
                    t != null -> "${t.message}"
                    response != null -> "HTTP ${response.code}"
                    else -> "Connection failed"
                }
                onError(msg)
            }
        }

        return EventSources.createFactory(sseClient)
            .newEventSource(request, listener)
    }

    suspend fun getDirectoryTree(
        path: String,
        depth: Int = 2,
        dirsOnly: Boolean = true,
    ): Result<Map<String, Any>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/directory/tree?path=$path&depth=$depth&dirs_only=$dirsOnly")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                @Suppress("UNCHECKED_CAST")
                Result.success(gson.fromJson(body, Map::class.java) as Map<String, Any>)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSessions(): Result<List<Session>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/sessions")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<Session>>() {}.type
                Result.success(gson.fromJson(body, type))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProjects(): Result<List<Project>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/projects")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<Project>>() {}.type
                Result.success(gson.fromJson(body, type))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProject(projectId: String, data: Map<String, Any?>): Result<Boolean> {
        return try {
            val jsonBody = gson.toJson(data)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/projects/$projectId")
                .patch(jsonBody)
                .build()
            val response = restClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProviders(): Result<List<Provider>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/providers")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<Provider>>() {}.type
                Result.success(gson.fromJson(body, type))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProvidersWithModels(): Result<List<ProviderWithModels>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/providers/with-models")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<ProviderWithModels>>() {}.type
                Result.success(gson.fromJson(body, type))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createProvider(data: Map<String, Any>): Result<Boolean> {
        return try {
            val jsonBody = gson.toJson(data)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/providers")
                .post(jsonBody)
                .build()
            val response = restClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProvider(providerId: String, data: Map<String, Any>): Result<Boolean> {
        return try {
            val jsonBody = gson.toJson(data)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/providers/$providerId")
                .patch(jsonBody)
                .build()
            val response = restClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProvider(providerId: String): Result<Boolean> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/providers/$providerId")
                .delete()
                .build()
            val response = restClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(sessionId: String): Result<List<Message>> {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/sessions/$sessionId/messages")
                .get()
                .build()
            val response = restClient.newCall(request).execute()
            val body = response.body?.string()
            if (response.isSuccessful && body != null) {
                val type = object : com.google.gson.reflect.TypeToken<List<Message>>() {}.type
                Result.success(gson.fromJson(body, type))
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
