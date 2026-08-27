package com.example.localvocabulary.vocabulary.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyEntryValidatorTest {
    @Test
    fun `blank headword is rejected before persistence`() {
        val result = VocabularyEntryValidator.validate(
            validDraft().copy(headword = "  \t  "),
        )

        assertEquals(
            VocabularyValidationResult.Invalid(VocabularyValidationError.MissingHeadword),
            result,
        )
    }

    @Test
    fun `valid draft trims text and normalizes BCP 47 tag`() {
        val result = VocabularyEntryValidator.validate(
            VocabularyEntryDraft(
                headword = "  colour  ",
                languageTag = "EN-gb",
                senses = listOf(
                    VocabularySenseDraft(
                        meaning = "  색깔  ",
                        partOfSpeech = "  noun ",
                        examples = listOf("  a bright colour  ", "  "),
                    ),
                ),
                notes = "  British spelling  ",
                tagIds = setOf(3),
            ),
        )

        assertTrue(result is VocabularyValidationResult.Valid)
        val draft = (result as VocabularyValidationResult.Valid).draft
        assertEquals("colour", draft.headword)
        assertEquals("en-GB", draft.languageTag)
        assertEquals("색깔", draft.senses.single().meaning)
        assertEquals("noun", draft.senses.single().partOfSpeech)
        assertEquals(listOf("a bright colour"), draft.senses.single().examples)
        assertEquals("British spelling", draft.notes)
    }

    @Test
    fun `underscore language identifier is rejected`() {
        val result = VocabularyEntryValidator.validate(validDraft(languageTag = "en_US"))

        assertEquals(
            VocabularyValidationResult.Invalid(VocabularyValidationError.InvalidLanguageTag),
            result,
        )
    }

    @Test
    fun `blank language tag is rejected`() {
        val result = VocabularyEntryValidator.validate(validDraft(languageTag = "  "))

        assertEquals(
            VocabularyValidationResult.Invalid(VocabularyValidationError.InvalidLanguageTag),
            result,
        )
    }

    @Test
    fun `at least one sense is required`() {
        val result = VocabularyEntryValidator.validate(validDraft(senses = emptyList()))

        assertEquals(
            VocabularyValidationResult.Invalid(VocabularyValidationError.MissingSense),
            result,
        )
    }

    @Test
    fun `every sense requires a meaning`() {
        val result = VocabularyEntryValidator.validate(
            validDraft(
                senses = listOf(
                    VocabularySenseDraft("meaning", "", emptyList()),
                    VocabularySenseDraft(" ", "", emptyList()),
                ),
            ),
        )

        assertEquals(
            VocabularyValidationResult.Invalid(VocabularyValidationError.MissingMeaning(1)),
            result,
        )
    }

    @Test
    fun `grammatical gender normalizes known values and retains unknown raw values`() {
        assertEquals(
            GrammaticalGenderCategory.MASCULINE,
            VocabularyGrammaticalGender.parse(" masculine ")?.category,
        )
        assertEquals(
            GrammaticalGenderCategory.FEMININE,
            VocabularyGrammaticalGender.parse("FEMININE")?.category,
        )
        assertEquals(
            GrammaticalGenderCategory.NEUTER,
            VocabularyGrammaticalGender.parse("neuter")?.category,
        )
        assertEquals(
            GrammaticalGenderCategory.COMMON,
            VocabularyGrammaticalGender.parse("common-gender")?.category,
        )
        val combined = VocabularyGrammaticalGender.parse("feminine, masculine")
        assertEquals(GrammaticalGenderCategory.OTHER, combined?.category)
        assertEquals("feminine, masculine", combined?.rawValue)
        assertNull(VocabularyGrammaticalGender.parse("  "))
    }

    private fun validDraft(
        languageTag: String = "en",
        senses: List<VocabularySenseDraft> = listOf(
            VocabularySenseDraft("meaning", "noun", emptyList()),
        ),
    ) = VocabularyEntryDraft(
        headword = "word",
        languageTag = languageTag,
        senses = senses,
        notes = "",
        tagIds = emptySet(),
    )
}
