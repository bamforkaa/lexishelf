package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryContentField
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import java.text.Normalizer
import java.util.Locale

data class SynthesizedSuggestionGroup(
    val resultLanguage: Bcp47LanguageTag?,
    val candidates: List<SynthesizedSuggestionCandidate>,
    val messages: List<SynthesizedSuggestionMessage> = emptyList(),
)

data class SynthesizedSuggestionCandidate(
    val key: String,
    val displayMeaning: String,
    val resultLanguage: Bcp47LanguageTag,
    val primarySource: DictionarySourceContribution,
    val sources: List<DictionarySourceContribution>,
    val morphologyContext: MorphologySuggestionContext? = null,
) {
    val primaryEntry: ExternalDictionaryEntry
        get() = primarySource.entry
}

data class DictionarySourceContribution(
    val providerId: DictionaryProviderId,
    val providerName: String,
    val entry: ExternalDictionaryEntry,
    val sourceIdentity: String,
)

data class SynthesizedSuggestionMessage(
    val key: String,
    val providerName: String,
    val text: String,
)

/**
 * Presentation-only synthesis over immutable common provider results.
 *
 * It deliberately performs exact lexical normalization, not semantic matching. Raw provider
 * entries remain in [DictionarySuggestionGroup] and one deterministic primary entry continues
 * through the existing import/provenance pipeline.
 */
internal object DictionarySuggestionSynthesizer {
    fun synthesize(groups: List<DictionarySuggestionGroup>): List<SynthesizedSuggestionGroup> {
        val rawCandidates = groups.flatMapIndexed { groupIndex, group ->
            group.selectableEntries().mapIndexedNotNull { entryIndex, entry ->
                entry.toRawCandidate(
                    providerName = group.providerName,
                    firstSeenOrder = groupIndex * RAW_ORDER_GROUP_STRIDE + entryIndex,
                    fallbackResultLanguage = group.languagePair?.resultLanguage,
                    morphologyContext = group.morphologyContext,
                )
            }
        }
        val candidates = rawCandidates
            .groupBy(RawSuggestionCandidate::baseIdentity)
            .values
            .flatMap(::mergeUnambiguousCandidates)
        val messages = groups.mapNotNull { group ->
            group.message?.let { message ->
                MessageWithLanguage(
                    resultLanguage = group.languagePair?.resultLanguage
                        ?: group.entries.firstMeaningLanguage(),
                    message = SynthesizedSuggestionMessage(
                        key = stableComponents(
                            "message",
                            group.languagePair?.stableKey().orEmpty(),
                            group.providerId.value,
                            message,
                        ),
                        providerName = group.providerName,
                        text = message,
                    ),
                )
            }
        }
        val languages = buildSet {
            candidates.mapTo(this) { it.candidate.resultLanguage as Bcp47LanguageTag? }
            messages.mapTo(this) { it.resultLanguage }
        }
        return languages.sortedWith(DictionaryResultLanguagePreference.languageComparator).map {
            language ->
            SynthesizedSuggestionGroup(
                resultLanguage = language,
                candidates = candidates
                    .filter { it.candidate.resultLanguage == language }
                    .sortedWith(
                        compareBy<SynthesizedCandidateWithOrder>(
                            { it.candidate.morphologyContext != null },
                            { it.candidate.morphologyContext?.role?.order ?: -1 },
                            { it.primarySource.role.order },
                            SynthesizedCandidateWithOrder::firstSeenOrder,
                            { it.candidate.key },
                        ),
                    )
                    .map(SynthesizedCandidateWithOrder::candidate),
                messages = messages
                    .filter { it.resultLanguage == language }
                    .map(MessageWithLanguage::message)
                    .sortedWith(compareBy({ it.providerName }, { it.key })),
            )
        }
    }

    private fun mergeUnambiguousCandidates(
        candidates: List<RawSuggestionCandidate>,
    ): List<SynthesizedCandidateWithOrder> {
        val hasSameProviderAmbiguity = candidates
            .groupingBy { it.source.providerId }
            .eachCount()
            .values
            .any { it > 1 }
        if (hasSameProviderAmbiguity) return candidates.map { listOf(it).toSynthesizedCandidate() }

        val distinctPos = candidates.mapNotNull(RawSuggestionCandidate::normalizedPartOfSpeech)
            .distinct()
        return if (distinctPos.size <= 1) {
            listOf(candidates.toSynthesizedCandidate())
        } else {
            candidates.groupBy { it.normalizedPartOfSpeech.orEmpty() }
                .values
                .map { it.toSynthesizedCandidate() }
        }
    }

