package com.example.localvocabulary.feature.wordbooks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WordbookSelectionMode { BROWSE, ADD, REMOVE }

enum class WordbookSelectionFilter { ALL, LANGUAGE, TAG }

data class WordbookDetailUiState(
    val isLoading: Boolean = true,
    val wordbook: VocabularyWordbook? = null,
    val entries: List<VocabularyEntry> = emptyList(),
    val membershipEntryIds: Set<Long> = emptySet(),
    val tags: List<VocabularyTag> = emptyList(),
    val languages: List<String> = emptyList(),
    val query: String = "",
    val filterCategory: WordbookSelectionFilter = WordbookSelectionFilter.ALL,
    val selectedLanguageTag: String? = null,
    val selectedTagId: Long? = null,
    val mode: WordbookSelectionMode = WordbookSelectionMode.BROWSE,
    val selectedEntryIds: Set<Long> = emptySet(),
    val isUpdating: Boolean = false,
    val isRemoveConfirmationVisible: Boolean = false,
    val message: String? = null,
    val errorMessage: String? = null,
)

sealed interface WordbookDetailAction {
    data class QueryChanged(val value: String) : WordbookDetailAction
    data object StartAdding : WordbookDetailAction
    data object StartRemoving : WordbookDetailAction
    data object CancelSelection : WordbookDetailAction
    data class EntryToggled(val entryId: Long) : WordbookDetailAction
    data class FilterSelected(val filter: WordbookSelectionFilter) : WordbookDetailAction
    data class LanguageSelected(val languageTag: String?) : WordbookDetailAction
    data class TagSelected(val tagId: Long?) : WordbookDetailAction
    data object AddSelected : WordbookDetailAction
    data object RemoveRequested : WordbookDetailAction
    data object RemoveCancelled : WordbookDetailAction
    data object RemoveConfirmed : WordbookDetailAction
}

private data class WordbookDetailControls(
    val query: String = "",
    val filterCategory: WordbookSelectionFilter = WordbookSelectionFilter.ALL,
    val selectedLanguageTag: String? = null,
    val selectedTagId: Long? = null,
    val mode: WordbookSelectionMode = WordbookSelectionMode.BROWSE,
    val selectedEntryIds: Set<Long> = emptySet(),
    val isUpdating: Boolean = false,
    val isRemoveConfirmationVisible: Boolean = false,
    val message: String? = null,
)

private data class WordbookDetailMetadata(
    val wordbook: VocabularyWordbook?,
    val membershipEntryIds: Set<Long>,
    val tags: List<VocabularyTag>,
    val languages: List<String>,
)

