package com.example.localvocabulary.dictionary.provider.jmdict

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionaryWrittenForm
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense

internal fun JmDictMatch.toExternalEntry(
    descriptor: DictionaryProviderDescriptor,
    datasetVersion: String = descriptor.dataset?.releaseId.orEmpty(),
): ExternalDictionaryEntry {
    val matchedReading = entry.readings.getOrNull(matchedElementOrder)
        .takeIf { matchKind == JmDictMatchKind.READING }
    val displayWriting = when {
        matchKind == JmDictMatchKind.WRITTEN_FORM -> entry.writtenForms[matchedElementOrder].text
        matchedReading?.hasNoWrittenForm == true -> matchedReading.text
        matchedReading != null && matchedReading.writtenFormRestrictions.isNotEmpty() ->
            entry.writtenForms.firstOrNull {
                it.text in matchedReading.writtenFormRestrictions
            }?.text
        else -> entry.writtenForms.firstOrNull()?.text ?: matchedReading?.text
    } ?: entry.readings.first().text

    val applicableReadings = when (matchKind) {
        JmDictMatchKind.READING -> listOfNotNull(matchedReading)
        JmDictMatchKind.WRITTEN_FORM -> entry.readings.filter { reading ->
            !reading.hasNoWrittenForm && (
                reading.writtenFormRestrictions.isEmpty() ||
                    displayWriting in reading.writtenFormRestrictions
                )
        }
    }
    val readingModels = applicableReadings.map { reading ->
        DictionaryReading(
            text = reading.text,
            writtenFormRestrictions = reading.writtenFormRestrictions.toSet(),
            appliesWithoutWrittenForm = reading.hasNoWrittenForm,
        )
    }
    val applicableReadingTexts = applicableReadings.mapTo(hashSetOf()) { it.text }
    val mappedSenses = entry.senses.filter { sense ->
        (sense.writtenFormRestrictions.isEmpty() || displayWriting in sense.writtenFormRestrictions) &&
            (sense.readingRestrictions.isEmpty() ||
                sense.readingRestrictions.any(applicableReadingTexts::contains))
    }.mapNotNull { sense ->
        val meanings = sense.glosses.filter { it.languageCode == "eng" }.map { gloss ->
            DictionaryMeaning(
                text = gloss.text,
                language = ENGLISH,
                kind = DictionaryResultKind.TRANSLATION,
            )
        }
        if (meanings.isEmpty()) return@mapNotNull null
        ExternalDictionarySense(
            meanings = meanings,
            partOfSpeech = sense.partOfSpeech.takeIf { it.isNotEmpty() }?.joinToString("; "),
            sourceSenseId = "${entry.entrySequence}:${sense.order + 1}",
            writtenFormRestrictions = sense.writtenFormRestrictions.toSet(),
            readingRestrictions = sense.readingRestrictions.toSet(),
        )
    }

    return ExternalDictionaryEntry(
        providerId = descriptor.id,
        sourceEntryId = entry.entrySequence,
        datasetVersion = datasetVersion,
        headword = displayWriting,
        sourceLanguage = JAPANESE,
        alternateWrittenForms = entry.writtenForms
            .filterNot { it.text == displayWriting }
            .map { DictionaryWrittenForm(it.text, JAPANESE) },
        linguisticFeatures = DictionaryLinguisticFeatures(
            reading = readingModels.firstOrNull(),
            alternativeReadings = readingModels.drop(1),
        ),
        senses = mappedSenses,
        attribution = descriptor.attribution,
    )
}

internal val JAPANESE = Bcp47LanguageTag.requireValid("ja")
internal val ENGLISH = Bcp47LanguageTag.requireValid("en")
