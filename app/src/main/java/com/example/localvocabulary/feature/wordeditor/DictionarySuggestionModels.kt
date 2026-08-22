package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry

data class DictionaryLanguagePairOption(
    val key: String,
    val languagePair: DictionaryLanguagePair,
    val providerCount: Int,
)

data class DictionarySuggestionGroup(
    val providerId: DictionaryProviderId,
    val providerName: String,
    val entries: List<ExternalDictionaryEntry> = emptyList(),
    val message: String? = null,
)

internal data class DictionarySuggestionRequest(
    val query: String = "",
    val languagePair: DictionaryLanguagePair? = null,
) {
    val isSearchable: Boolean
        get() = query.isNotBlank() && languagePair != null
}

internal fun DictionaryLanguagePair.stableKey(): String = listOf(
    sourceLanguage.value,
    resultLanguage.value,
    resultKind.name,
).joinToString("|")
