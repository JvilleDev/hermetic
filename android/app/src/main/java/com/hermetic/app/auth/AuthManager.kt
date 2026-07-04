package com.hermetic.app.auth

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.hermetic.app.data.remote.HermeticApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AuthState(
    val token: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val userId: String? = null,
)

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HermeticApi,
    private val gson: Gson,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hermetic_auth", Context.MODE_PRIVATE)

    var state: AuthState = loadState()
        private set

    val hostUrl: String
        get() = prefs.getString("host_url", null) ?: ""

    init {
        val saved = prefs.getString("host_url", null)
        if (!saved.isNullOrBlank()) {
            api.setBaseUrl(saved)
        }
        if (state.token != null) {
            api.setAuthToken(state.token!!)
        }
    }

    val isLoggedIn: Boolean
        get() = state.token != null

    val hasHost: Boolean
        get() = hostUrl.isNotBlank()

    private fun loadState(): AuthState {
        val json = prefs.getString("auth_state", null) ?: return AuthState()
        return try {
            gson.fromJson(json, AuthState::class.java)
        } catch (_: Exception) {
            AuthState()
        }
    }

    private fun saveState() {
        prefs.edit().putString("auth_state", gson.toJson(state)).apply()
    }

    fun setHost(url: String) {
        val clean = url.trimEnd('/')
        prefs.edit().putString("host_url", clean).apply()
        api.setBaseUrl(clean)
    }

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val response = api.login(email, password)
            state = AuthState(
                token = response["token"] as? String,
                userEmail = (response["user"] as? Map<*, *>)?.get("email") as? String,
                userName = (response["user"] as? Map<*, *>)?.get("name") as? String,
                userId = (response["user"] as? Map<*, *>)?.get("id") as? String,
            )
            saveState()
            api.setAuthToken(state.token ?: "")
            Result.success(state.token ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String, name: String = ""): Result<String> {
        return try {
            val response = api.register(email, password, name)
            state = AuthState(
                token = response["token"] as? String,
                userEmail = (response["user"] as? Map<*, *>)?.get("email") as? String,
                userName = (response["user"] as? Map<*, *>)?.get("name") as? String,
                userId = (response["user"] as? Map<*, *>)?.get("id") as? String,
            )
            saveState()
            api.setAuthToken(state.token ?: "")
            Result.success(state.token ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyToken(): Boolean {
        if (state.token == null) return false
        return if (api.verifyAuth()) {
            true
        } else {
            logout()
            false
        }
    }

    fun logout() {
        state = AuthState()
        prefs.edit().clear().apply()
        api.setAuthToken("")
    }

    suspend fun registerFcmToken(fcmToken: String) {
        if (state.token == null) return
        try {
            api.registerFcmToken(fcmToken)
        } catch (_: Exception) {}
    }
}