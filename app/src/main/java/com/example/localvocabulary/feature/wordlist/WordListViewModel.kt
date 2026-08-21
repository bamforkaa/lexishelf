package com.example.localvocabulary.feature.wordlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class WordListUiState(
    val isLoading: Boolean = true,
    val entries: List<VocabularyEntry> = emptyList(),
    val tags: List<VocabularyTag> = emptyList(),
    val query: String = "",
    val selectedTagId: Long? = null,
    val errorMessage: String? = null,
)

sealed interface WordListAction {
    data class QueryChanged(val value: String) : WordListAction
    data class TagSelected(val tagId: Long?) : WordListAction
}

private sealed interface EntryLoadResult {
    data class Loaded(val entries: List<VocabularyEntry>) : EntryLoadResult
    data class Failed(val message: String) : EntryLoadResult
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WordListViewModel @Inject constructor(
    vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedTagId = MutableStateFlow<Long?>(null)
    private val tags = tagRepository.observeTags().onEach { availableTags ->
        selectedTagId.update { selectedId ->
            selectedId?.takeIf { id -> availableTags.any { tag -> tag.id == id } }
        }
    }

    private val entries = combine(query, selectedTagId, ::Pair)
        .flatMapLatest { (query, tagId) ->
            vocabularyRepository.observeEntries(query, tagId)
                .map<List<VocabularyEntry>, EntryLoadResult>(EntryLoadResult::Loaded)
                .catch { error ->
                    emit(EntryLoadResult.Failed(error.message ?: "저장된 단어를 불러오지 못했습니다."))
                }
        }

    val uiState: StateFlow<WordListUiState> = combine(
        query,
        selectedTagId,
        tags,
        entries,
    ) { currentQuery, currentTagId, tags, entryResult ->
        when (entryResult) {
            is EntryLoadResult.Loaded -> WordListUiState(
                isLoading = false,
                entries = entryResult.entries,
                tags = tags,
                query = currentQuery,
                selectedTagId = currentTagId,
            )
            is EntryLoadResult.Failed -> WordListUiState(
                isLoading = false,
                tags = tags,
                query = currentQuery,
                selectedTagId = currentTagId,
                errorMessage = entryResult.message,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordListUiState(),
    )

    fun onAction(action: WordListAction) {
        when (action) {
            is WordListAction.QueryChanged -> query.value = action.value
            is WordListAction.TagSelected -> selectedTagId.value = action.tagId
        }
    }
}
