package com.example.localvocabulary.settings

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val defaultLanguageTag: String = "en",
    val userLanguageTags: Set<String> = emptySet(),
    val reviewLimits: com.example.localvocabulary.review.domain.ReviewLimits =
        com.example.localvocabulary.review.domain.ReviewLimits(),
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultLanguageTag(languageTag: String)
    suspend fun addUserLanguageTag(languageTag: String)
    suspend fun setReviewLimits(limits: com.example.localvocabulary.review.domain.ReviewLimits)
}
