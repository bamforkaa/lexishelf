package com.example.localvocabulary.feature.worddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.localvocabulary.review.domain.ReviewRepository
import com.example.localvocabulary.review.domain.ReviewState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WordDetailUiState {
    data object Loading : WordDetailUiState
    data class Content(
        val entry: VocabularyEntry, val isDeleting: Boolean = false,
        val reviewStates: List<ReviewState> = emptyList(), val reviewError: String? = null,
    ) : WordDetailUiState
    data object NotFound : WordDetailUiState
    data class Error(val message: String) : WordDetailUiState
}

sealed interface WordDetailAction {
    data object DeleteConfirmed : WordDetailAction
    data class SetReviewEnabled(val senseId: String, val enabled: Boolean) : WordDetailAction
}

sealed interface WordDetailEffect {
    data object Deleted : WordDetailEffect
}

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {
    private val entryId: Long = checkNotNull(savedStateHandle["entryId"])
    private val mutableEffects = MutableSharedFlow<WordDetailEffect>()
    val effects = mutableEffects.asSharedFlow()

    private val reviewError = MutableStateFlow<String?>(null)
    val uiState: StateFlow<WordDetailUiState> = combine(
        vocabularyRepository.observeEntry(entryId), reviewRepository.observeStates(), reviewError,
    ) { entry, reviews, error ->
            if (entry == null) WordDetailUiState.NotFound else WordDetailUiState.Content(
                entry, reviewStates = reviews.filter { state -> entry.senses.any { it.stableId == state.senseStableId } }, reviewError = error,
            )
        }
        .catch { error ->
            emit(WordDetailUiState.Error(error.message ?: "단어를 불러오지 못했습니다."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WordDetailUiState.Loading,
        )

    fun onAction(action: WordDetailAction) {
        when (action) {
            is WordDetailAction.SetReviewEnabled -> viewModelScope.launch {
                try {
                    reviewRepository.setEnabled(action.senseId, action.enabled)
                    reviewError.value = null
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { reviewError.value = error.message ?: "복습 설정을 저장하지 못했습니다." }
            }
            WordDetailAction.DeleteConfirmed -> viewModelScope.launch {
                vocabularyRepository.delete(entryId)
                mutableEffects.emit(WordDetailEffect.Deleted)
            }
        }
    }
}
