package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationResult
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditableExample(
    val key: Long,
    val text: String = "",
)

data class EditableSense(
    val key: Long,
    val meaning: String = "",
    val partOfSpeech: String = "",
    val examples: List<EditableExample> = listOf(EditableExample(-2)),
)

data class WordEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val entryId: Long? = null,
    val headword: String = "",
    val languageTag: String = "en",
    val senses: List<EditableSense> = listOf(EditableSense(-1)),
    val notes: String = "",
    val availableTags: List<VocabularyTag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val validationError: VocabularyValidationError? = null,
    val loadErrorMessage: String? = null,
    val saveErrorMessage: String? = null,
)

sealed interface WordEditorAction {
    data class HeadwordChanged(val value: String) : WordEditorAction
    data class LanguageTagChanged(val value: String) : WordEditorAction
    data class NotesChanged(val value: String) : WordEditorAction
    data object AddSense : WordEditorAction
    data class RemoveSense(val senseKey: Long) : WordEditorAction
    data class MeaningChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class PartOfSpeechChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class AddExample(val senseKey: Long) : WordEditorAction
    data class RemoveExample(val senseKey: Long, val exampleKey: Long) : WordEditorAction
    data class ExampleChanged(val senseKey: Long, val exampleKey: Long, val value: String) : WordEditorAction
    data class TagToggled(val tagId: Long) : WordEditorAction
    data object Save : WordEditorAction
}

sealed interface WordEditorEffect {
    data class Saved(val entryId: Long) : WordEditorEffect
}

@HiltViewModel
class WordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val requestedEntryId: Long? = savedStateHandle["entryId"]
    private val mutableUiState = MutableStateFlow(WordEditorUiState(entryId = requestedEntryId))
    val uiState: StateFlow<WordEditorUiState> = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<WordEditorEffect>()
    val effects = mutableEffects.asSharedFlow()
    private var nextLocalKey = -10L

    init {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                mutableUiState.update { it.copy(availableTags = tags) }
            }
        }
        viewModelScope.launch {
            if (requestedEntryId == null) {
                val settings = settingsRepository.settings.first()
                mutableUiState.update {
                    it.copy(isLoading = false, languageTag = settings.defaultLanguageTag)
                }
            } else {
                runCatching { vocabularyRepository.observeEntry(requestedEntryId).first() }
                    .onSuccess { entry ->
                        mutableUiState.update { state ->
                            if (entry == null) {
                                state.copy(isLoading = false, loadErrorMessage = "단어를 찾을 수 없습니다.")
                            } else {
                                state.copy(
                                    isLoading = false,
                                    headword = entry.headword,
                                    languageTag = entry.languageTag,
                                    senses = entry.senses.map { sense ->
                                        EditableSense(
                                            key = sense.id,
                                            meaning = sense.meaning,
                                            partOfSpeech = sense.partOfSpeech,
                                            examples = sense.examples.map { EditableExample(it.id, it.text) }
                                                .ifEmpty { listOf(EditableExample(newKey())) },
                                        )
                                    },
                                    notes = entry.notes,
                                    selectedTagIds = entry.tags.mapTo(mutableSetOf()) { it.id },
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                loadErrorMessage = error.message ?: "단어를 불러오지 못했습니다.",
                            )
                        }
                    }
            }
        }
    }

    fun onAction(action: WordEditorAction) {
        when (action) {
            is WordEditorAction.HeadwordChanged -> updateForm { copy(headword = action.value) }
            is WordEditorAction.LanguageTagChanged -> updateForm { copy(languageTag = action.value) }
            is WordEditorAction.NotesChanged -> updateForm { copy(notes = action.value) }
            WordEditorAction.AddSense -> updateForm {
                copy(senses = senses + EditableSense(key = newKey(), examples = listOf(EditableExample(newKey()))))
            }
            is WordEditorAction.RemoveSense -> updateForm {
                copy(senses = senses.filterNot { it.key == action.senseKey })
            }
            is WordEditorAction.MeaningChanged -> updateSense(action.senseKey) {
                copy(meaning = action.value)
            }
            is WordEditorAction.PartOfSpeechChanged -> updateSense(action.senseKey) {
                copy(partOfSpeech = action.value)
            }
            is WordEditorAction.AddExample -> updateSense(action.senseKey) {
                copy(examples = examples + EditableExample(newKey()))
            }
            is WordEditorAction.RemoveExample -> updateSense(action.senseKey) {
                copy(examples = examples.filterNot { it.key == action.exampleKey })
            }
            is WordEditorAction.ExampleChanged -> updateSense(action.senseKey) {
                copy(
                    examples = examples.map { example ->
                        if (example.key == action.exampleKey) example.copy(text = action.value) else example
                    },
                )
            }
            is WordEditorAction.TagToggled -> updateForm {
                val selection = selectedTagIds.toMutableSet()
                if (!selection.add(action.tagId)) selection.remove(action.tagId)
                copy(selectedTagIds = selection)
            }
            WordEditorAction.Save -> save()
        }
    }

    private fun save() {
        val state = mutableUiState.value
        if (state.isSaving) return
        val result = VocabularyEntryValidator.validate(
            VocabularyEntryDraft(
                id = state.entryId,
                headword = state.headword,
                languageTag = state.languageTag,
                senses = state.senses.map { sense ->
                    VocabularySenseDraft(
                        meaning = sense.meaning,
                        partOfSpeech = sense.partOfSpeech,
                        examples = sense.examples.map { it.text },
                    )
                },
                notes = state.notes,
                tagIds = state.selectedTagIds,
            ),
        )
        if (result is VocabularyValidationResult.Invalid) {
            mutableUiState.update { it.copy(validationError = result.error) }
            return
        }

        val draft = (result as VocabularyValidationResult.Valid).draft
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
            runCatching { vocabularyRepository.save(draft) }
                .onSuccess { id ->
                    mutableUiState.update { it.copy(isSaving = false, entryId = id) }
                    mutableEffects.emit(WordEditorEffect.Saved(id))
                }
                .onFailure { error ->
                    mutableUiState.update {
                        it.copy(
                            isSaving = false,
                            saveErrorMessage = error.message ?: "단어를 저장하지 못했습니다.",
                        )
                    }
                }
        }
    }

    private fun updateForm(transform: WordEditorUiState.() -> WordEditorUiState) {
        mutableUiState.update { it.transform().copy(validationError = null, saveErrorMessage = null) }
    }

    private fun updateSense(key: Long, transform: EditableSense.() -> EditableSense) {
        updateForm {
            copy(senses = senses.map { if (it.key == key) it.transform() else it })
        }
    }

    private fun newKey(): Long = nextLocalKey--
}
