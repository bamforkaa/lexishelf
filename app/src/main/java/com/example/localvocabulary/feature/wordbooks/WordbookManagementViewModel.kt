package com.example.localvocabulary.feature.wordbooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbookSummary
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordbookManagementUiState(
    val wordbooks: List<VocabularyWordbookSummary> = emptyList(),
    val editingWordbookId: Long? = null,
    val nameInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteWordbook: VocabularyWordbook? = null,
)

sealed interface WordbookManagementAction {
    data class NameChanged(val value: String) : WordbookManagementAction
    data class EditStarted(val wordbook: VocabularyWordbook) : WordbookManagementAction
    data object EditCancelled : WordbookManagementAction
    data object Save : WordbookManagementAction
    data class DeleteRequested(val wordbook: VocabularyWordbook) : WordbookManagementAction
    data object DeleteCancelled : WordbookManagementAction
    data object DeleteConfirmed : WordbookManagementAction
}

private data class WordbookEditorState(
    val editingWordbookId: Long? = null,
    val nameInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val pendingDeleteWordbook: VocabularyWordbook? = null,
)

@HiltViewModel
class WordbookManagementViewModel @Inject constructor(
    private val wordbookRepository: WordbookRepository,
) : ViewModel() {
    private val editorState = MutableStateFlow(WordbookEditorState())

    val uiState: StateFlow<WordbookManagementUiState> = combine(
        wordbookRepository.observeWordbookSummaries(),
        editorState,
    ) { wordbooks, editor ->
        WordbookManagementUiState(
            wordbooks = wordbooks,
            editingWordbookId = editor.editingWordbookId,
            nameInput = editor.nameInput,
            errorMessage = editor.errorMessage,
            isSaving = editor.isSaving,
            pendingDeleteWordbook = editor.pendingDeleteWordbook,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WordbookManagementUiState(),
    )

    fun onAction(action: WordbookManagementAction) {
        when (action) {
            is WordbookManagementAction.NameChanged -> editorState.update {
                it.copy(nameInput = action.value, errorMessage = null)
            }
            is WordbookManagementAction.EditStarted -> editorState.value = WordbookEditorState(
                editingWordbookId = action.wordbook.id,
                nameInput = action.wordbook.name,
            )
            WordbookManagementAction.EditCancelled -> editorState.value = WordbookEditorState()
            WordbookManagementAction.Save -> saveWordbook()
            is WordbookManagementAction.DeleteRequested -> editorState.update {
                it.copy(pendingDeleteWordbook = action.wordbook)
            }
            WordbookManagementAction.DeleteCancelled -> editorState.update {
                it.copy(pendingDeleteWordbook = null)
            }
            WordbookManagementAction.DeleteConfirmed -> viewModelScope.launch {
                val id = editorState.value.pendingDeleteWordbook?.id ?: return@launch
                wordbookRepository.delete(id)
                if (editorState.value.editingWordbookId == id) {
                    editorState.value = WordbookEditorState()
                } else {
                    editorState.update { it.copy(pendingDeleteWordbook = null) }
                }
            }
        }
    }

    private fun saveWordbook() {
        val current = editorState.value
        if (current.isSaving) return
        viewModelScope.launch {
            editorState.update { it.copy(isSaving = true, errorMessage = null) }
            when (wordbookRepository.save(current.editingWordbookId, current.nameInput)) {
                is SaveWordbookResult.Saved -> editorState.value = WordbookEditorState()
                SaveWordbookResult.BlankName -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "단어장 이름을 입력하세요.")
                }
                is SaveWordbookResult.NameConflict -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "같은 이름의 단어장이 이미 있습니다.")
                }
                SaveWordbookResult.NotFound -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "수정할 단어장을 찾을 수 없습니다.")
                }
            }
        }
    }
}
