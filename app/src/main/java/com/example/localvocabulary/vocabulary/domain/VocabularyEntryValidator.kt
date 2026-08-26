package com.example.localvocabulary.vocabulary.domain

import java.util.IllformedLocaleException
import java.util.Locale

sealed interface VocabularyValidationError {
    data object MissingHeadword : VocabularyValidationError
    data object InvalidLanguageTag : VocabularyValidationError
    data object MissingSense : VocabularyValidationError
    data class MissingMeaning(val senseIndex: Int) : VocabularyValidationError
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
    val wordbookIds: Set<Long> = emptySet(),
)

object VocabularyEntryValidator {
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
                examples = sense.examples.map(String::trim).filter(String::isNotEmpty),
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
