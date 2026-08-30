package com.example.localvocabulary.feature.writingpractice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeFilter
import com.example.localvocabulary.vocabulary.domain.VocabularyPracticeItem
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PracticeScopeType { ALL, LANGUAGE, WORDBOOK, TAG }

enum class PracticeInputMode { HANDWRITING, KEYBOARD }

enum class PracticePhase { SETUP, QUESTION, COMPLETE }

data class PracticeAnswerFeedback(
    val isCorrect: Boolean,
    val expectedHeadword: String,
    val submittedAnswer: String,
)

data class WritingPracticeUiState(
    val phase: PracticePhase = PracticePhase.SETUP,
    val languages: List<String> = emptyList(),
    val wordbooks: List<VocabularyWordbook> = emptyList(),
    val tags: List<VocabularyTag> = emptyList(),
    val scopeType: PracticeScopeType = PracticeScopeType.ALL,
    val selectedLanguageTag: String? = null,
    val selectedWordbookId: Long? = null,
    val selectedTagId: Long? = null,
    val sessionLength: PracticeSessionLength = PracticeSessionLength.TEN,
    val eligibleCount: Int = 0,
    val excludedCount: Int = 0,
    val isLoadingEligibility: Boolean = true,
    val errorMessage: String? = null,
    val currentQuestion: PracticeQuestion? = null,
    val currentIndex: Int = 0,
    val sessionTotal: Int = 0,
    val sessionEntryIds: List<Long> = emptyList(),
    val answer: String = "",
    val inputMode: PracticeInputMode = PracticeInputMode.HANDWRITING,
    val answerError: String? = null,
    val feedback: PracticeAnswerFeedback? = null,
    val showExpandedHints: Boolean = false,
    val firstAttemptCorrect: Int = 0,
    val firstAttemptIncorrect: Int = 0,
    val handwritingSessionKey: Long = 0,
) {
    val hasCompleteScopeSelection: Boolean
        get() = when (scopeType) {
            PracticeScopeType.ALL -> true
            PracticeScopeType.LANGUAGE -> selectedLanguageTag != null
            PracticeScopeType.WORDBOOK -> selectedWordbookId != null
            PracticeScopeType.TAG -> selectedTagId != null
        }

    val canStart: Boolean
        get() = phase == PracticePhase.SETUP && hasCompleteScopeSelection &&
            !isLoadingEligibility && eligibleCount > 0 && errorMessage == null
}

sealed interface WritingPracticeAction {
    data class ScopeTypeSelected(val type: PracticeScopeType) : WritingPracticeAction
    data class LanguageSelected(val languageTag: String) : WritingPracticeAction
    data class WordbookSelected(val wordbookId: Long) : WritingPracticeAction
    data class TagSelected(val tagId: Long) : WritingPracticeAction
    data class SessionLengthSelected(val length: PracticeSessionLength) : WritingPracticeAction
    data object Start : WritingPracticeAction
    data class InputModeSelected(val mode: PracticeInputMode) : WritingPracticeAction
    data class AnswerChanged(val value: String) : WritingPracticeAction
    data object ToggleExpandedHints : WritingPracticeAction
    data object Submit : WritingPracticeAction
    data object Retry : WritingPracticeAction
    data object Next : WritingPracticeAction
    data object Restart : WritingPracticeAction
    data object ReturnToSetup : WritingPracticeAction
}

