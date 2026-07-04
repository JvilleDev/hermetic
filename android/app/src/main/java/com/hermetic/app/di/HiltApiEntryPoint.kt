package com.hermetic.app.di

import com.hermetic.app.auth.AuthManager
import com.hermetic.app.data.remote.HermeticApi
import com.hermetic.app.data.repository.ChatRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HiltApiEntryPoint {
    fun getHermeticApi(): HermeticApi
    fun getChatRepository(): ChatRepository
    fun getAuthManager(): AuthManager
}
