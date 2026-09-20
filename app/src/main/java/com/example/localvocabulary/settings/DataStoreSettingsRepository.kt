package com.example.localvocabulary.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.localvocabulary.review.domain.ReviewLimits
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localvocabulary.core.model.AppLanguageCatalog
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        val totalLimit = (preferences[REVIEW_TOTAL_LIMIT] ?: 40).coerceIn(1, 500)
        AppSettings(
            reviewLimits = ReviewLimits((preferences[REVIEW_NEW_LIMIT] ?: 15).coerceIn(0, minOf(100, totalLimit)), totalLimit),
            defaultLanguageTag = preferences[DEFAULT_LANGUAGE_TAG] ?: DEFAULT_LANGUAGE,
            userLanguageTags = preferences[USER_LANGUAGE_TAGS]
                .orEmpty()
                .mapNotNull(VocabularyEntryValidator::normalizeLanguageTag)
                .filterNot { AppLanguageCatalog.find(it) != null }
                .toSet(),
        )
    }

    override suspend fun setDefaultLanguageTag(languageTag: String) {
        val normalized = requireNotNull(VocabularyEntryValidator.normalizeLanguageTag(languageTag)) {
            "A valid BCP 47 language tag is required"
        }
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_LANGUAGE_TAG] = normalized
            addUserLanguageTag(preferences, normalized)
        }
    }

    override suspend fun setReviewLimits(limits: ReviewLimits) {
        context.settingsDataStore.edit { preferences ->
            preferences[REVIEW_NEW_LIMIT] = limits.newPerDay
            preferences[REVIEW_TOTAL_LIMIT] = limits.totalPerDay
        }
    }

    override suspend fun addUserLanguageTag(languageTag: String) {
        val normalized = requireNotNull(VocabularyEntryValidator.normalizeLanguageTag(languageTag)) {
            "A valid BCP 47 language tag is required"
        }
        context.settingsDataStore.edit { preferences ->
            addUserLanguageTag(preferences, normalized)
        }
    }

    private fun addUserLanguageTag(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        normalized: String,
    ) {
        if (AppLanguageCatalog.find(normalized) != null) return
        val current = preferences[USER_LANGUAGE_TAGS].orEmpty()
            .mapNotNull(VocabularyEntryValidator::normalizeLanguageTag)
            .toSet()
        preferences[USER_LANGUAGE_TAGS] = current + normalized
    }

    private companion object {
        val REVIEW_NEW_LIMIT = intPreferencesKey("review_new_limit")
        val REVIEW_TOTAL_LIMIT = intPreferencesKey("review_total_limit")
        const val DEFAULT_LANGUAGE = "en"
        val DEFAULT_LANGUAGE_TAG = stringPreferencesKey("default_language_tag")
        val USER_LANGUAGE_TAGS = stringSetPreferencesKey("user_language_tags")
    }
}