@HiltViewModel
class WritingPracticeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
    wordbookRepository: WordbookRepository,
    private val sessionFactory: PracticeSessionFactory,
) : ViewModel() {
    private val preselectedWordbookId = savedStateHandle.get<Long>("practiceWordbookId")
    private val mutableUiState = MutableStateFlow(
        WritingPracticeUiState(
            scopeType = if (preselectedWordbookId == null) {
                PracticeScopeType.ALL
            } else {
                PracticeScopeType.WORDBOOK
            },
            selectedWordbookId = preselectedWordbookId,
        ),
    )
    val uiState: StateFlow<WritingPracticeUiState> = mutableUiState.asStateFlow()

    private var eligibilityJob: Job? = null
    private var eligibleItems: List<VocabularyPracticeItem> = emptyList()
    private var session: PracticeSession? = null

    init {
        viewModelScope.launch {
            combine(
                vocabularyRepository.observeLanguages(),
                wordbookRepository.observeWordbooks(),
                tagRepository.observeTags(),
            ) { languages, wordbooks, tags -> Triple(languages, wordbooks, tags) }
                .collect { (languages, wordbooks, tags) ->
                    mutableUiState.update { current ->
                        current.copy(
                            languages = languages,
                            wordbooks = wordbooks,
                            tags = tags,
                        )
                    }
                    if (mutableUiState.value.phase == PracticePhase.SETUP) {
                        refreshEligibility()
                    }
                }
        }
    }

    fun onAction(action: WritingPracticeAction) {
        when (action) {
            is WritingPracticeAction.ScopeTypeSelected -> selectScopeType(action.type)
            is WritingPracticeAction.LanguageSelected -> {
                mutableUiState.update { it.copy(selectedLanguageTag = action.languageTag) }
                refreshEligibility()
            }
            is WritingPracticeAction.WordbookSelected -> {
                mutableUiState.update { it.copy(selectedWordbookId = action.wordbookId) }
                refreshEligibility()
            }
            is WritingPracticeAction.TagSelected -> {
                mutableUiState.update { it.copy(selectedTagId = action.tagId) }
                refreshEligibility()
            }
            is WritingPracticeAction.SessionLengthSelected -> mutableUiState.update {
                it.copy(sessionLength = action.length)
            }
            WritingPracticeAction.Start -> startSession()
            is WritingPracticeAction.InputModeSelected -> selectInputMode(action.mode)
            is WritingPracticeAction.AnswerChanged -> mutableUiState.update {
                if (it.feedback == null) it.copy(answer = action.value, answerError = null) else it
            }
            WritingPracticeAction.ToggleExpandedHints -> mutableUiState.update {
                it.copy(showExpandedHints = !it.showExpandedHints)
            }
            WritingPracticeAction.Submit -> submitAnswer()
            WritingPracticeAction.Retry -> retryQuestion()
            WritingPracticeAction.Next -> moveNext()
            WritingPracticeAction.Restart -> startSession()
            WritingPracticeAction.ReturnToSetup -> returnToSetup()
        }
    }

    private fun selectScopeType(type: PracticeScopeType) {
        mutableUiState.update {
            it.copy(
                scopeType = type,
                selectedLanguageTag = it.selectedLanguageTag.takeIf {
                    type == PracticeScopeType.LANGUAGE
                },
                selectedWordbookId = it.selectedWordbookId.takeIf {
                    type == PracticeScopeType.WORDBOOK
                },
                selectedTagId = it.selectedTagId.takeIf { type == PracticeScopeType.TAG },
                errorMessage = null,
            )
        }
        refreshEligibility()
    }

    private fun refreshEligibility() {
        eligibilityJob?.cancel()
        val state = mutableUiState.value
        val filter = state.toFilterOrNull()
        if (filter == null) {
            eligibleItems = emptyList()
            mutableUiState.update {
                it.copy(
                    eligibleCount = 0,
                    excludedCount = 0,
                    isLoadingEligibility = false,
                    errorMessage = null,
                )
            }
            return
        }
        mutableUiState.update {
            it.copy(isLoadingEligibility = true, errorMessage = null)
        }
        eligibilityJob = viewModelScope.launch {
            try {
                val candidates = vocabularyRepository.findPracticeItems(filter)
                val eligible = candidates.filter { PracticeQuestionFactory.create(it) != null }
                eligibleItems = eligible
                mutableUiState.update {
                    it.copy(
                        eligibleCount = eligible.size,
                        excludedCount = candidates.size - eligible.size,
                        isLoadingEligibility = false,
                        errorMessage = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                eligibleItems = emptyList()
                mutableUiState.update {
                    it.copy(
                        eligibleCount = 0,
                        excludedCount = 0,
                        isLoadingEligibility = false,
                        errorMessage = error.message ?: "연습할 단어를 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    private fun startSession() {
        val state = mutableUiState.value
        if (state.phase == PracticePhase.SETUP && !state.canStart) return
        val created = sessionFactory.create(eligibleItems, state.sessionLength) ?: return
        session = created
        mutableUiState.update {
            it.copy(
                phase = PracticePhase.QUESTION,
                currentQuestion = created.currentQuestion,
                currentIndex = 0,
                sessionTotal = created.questions.size,
                sessionEntryIds = created.orderedEntryIds,
                answer = "",
                answerError = null,
                feedback = null,
                showExpandedHints = false,
                firstAttemptCorrect = 0,
                firstAttemptIncorrect = 0,
                handwritingSessionKey = it.handwritingSessionKey + 1,
            )
        }
    }

    private fun selectInputMode(mode: PracticeInputMode) {
        mutableUiState.update {
            if (it.feedback != null || it.inputMode == mode) it else it.copy(
                inputMode = mode,
                answer = "",
                answerError = null,
                handwritingSessionKey = it.handwritingSessionKey + 1,
            )
        }
    }

    private fun submitAnswer() {
        val currentSession = session ?: return
        if (mutableUiState.value.feedback != null) return
        when (
            val result = PracticeAnswerEvaluator.evaluate(
                mutableUiState.value.answer,
                currentSession.currentQuestion.headword,
            )
        ) {
            PracticeAnswerResult.Empty -> mutableUiState.update {
                it.copy(answerError = "답을 입력하세요.")
            }
            PracticeAnswerResult.Correct,
            PracticeAnswerResult.Incorrect,
            -> {
                val isCorrect = result == PracticeAnswerResult.Correct
                val updatedSession = currentSession.recordFirstAttempt(isCorrect)
                session = updatedSession
                mutableUiState.update {
                    it.copy(
                        answer = normalizePracticeAnswer(it.answer),
                        answerError = null,
                        feedback = PracticeAnswerFeedback(
                            isCorrect = isCorrect,
                            expectedHeadword = currentSession.currentQuestion.headword,
                            submittedAnswer = normalizePracticeAnswer(it.answer),
                        ),
                        firstAttemptCorrect = updatedSession.firstAttemptResults.values.count {
                            correct -> correct
                        },
                        firstAttemptIncorrect = updatedSession.firstAttemptResults.values.count {
                            correct -> !correct
                        },
                    )
                }
            }
        }
    }

    private fun retryQuestion() {
        if (mutableUiState.value.feedback?.isCorrect != false) return
        mutableUiState.update {
            it.copy(
                answer = "",
                answerError = null,
                feedback = null,
                handwritingSessionKey = it.handwritingSessionKey + 1,
            )
        }
    }

    private fun moveNext() {
        val currentSession = session ?: return
        if (mutableUiState.value.feedback == null) return
        if (currentSession.isLastQuestion) {
            mutableUiState.update {
                it.copy(
                    phase = PracticePhase.COMPLETE,
                    currentQuestion = null,
                    answer = "",
                    feedback = null,
                    answerError = null,
                    handwritingSessionKey = it.handwritingSessionKey + 1,
                )
            }
            return
        }
        val updatedSession = currentSession.moveNext()
        session = updatedSession
        mutableUiState.update {
            it.copy(
                currentQuestion = updatedSession.currentQuestion,
                currentIndex = updatedSession.currentIndex,
                answer = "",
                answerError = null,
                feedback = null,
                showExpandedHints = false,
                handwritingSessionKey = it.handwritingSessionKey + 1,
            )
        }
    }

    private fun returnToSetup() {
        session = null
        mutableUiState.update {
            it.copy(
                phase = PracticePhase.SETUP,
                currentQuestion = null,
                currentIndex = 0,
                sessionTotal = 0,
                sessionEntryIds = emptyList(),
                answer = "",
                answerError = null,
                feedback = null,
                showExpandedHints = false,
                firstAttemptCorrect = 0,
                firstAttemptIncorrect = 0,
                handwritingSessionKey = it.handwritingSessionKey + 1,
            )
        }
        refreshEligibility()
    }

    private fun WritingPracticeUiState.toFilterOrNull(): VocabularyPracticeFilter? = when (
        scopeType
    ) {
        PracticeScopeType.ALL -> VocabularyPracticeFilter()
        PracticeScopeType.LANGUAGE -> selectedLanguageTag?.let {
            VocabularyPracticeFilter(languageTag = it)
        }
        PracticeScopeType.WORDBOOK -> selectedWordbookId?.let {
            VocabularyPracticeFilter(wordbookId = it)
        }
        PracticeScopeType.TAG -> selectedTagId?.let { VocabularyPracticeFilter(tagId = it) }
    }
}
