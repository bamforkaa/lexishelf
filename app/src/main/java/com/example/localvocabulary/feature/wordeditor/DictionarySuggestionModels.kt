package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag

data class DictionarySuggestionGroup(
    val providerId: DictionaryProviderId,
    val providerName: String,
    val entries: List<ExternalDictionaryEntry> = emptyList(),
    val message: String? = null,
    val languagePair: DictionaryLanguagePair? = null,
)

internal object DictionaryResultLanguagePreference {
    private val preferredLanguages = listOf("ko", "en")

    val languageComparator: Comparator<Bcp47LanguageTag?> = compareBy(
        { language -> language?.let(::priority) ?: preferredLanguages.size + 1 },
        { it?.value.orEmpty() },
    )

    val comparator: Comparator<DictionaryLanguagePair> = compareBy(
        { pair -> priority(pair.resultLanguage) },
        { it.resultLanguage.value },
        { it.resultKind.name },
    )

    private fun priority(language: Bcp47LanguageTag): Int {
        val baseLanguage = language.value.substringBefore('-')
        return preferredLanguages.indexOf(baseLanguage).takeIf { it >= 0 }
            ?: preferredLanguages.size
    }
}

internal data class DictionarySuggestionRequest(
    val query: String = "",
    val languagePairs: List<DictionaryLanguagePair> = emptyList(),
) {
    val isSearchable: Boolean
        get() = query.isNotBlank() && languagePairs.isNotEmpty()
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
