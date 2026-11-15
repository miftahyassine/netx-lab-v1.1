package com.netxlab.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme { DARK, GREY, WHITE }

data class AppSettings(
    val theme: AppTheme = AppTheme.DARK,
    val infoIcons: Boolean = true,
    val haptics: Boolean = true,
    val sound: Boolean = false,
    val timeoutMs: Int = 1500,
    val developer: Boolean = false,
    val externalConsent: Boolean = false,
    val completedLessons: Set<String> = emptySet(),
    val activeProject: Long = -1L
)

private val Context.store by preferencesDataStore("netxlab_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val kTheme = stringPreferencesKey("theme")
    private val kInfo = booleanPreferencesKey("info_icons")
    private val kHaptics = booleanPreferencesKey("haptics")
    private val kSound = booleanPreferencesKey("sound")
    private val kTimeout = intPreferencesKey("timeout")
    private val kDev = booleanPreferencesKey("developer")
    private val kConsent = booleanPreferencesKey("external_consent")
    private val kLessons = stringSetPreferencesKey("lessons")
    private val kProject = longPreferencesKey("active_project")

    val flow: Flow<AppSettings> = ctx.store.data.map { p ->
        AppSettings(
            theme = runCatching { AppTheme.valueOf(p[kTheme] ?: "DARK") }.getOrDefault(AppTheme.DARK),
            infoIcons = p[kInfo] ?: true,
            haptics = p[kHaptics] ?: true,
            sound = p[kSound] ?: false,
            timeoutMs = p[kTimeout] ?: 1500,
            developer = p[kDev] ?: false,
            externalConsent = p[kConsent] ?: false,
            completedLessons = p[kLessons] ?: emptySet(),
            activeProject = p[kProject] ?: -1L
        )
    }

    suspend fun setTheme(t: AppTheme) = ctx.store.edit { it[kTheme] = t.name }
    suspend fun setInfoIcons(v: Boolean) = ctx.store.edit { it[kInfo] = v }
    suspend fun setHaptics(v: Boolean) = ctx.store.edit { it[kHaptics] = v }
    suspend fun setSound(v: Boolean) = ctx.store.edit { it[kSound] = v }
    suspend fun setTimeout(v: Int) = ctx.store.edit { it[kTimeout] = v }
    suspend fun setDeveloper(v: Boolean) = ctx.store.edit { it[kDev] = v }
    suspend fun setConsent(v: Boolean) = ctx.store.edit { it[kConsent] = v }
    suspend fun completeLesson(id: String) = ctx.store.edit { it[kLessons] = (it[kLessons] ?: emptySet()) + id }
    suspend fun resetLessons() = ctx.store.edit { it.remove(kLessons) }
    suspend fun setActiveProject(id: Long) = ctx.store.edit { it[kProject] = id }
}
