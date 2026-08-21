package com.example.localvocabulary.settings

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val defaultLanguageTag: String = "en",
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultLanguageTag(languageTag: String)
}
