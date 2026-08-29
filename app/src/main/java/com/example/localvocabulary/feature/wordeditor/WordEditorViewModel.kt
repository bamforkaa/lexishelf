package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryLemmaCandidate
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyQuery
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResolver
import com.example.localvocabulary.dictionary.domain.DictionaryMorphologyResult
import com.example.localvocabulary.dictionary.domain.NoOpDictionaryMorphologyResolver
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import com.example.localvocabulary.dictionary.reference.ExternalDictionaryReference
import com.example.localvocabulary.dictionary.reference.ExternalDictionaryReferenceProvider
import com.example.localvocabulary.dictionary.reference.NaverDictionaryLinkProvider
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.PronunciationNotation
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyGrammaticalGender
import com.example.localvocabulary.vocabulary.domain.VocabularyPronunciationDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyWordbook
import com.example.localvocabulary.vocabulary.domain.WordbookRepository
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationError
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationResult
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

data class EditableExample(
    val key: Long,
    val text: String = "",
)

data class SuggestionSenseContribution(
    val meaning: String,
    val partOfSpeech: String,
    val exampleTextsByKey: Map<Long, String>,
    val grammaticalGender: String,
)

data class EditablePronunciation(
    val key: Long,
    val stableId: String? = null,
    val notation: PronunciationNotation = PronunciationNotation.IPA,
    val value: String = "",
    val languageTag: String? = null,
    val provenance: DictionaryProvenance? = null,
    val suggestionKeys: Set<String> = emptySet(),
    val sessionSuggestionKeys: Set<String> = emptySet(),
    val importedValue: String? = null,
    val isUserEdited: Boolean = false,
)

data class EditableSense(
    val key: Long,
    val meaning: String = "",
    val partOfSpeech: String = "",
    val examples: List<EditableExample> = listOf(EditableExample(-2)),
    val provenance: DictionaryProvenance? = null,
    val importSuggestionKey: String? = null,
    val sessionContribution: SuggestionSenseContribution? = null,
    val grammaticalGender: String = "",
    val isGrammaticalGenderVisible: Boolean = false,
)

data class WordEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val entryId: Long? = null,
    val headword: String = "",
    val languageTag: String = "en",
    val userLanguageTags: Set<String> = emptySet(),
    val reading: String = "",
    val readingProvenance: DictionaryProvenance? = null,
    val isReadingUserEdited: Boolean = false,
    val readingSuggestionKeys: Set<String> = emptySet(),
    val sessionReadingSuggestionKeys: Set<String> = emptySet(),
    val pronunciations: List<EditablePronunciation> = emptyList(),
    val senses: List<EditableSense> = listOf(EditableSense(-1)),
    val notes: String = "",
    val availableTags: List<VocabularyTag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val availableWordbooks: List<VocabularyWordbook> = emptyList(),
    val selectedWordbookIds: Set<Long> = emptySet(),
    val isTagCreatorVisible: Boolean = false,
    val newTagName: String = "",
    val tagCreationError: String? = null,
    val tagCreationMessage: String? = null,
    val isCreatingTag: Boolean = false,
    val isWordbookCreatorVisible: Boolean = false,
    val newWordbookName: String = "",
    val wordbookCreationError: String? = null,
    val wordbookCreationMessage: String? = null,
    val isCreatingWordbook: Boolean = false,
    val dictionaryLanguagePairs: List<DictionaryLanguagePair> = emptyList(),
    val isDictionarySearchInProgress: Boolean = false,
    val dictionarySuggestionGroups: List<DictionarySuggestionGroup> = emptyList(),
    val synthesizedSuggestionGroups: List<SynthesizedSuggestionGroup> = emptyList(),
    val dictionarySuggestionMessage: String? = null,
    val dictionaryReference: ExternalDictionaryEntry? = null,
    val dictionaryReferenceSuggestionKey: String? = null,
    val externalDictionaryReference: ExternalDictionaryReference? = null,
    val selectedSuggestionKeys: Set<String> = emptySet(),
    val validationError: VocabularyValidationError? = null,
    val loadErrorMessage: String? = null,
    val saveErrorMessage: String? = null,
    val duplicateCandidate: VocabularyEntry? = null,
)

sealed interface WordEditorAction {
    data class HeadwordChanged(val value: String) : WordEditorAction
    data class LanguageTagChanged(val value: String) : WordEditorAction
    data class UserLanguageAdded(val languageTag: String) : WordEditorAction
    data class ReadingChanged(val value: String) : WordEditorAction
    data object AddPronunciation : WordEditorAction
    data class RemovePronunciation(val pronunciationKey: Long) : WordEditorAction
    data class PronunciationValueChanged(
        val pronunciationKey: Long,
        val value: String,
    ) : WordEditorAction
    data class PronunciationNotationChanged(
        val pronunciationKey: Long,
        val notation: PronunciationNotation,
    ) : WordEditorAction
    data class DictionarySuggestionSelected(
        val entry: ExternalDictionaryEntry,
    ) : WordEditorAction
    data object OpenExternalDictionaryReference : WordEditorAction
    data class NotesChanged(val value: String) : WordEditorAction
    data object AddSense : WordEditorAction
    data class RemoveSense(val senseKey: Long) : WordEditorAction
    data class MeaningChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class PartOfSpeechChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class GrammaticalGenderChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class GrammaticalGenderVisibilityChanged(
        val senseKey: Long,
        val visible: Boolean,
    ) : WordEditorAction
    data class AddExample(val senseKey: Long) : WordEditorAction
    data class RemoveExample(val senseKey: Long, val exampleKey: Long) : WordEditorAction
    data class ExampleChanged(
        val senseKey: Long,
        val exampleKey: Long,
        val value: String,
    ) : WordEditorAction
    data class TagToggled(val tagId: Long) : WordEditorAction
    data object CreateTagRequested : WordEditorAction
    data class NewTagNameChanged(val value: String) : WordEditorAction
    data object CreateTagConfirmed : WordEditorAction
    data object CreateTagDismissed : WordEditorAction
    data class WordbookToggled(val wordbookId: Long) : WordEditorAction
    data object CreateWordbookRequested : WordEditorAction
    data class NewWordbookNameChanged(val value: String) : WordEditorAction
    data object CreateWordbookConfirmed : WordEditorAction
    data object CreateWordbookDismissed : WordEditorAction
    data object OpenExistingDuplicate : WordEditorAction
    data object SaveDuplicateAnyway : WordEditorAction
    data object DismissDuplicateWarning : WordEditorAction
    data object Save : WordEditorAction
}