private sealed interface WordbookEntriesResult {
    data class Loaded(val entries: List<VocabularyEntry>) : WordbookEntriesResult
    data class Failed(val message: String) : WordbookEntriesResult
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WordbookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
    private val wordbookRepository: WordbookRepository,
) : ViewModel() {
    private val wordbookId = requireNotNull(savedStateHandle.get<Long>("wordbookId"))
    private val controls = MutableStateFlow(WordbookDetailControls())

    private val tags = tagRepository.observeTags()
    private val languages = vocabularyRepository.observeLanguages()
    private val metadata = combine(
        wordbookRepository.observeWordbooks(),
        wordbookRepository.observeEntryIds(wordbookId),
        tags,
        languages,
    ) { wordbooks, membershipIds, tags, languages ->
        WordbookDetailMetadata(
            wordbook = wordbooks.firstOrNull { it.id == wordbookId },
            membershipEntryIds = membershipIds,
            tags = tags,
            languages = languages,
        )
    }

    private val entries: Flow<WordbookEntriesResult> = controls
        .map { state ->
            EntryQuery(
                query = state.query,
                tagId = state.selectedTagId,
                languageTag = state.selectedLanguageTag,
                wordbookId = if (state.mode == WordbookSelectionMode.ADD) null else wordbookId,
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            vocabularyRepository.observeEntries(
                query = query.query,
                tagId = query.tagId,
                wordbookId = query.wordbookId,
                languageTag = query.languageTag,
            ).map<List<VocabularyEntry>, WordbookEntriesResult>(WordbookEntriesResult::Loaded)
                .catch { error ->
                    emit(
                        WordbookEntriesResult.Failed(
                            error.message ?: "단어를 불러오지 못했습니다.",
                        ),
                    )
                }
        }

    val uiState: StateFlow<WordbookDetailUiState> = combine(
        metadata,
        entries,
        controls,
    ) { metadata, entriesResult, controls ->
        val loadedEntries = (entriesResult as? WordbookEntriesResult.Loaded)?.entries.orEmpty()
        WordbookDetailUiState(
            isLoading = false,
            wordbook = metadata.wordbook,
            entries = loadedEntries,
            membershipEntryIds = metadata.membershipEntryIds,
            tags = metadata.tags,
            languages = metadata.languages,
            query = controls.query,
            filterCategory = controls.filterCategory,
            selectedLanguageTag = controls.selectedLanguageTag,
            selectedTagId = controls.selectedTagId,
            mode = controls.mode,
            selectedEntryIds = controls.selectedEntryIds,
            isUpdating = controls.isUpdating,
            isRemoveConfirmationVisible = controls.isRemoveConfirmationVisible,
            message = controls.message,
            errorMessage = when {
                metadata.wordbook == null -> "단어장을 찾을 수 없습니다."
                entriesResult is WordbookEntriesResult.Failed -> entriesResult.message
                else -> null
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordbookDetailUiState(),
    )

    fun onAction(action: WordbookDetailAction) {
        when (action) {
            is WordbookDetailAction.QueryChanged -> controls.update {
                it.copy(query = action.value, message = null)
            }
            WordbookDetailAction.StartAdding -> beginSelection(WordbookSelectionMode.ADD)
            WordbookDetailAction.StartRemoving -> beginSelection(WordbookSelectionMode.REMOVE)
            WordbookDetailAction.CancelSelection -> controls.value = WordbookDetailControls()
            is WordbookDetailAction.EntryToggled -> toggleEntry(action.entryId)
            is WordbookDetailAction.FilterSelected -> controls.update { current ->
                current.copy(
                    filterCategory = action.filter,
                    selectedLanguageTag = current.selectedLanguageTag
                        .takeIf { action.filter == WordbookSelectionFilter.LANGUAGE },
                    selectedTagId = current.selectedTagId
                        .takeIf { action.filter == WordbookSelectionFilter.TAG },
                )
            }
            is WordbookDetailAction.LanguageSelected -> controls.update {
                it.copy(selectedLanguageTag = action.languageTag)
            }
            is WordbookDetailAction.TagSelected -> controls.update {
                it.copy(selectedTagId = action.tagId)
            }
            WordbookDetailAction.AddSelected -> addSelected()
            WordbookDetailAction.RemoveRequested -> controls.update {
                if (it.selectedEntryIds.isEmpty()) it else it.copy(isRemoveConfirmationVisible = true)
            }
            WordbookDetailAction.RemoveCancelled -> controls.update {
                it.copy(isRemoveConfirmationVisible = false)
            }
            WordbookDetailAction.RemoveConfirmed -> removeSelected()
        }
    }

    private fun beginSelection(mode: WordbookSelectionMode) {
        controls.value = WordbookDetailControls(mode = mode)
    }

    private fun toggleEntry(entryId: Long) {
        val currentState = uiState.value
        if (
            currentState.mode == WordbookSelectionMode.ADD &&
            entryId in currentState.membershipEntryIds
        ) {
            return
        }
        controls.update { current ->
            val selection = current.selectedEntryIds.toMutableSet()
            if (!selection.add(entryId)) selection.remove(entryId)
            current.copy(selectedEntryIds = selection, message = null)
        }
    }

    private fun addSelected() {
        val entryIds = controls.value.selectedEntryIds
        if (entryIds.isEmpty() || controls.value.isUpdating) return
        viewModelScope.launch {
            controls.update { it.copy(isUpdating = true, message = null) }
            runCatching { wordbookRepository.addEntries(wordbookId, entryIds) }
                .onSuccess { added ->
                    controls.value = WordbookDetailControls(message = "${added}개 단어를 추가했습니다.")
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isUpdating = false,
                            message = error.message ?: "단어를 추가하지 못했습니다.",
                        )
                    }
                }
        }
    }

    private fun removeSelected() {
        val entryIds = controls.value.selectedEntryIds
        if (entryIds.isEmpty() || controls.value.isUpdating) return
        viewModelScope.launch {
            controls.update {
                it.copy(isUpdating = true, isRemoveConfirmationVisible = false, message = null)
            }
            runCatching { wordbookRepository.removeEntries(wordbookId, entryIds) }
                .onSuccess { removed ->
                    controls.value = WordbookDetailControls(
                        message = "${removed}개 단어를 단어장에서 제거했습니다.",
                    )
                }
                .onFailure { error ->
                    controls.update {
                        it.copy(
                            isUpdating = false,
                            message = error.message ?: "단어장에서 제거하지 못했습니다.",
                        )
                    }
                }
        }
    }

    private data class EntryQuery(
        val query: String,
        val tagId: Long?,
        val languageTag: String?,
        val wordbookId: Long?,
    )
}
