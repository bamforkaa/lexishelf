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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WordDetailUiState {
    data object Loading : WordDetailUiState
    data class Content(val entry: VocabularyEntry, val isDeleting: Boolean = false) : WordDetailUiState
    data object NotFound : WordDetailUiState
    data class Error(val message: String) : WordDetailUiState
}

sealed interface WordDetailAction {
    data object DeleteConfirmed : WordDetailAction
}

sealed interface WordDetailEffect {
    data object Deleted : WordDetailEffect
}

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
) : ViewModel() {
    private val entryId: Long = checkNotNull(savedStateHandle["entryId"])
    private val mutableEffects = MutableSharedFlow<WordDetailEffect>()
    val effects = mutableEffects.asSharedFlow()

    val uiState: StateFlow<WordDetailUiState> = vocabularyRepository.observeEntry(entryId)
        .map<VocabularyEntry?, WordDetailUiState> { entry ->
            entry?.let(WordDetailUiState::Content) ?: WordDetailUiState.NotFound
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
            WordDetailAction.DeleteConfirmed -> viewModelScope.launch {
                vocabularyRepository.delete(entryId)
                mutableEffects.emit(WordDetailEffect.Deleted)
            }
        }
    }
}
