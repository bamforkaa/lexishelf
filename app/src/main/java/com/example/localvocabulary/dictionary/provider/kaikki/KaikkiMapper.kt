package com.example.localvocabulary.dictionary.provider.kaikki

import com.example.localvocabulary.dictionary.domain.DictionaryInflection
import com.example.localvocabulary.dictionary.domain.DictionaryExample
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryPronunciation
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun KaikkiMatch.toExternalEntry(
    descriptor: DictionaryProviderDescriptor,
    sourceLanguageTag: String,
    datasetVersion: String,
): ExternalDictionaryEntry {
    val sourceLanguage = requireNotNull(
        KAIKKI_SOURCE_LANGUAGES.firstOrNull { it.value == sourceLanguageTag },
    )
    return ExternalDictionaryEntry(
    providerId = descriptor.id,
    sourceEntryId = sourceEntryId,
    datasetVersion = datasetVersion,
    headword = entry.headword,
    sourceLanguage = sourceLanguage,
    linguisticFeatures = DictionaryLinguisticFeatures(
        pronunciations = entry.pronunciations.map { DictionaryPronunciation(text = it) },
        inflections = entry.retainedForms.map { form ->
            DictionaryInflection(form = form.form, label = form.label)
        },
        totalInflectionCount = entry.totalFormCount,
    ),
    senses = entry.senses.sortedBy(KaikkiSenseRecord::order).map { sense ->
        ExternalDictionarySense(
            meanings = sense.glosses.map { gloss ->
                DictionaryMeaning(
                    text = gloss,
                    language = KAIKKI_ENGLISH_LANGUAGE,
                    kind = DictionaryResultKind.TRANSLATION,
                )
            },
            partOfSpeech = normalizedPartOfSpeech(entry.rawPartOfSpeech),
            sourcePartOfSpeech = entry.rawPartOfSpeech.takeIf(String::isNotBlank),
            grammaticalGender = sense.grammaticalGender,
            examples = sense.retainedExamples.map { text ->
                DictionaryExample(text = text, language = sourceLanguage)
            },
            availableExampleCount = sense.availableExampleCount,
            sourceSenseId = sense.sourceSenseId.takeIf(String::isNotBlank)
                ?: "$sourceEntryId:${sense.order + 1}",
        )
    },
    attribution = descriptor.attribution.copy(
        sourceUrl = "https://en.wiktionary.org/wiki/${entry.headword.wikiPathSegment()}",
    ),
)
}

private fun String.wikiPathSegment(): String = URLEncoder
    .encode(this, StandardCharsets.UTF_8.toString())
    .replace("+", "%20")

internal fun normalizedPartOfSpeech(rawValue: String): String? = when (rawValue) {
    "" -> null
    "adj" -> "adjective"
    "adv" -> "adverb"
    "name", "proper-noun" -> "proper noun"
    "num" -> "numeral"
    "pron" -> "pronoun"
    "prep" -> "preposition"
    "postp" -> "postposition"
    "conj" -> "conjunction"
    "intj" -> "interjection"
    "punct" -> "punctuation"
    else -> rawValue
}
