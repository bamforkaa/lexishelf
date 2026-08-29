package com.example.localvocabulary.handwriting.domain

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import kotlinx.coroutines.flow.Flow

interface HandwritingLanguagePreferences {
    val recentLanguageTags: Flow<List<String>>

    suspend fun recordLanguage(languageTag: String)
}

fun prioritizeHandwritingLanguageTags(
    contextLanguageTag: String?,
    recentLanguageTags: List<String>,
    installedLanguageTags: List<String>,
    vocabularyLanguageTags: List<String>,
): List<String> = buildList {
    addAll(listOfNotNull(contextLanguageTag))
    addAll(recentLanguageTags)
    addAll(installedLanguageTags)
    addAll(vocabularyLanguageTags)
}.mapNotNull { Bcp47LanguageTag.parse(it)?.value }
    .distinct()

fun updateRecentHandwritingLanguageTags(
    current: List<String>,
    selectedLanguageTag: String,
    limit: Int = 5,
): List<String> {
    val selected = Bcp47LanguageTag.parse(selectedLanguageTag)?.value ?: return current
    return (listOf(selected) + current)
        .mapNotNull { Bcp47LanguageTag.parse(it)?.value }
        .distinct()
        .take(limit)
}
