package com.example.localvocabulary.dictionary.provider.cccedict

import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryWrittenForm
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense

internal fun CcCedictRecord.toExternalEntry(
    descriptor: DictionaryProviderDescriptor,
    languagePair: DictionaryLanguagePair,
): ExternalDictionaryEntry {
    val simplifiedQuery = languagePair.sourceLanguage == CC_CEDICT_SIMPLIFIED_CHINESE
    val headword = if (simplifiedQuery) simplified else traditional
    val alternateHeadword = if (simplifiedQuery) traditional else simplified
    val alternateLanguage = if (simplifiedQuery) {
        CC_CEDICT_TRADITIONAL_CHINESE
    } else {
        CC_CEDICT_SIMPLIFIED_CHINESE
    }

    return ExternalDictionaryEntry(
        providerId = descriptor.id,
        sourceEntryId = stableSourceId,
        datasetVersion = descriptor.dataset?.releaseId,
        headword = headword,
        sourceLanguage = languagePair.sourceLanguage,
        headwordScriptCode = if (simplifiedQuery) "Hans" else "Hant",
        alternateWrittenForms = listOf(
            DictionaryWrittenForm(
                text = alternateHeadword,
                language = alternateLanguage,
                scriptCode = if (simplifiedQuery) "Hant" else "Hans",
            ),
        ).filterNot { it.text == headword },
        linguisticFeatures = DictionaryLinguisticFeatures(
            reading = DictionaryReading(text = pinyin, scriptCode = "Latn"),
        ),
        senses = senses.map { glosses ->
            ExternalDictionarySense(
                meanings = glosses.map { gloss ->
                    DictionaryMeaning(
                        text = gloss,
                        language = languagePair.resultLanguage,
                        kind = languagePair.resultKind,
                    )
                },
            )
        },
        attribution = descriptor.attribution,
    )
}
