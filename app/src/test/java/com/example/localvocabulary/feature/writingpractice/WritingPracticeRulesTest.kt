package com.example.localvocabulary.feature.writingpractice

import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeItem
import java.text.Normalizer
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingPracticeRulesTest {
    @Test
    fun `meaning is primary and useful reading pronunciation grammar and example are ordered hints`() {
        val question = requireNotNull(
            PracticeQuestionFactory.create(
                item(
                    headword = "食べる",
                    meaning = "먹다",
                    reading = "たべる",
                    pronunciation = "ta.be.ɾɯ",
                    partOfSpeech = "verb",
                    gender = "NEUTER",
                    example = "毎日ご飯を食べます。",
                ),
            ),
        )

        assertEquals(PracticeHint(PracticeHintKind.MEANING, "먹다"), question.primaryHint)
        assertEquals(
            listOf(PracticeHintKind.READING, PracticeHintKind.PRONUNCIATION),
            question.secondaryHints.map(PracticeHint::kind),
        )
        assertEquals(
            listOf(PracticeHintKind.GRAMMAR, PracticeHintKind.EXAMPLE),
            question.expandedHints.map(PracticeHint::kind),
        )
        assertEquals("verb · NEUTER", question.expandedHints.first().text)
    }

    @Test
    fun `headword-equivalent reading and containing example never leak the answer`() {
        val question = requireNotNull(
            PracticeQuestionFactory.create(
                item(
                    headword = "たべる",
                    meaning = "먹다",
                    reading = "たべる",
                    example = "たべる 것은 먹는다는 뜻이다.",
                ),
            ),
        )

        assertTrue(question.secondaryHints.isEmpty())
        assertTrue(question.expandedHints.isEmpty())
        assertNull(
            PracticeQuestionFactory.create(
                item(
                    headword = "hello",
                    meaning = "hello",
                    reading = " hello ",
                    example = "Say HELLO to everyone.",
                ),
            ),
        )
    }

    @Test
    fun `invalid language and entries without a safe hint are ineligible`() {
        assertNull(PracticeQuestionFactory.create(item(languageTag = "not_a_tag")))
        assertNull(PracticeQuestionFactory.create(item(meaning = "")))
        assertNotNull(
            PracticeQuestionFactory.create(
                item(headword = "你好", meaning = "안녕", reading = "nǐ hǎo", languageTag = "zh-Hans"),
            ),
        )
    }

    @Test
    fun `answer matching uses NFC trimmed collapsed whitespace and remains case-sensitive`() {
        val decomposed = Normalizer.normalize("école", Normalizer.Form.NFD)
        assertEquals(PracticeAnswerResult.Correct, PracticeAnswerEvaluator.evaluate("  New\n York ", "New York"))
        assertEquals(PracticeAnswerResult.Correct, PracticeAnswerEvaluator.evaluate(decomposed, "école"))
        assertEquals(PracticeAnswerResult.Incorrect, PracticeAnswerEvaluator.evaluate("wasser", "Wasser"))
        assertEquals(PracticeAnswerResult.Incorrect, PracticeAnswerEvaluator.evaluate("食べ", "食べる"))
        assertEquals(PracticeAnswerResult.Incorrect, PracticeAnswerEvaluator.evaluate("안녕", "你好"))
        assertEquals(PracticeAnswerResult.Empty, PracticeAnswerEvaluator.evaluate(" \n ", "hello"))
    }

    @Test
    fun `session length and shuffled ID snapshot are stable and contain no repeats`() {
        val items = (1L..25L).map { id -> item(id = id, headword = "word-$id") }
        val factory = PracticeSessionFactory()
        val ten = requireNotNull(factory.create(items, PracticeSessionLength.TEN, Random(7)))
        val twenty = requireNotNull(factory.create(items, PracticeSessionLength.TWENTY, Random(7)))
        val all = requireNotNull(factory.create(items, PracticeSessionLength.ALL, Random(7)))

        assertEquals(10, ten.questions.size)
        assertEquals(20, twenty.questions.size)
        assertEquals(25, all.questions.size)
        assertEquals(ten.orderedEntryIds, ten.copy().orderedEntryIds)
        assertEquals(ten.orderedEntryIds.size, ten.orderedEntryIds.distinct().size)
        assertFalse(ten.orderedEntryIds == items.take(10).map(VocabularyPracticeItem::entryId))
    }

    private fun item(
        id: Long = 1,
        headword: String = "Wasser",
        languageTag: String = "de",
        meaning: String = "물",
        reading: String = "",
        pronunciation: String = "",
        partOfSpeech: String = "",
        gender: String = "",
        example: String = "",
    ) = VocabularyPracticeItem(
        entryId = id,
        headword = headword,
        languageTag = languageTag,
        representativeMeaning = meaning,
        reading = reading,
        pronunciation = pronunciation,
        partOfSpeech = partOfSpeech,
        grammaticalGender = gender,
        example = example,
    )
}
