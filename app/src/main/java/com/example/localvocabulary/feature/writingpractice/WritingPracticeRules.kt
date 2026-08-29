package com.example.localvocabulary.feature.writingpractice

import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeItem
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

enum class PracticeHintKind {
    MEANING,
    READING,
    PRONUNCIATION,
    GRAMMAR,
    EXAMPLE,
}

data class PracticeHint(
    val kind: PracticeHintKind,
    val text: String,
)

data class PracticeQuestion(
    val entryId: Long,
    val headword: String,
    val languageTag: String,
    val primaryHint: PracticeHint,
    val secondaryHints: List<PracticeHint>,
    val expandedHints: List<PracticeHint>,
)

object PracticeQuestionFactory {
    fun create(item: VocabularyPracticeItem): PracticeQuestion? {
        val headword = item.headword.trim()
        val normalizedHeadword = normalizePracticeAnswer(headword)
        val languageTag = VocabularyEntryValidator.normalizeLanguageTag(item.languageTag)
        if (normalizedHeadword.isEmpty() || languageTag == null) return null

        val meaning = item.representativeMeaning.safeHint(
            normalizedHeadword,
            PracticeHintKind.MEANING,
        )
        val reading = item.reading.safeHint(normalizedHeadword, PracticeHintKind.READING)
        val pronunciation = item.pronunciation.safeHint(
            normalizedHeadword,
            PracticeHintKind.PRONUNCIATION,
        )
        val grammar = listOf(item.partOfSpeech, item.grammaticalGender)
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .joinToString(" · ")
            .safeHint(normalizedHeadword, PracticeHintKind.GRAMMAR)
        val example = item.example
            .takeUnless { containsNormalizedHeadword(it, normalizedHeadword) }
            ?.safeHint(normalizedHeadword, PracticeHintKind.EXAMPLE)

        val orderedHints = listOfNotNull(meaning, reading, pronunciation, grammar, example)
        val primary = meaning ?: orderedHints.firstOrNull() ?: return null
        val secondary = listOfNotNull(reading, pronunciation).filterNot { it == primary }
        val expanded = listOfNotNull(grammar, example).filterNot { it == primary }
        return PracticeQuestion(
            entryId = item.entryId,
            headword = headword,
            languageTag = languageTag,
            primaryHint = primary,
            secondaryHints = secondary,
            expandedHints = expanded,
        )
    }

    private fun String.safeHint(
        normalizedHeadword: String,
        kind: PracticeHintKind,
    ): PracticeHint? {
        val value = trim().replace(PRACTICE_WHITESPACE, " ")
        if (value.isEmpty()) return null
        if (normalizeForLeakCheck(value) == normalizeForLeakCheck(normalizedHeadword)) return null
        return PracticeHint(kind, value)
    }

    private fun containsNormalizedHeadword(value: String, normalizedHeadword: String): Boolean {
        val hint = normalizeForLeakCheck(value)
        val answer = normalizeForLeakCheck(normalizedHeadword)
        return answer.isNotEmpty() && hint.contains(answer)
    }

    private fun normalizeForLeakCheck(value: String): String =
        normalizePracticeAnswer(value).lowercase(Locale.ROOT)
}

sealed interface PracticeAnswerResult {
    data object Empty : PracticeAnswerResult
    data object Correct : PracticeAnswerResult
    data object Incorrect : PracticeAnswerResult
}

object PracticeAnswerEvaluator {
    fun evaluate(answer: String, expectedHeadword: String): PracticeAnswerResult {
        val normalizedAnswer = normalizePracticeAnswer(answer)
        if (normalizedAnswer.isEmpty()) return PracticeAnswerResult.Empty
        return if (normalizedAnswer == normalizePracticeAnswer(expectedHeadword)) {
            PracticeAnswerResult.Correct
        } else {
            PracticeAnswerResult.Incorrect
        }
    }
}

fun normalizePracticeAnswer(value: String): String = Normalizer.normalize(
    value.trim().replace(PRACTICE_WHITESPACE, " "),
    Normalizer.Form.NFC,
)

enum class PracticeSessionLength(val maximumQuestionCount: Int?) {
    TEN(10),
    TWENTY(20),
    ALL(null),
}

data class PracticeSession(
    val questions: List<PracticeQuestion>,
    val currentIndex: Int = 0,
    val firstAttemptResults: Map<Long, Boolean> = emptyMap(),
) {
    init {
        require(questions.isNotEmpty()) { "A practice session requires at least one question" }
        require(currentIndex in questions.indices) { "Current practice index is out of range" }
    }

    val currentQuestion: PracticeQuestion
        get() = questions[currentIndex]

    val orderedEntryIds: List<Long>
        get() = questions.map(PracticeQuestion::entryId)

    val isLastQuestion: Boolean
        get() = currentIndex == questions.lastIndex

    fun recordFirstAttempt(correct: Boolean): PracticeSession = copy(
        firstAttemptResults = if (currentQuestion.entryId in firstAttemptResults) {
            firstAttemptResults
        } else {
            firstAttemptResults + (currentQuestion.entryId to correct)
        },
    )

    fun moveNext(): PracticeSession = copy(currentIndex = currentIndex + 1)
}

class PracticeSessionFactory @Inject constructor() {
    fun create(
        items: List<VocabularyPracticeItem>,
        sessionLength: PracticeSessionLength,
        random: Random = Random.Default,
    ): PracticeSession? {
        val questions = items
            .mapNotNull(PracticeQuestionFactory::create)
            .distinctBy(PracticeQuestion::entryId)
            .shuffled(random)
            .let { shuffled ->
                sessionLength.maximumQuestionCount?.let(shuffled::take) ?: shuffled
            }
        return questions.takeIf(List<PracticeQuestion>::isNotEmpty)?.let(::PracticeSession)
    }
}

private val PRACTICE_WHITESPACE = Regex("\\s+")
