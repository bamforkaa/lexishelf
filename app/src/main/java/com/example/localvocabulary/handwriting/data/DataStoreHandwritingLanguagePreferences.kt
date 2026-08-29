package com.example.localvocabulary.handwriting.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.handwriting.domain.HandwritingLanguagePreferences
import com.example.localvocabulary.handwriting.domain.updateRecentHandwritingLanguageTags
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.handwritingPreferencesDataStore by preferencesDataStore(
    name = "handwriting_preferences",
)

@Singleton
class DataStoreHandwritingLanguagePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : HandwritingLanguagePreferences {
    override val recentLanguageTags: Flow<List<String>> =
        context.handwritingPreferencesDataStore.data.map { preferences ->
            preferences[RECENT_LANGUAGE_TAGS]
                .orEmpty()
                .split(SEPARATOR)
                .mapNotNull { Bcp47LanguageTag.parse(it)?.value }
                .distinct()
                .take(RECENT_LANGUAGE_LIMIT)
        }

    override suspend fun recordLanguage(languageTag: String) {
        val canonical = requireNotNull(Bcp47LanguageTag.parse(languageTag)) {
            "A valid BCP 47 language tag is required"
        }.value
        context.handwritingPreferencesDataStore.edit { preferences ->
            val current = preferences[RECENT_LANGUAGE_TAGS]
                .orEmpty()
                .split(SEPARATOR)
            preferences[RECENT_LANGUAGE_TAGS] = updateRecentHandwritingLanguageTags(
                current = current,
                selectedLanguageTag = canonical,
                limit = RECENT_LANGUAGE_LIMIT,
            ).joinToString(SEPARATOR)
        }
    }

    private companion object {
        const val SEPARATOR = "\n"
        const val RECENT_LANGUAGE_LIMIT = 5
        val RECENT_LANGUAGE_TAGS = stringPreferencesKey("recent_language_tags")
    }
}
