package com.example.localvocabulary.dictionary.provider.koreanbasic

import com.example.localvocabulary.dictionary.domain.DictionaryLookupKind
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense

internal fun List<KoreanBasicDictionaryRecord>.toExternalEntries(
    queryText: String,
    descriptor: DictionaryProviderDescriptor,
    languagePair: DictionaryLanguagePair,
    datasetVersion: String? = descriptor.dataset?.releaseId,
): List<ExternalDictionaryEntry> = if (languagePair.sourceLanguage == KOREAN_LANGUAGE) {
    toForwardEntries(descriptor, languagePair, datasetVersion)
} else {
    toReverseEntries(queryText.trim(), descriptor, languagePair, datasetVersion)
}

private fun List<KoreanBasicDictionaryRecord>.toForwardEntries(
    descriptor: DictionaryProviderDescriptor,
    languagePair: DictionaryLanguagePair,
    datasetVersion: String?,
): List<ExternalDictionaryEntry> = groupBy(KoreanBasicDictionaryRecord::stableSourceEntryId)
    .values
    .map { entryRecords ->
        val first = entryRecords.first()
        ExternalDictionaryEntry(
            providerId = descriptor.id,
            sourceEntryId = first.stableSourceEntryId,
            datasetVersion = datasetVersion,
            headword = first.koreanHeadword,
            sourceLanguage = languagePair.sourceLanguage,
            senses = entryRecords
                .groupBy(KoreanBasicDictionaryRecord::officialSenseId)
                .values
                .map { senseRecords ->
                    val firstSense = senseRecords.first()
                    ExternalDictionarySense(
                        meanings = senseRecords
                            .distinctBy(KoreanBasicDictionaryRecord::translationTerm)
                            .map { record ->
                                DictionaryMeaning(
                                    text = record.translationTerm,
                                    language = languagePair.resultLanguage,
                                    kind = languagePair.resultKind,
                                )
                            },
                        partOfSpeech = firstSense.partOfSpeech,
                        sourceSenseId = firstSense.officialSenseId,
                    )
                },
            attribution = descriptor.attribution,
        )
    }

private fun List<KoreanBasicDictionaryRecord>.toReverseEntries(
    queryText: String,
    descriptor: DictionaryProviderDescriptor,
    languagePair: DictionaryLanguagePair,
    datasetVersion: String?,
): List<ExternalDictionaryEntry> = groupBy { record ->
    record.stableSourceEntryId to record.officialSenseId
}.values.map { senseRecords ->
    val first = senseRecords.first()
    ExternalDictionaryEntry(
        providerId = descriptor.id,
        sourceEntryId = first.stableSourceEntryId,
        datasetVersion = datasetVersion,
        headword = queryText,
        lookupKind = DictionaryLookupKind.REVERSE_TRANSLATION,
        sourceLanguage = languagePair.sourceLanguage,
        senses = listOf(
            ExternalDictionarySense(
                meanings = listOf(
                    DictionaryMeaning(
                        text = first.koreanHeadword,
                        language = languagePair.resultLanguage,
                        kind = languagePair.resultKind,
                    ),
                ),
                // The POS belongs to the Korean headword, not the reverse translation.
                partOfSpeech = null,
                sourceSenseId = first.officialSenseId,
            ),
        ),
        attribution = descriptor.attribution,
    )
}
