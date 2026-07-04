package com.hermetic.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.hermetic.app.auth.AuthManager
import com.hermetic.app.di.HiltApiEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class HermeticApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        FirebaseApp.initializeApp(this)

        // Register existing FCM token for logged-in users
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token
                val entryPoint = EntryPointAccessors.fromApplication(
                    this@HermeticApp,
                    HiltApiEntryPoint::class.java
                )
                val authManager = entryPoint.getAuthManager()
                if (authManager.isLoggedIn) {
                    authManager.registerFcmToken(token)
                }
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "hermetic_tasks",
                "Tareas completadas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando una tarea termina"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
