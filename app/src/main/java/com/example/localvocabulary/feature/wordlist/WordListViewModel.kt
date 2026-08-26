package com.example.localvocabulary.feature.wordlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
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
import kotlinx.coroutines.flow.flowOf

enum class VocabularyFilterCategory { ALL, LANGUAGE, WORDBOOK, TAG }

data class WordListUiState(
    val isLoading: Boolean = true,
    val entries: List<VocabularyEntry> = emptyList(),
    val tags: List<VocabularyTag> = emptyList(),
    val wordbooks: List<VocabularyWordbook> = emptyList(),
    val languages: List<String> = emptyList(),
    val query: String = "",
    val selectedTagId: Long? = null,
    val selectedTagName: String? = null,
    val selectedWordbookId: Long? = null,
    val selectedWordbookName: String? = null,
    val selectedLanguageTag: String? = null,
    val filterCategory: VocabularyFilterCategory = VocabularyFilterCategory.ALL,
    val isCollectionView: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface WordListAction {
    data class QueryChanged(val value: String) : WordListAction
    data class TagSelected(val tagId: Long?) : WordListAction
    data class WordbookSelected(val wordbookId: Long?) : WordListAction
    data class LanguageSelected(val languageTag: String?) : WordListAction
    data class FilterCategorySelected(val category: VocabularyFilterCategory) : WordListAction
}

private sealed interface EntryLoadResult {
    data class Loaded(val entries: List<VocabularyEntry>) : EntryLoadResult
    data class Failed(val message: String) : EntryLoadResult
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WordListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
    wordbookRepository: WordbookRepository = EmptyWordbookRepository,
) : ViewModel() {
    private val collectionTagId: Long? = savedStateHandle["tagId"]
    private val collectionWordbookId: Long? = savedStateHandle["wordbookId"]
    private val query = MutableStateFlow("")
    private val selectedTagId = MutableStateFlow(collectionTagId)
    private val selectedWordbookId = MutableStateFlow(collectionWordbookId)
    private val selectedLanguageTag = MutableStateFlow<String?>(null)
    private val filterCategory = MutableStateFlow(
        when {
            collectionTagId != null -> VocabularyFilterCategory.TAG
            collectionWordbookId != null -> VocabularyFilterCategory.WORDBOOK
            else -> VocabularyFilterCategory.ALL
        },
    )
    private val tags = tagRepository.observeTags().onEach { availableTags ->
        selectedTagId.update { selectedId ->
            selectedId?.takeIf { id -> availableTags.any { tag -> tag.id == id } }
        }
    }
    private val wordbooks = wordbookRepository.observeWordbooks().onEach { available ->
        selectedWordbookId.update { selectedId ->
            selectedId?.takeIf { id -> available.any { it.id == id } }
        }
    }
    private val languages = vocabularyRepository.observeLanguages().onEach { available ->
        selectedLanguageTag.update { selected -> selected?.takeIf { it in available } }
    }

    private val filters = combine(
        query,
        selectedTagId,
        selectedWordbookId,
        selectedLanguageTag,
    ) { query, tagId, wordbookId, languageTag ->
        EntryFilters(query, tagId, wordbookId, languageTag)
    }

    private val entries = filters
        .flatMapLatest { filters ->
            vocabularyRepository.observeEntries(
                filters.query,
                filters.tagId,
                filters.wordbookId,
                filters.languageTag,
            )
                .map<List<VocabularyEntry>, EntryLoadResult>(EntryLoadResult::Loaded)
                .catch { error ->
                    emit(EntryLoadResult.Failed(error.message ?: "저장된 단어를 불러오지 못했습니다."))
                }
        }

    private val metadata = combine(
        filters,
        tags,
        wordbooks,
        languages,
        filterCategory,
    ) { filters, tags, wordbooks, languages, category ->
        ListMetadata(filters, tags, wordbooks, languages, category)
    }

    val uiState: StateFlow<WordListUiState> = combine(
        metadata,
        entries,
    ) { metadata, entryResult ->
        val filters = metadata.filters
        val tags = metadata.tags
        val wordbooks = metadata.wordbooks
        val languages = metadata.languages
        when (entryResult) {
            is EntryLoadResult.Loaded -> WordListUiState(
                isLoading = false,
                entries = entryResult.entries,
                tags = tags,
                wordbooks = wordbooks,
                languages = languages,
                query = filters.query,
                selectedTagId = filters.tagId,
                selectedTagName = tags.firstOrNull { it.id == filters.tagId }?.name,
                selectedWordbookId = filters.wordbookId,
                selectedWordbookName = wordbooks.firstOrNull { it.id == filters.wordbookId }?.name,
                selectedLanguageTag = filters.languageTag,
                filterCategory = metadata.category,
                isCollectionView = collectionTagId != null || collectionWordbookId != null,
            )
            is EntryLoadResult.Failed -> WordListUiState(
                isLoading = false,
                tags = tags,
                wordbooks = wordbooks,
                languages = languages,
                query = filters.query,
                selectedTagId = filters.tagId,
                selectedTagName = tags.firstOrNull { it.id == filters.tagId }?.name,
                selectedWordbookId = filters.wordbookId,
                selectedWordbookName = wordbooks.firstOrNull { it.id == filters.wordbookId }?.name,
                selectedLanguageTag = filters.languageTag,
                filterCategory = metadata.category,
                isCollectionView = collectionTagId != null || collectionWordbookId != null,
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
            is WordListAction.WordbookSelected -> selectedWordbookId.value = action.wordbookId
            is WordListAction.LanguageSelected -> selectedLanguageTag.value = action.languageTag
            is WordListAction.FilterCategorySelected -> {
                filterCategory.value = action.category
                if (action.category != VocabularyFilterCategory.TAG && collectionTagId == null) {
                    selectedTagId.value = null
                }
                if (
                    action.category != VocabularyFilterCategory.WORDBOOK &&
                    collectionWordbookId == null
                ) {
                    selectedWordbookId.value = null
                }
                if (action.category != VocabularyFilterCategory.LANGUAGE) {
                    selectedLanguageTag.value = null
                }
            }
        }
    }
}

private data class EntryFilters(
    val query: String,
    val tagId: Long?,
    val wordbookId: Long?,
    val languageTag: String?,
)

private data class ListMetadata(
    val filters: EntryFilters,
    val tags: List<VocabularyTag>,
    val wordbooks: List<VocabularyWordbook>,
    val languages: List<String>,
    val category: VocabularyFilterCategory,
)

private object EmptyWordbookRepository : WordbookRepository {
    override fun observeWordbooks(): kotlinx.coroutines.flow.Flow<List<VocabularyWordbook>> =
        flowOf(emptyList())
    override suspend fun save(
        id: Long?,
        name: String,
    ): com.example.localvocabulary.vocabulary.domain.SaveWordbookResult =
        com.example.localvocabulary.vocabulary.domain.SaveWordbookResult.BlankName
    override suspend fun delete(id: Long) = Unit
}
