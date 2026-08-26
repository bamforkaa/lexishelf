package com.example.localvocabulary.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
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
        AppSettings(
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
        const val DEFAULT_LANGUAGE = "en"
        val DEFAULT_LANGUAGE_TAG = stringPreferencesKey("default_language_tag")
        val USER_LANGUAGE_TAGS = stringSetPreferencesKey("user_language_tags")
    }
}
