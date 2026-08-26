package com.example.localvocabulary.settings

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val defaultLanguageTag: String = "en",
    val userLanguageTags: Set<String> = emptySet(),
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultLanguageTag(languageTag: String)
    suspend fun addUserLanguageTag(languageTag: String)
}