    private fun List<RawSuggestionCandidate>.toSynthesizedCandidate(): SynthesizedCandidateWithOrder {
        val orderedSources = map(RawSuggestionCandidate::source).sortedWith(primarySourceComparator)
        val primary = orderedSources.first()
        val representative = first { it.source.sourceIdentity == primary.sourceIdentity }
        val sourceIdentitySet = orderedSources.map(DictionarySourceContribution::sourceIdentity).sorted()
        val semanticDiscriminator = listOf(
            representative.baseIdentity.normalizedHeadword,
            representative.normalizedPartOfSpeech.orEmpty(),
            representative.baseIdentity.writtenFormRestrictions,
            representative.baseIdentity.readingRestrictions,
            representative.baseIdentity.resolutionKey,
        ).joinToString(SEPARATOR)
        return SynthesizedCandidateWithOrder(
            candidate = SynthesizedSuggestionCandidate(
                key = stableComponents(
                    "candidate",
                    representative.resultLanguage.value,
                    representative.baseIdentity.normalizedMeaning,
                    semanticDiscriminator,
                    stableComponents(*sourceIdentitySet.toTypedArray()),
                ),
                displayMeaning = primary.entry.displayMeaning(),
                resultLanguage = representative.resultLanguage,
                primarySource = primary,
                sources = orderedSources,
                morphologyContext = representative.morphologyContext,
            ),
            firstSeenOrder = minOf(RawSuggestionCandidate::firstSeenOrder),
            primarySource = primary,
        )
    }

    private fun ExternalDictionaryEntry.toRawCandidate(
        providerName: String,
        firstSeenOrder: Int,
        fallbackResultLanguage: Bcp47LanguageTag?,
        morphologyContext: MorphologySuggestionContext?,
    ): RawSuggestionCandidate? {
        val sense = senses.singleOrNull() ?: return null
        val resultLanguage = sense.meanings.firstOrNull()?.language ?: fallbackResultLanguage
            ?: return null
        val normalizedMeanings = sense.meanings.map { normalizeLexicalText(it.text) }
            .filter(String::isNotBlank)
        if (normalizedMeanings.isEmpty()) return null
        val source = DictionarySourceContribution(
            providerId = providerId,
            providerName = providerName,
            entry = this,
            sourceIdentity = stableComponents(
                providerId.value,
                sourceEntryId.orEmpty(),
                sense.sourceSenseId.orEmpty(),
                datasetVersion.orEmpty(),
                normalizeLexicalText(headword),
            ),
        )
        return RawSuggestionCandidate(
            source = source,
            resultLanguage = resultLanguage,
            normalizedPartOfSpeech = sense.partOfSpeech
                ?.let(::normalizeLexicalText)
                ?.lowercase(Locale.ROOT)
                ?.takeIf(String::isNotBlank),
            baseIdentity = ExactCandidateIdentity(
                resultLanguage = resultLanguage.value,
                normalizedHeadword = normalizeLexicalText(headword),
                normalizedMeaning = normalizedMeanings.joinToString(SEPARATOR),
                writtenFormRestrictions = sense.writtenFormRestrictions
                    .map(::normalizeLexicalText).sorted().joinToString(SEPARATOR),
                readingRestrictions = sense.readingRestrictions
                    .map(::normalizeLexicalText).sorted().joinToString(SEPARATOR),
                resolutionKey = morphologyContext?.let {
                    stableComponents(
                        normalizeLexicalText(it.surface),
                        normalizeLexicalText(it.lemma),
                        it.resolverProviderId.value,
                    )
                }.orEmpty(),
            ),
            firstSeenOrder = firstSeenOrder,
            morphologyContext = morphologyContext,
        )
    }

    private val primarySourceComparator = compareByDescending<DictionarySourceContribution> {
        it.entry.hasImportableMeaning()
    }.thenByDescending {
        it.entry.hasImportableExamples()
    }.thenByDescending {
        it.entry.hasImportableReading()
    }.thenByDescending {
        it.entry.senses.singleOrNull()?.partOfSpeech?.isNotBlank() == true
    }.thenByDescending {
        it.entry.hasSupplementaryMetadata()
    }.thenBy {
        it.role.order
    }.thenBy(DictionarySourceContribution::sourceIdentity)

