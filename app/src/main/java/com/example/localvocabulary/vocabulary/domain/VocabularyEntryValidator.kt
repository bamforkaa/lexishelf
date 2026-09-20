package com.example.localvocabulary.vocabulary.domain

import java.util.IllformedLocaleException
import java.util.Locale

sealed interface VocabularyValidationError {
    data object MissingHeadword : VocabularyValidationError
    data object InvalidLanguageTag : VocabularyValidationError
    data object InvalidChildIdentity : VocabularyValidationError
    data class InvalidExample(val senseIndex: Int, val exampleIndex: Int) : VocabularyValidationError
    data object MissingSense : VocabularyValidationError
    data class MissingMeaning(val senseIndex: Int) : VocabularyValidationError
    data class InvalidPronunciationLanguageTag(val pronunciationIndex: Int) :
        VocabularyValidationError
}

sealed interface VocabularyValidationResult {
    data class Valid(val draft: ValidatedVocabularyDraft) : VocabularyValidationResult
    data class Invalid(val error: VocabularyValidationError) : VocabularyValidationResult
}

data class ValidatedVocabularyDraft(
    val id: Long?,
    val headword: String,
    val languageTag: String,
    val senses: List<VocabularySenseDraft>,
    val notes: String,
    val tagIds: Set<Long>,
    val reading: String = "",
    val readingProvenance: DictionaryProvenance? = null,
    val pronunciations: List<VocabularyPronunciationDraft> = emptyList(),
    val wordbookIds: Set<Long> = emptySet(),
)

object VocabularyEntryValidator {
    private val CHILD_ID = Regex("[A-Za-z0-9._:-]{1,128}")
    fun validate(draft: VocabularyEntryDraft): VocabularyValidationResult {
        val headword = draft.headword.trim()
        if (headword.isEmpty()) {
            return VocabularyValidationResult.Invalid(VocabularyValidationError.MissingHeadword)
        }

        val languageTag = normalizeLanguageTag(draft.languageTag)
            ?: return VocabularyValidationResult.Invalid(VocabularyValidationError.InvalidLanguageTag)

        if (draft.senses.isEmpty()) {
            return VocabularyValidationResult.Invalid(VocabularyValidationError.MissingSense)
        }

        val senseIds = draft.senses.mapNotNull { it.stableId }
        val exampleIds = draft.senses.flatMap { it.examples }.mapNotNull { it.stableId }
        if (listOf(senseIds, exampleIds).any { ids ->
                ids.size != ids.distinct().size || ids.any { !CHILD_ID.matches(it) }
            }) {
            return VocabularyValidationResult.Invalid(VocabularyValidationError.InvalidChildIdentity)
        }
        val senses = draft.senses.mapIndexed { index, sense ->
            val meaning = sense.meaning.trim()
            if (meaning.isEmpty()) {
                return VocabularyValidationResult.Invalid(
                    VocabularyValidationError.MissingMeaning(index),
                )
            }
            sense.copy(
                meaning = meaning,
                partOfSpeech = sense.partOfSpeech.trim(),
                examples = sense.examples.mapIndexedNotNull { exampleIndex, example ->
                    val text = example.text.trim()
                    if ((example.capturedAt ?: 0) < 0 || (text.isEmpty() && (
                            example.meaning.isNotBlank() || !example.sourceTitle.isNullOrBlank() ||
                                !example.sourceUrl.isNullOrBlank() || !example.sourceLocator.isNullOrBlank()
                            ))) {
                        return VocabularyValidationResult.Invalid(
                            VocabularyValidationError.InvalidExample(index, exampleIndex),
                        )
                    }
                    if (text.isEmpty()) return@mapIndexedNotNull null
                    example.copy(
                        text = text,
                        meaning = example.meaning.trim(),
                        sourceTitle = example.sourceTitle?.trim()?.takeIf(String::isNotEmpty),
                        sourceUrl = example.sourceUrl?.trim()?.takeIf(String::isNotEmpty),
                        sourceLocator = example.sourceLocator?.trim()?.takeIf(String::isNotEmpty),
                    )
                },
            )
        }

        val pronunciationIdentities = mutableSetOf<Triple<PronunciationNotation, String, String?>>()
        val pronunciations = draft.pronunciations.mapIndexedNotNull { index, pronunciation ->
            val value = pronunciation.value.trim()
            if (value.isEmpty()) return@mapIndexedNotNull null
            val pronunciationLanguage = pronunciation.languageTag?.let { rawLanguage ->
                normalizeLanguageTag(rawLanguage) ?: return VocabularyValidationResult.Invalid(
                    VocabularyValidationError.InvalidPronunciationLanguageTag(index),
                )
            }
            val identity = Triple(pronunciation.notation, value, pronunciationLanguage)
            if (!pronunciationIdentities.add(identity)) return@mapIndexedNotNull null
            pronunciation.copy(
                stableId = pronunciation.stableId?.trim()?.takeIf(String::isNotEmpty),
                value = value,
                languageTag = pronunciationLanguage,
            )
        }

        return VocabularyValidationResult.Valid(
            ValidatedVocabularyDraft(
                id = draft.id,
                headword = headword,
                languageTag = languageTag,
                senses = senses,
                notes = draft.notes.trim(),
                tagIds = draft.tagIds,
                reading = draft.reading.trim(),
                readingProvenance = draft.readingProvenance,
                pronunciations = pronunciations,
                wordbookIds = draft.wordbookIds,
            ),
        )
    }

    fun normalizeLanguageTag(rawTag: String): String? {
        val candidate = rawTag.trim()
        if (candidate.isEmpty() || '_' in candidate) return null
        return try {
            val locale = Locale.Builder().setLanguageTag(candidate).build()
            locale.toLanguageTag().takeUnless { it == "und" }
        } catch (_: IllformedLocaleException) {
            null
        }
    }
}