sealed interface WordEditorEffect {
    data class Saved(val entryId: Long) : WordEditorEffect
    data class OpenExternalDictionaryReference(val uri: String) : WordEditorEffect
    data class OpenExistingEntry(val entryId: Long) : WordEditorEffect
}

@OptIn(FlowPreview::class)
@HiltViewModel
class WordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    private val tagRepository: TagRepository,
    private val settingsRepository: SettingsRepository,
    private val dictionaryProviderRegistry: DictionaryProviderRegistry,
    private val dictionaryMorphologyResolver: DictionaryMorphologyResolver =
        NoOpDictionaryMorphologyResolver,
    private val timeProvider: TimeProvider,
    private val externalReferenceProvider: ExternalDictionaryReferenceProvider =
        NaverDictionaryLinkProvider(),
    private val wordbookRepository: WordbookRepository = EmptyWordbookRepository,
) : ViewModel() {
    private val requestedEntryId: Long? = savedStateHandle["entryId"]
    private val mutableUiState = MutableStateFlow(WordEditorUiState(entryId = requestedEntryId))
    val uiState: StateFlow<WordEditorUiState> = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<WordEditorEffect>()
    val effects = mutableEffects.asSharedFlow()
    private val dictionarySuggestionRequests = MutableStateFlow(DictionarySuggestionRequest())
    private var nextLocalKey = -10L
    private var pendingDuplicateDraft: ValidatedVocabularyDraft? = null

    init {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                mutableUiState.update { it.copy(availableTags = tags) }
            }
        }
        viewModelScope.launch {
            wordbookRepository.observeWordbooks().collect { wordbooks ->
                mutableUiState.update { it.copy(availableWordbooks = wordbooks) }
            }
        }
        viewModelScope.launch {
            dictionarySuggestionRequests
                .debounce { request ->
                    if (request.isSearchable) DICTIONARY_SEARCH_DEBOUNCE_MILLIS else 0L
                }
                .distinctUntilChanged()
                .collectLatest { request ->
                    if (request.isSearchable) {
                        searchDictionarySuggestions(request)
                    } else if (dictionarySuggestionRequests.value == request) {
                        mutableUiState.update {
                            it.copy(
                                isDictionarySearchInProgress = false,
                                dictionarySuggestionGroups = emptyList(),
                                synthesizedSuggestionGroups = emptyList(),
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            if (requestedEntryId == null) {
                val settings = settingsRepository.settings.first()
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        languageTag = settings.defaultLanguageTag,
                        userLanguageTags = settings.userLanguageTags,
                    )
                }
                refreshDictionaryLanguageOptions()
            } else {
                val settings = settingsRepository.settings.first()
                mutableUiState.update { it.copy(userLanguageTags = settings.userLanguageTags) }
                loadExistingEntry(requestedEntryId)
            }
        }
    }

    fun onAction(action: WordEditorAction) {
        when (action) {
            is WordEditorAction.HeadwordChanged -> {
                updateForm {
                    val currentQuery = headword.trim()
                    val nextQuery = action.value.trim()
                    val state = if (currentQuery == nextQuery) {
                        this
                    } else {
                        clearSessionDictionaryContributions()
                    }
                    state.copy(
                        headword = action.value,
                        dictionaryReference = null,
                        dictionaryReferenceSuggestionKey = null,
                    )
                }
                refreshExternalDictionaryReference()
                scheduleDictionarySuggestions()
            }
            is WordEditorAction.LanguageTagChanged -> {
                updateForm {
                    clearSessionDictionaryContributions().copy(languageTag = action.value)
                }
                refreshDictionaryLanguageOptions()
                refreshExternalDictionaryReference()
            }
            is WordEditorAction.UserLanguageAdded -> addUserLanguage(action.languageTag)
            is WordEditorAction.ReadingChanged -> updateForm {
                copy(
                    reading = action.value,
                    readingProvenance = readingProvenance?.markModified(),
                    isReadingUserEdited = true,
                )
            }
            WordEditorAction.AddPronunciation -> updateForm {
                copy(
                    pronunciations = pronunciations + EditablePronunciation(
                        key = newKey(),
                        languageTag = VocabularyEntryValidator.normalizeLanguageTag(languageTag),
                    ),
                )
            }
            is WordEditorAction.RemovePronunciation -> updateForm {
                copy(
                    pronunciations = pronunciations.filterNot {
                        it.key == action.pronunciationKey
                    },
                )
            }
            is WordEditorAction.PronunciationValueChanged -> updateForm {
                copy(
                    pronunciations = pronunciations.map { pronunciation ->
                        if (pronunciation.key == action.pronunciationKey) {
                            pronunciation.copy(
                                value = action.value,
                                provenance = pronunciation.provenance?.markModified(),
                                isUserEdited = true,
                            )
                        } else {
                            pronunciation
                        }
                    },
                )
            }
            is WordEditorAction.PronunciationNotationChanged -> updateForm {
                copy(
                    pronunciations = pronunciations.map { pronunciation ->
                        if (pronunciation.key == action.pronunciationKey) {
                            pronunciation.copy(
                                notation = action.notation,
                                provenance = pronunciation.provenance?.markModified(),
                                isUserEdited = true,
                            )
                        } else {
                            pronunciation
                        }
                    },
                )
            }
            is WordEditorAction.DictionarySuggestionSelected -> {
                selectDictionarySuggestion(action.entry)
            }
            WordEditorAction.OpenExternalDictionaryReference -> openExternalDictionaryReference()
            is WordEditorAction.NotesChanged -> updateForm { copy(notes = action.value) }
            WordEditorAction.AddSense -> updateSenses {
                copy(
                    senses = senses + EditableSense(
                        key = newKey(),
                        examples = listOf(EditableExample(newKey())),
                    ),
                )
            }
            is WordEditorAction.RemoveSense -> updateSenses {
                copy(senses = senses.filterNot { it.key == action.senseKey })
            }
            is WordEditorAction.MeaningChanged -> updateSense(action.senseKey) {
                copy(meaning = action.value)
            }
            is WordEditorAction.PartOfSpeechChanged -> updateSense(action.senseKey) {
                copy(partOfSpeech = action.value)
            }
            is WordEditorAction.GrammaticalGenderChanged -> updateSense(action.senseKey) {
                copy(
                    grammaticalGender = action.value,
                    isGrammaticalGenderVisible = true,
                )
            }
            is WordEditorAction.GrammaticalGenderVisibilityChanged ->
                updateSense(action.senseKey) {
                    copy(
                        grammaticalGender = if (action.visible) grammaticalGender else "",
                        isGrammaticalGenderVisible = action.visible,
                    )
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
                        if (example.key == action.exampleKey) {
                            example.copy(text = action.value)
                        } else {
                            example
                        }
                    },
                )
            }
            is WordEditorAction.TagToggled -> updateForm {
                val selection = selectedTagIds.toMutableSet()
                if (!selection.add(action.tagId)) selection.remove(action.tagId)
                copy(selectedTagIds = selection)
            }
            WordEditorAction.CreateTagRequested -> mutableUiState.update {
                it.copy(
                    isTagCreatorVisible = true,
                    newTagName = "",
                    tagCreationError = null,
                    tagCreationMessage = null,
                )
            }
            is WordEditorAction.NewTagNameChanged -> mutableUiState.update {
                it.copy(newTagName = action.value, tagCreationError = null)
            }
            WordEditorAction.CreateTagConfirmed -> createTag()
            WordEditorAction.CreateTagDismissed -> mutableUiState.update {
                it.copy(
                    isTagCreatorVisible = false,
                    newTagName = "",
                    tagCreationError = null,
                    isCreatingTag = false,
                )
            }
            is WordEditorAction.WordbookToggled -> updateForm {
                val selection = selectedWordbookIds.toMutableSet()
                if (!selection.add(action.wordbookId)) selection.remove(action.wordbookId)
                copy(selectedWordbookIds = selection)
            }
            WordEditorAction.CreateWordbookRequested -> mutableUiState.update {
                it.copy(
                    isWordbookCreatorVisible = true,
                    newWordbookName = "",
                    wordbookCreationError = null,
                    wordbookCreationMessage = null,
                )
            }
            is WordEditorAction.NewWordbookNameChanged -> mutableUiState.update {
                it.copy(newWordbookName = action.value, wordbookCreationError = null)
            }
            WordEditorAction.CreateWordbookConfirmed -> createWordbook()
            WordEditorAction.CreateWordbookDismissed -> mutableUiState.update {
                it.copy(
                    isWordbookCreatorVisible = false,
                    newWordbookName = "",
                    wordbookCreationError = null,
                    isCreatingWordbook = false,
                )
            }
            WordEditorAction.OpenExistingDuplicate -> openExistingDuplicate()
            WordEditorAction.SaveDuplicateAnyway -> saveDuplicateAnyway()
            WordEditorAction.DismissDuplicateWarning -> {
                pendingDuplicateDraft = null
                mutableUiState.update { it.copy(duplicateCandidate = null, isSaving = false) }
            }
            WordEditorAction.Save -> save()
        }
    }

    private fun addUserLanguage(languageTag: String) {
        viewModelScope.launch {
            try {
                settingsRepository.addUserLanguageTag(languageTag)
                val storedTags = settingsRepository.settings.first().userLanguageTags
                mutableUiState.update { it.copy(userLanguageTags = storedTags) }
            } catch (exception: IllegalArgumentException) {
                mutableUiState.update { it.copy(saveErrorMessage = exception.message) }
            }
        }
    }

    private fun createWordbook() {
        val state = mutableUiState.value
        if (state.isCreatingWordbook) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    isCreatingWordbook = true,
                    wordbookCreationError = null,
                    wordbookCreationMessage = null,
                )
            }
            when (val result = wordbookRepository.save(null, state.newWordbookName)) {
                is SaveWordbookResult.Saved -> selectCreatedWordbook(
                    result.id,
                    "새 단어장을 선택했습니다.",
                )
                is SaveWordbookResult.NameConflict -> selectCreatedWordbook(
                    result.existingId,
                    "이미 있는 단어장을 선택했습니다.",
                )
                SaveWordbookResult.BlankName -> mutableUiState.update {
                    it.copy(
                        isCreatingWordbook = false,
                        wordbookCreationError = "단어장 이름을 입력하세요.",
                    )
                }
                SaveWordbookResult.NotFound -> mutableUiState.update {
                    it.copy(
                        isCreatingWordbook = false,
                        wordbookCreationError = "단어장을 만들지 못했습니다.",
                    )
                }
            }
        }
    }

    private fun selectCreatedWordbook(wordbookId: Long, message: String) {
        mutableUiState.update {
            it.copy(
                selectedWordbookIds = it.selectedWordbookIds + wordbookId,
                isWordbookCreatorVisible = false,
                newWordbookName = "",
                wordbookCreationError = null,
                wordbookCreationMessage = message,
                isCreatingWordbook = false,
            )
        }
    }

    private fun createTag() {
        val state = mutableUiState.value
        if (state.isCreatingTag) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isCreatingTag = true, tagCreationError = null, tagCreationMessage = null)
            }
            when (val result = tagRepository.save(null, state.newTagName)) {
                is SaveTagResult.Saved -> selectCreatedTag(
                    result.id,
                    message = "새 태그를 선택했습니다.",
                )
                is SaveTagResult.NameConflict -> selectCreatedTag(
                    result.existingId,
                    message = "이미 있는 태그를 선택했습니다.",
                )
                SaveTagResult.BlankName -> mutableUiState.update {
                    it.copy(isCreatingTag = false, tagCreationError = "태그 이름을 입력하세요.")
                }
                SaveTagResult.NotFound -> mutableUiState.update {
                    it.copy(isCreatingTag = false, tagCreationError = "태그를 만들지 못했습니다.")
                }
            }
        }
    }

    private fun selectCreatedTag(tagId: Long, message: String) {
        mutableUiState.update {
            it.copy(
                selectedTagIds = it.selectedTagIds + tagId,
                isTagCreatorVisible = false,
                newTagName = "",
                tagCreationError = null,
                tagCreationMessage = message,
                isCreatingTag = false,
            )
        }
    }

    private suspend fun loadExistingEntry(entryId: Long) {
        runCatching { vocabularyRepository.observeEntry(entryId).first() }
            .onSuccess { entry ->
                mutableUiState.update { state ->
                    if (entry == null) {
                        state.copy(isLoading = false, loadErrorMessage = "단어를 찾을 수 없습니다.")
                    } else {
                        state.copy(
                            isLoading = false,
                            headword = entry.headword,
                            languageTag = entry.languageTag,
                            reading = entry.reading,
                            readingProvenance = entry.readingProvenance,
                            isReadingUserEdited = true,
                            pronunciations = entry.pronunciations.map { pronunciation ->
                                EditablePronunciation(
                                    key = pronunciation.id,
                                    stableId = pronunciation.stableId,
                                    notation = pronunciation.notation,
                                    value = pronunciation.value,
                                    languageTag = pronunciation.languageTag,
                                    provenance = pronunciation.provenance,
                                    isUserEdited = pronunciation.provenance == null ||
                                        pronunciation.provenance.modifiedAfterImport,
                                )
                            },
                            senses = entry.senses.map { sense ->
                                EditableSense(
                                    key = sense.id,
                                    meaning = sense.meaning,
                                    partOfSpeech = sense.partOfSpeech,
                                    examples = sense.examples.map { EditableExample(it.id, it.text) }
                                        .ifEmpty { listOf(EditableExample(newKey())) },
                                    provenance = sense.provenance,
                                    grammaticalGender = sense.grammaticalGender
                                        ?.displayValue()
                                        .orEmpty(),
                                    isGrammaticalGenderVisible =
                                        sense.grammaticalGender != null,
                                )
                            },
                            notes = entry.notes,
                            selectedTagIds = entry.tags.mapTo(mutableSetOf()) { it.id },
                            selectedWordbookIds = entry.wordbooks.mapTo(mutableSetOf()) { it.id },
                        )
                    }
                }
                if (entry != null) refreshDictionaryLanguageOptions()
                refreshExternalDictionaryReference()
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

    private fun refreshDictionaryLanguageOptions() {
        val state = mutableUiState.value
        val sourceLanguage = Bcp47LanguageTag.parse(state.languageTag)
        val descriptors = dictionaryProviderRegistry.descriptors()
        val languagePairs = sourceLanguage?.let { source ->
            descriptors
                .flatMap(DictionaryProviderDescriptor::supportedLanguagePairs)
                .filter { it.sourceLanguage == source }
                .distinct()
                .sortedWith(DictionaryResultLanguagePreference.comparator)
        }.orEmpty()
        val message = when {
            state.languageTag.isBlank() -> null
            sourceLanguage == null -> "유효한 BCP 47 원문 언어 태그를 입력하면 사전을 검색합니다."
            languagePairs.isEmpty() -> "현재 원문 언어를 지원하는 사전 공급원이 없습니다."
            else -> null
        }
        mutableUiState.update {
            it.copy(
                dictionaryLanguagePairs = languagePairs,
                dictionarySuggestionGroups = emptyList(),
                synthesizedSuggestionGroups = emptyList(),
                dictionarySuggestionMessage = message,
                dictionaryReference = null,
                dictionaryReferenceSuggestionKey = null,
            )
        }
        scheduleDictionarySuggestions()
    }

    private fun scheduleDictionarySuggestions() {
        val state = mutableUiState.value
        val request = DictionarySuggestionRequest(
            query = state.headword.trim(),
            languagePairs = state.dictionaryLanguagePairs,
        )
        if (request == dictionarySuggestionRequests.value) return
        mutableUiState.update {
            it.copy(
                isDictionarySearchInProgress = false,
                dictionarySuggestionGroups = emptyList(),
                synthesizedSuggestionGroups = emptyList(),
                dictionarySuggestionMessage = when {
                    request.query.isBlank() -> it.dictionarySuggestionMessage
                    request.languagePairs.isEmpty() -> it.dictionarySuggestionMessage
                    else -> null
                },
            )
        }
        dictionarySuggestionRequests.value = request
    }

    private suspend fun searchDictionarySuggestions(request: DictionarySuggestionRequest) {
        val searches = request.languagePairs.flatMap { languagePair ->
            val query = DictionaryQuery(
                text = request.query,
                languagePair = languagePair,
                resultLimit = DICTIONARY_RESULT_LIMIT,
            )
            dictionaryProviderRegistry.descriptorsSupporting(query).map { descriptor ->
                descriptor to query
            }
        }
        if (searches.isEmpty()) {
            if (dictionarySuggestionRequests.value == request) {
                mutableUiState.update {
                    it.copy(
                        isDictionarySearchInProgress = false,
                        dictionarySuggestionGroups = emptyList(),
                        synthesizedSuggestionGroups = emptyList(),
                        dictionarySuggestionMessage = "선택한 언어 조합을 지원하는 사전이 없습니다.",
                    )
                }
            }
            return
        }

        mutableUiState.update {
            it.copy(isDictionarySearchInProgress = true, dictionarySuggestionMessage = null)
        }
        val directGroups = coroutineScope {
            searches.mapNotNull { (descriptor, query) ->
                dictionaryProviderRegistry.find(descriptor.id)?.let { provider ->
                    async { searchProviderSafely(descriptor, provider, query) }
                }
            }.map { it.await() }
        }
        val morphologyGroups = searchMorphologySuggestions(request)
        val groups = directGroups.filterNot { group ->
            morphologyGroups.isNotEmpty() && group.failure == DictionaryProviderError.NoResult
        } + morphologyGroups
        if (dictionarySuggestionRequests.value != request) return
        val synthesizedGroups = DictionarySuggestionSynthesizer.synthesize(groups)
        mutableUiState.update { state ->
            state.restoreSuggestionOwnership(synthesizedGroups).copy(
                isDictionarySearchInProgress = false,
                dictionarySuggestionGroups = groups,
                synthesizedSuggestionGroups = synthesizedGroups,
                dictionarySuggestionMessage = null,
            )
        }
    }

    private suspend fun searchMorphologySuggestions(
        request: DictionarySuggestionRequest,
    ): List<DictionarySuggestionGroup> {
        val sourceLanguages = request.languagePairs
            .map(DictionaryLanguagePair::sourceLanguage)
            .distinct()
            .filter { it in dictionaryMorphologyResolver.supportedSourceLanguages }
        if (sourceLanguages.isEmpty()) return emptyList()
        val resolved = sourceLanguages.flatMap { sourceLanguage ->
            val result = try {
                dictionaryMorphologyResolver.resolve(
                    DictionaryMorphologyQuery(
                        surface = request.query,
                        sourceLanguage = sourceLanguage,
                        resultLimit = MORPHOLOGY_LEMMA_LIMIT,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                DictionaryMorphologyResult.NoResult
            }
            when (result) {
                is DictionaryMorphologyResult.Resolved -> result.candidates.mapIndexed {
                    index, candidate ->
                    RankedMorphologyCandidate(
                        candidate = candidate,
                        role = if (index == 0) {
                            MorphologyAnalysisRole.PRIMARY
                        } else {
                            MorphologyAnalysisRole.ALTERNATE
                        },
                        hasAlternates = result.candidates.size > 1 || result.isTruncated,
                        isTruncated = result.isTruncated,
                    )
                }
                else -> emptyList()
            }
        }
        if (resolved.isEmpty()) return emptyList()

        return coroutineScope {
            resolved.flatMap { rankedCandidate ->
                val lemmaCandidate = rankedCandidate.candidate
                request.languagePairs
                    .filter { it.sourceLanguage == lemmaCandidate.sourceLanguage }
                    .flatMap { languagePair ->
                        val lemmaQuery = DictionaryQuery(
                            text = lemmaCandidate.lemma,
                            languagePair = languagePair,
                            resultLimit = DICTIONARY_RESULT_LIMIT,
                        )
                        dictionaryProviderRegistry.descriptorsSupporting(lemmaQuery).mapNotNull {
                            descriptor ->
                            dictionaryProviderRegistry.find(descriptor.id)?.let { provider ->
                                async {
                                    searchProviderExactOnlySafely(
                                        descriptor = descriptor,
                                        provider = provider,
                                        query = lemmaQuery,
                                        context = MorphologySuggestionContext(
                                            surface = lemmaCandidate.surface,
                                            lemma = lemmaCandidate.lemma,
                                            resolverProviderId =
                                                lemmaCandidate.resolverProviderId,
                                            resolverName = dictionaryMorphologyResolver.displayName,
                                            role = rankedCandidate.role,
                                            hasAlternates = rankedCandidate.hasAlternates,
                                            isTruncated = rankedCandidate.isTruncated,
                                        ),
                                    )
                                }
                            }
                        }
                    }
            }.mapNotNull { it.await() }
        }
    }

    private suspend fun searchProviderExactOnlySafely(
        descriptor: DictionaryProviderDescriptor,
        provider: DictionaryProvider,
        query: DictionaryQuery,
        context: MorphologySuggestionContext,
    ): DictionarySuggestionGroup? = try {
        when (val result = provider.exactLookup(query)) {
            is DictionarySearchResult.Success -> searchProvider(descriptor, query, result).copy(
                morphologyContext = context,
            )
            else -> null
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private data class RankedMorphologyCandidate(
        val candidate: DictionaryLemmaCandidate,
        val role: MorphologyAnalysisRole,
        val hasAlternates: Boolean,
        val isTruncated: Boolean,
    )

    private fun searchProvider(
        descriptor: DictionaryProviderDescriptor,
        query: DictionaryQuery,
        result: DictionarySearchResult,
    ): DictionarySuggestionGroup = when (result) {
        is DictionarySearchResult.Success -> DictionarySuggestionGroup(
            providerId = descriptor.id,
            providerName = descriptor.displayName,
            entries = result.page.entries,
            message = if (result.page.isTruncated) {
                "상위 ${result.page.entries.size}개 결과를 표시합니다."
            } else {
                null
            },
            languagePair = query.languagePair,
        )
        is DictionarySearchResult.Failure -> DictionarySuggestionGroup(
            providerId = descriptor.id,
            providerName = descriptor.displayName,
            message = result.error.toSuggestionMessage(descriptor.displayName),
            languagePair = query.languagePair,
            failure = result.error,
        )
    }

    private suspend fun searchProviderSafely(
        descriptor: DictionaryProviderDescriptor,
        provider: DictionaryProvider,
        query: DictionaryQuery,
    ): DictionarySuggestionGroup = try {
        searchProvider(descriptor, query, provider.search(query))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        DictionarySuggestionGroup(
            providerId = descriptor.id,
            providerName = descriptor.displayName,
            message = error.message ?: "${descriptor.displayName} 검색에 실패했습니다.",
            languagePair = query.languagePair,
        )
    }

    private fun selectDictionarySuggestion(entry: ExternalDictionaryEntry) {
        val rawKey = entry.suggestionKey()
        val candidate = mutableUiState.value.synthesizedSuggestionGroups
            .asSequence()
            .flatMap { it.candidates.asSequence() }
            .firstOrNull { it.primaryEntry.suggestionKey() == rawKey }
            ?: return
        val suggestionKey = candidate.key
        if (suggestionKey in mutableUiState.value.selectedSuggestionKeys) {
            deselectDictionarySuggestion(suggestionKey)
            return
        }
        when (
            val mapping = DictionaryEntryDraftMapper.map(
                entry = candidate.primaryEntry,
                importedAtEpochMillis = timeProvider.currentTimeMillis(),
            )
        ) {
            is DictionaryEntryDraftMappingResult.Ready -> {
                val seededSenses = mapping.seed.draft.senses
                    .takeIf { senses -> senses.any { it.hasContent() } }
                    ?.let { toEditableSenses(it, suggestionKey) }
                    .orEmpty()
                val seededPronunciations = mapping.seed.draft.pronunciations
                mutableUiState.update { state ->
                    val acceptsReading = !state.isReadingUserEdited && state.reading.isBlank() &&
                        mapping.seed.draft.reading.isNotBlank()
                    val sharesReading = !state.isReadingUserEdited &&
                        state.reading.isNotBlank() &&
                        state.reading == mapping.seed.draft.reading
                    val uniqueSenses = seededSenses.filterNot { imported ->
                        val provenance = imported.provenance ?: return@filterNot false
                        state.senses.any { existing ->
                            existing.provenance?.hasSameSourceAs(provenance) == true
                        }
                    }
                    val existingSenses = state.senses.takeUnless {
                        it.size == 1 && it.single().isBlankPlaceholder()
                    }.orEmpty()
                    state.copy(
                        senses = (existingSenses + uniqueSenses)
                            .ifEmpty { state.senses },
                        reading = if (acceptsReading) mapping.seed.draft.reading else state.reading,
                        readingProvenance = if (acceptsReading) {
                            mapping.seed.draft.readingProvenance
                        } else {
                            state.readingProvenance
                        },
                        pronunciations = state.pronunciations.withSuggestionPronunciations(
                            seededPronunciations,
                            suggestionKey,
                        ),
                        readingSuggestionKeys = if (acceptsReading || sharesReading) {
                            state.readingSuggestionKeys + suggestionKey
                        } else {
                            state.readingSuggestionKeys
                        },
                        sessionReadingSuggestionKeys = if (acceptsReading || sharesReading) {
                            state.sessionReadingSuggestionKeys + suggestionKey
                        } else {
                            state.sessionReadingSuggestionKeys
                        },
                        dictionaryReference = mapping.seed.transientEntry,
                        dictionaryReferenceSuggestionKey = suggestionKey,
                        selectedSuggestionKeys = state.selectedSuggestionKeys + suggestionKey,
                    )
                }
            }
            is DictionaryEntryDraftMappingResult.ReferenceOnly -> mutableUiState.update {
                it.copy(
                    dictionaryReference = mapping.transientEntry,
                    dictionaryReferenceSuggestionKey = suggestionKey,
                    selectedSuggestionKeys = it.selectedSuggestionKeys + suggestionKey,
                )
            }
        }
    }

    private fun deselectDictionarySuggestion(suggestionKey: String) {
        mutableUiState.update { state ->
            val remainingSelectedKeys = state.selectedSuggestionKeys - suggestionKey
            val remainingReadingKeys = state.readingSuggestionKeys - suggestionKey
            val remainingSessionReadingKeys = state.sessionReadingSuggestionKeys - suggestionKey
            val retainedPronunciations = state.pronunciations.mapNotNull { pronunciation ->
                if (suggestionKey !in pronunciation.suggestionKeys) {
                    return@mapNotNull pronunciation
                }
                val remainingOwners = pronunciation.suggestionKeys - suggestionKey
                val remainingSessionOwners = pronunciation.sessionSuggestionKeys - suggestionKey
                if (remainingOwners.isNotEmpty()) {
                    val replacement = pronunciationProvenanceForSelection(
                        pronunciation,
                        remainingOwners,
                    )
                    pronunciation.copy(
                        provenance = replacement?.let {
                            if (pronunciation.provenance?.modifiedAfterImport == true) {
                                it.markModified()
                            } else {
                                it
                            }
                        } ?: pronunciation.provenance,
                        suggestionKeys = remainingOwners,
                        sessionSuggestionKeys = remainingSessionOwners,
                    )
                } else if (
                    pronunciation.isUserEdited ||
                    pronunciation.provenance?.modifiedAfterImport == true
                ) {
                    pronunciation.copy(
                        provenance = null,
                        suggestionKeys = emptySet(),
                        sessionSuggestionKeys = emptySet(),
                        importedValue = null,
                    )
                } else {
                    null
                }
            }
            val retainedSenses = state.senses.mapNotNull { sense ->
                val provenance = sense.provenance
                if (sense.importSuggestionKey != suggestionKey || provenance == null) {
                    return@mapNotNull sense
                }
                if (sense.sessionContribution != null) {
                    sense.withoutUnchangedSessionContribution()
                } else if (provenance.modifiedAfterImport) {
                    sense.copy(provenance = null, importSuggestionKey = null)
                } else {
                    null
                }
            }.ifEmpty {
                listOf(EditableSense(key = newKey(), examples = listOf(EditableExample(newKey()))))
            }
            val removesReading = suggestionKey in state.readingSuggestionKeys &&
                remainingReadingKeys.isEmpty() && state.readingProvenance != null
            val keepEditedReading = removesReading &&
                state.readingProvenance.modifiedAfterImport
            val replacementReadingProvenance = if (
                suggestionKey in state.readingSuggestionKeys && remainingReadingKeys.isNotEmpty()
            ) {
                readingProvenanceForSelection(remainingReadingKeys)?.let { replacement ->
                    if (state.readingProvenance?.modifiedAfterImport == true) {
                        replacement.markModified()
                    } else {
                        replacement
                    }
                }
            } else {
                state.readingProvenance
            }
            state.copy(
                senses = retainedSenses,
                pronunciations = retainedPronunciations,
                reading = if (removesReading && !keepEditedReading) "" else state.reading,
                readingProvenance = if (removesReading) null else replacementReadingProvenance,
                readingSuggestionKeys = remainingReadingKeys,
                sessionReadingSuggestionKeys = remainingSessionReadingKeys,
                isReadingUserEdited = if (removesReading && !keepEditedReading) {
                    false
                } else {
                    state.isReadingUserEdited
                },
                dictionaryReference = if (state.dictionaryReferenceSuggestionKey == suggestionKey) {
                    null
                } else {
                    state.dictionaryReference
                },
                dictionaryReferenceSuggestionKey = if (
                    state.dictionaryReferenceSuggestionKey == suggestionKey
                ) {
                    null
                } else {
                    state.dictionaryReferenceSuggestionKey
                },
                selectedSuggestionKeys = remainingSelectedKeys,
            )
        }
    }

    private fun readingProvenanceForSelection(
        selectedKeys: Set<String>,
    ): DictionaryProvenance? = mutableUiState.value.synthesizedSuggestionGroups
        .asSequence()
        .flatMap { it.candidates.asSequence() }
        .filter { it.key in selectedKeys }
        .mapNotNull { candidate ->
            when (
                val mapped = DictionaryEntryDraftMapper.map(
                    candidate.primaryEntry,
                    importedAtEpochMillis = 0,
                )
            ) {
                is DictionaryEntryDraftMappingResult.Ready ->
                    mapped.seed.draft.readingProvenance
                is DictionaryEntryDraftMappingResult.ReferenceOnly -> null
            }
        }
        .firstOrNull()

    private fun pronunciationProvenanceForSelection(
        pronunciation: EditablePronunciation,
        selectedKeys: Set<String>,
    ): DictionaryProvenance? = mutableUiState.value.synthesizedSuggestionGroups
        .asSequence()
        .flatMap { it.candidates.asSequence() }
        .filter { it.key in selectedKeys }
        .flatMap { it.sources.asSequence() }
        .mapNotNull { source ->
            when (
                val mapped = DictionaryEntryDraftMapper.map(
                    source.entry,
                    importedAtEpochMillis = 0,
                )
            ) {
                is DictionaryEntryDraftMappingResult.Ready -> mapped.seed.draft.pronunciations
                    .firstOrNull { pronunciation.sameValueAs(it) }
                    ?.provenance
                is DictionaryEntryDraftMappingResult.ReferenceOnly -> null
            }
        }
        .firstOrNull()

    private fun WordEditorUiState.restoreSuggestionOwnership(
        groups: List<SynthesizedSuggestionGroup>,
    ): WordEditorUiState {
        var restoredSenses = senses
        var restoredPronunciations = pronunciations
        val restoredSelections = selectedSuggestionKeys.toMutableSet()
        val restoredReadingOwners = readingSuggestionKeys.toMutableSet()

        groups.asSequence()
            .flatMap { it.candidates.asSequence() }
            .forEach { candidate ->
                val key = candidate.key
                val mappings = candidate.sources.mapNotNull { source ->
                    DictionaryEntryDraftMapper.map(source.entry, importedAtEpochMillis = 0)
                        as? DictionaryEntryDraftMappingResult.Ready
                }
                val candidateProvenances = mappings.flatMap { it.seed.draft.senses }
                    .mapNotNull { it.provenance }
                var ownsPersistedContent = false
                restoredSenses = restoredSenses.map { sense ->
                    val matches = sense.provenance?.let { existing ->
                        candidateProvenances.any(existing::hasSameSourceAs)
                    } == true
                    if (matches) {
                        ownsPersistedContent = true
                        if (sense.importSuggestionKey == null) {
                            sense.copy(importSuggestionKey = key)
                        } else {
                            sense
                        }
                    } else {
                        sense
                    }
                }

                val ownsReading = readingProvenance?.let { existing ->
                    mappings.mapNotNull { it.seed.draft.readingProvenance }
                        .any(existing::hasSameSourceAs)
                } == true
                if (ownsReading) restoredReadingOwners += key
                val mappedPronunciations = mappings.flatMap { it.seed.draft.pronunciations }
                var ownsPronunciation = false
                restoredPronunciations = restoredPronunciations.map { pronunciation ->
                    val matches = mappedPronunciations.any { mapped ->
                        pronunciation.sameValueAs(mapped) &&
                            pronunciation.provenance?.let { existing ->
                                mapped.provenance?.let(existing::hasSameSourceAs)
                            } == true
                    }
                    if (matches) {
                        ownsPronunciation = true
                        pronunciation.copy(suggestionKeys = pronunciation.suggestionKeys + key)
                    } else {
                        pronunciation
                    }
                }
                if (ownsPersistedContent || ownsReading || ownsPronunciation) {
                    restoredSelections += key
                }
            }

        return copy(
            senses = restoredSenses,
            pronunciations = restoredPronunciations,
            selectedSuggestionKeys = restoredSelections,
            readingSuggestionKeys = restoredReadingOwners,
        )
    }

    private fun refreshExternalDictionaryReference() {
        val state = mutableUiState.value
        val language = Bcp47LanguageTag.parse(state.languageTag)
        val reference = language?.let { externalReferenceProvider.resolve(it, state.headword) }
        mutableUiState.update { it.copy(externalDictionaryReference = reference) }
    }

    private fun openExternalDictionaryReference() {
        val uri = mutableUiState.value.externalDictionaryReference?.uri ?: return
        viewModelScope.launch {
            mutableEffects.emit(WordEditorEffect.OpenExternalDictionaryReference(uri))
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
                        provenance = sense.provenance,
                        grammaticalGender = VocabularyGrammaticalGender.parse(
                            sense.grammaticalGender,
                        ),
                    )
                },
                notes = state.notes,
                tagIds = state.selectedTagIds,
                reading = state.reading,
                readingProvenance = state.readingProvenance,
                pronunciations = state.pronunciations.map { pronunciation ->
                    VocabularyPronunciationDraft(
                        stableId = pronunciation.stableId,
                        notation = pronunciation.notation,
                        value = pronunciation.value,
                        languageTag = pronunciation.languageTag,
                        provenance = pronunciation.provenance,
                    )
                },
                wordbookIds = state.selectedWordbookIds,
            ),
        )
        if (result is VocabularyValidationResult.Invalid) {
            mutableUiState.update { it.copy(validationError = result.error) }
            return
        }

        val draft = (result as VocabularyValidationResult.Valid).draft
        viewModelScope.launch {
            mutableUiState.update { it.copy(isSaving = true, saveErrorMessage = null) }
            runCatching {
                vocabularyRepository.findDuplicateCandidates(
                    headword = draft.headword,
                    languageTag = draft.languageTag,
                    excludingEntryId = draft.id,
                )
            }.onSuccess { duplicates ->
                val candidate = duplicates.firstOrNull()
                if (candidate == null) {
                    persistDraft(draft)
                } else {
                    pendingDuplicateDraft = draft
                    mutableUiState.update {
                        it.copy(isSaving = false, duplicateCandidate = candidate)
                    }
                }
            }.onFailure(::showSaveFailure)
        }
    }

    private fun saveDuplicateAnyway() {
        val draft = pendingDuplicateDraft ?: return
        pendingDuplicateDraft = null
        mutableUiState.update { it.copy(duplicateCandidate = null, isSaving = true) }
        viewModelScope.launch { persistDraft(draft) }
    }

    private fun openExistingDuplicate() {
        val entryId = mutableUiState.value.duplicateCandidate?.id ?: return
        pendingDuplicateDraft = null
        mutableUiState.update { it.copy(duplicateCandidate = null, isSaving = false) }
        viewModelScope.launch { mutableEffects.emit(WordEditorEffect.OpenExistingEntry(entryId)) }
    }

    private suspend fun persistDraft(draft: ValidatedVocabularyDraft) {
        runCatching { vocabularyRepository.save(draft) }
            .onSuccess { id ->
                mutableUiState.update {
                    it.copy(isSaving = false, entryId = id, duplicateCandidate = null)
                }
                mutableEffects.emit(WordEditorEffect.Saved(id))
            }
            .onFailure(::showSaveFailure)
    }

    private fun showSaveFailure(error: Throwable) {
        mutableUiState.update {
            it.copy(
                isSaving = false,
                saveErrorMessage = error.message ?: "단어를 저장하지 못했습니다.",
            )
        }
    }

    private fun updateForm(transform: WordEditorUiState.() -> WordEditorUiState) {
        mutableUiState.update { it.transform().copy(validationError = null, saveErrorMessage = null) }
    }

    private fun updateSenses(transform: WordEditorUiState.() -> WordEditorUiState) {
        updateForm(transform)
    }

    private fun updateSense(key: Long, transform: EditableSense.() -> EditableSense) {
        updateSenses {
            copy(
                senses = senses.map { sense ->
                    if (sense.key == key) {
                        sense.transform().copy(
                            provenance = sense.provenance?.markModified(),
                        )
                    } else {
                        sense
                    }
                },
            )
        }
    }

    private fun toEditableSenses(
        senses: List<VocabularySenseDraft>,
        suggestionKey: String,
    ): List<EditableSense> =
        senses.map { sense ->
            val editableExamples = sense.examples.map { example ->
                EditableExample(key = newKey(), text = example)
            }
            EditableSense(
                key = newKey(),
                meaning = sense.meaning,
                partOfSpeech = sense.partOfSpeech,
                examples = editableExamples.ifEmpty { listOf(EditableExample(newKey())) },
                provenance = sense.provenance,
                importSuggestionKey = suggestionKey,
                grammaticalGender = sense.grammaticalGender?.displayValue().orEmpty(),
                isGrammaticalGenderVisible = sense.grammaticalGender != null,
                sessionContribution = SuggestionSenseContribution(
                    meaning = sense.meaning,
                    partOfSpeech = sense.partOfSpeech,
                    exampleTextsByKey = editableExamples.associate { it.key to it.text },
                    grammaticalGender = sense.grammaticalGender?.displayValue().orEmpty(),
                ),
            )
        }.ifEmpty { listOf(EditableSense(newKey())) }

    private fun List<EditablePronunciation>.withSuggestionPronunciations(
        imported: List<VocabularyPronunciationDraft>,
        suggestionKey: String,
    ): List<EditablePronunciation> {
        val result = toMutableList()
        imported.forEach { pronunciation ->
            val existingIndex = result.indexOfFirst { it.sameValueAs(pronunciation) }
            val existing = result.getOrNull(existingIndex)
            if (existing == null) {
                result += EditablePronunciation(
                    key = newKey(),
                    stableId = pronunciation.stableId,
                    notation = pronunciation.notation,
                    value = pronunciation.value,
                    languageTag = pronunciation.languageTag,
                    provenance = pronunciation.provenance,
                    suggestionKeys = setOf(suggestionKey),
                    sessionSuggestionKeys = setOf(suggestionKey),
                    importedValue = pronunciation.value,
                )
            } else if (!existing.isUserEdited && existing.provenance != null) {
                result[existingIndex] = existing.copy(
                    suggestionKeys = existing.suggestionKeys + suggestionKey,
                    sessionSuggestionKeys = existing.sessionSuggestionKeys + suggestionKey,
                )
            }
        }
        return result
    }

    private fun EditablePronunciation.sameValueAs(
        other: VocabularyPronunciationDraft,
    ): Boolean = notation == other.notation && value == other.value &&
        languageTag == other.languageTag

    private fun WordEditorUiState.clearSessionDictionaryContributions(): WordEditorUiState {
        val retainedSenses = senses.mapNotNull { sense ->
            if (sense.sessionContribution != null) {
                sense.withoutUnchangedSessionContribution()
            } else {
                sense.copy(importSuggestionKey = null)
            }
        }.ifEmpty {
            listOf(EditableSense(key = newKey(), examples = listOf(EditableExample(newKey()))))
        }
        val hasSessionReading = sessionReadingSuggestionKeys.isNotEmpty()
        return copy(
            senses = retainedSenses,
            pronunciations = pronunciations.mapNotNull { pronunciation ->
                if (pronunciation.sessionSuggestionKeys.isEmpty()) {
                    pronunciation.copy(suggestionKeys = emptySet())
                } else if (pronunciation.isUserEdited) {
                    pronunciation.copy(
                        provenance = null,
                        suggestionKeys = emptySet(),
                        sessionSuggestionKeys = emptySet(),
                        importedValue = null,
                    )
                } else {
                    null
                }
            },
            reading = if (hasSessionReading && !isReadingUserEdited) "" else reading,
            readingProvenance = if (hasSessionReading) null else readingProvenance,
            readingSuggestionKeys = emptySet(),
            sessionReadingSuggestionKeys = emptySet(),
            dictionaryReference = null,
            dictionaryReferenceSuggestionKey = null,
            selectedSuggestionKeys = emptySet(),
        )
    }

    private fun EditableSense.withoutUnchangedSessionContribution(): EditableSense? {
        val contribution = requireNotNull(sessionContribution)
        val retainedExamples = examples.filter { example ->
            contribution.exampleTextsByKey[example.key]?.let { importedText ->
                example.text != importedText
            } ?: true
        }
        val retained = copy(
            meaning = meaning.takeUnless { it == contribution.meaning }.orEmpty(),
            partOfSpeech = partOfSpeech.takeUnless {
                it == contribution.partOfSpeech
            }.orEmpty(),
            examples = retainedExamples,
            grammaticalGender = grammaticalGender.takeUnless {
                it == contribution.grammaticalGender
            }.orEmpty(),
            isGrammaticalGenderVisible = grammaticalGender.isNotBlank() &&
                grammaticalGender != contribution.grammaticalGender,
            provenance = null,
            importSuggestionKey = null,
            sessionContribution = null,
        )
        return retained.takeIf { it.hasContent() }
    }

    private fun EditableSense.hasContent(): Boolean =
        meaning.isNotBlank() || partOfSpeech.isNotBlank() || grammaticalGender.isNotBlank() ||
            examples.any { it.text.isNotBlank() }

    private fun VocabularySenseDraft.hasContent(): Boolean =
        meaning.isNotBlank() || partOfSpeech.isNotBlank() || grammaticalGender != null ||
            examples.any(String::isNotBlank)

    private fun EditableSense.isBlankPlaceholder(): Boolean =
        provenance == null && meaning.isBlank() && partOfSpeech.isBlank() &&
            grammaticalGender.isBlank() && examples.all { it.text.isBlank() }

    private fun DictionaryProviderError.toSuggestionMessage(providerName: String): String =
        when (this) {
            is DictionaryProviderError.UnsupportedSourceLanguage ->
                "$providerName 공급원은 현재 원문 언어를 지원하지 않습니다."
            is DictionaryProviderError.UnsupportedResultLanguage ->
                "$providerName 공급원은 현재 결과 언어를 지원하지 않습니다."
            DictionaryProviderError.MissingCredential -> "$providerName 자격 증명이 없습니다."
            DictionaryProviderError.AuthenticationFailed -> "$providerName 인증에 실패했습니다."
            is DictionaryProviderError.RateLimited -> "$providerName 요청 한도를 초과했습니다."
            DictionaryProviderError.NetworkUnavailable -> "$providerName 네트워크를 사용할 수 없습니다."
            DictionaryProviderError.ProviderUnavailable -> "$providerName 공급원을 사용할 수 없습니다."
            is DictionaryProviderError.MalformedProviderData ->
                detail ?: "$providerName 데이터 형식이 올바르지 않습니다."
            DictionaryProviderError.NoResult -> "$providerName 검색 결과가 없습니다."
            DictionaryProviderError.LocalDatasetUnavailable ->
                "$providerName 데이터셋이 설치되지 않았습니다."
            is DictionaryProviderError.Unknown ->
                detail ?: "$providerName 검색 중 알 수 없는 오류가 발생했습니다."
        }

    private fun newKey(): Long = nextLocalKey--

    companion object {
        internal const val DICTIONARY_SEARCH_DEBOUNCE_MILLIS = 400L
        private const val DICTIONARY_RESULT_LIMIT = 20
        private const val MORPHOLOGY_LEMMA_LIMIT = 5
    }
}

private object EmptyWordbookRepository : WordbookRepository {
    override fun observeWordbooks() = flowOf(emptyList<VocabularyWordbook>())
    override suspend fun save(id: Long?, name: String): SaveWordbookResult =
        SaveWordbookResult.BlankName
    override suspend fun delete(id: Long) = Unit
}