    private fun ExternalDictionaryEntry.hasImportableMeaning(): Boolean {
        if (attribution.licenseName.isNullOrBlank()) return false
        return senses.singleOrNull()?.meanings.orEmpty().any { meaning ->
            val field = when (meaning.kind) {
                DictionaryResultKind.MONOLINGUAL_DEFINITION ->
                    DictionaryContentField.MONOLINGUAL_DEFINITION
                DictionaryResultKind.TRANSLATION -> DictionaryContentField.TRANSLATION
            }
            meaning.text.isNotBlank() && attribution.usagePolicy.permitsExportableVocabularyCopy(field)
        }
    }

    private fun ExternalDictionaryEntry.hasImportableExamples(): Boolean =
        senses.singleOrNull()?.examples.orEmpty().any { it.text.isNotBlank() } &&
            attribution.usagePolicy.permitsExportableVocabularyCopy(DictionaryContentField.EXAMPLE)

    private fun ExternalDictionaryEntry.hasImportableReading(): Boolean =
        linguisticFeatures.reading?.text?.isNotBlank() == true &&
            attribution.usagePolicy.permitsExportableVocabularyCopy(DictionaryContentField.READING)

    private fun ExternalDictionaryEntry.hasSupplementaryMetadata(): Boolean {
        val sense = senses.singleOrNull()
        return linguisticFeatures.pronunciations.isNotEmpty() ||
            linguisticFeatures.inflections.isNotEmpty() ||
            linguisticFeatures.totalInflectionCount > 0 ||
            !sense?.grammaticalGender.isNullOrBlank() ||
            (sense?.availableExampleCount ?: 0) > 0
    }

    private val DictionarySourceContribution.role: ProviderRole
        get() = providerRoles[providerId.value] ?: ProviderRole.OTHER

    private val providerRoles = mapOf(
        "korean-basic-dictionary" to ProviderRole.KOREAN_SPECIALIST,
        "jmdict" to ProviderRole.JAPANESE_SPECIALIST,
        "cc-cedict" to ProviderRole.CHINESE_SPECIALIST,
        "kaikki" to ProviderRole.GENERAL_ENGLISH,
        "panlex" to ProviderRole.LEXICAL_FALLBACK,
    )

    private fun ExternalDictionaryEntry.displayMeaning(): String = senses.singleOrNull()
        ?.meanings.orEmpty()
        .joinToString("; ") { it.text }
        .ifBlank { headword }

    private fun List<ExternalDictionaryEntry>.firstMeaningLanguage(): Bcp47LanguageTag? =
        asSequence().flatMap { it.senses.asSequence() }
            .flatMap { it.meanings.asSequence() }
            .map { it.language }
            .firstOrNull()

    private fun normalizeLexicalText(value: String): String = WHITESPACE.replace(
        Normalizer.normalize(value, Normalizer.Form.NFC).trim(),
        " ",
    )

    private fun stableComponents(vararg values: String): String = values.joinToString(SEPARATOR) {
        value -> "${value.length}:$value"
    }

    private data class RawSuggestionCandidate(
        val source: DictionarySourceContribution,
        val resultLanguage: Bcp47LanguageTag,
        val normalizedPartOfSpeech: String?,
        val baseIdentity: ExactCandidateIdentity,
        val firstSeenOrder: Int,
        val morphologyContext: MorphologySuggestionContext?,
    )

    private data class ExactCandidateIdentity(
        val resultLanguage: String,
        val normalizedHeadword: String,
        val normalizedMeaning: String,
        val writtenFormRestrictions: String,
        val readingRestrictions: String,
        val resolutionKey: String,
    )

    private data class SynthesizedCandidateWithOrder(
        val candidate: SynthesizedSuggestionCandidate,
        val firstSeenOrder: Int,
        val primarySource: DictionarySourceContribution,
    )

    private data class MessageWithLanguage(
        val resultLanguage: Bcp47LanguageTag?,
        val message: SynthesizedSuggestionMessage,
    )

    private enum class ProviderRole(val order: Int) {
        KOREAN_SPECIALIST(0),
        JAPANESE_SPECIALIST(1),
        CHINESE_SPECIALIST(2),
        GENERAL_ENGLISH(3),
        LEXICAL_FALLBACK(4),
        OTHER(5),
    }

    private val WHITESPACE = Regex("\\s+")
    private const val SEPARATOR = "\u001F"
    private const val RAW_ORDER_GROUP_STRIDE = 1_000
}
