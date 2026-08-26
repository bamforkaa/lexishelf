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

internal object DictionaryResultLanguagePreference {
    private val preferredLanguages = listOf("ko", "en")

    val comparator: Comparator<DictionaryLanguagePair> = compareBy(
        { pair ->
            val baseLanguage = pair.resultLanguage.value.substringBefore('-')
            preferredLanguages.indexOf(baseLanguage).takeIf { it >= 0 } ?: preferredLanguages.size
        },
        { it.resultLanguage.value },
        { it.resultKind.name },
    )
}

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

internal fun ExternalDictionaryEntry.suggestionKey(): String = listOf(
    providerId.value,
    sourceEntryId.orEmpty(),
    senses.singleOrNull()?.sourceSenseId.orEmpty(),
    headword,
).joinToString("|")

internal fun DictionarySuggestionGroup.selectableEntries(): List<ExternalDictionaryEntry> =
    entries.flatMap { entry ->
        if (entry.senses.isEmpty()) {
            listOf(entry)
        } else {
            entry.senses.map { sense -> entry.copy(senses = listOf(sense)) }
        }
    }.take(MAX_SELECTABLE_ROWS_PER_PROVIDER)

internal const val MAX_SELECTABLE_ROWS_PER_PROVIDER = 25
