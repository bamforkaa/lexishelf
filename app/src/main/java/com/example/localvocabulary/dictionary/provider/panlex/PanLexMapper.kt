package com.example.localvocabulary.dictionary.provider.panlex

import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense

internal fun PanLexRelationRecord.toExternalEntry(
    descriptor: DictionaryProviderDescriptor,
    languagePair: DictionaryLanguagePair,
): ExternalDictionaryEntry = ExternalDictionaryEntry(
    providerId = descriptor.id,
    sourceEntryId = "ex:$sourceExpressionId->ex:$targetExpressionId",
    datasetVersion = descriptor.dataset?.releaseId,
    headword = sourceText,
    sourceLanguage = languagePair.sourceLanguage,
    senses = listOf(
        ExternalDictionarySense(
            meanings = listOf(
                DictionaryMeaning(
                    text = targetText,
                    language = languagePair.resultLanguage,
                    kind = languagePair.resultKind,
                ),
            ),
            sourceSenseId = "mn:$representativeMeaningId:src:$representativeSourceId",
        ),
    ),
    attribution = descriptor.attribution,
)
