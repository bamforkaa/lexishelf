package com.example.localvocabulary.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        AppSettings(defaultLanguageTag = preferences[DEFAULT_LANGUAGE_TAG] ?: DEFAULT_LANGUAGE)
    }

    override suspend fun setDefaultLanguageTag(languageTag: String) {
        val normalized = requireNotNull(VocabularyEntryValidator.normalizeLanguageTag(languageTag)) {
            "A valid BCP 47 language tag is required"
        }
        context.settingsDataStore.edit { preferences ->
            preferences[DEFAULT_LANGUAGE_TAG] = normalized
        }
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
        val DEFAULT_LANGUAGE_TAG = stringPreferencesKey("default_language_tag")
    }
}
