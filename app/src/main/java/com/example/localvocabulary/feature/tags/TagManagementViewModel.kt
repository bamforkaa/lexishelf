package com.example.localvocabulary.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TagManagementUiState(
    val tags: List<VocabularyTag> = emptyList(),
    val editingTagId: Long? = null,
    val nameInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

sealed interface TagManagementAction {
    data class NameChanged(val value: String) : TagManagementAction
    data class EditStarted(val tag: VocabularyTag) : TagManagementAction
    data object EditCancelled : TagManagementAction
    data object Save : TagManagementAction
    data class Delete(val tagId: Long) : TagManagementAction
}

private data class TagEditorState(
    val editingTagId: Long? = null,
    val nameInput: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class TagManagementViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val editorState = MutableStateFlow(TagEditorState())

    val uiState: StateFlow<TagManagementUiState> = combine(
        tagRepository.observeTags(),
        editorState,
    ) { tags, editor ->
        TagManagementUiState(
            tags = tags,
            editingTagId = editor.editingTagId,
            nameInput = editor.nameInput,
            errorMessage = editor.errorMessage,
            isSaving = editor.isSaving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TagManagementUiState(),
    )

    fun onAction(action: TagManagementAction) {
        when (action) {
            is TagManagementAction.NameChanged -> editorState.update {
                it.copy(nameInput = action.value, errorMessage = null)
            }
            is TagManagementAction.EditStarted -> editorState.value = TagEditorState(
                editingTagId = action.tag.id,
                nameInput = action.tag.name,
            )
            TagManagementAction.EditCancelled -> editorState.value = TagEditorState()
            TagManagementAction.Save -> saveTag()
            is TagManagementAction.Delete -> viewModelScope.launch {
                tagRepository.delete(action.tagId)
                if (editorState.value.editingTagId == action.tagId) {
                    editorState.value = TagEditorState()
                }
            }
        }
    }

    private fun saveTag() {
        val current = editorState.value
        if (current.isSaving) return
        viewModelScope.launch {
            editorState.update { it.copy(isSaving = true, errorMessage = null) }
            when (tagRepository.save(current.editingTagId, current.nameInput)) {
                is SaveTagResult.Saved -> editorState.value = TagEditorState()
                SaveTagResult.BlankName -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "태그 이름을 입력하세요.")
                }
                SaveTagResult.NameConflict -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "같은 이름의 태그가 이미 있습니다.")
                }
                SaveTagResult.NotFound -> editorState.update {
                    it.copy(isSaving = false, errorMessage = "수정할 태그를 찾을 수 없습니다.")
                }
            }
        }
    }
}
