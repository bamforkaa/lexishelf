package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionarySenseLabel
import com.example.localvocabulary.dictionary.domain.DictionarySenseLabelType
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.normalizedSenseLabels

data class DictionarySuggestionGroup(
    val providerId: DictionaryProviderId,
    val providerName: String,
    val entries: List<ExternalDictionaryEntry> = emptyList(),
    val message: String? = null,
    val languagePair: DictionaryLanguagePair? = null,
    val morphologyContext: MorphologySuggestionContext? = null,
    val failure: DictionaryProviderError? = null,
)

data class MorphologySuggestionContext(
    val surface: String,
    val lemma: String,
    val resolverProviderId: DictionaryProviderId,
    val resolverName: String,
    val role: MorphologyAnalysisRole = MorphologyAnalysisRole.PRIMARY,
    val hasAlternates: Boolean = false,
    val isTruncated: Boolean = false,
)

enum class MorphologyAnalysisRole(val order: Int) {
    PRIMARY(0),
    ALTERNATE(1),
}

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

internal data class CompactSenseLabelText(
    val visibleText: String,
    val accessibilityText: String,
)

internal fun List<DictionarySenseLabel>.toCompactSenseLabelText(
    maxVisibleLabels: Int = 3,
): CompactSenseLabelText? {
    require(maxVisibleLabels > 0)
    val labels = normalizedSenseLabels()
    if (labels.isEmpty()) return null
    val allText = labels.joinToString(" · ", transform = DictionarySenseLabel::displayText)
    val visibleCount = minOf(maxVisibleLabels, labels.size)
    val visibleLabels = labels.take(visibleCount)
        .joinToString(" · ", transform = DictionarySenseLabel::displayText)
    val hiddenCount = labels.size - visibleCount
    return CompactSenseLabelText(
        visibleText = if (hiddenCount > 0) "$visibleLabels · +$hiddenCount" else visibleLabels,
        accessibilityText = allText,
    )
}

private fun DictionarySenseLabel.displayText(): String {
    val base = when (type) {
        DictionarySenseLabelType.FORMAL -> "formal"
        DictionarySenseLabelType.INFORMAL -> "informal"
        DictionarySenseLabelType.COLLOQUIAL -> "colloquial"
        DictionarySenseLabelType.SLANG -> "slang"
        DictionarySenseLabelType.VULGAR -> "vulgar"
        DictionarySenseLabelType.OFFENSIVE -> "offensive"
        DictionarySenseLabelType.DEROGATORY -> "derogatory"
        DictionarySenseLabelType.LITERARY -> "literary"
        DictionarySenseLabelType.ARCHAIC -> "archaic"
        DictionarySenseLabelType.OBSOLETE -> "obsolete"
        DictionarySenseLabelType.DATED -> "dated"
        DictionarySenseLabelType.RARE -> "rare"
        DictionarySenseLabelType.TRANSITIVE -> "transitive"
        DictionarySenseLabelType.INTRANSITIVE -> "intransitive"
        DictionarySenseLabelType.COUNTABLE -> "countable"
        DictionarySenseLabelType.UNCOUNTABLE -> "uncountable"
        DictionarySenseLabelType.AUXILIARY -> "auxiliary"
        DictionarySenseLabelType.IMPERSONAL -> "impersonal"
        DictionarySenseLabelType.REGIONAL -> "regional"
        DictionarySenseLabelType.DIALECTAL -> "dialectal"
    }
    return regionalDetail?.let { "$base: $it" } ?: base
}
