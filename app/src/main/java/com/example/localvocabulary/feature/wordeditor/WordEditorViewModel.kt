package com.example.localvocabulary.feature.wordeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import com.example.localvocabulary.dictionary.registry.DictionaryProviderRegistry
import com.example.localvocabulary.settings.SettingsRepository
import com.example.localvocabulary.vocabulary.domain.TagRepository
import com.example.localvocabulary.vocabulary.domain.DictionaryProvenance
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
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
    val provenance: DictionaryProvenance? = null,
)

data class WordEditorUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val entryId: Long? = null,
    val headword: String = "",
    val languageTag: String = "en",
    val reading: String = "",
    val readingProvenance: DictionaryProvenance? = null,
    val isReadingUserEdited: Boolean = false,
    val senses: List<EditableSense> = listOf(EditableSense(-1)),
    val notes: String = "",
    val availableTags: List<VocabularyTag> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val dictionaryLanguageOptions: List<DictionaryLanguagePairOption> = emptyList(),
    val selectedDictionaryLanguageOptionKey: String? = null,
    val isDictionarySearchInProgress: Boolean = false,
    val dictionarySuggestionGroups: List<DictionarySuggestionGroup> = emptyList(),
    val dictionarySuggestionMessage: String? = null,
    val dictionaryReference: ExternalDictionaryEntry? = null,
    val validationError: VocabularyValidationError? = null,
    val loadErrorMessage: String? = null,
    val saveErrorMessage: String? = null,
)

sealed interface WordEditorAction {
    data class HeadwordChanged(val value: String) : WordEditorAction
    data class LanguageTagChanged(val value: String) : WordEditorAction
    data class ReadingChanged(val value: String) : WordEditorAction
    data class DictionaryLanguagePairSelected(val key: String) : WordEditorAction
    data class DictionarySuggestionSelected(
        val entry: ExternalDictionaryEntry,
    ) : WordEditorAction
    data class NotesChanged(val value: String) : WordEditorAction
    data object AddSense : WordEditorAction
    data class RemoveSense(val senseKey: Long) : WordEditorAction
    data class MeaningChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class PartOfSpeechChanged(val senseKey: Long, val value: String) : WordEditorAction
    data class AddExample(val senseKey: Long) : WordEditorAction
    data class RemoveExample(val senseKey: Long, val exampleKey: Long) : WordEditorAction
    data class ExampleChanged(
        val senseKey: Long,
        val exampleKey: Long,
        val value: String,
    ) : WordEditorAction
    data class TagToggled(val tagId: Long) : WordEditorAction
    data object Save : WordEditorAction
}

sealed interface WordEditorEffect {
    data class Saved(val entryId: Long) : WordEditorEffect
}

@OptIn(FlowPreview::class)
@HiltViewModel
class WordEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vocabularyRepository: VocabularyRepository,
    tagRepository: TagRepository,
    settingsRepository: SettingsRepository,
    private val dictionaryProviderRegistry: DictionaryProviderRegistry,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val requestedEntryId: Long? = savedStateHandle["entryId"]
    private val mutableUiState = MutableStateFlow(WordEditorUiState(entryId = requestedEntryId))
    val uiState: StateFlow<WordEditorUiState> = mutableUiState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<WordEditorEffect>()
    val effects = mutableEffects.asSharedFlow()
    private val dictionarySuggestionRequests = MutableStateFlow(DictionarySuggestionRequest())
    private var nextLocalKey = -10L

    init {
        viewModelScope.launch {
            tagRepository.observeTags().collect { tags ->
                mutableUiState.update { it.copy(availableTags = tags) }
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
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            if (requestedEntryId == null) {
                val settings = settingsRepository.settings.first()
                mutableUiState.update {
                    it.copy(isLoading = false, languageTag = settings.defaultLanguageTag)
                }
                refreshDictionaryLanguageOptions()
            } else {
                loadExistingEntry(requestedEntryId)
            }
        }
    }

    fun onAction(action: WordEditorAction) {
        when (action) {
            is WordEditorAction.HeadwordChanged -> {
                updateForm {
                    copy(headword = action.value, dictionaryReference = null)
                }
                scheduleDictionarySuggestions()
            }
            is WordEditorAction.LanguageTagChanged -> {
                updateForm {
                    copy(languageTag = action.value, dictionaryReference = null)
                }
                refreshDictionaryLanguageOptions()
            }
            is WordEditorAction.ReadingChanged -> updateForm {
                copy(
                    reading = action.value,
                    readingProvenance = readingProvenance?.markModified(),
                    isReadingUserEdited = true,
                )
            }
            is WordEditorAction.DictionaryLanguagePairSelected -> {
                selectDictionaryLanguagePair(action.key)
            }
            is WordEditorAction.DictionarySuggestionSelected -> {
                selectDictionarySuggestion(action.entry)
            }
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
            WordEditorAction.Save -> save()
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
                            senses = entry.senses.map { sense ->
                                EditableSense(
                                    key = sense.id,
                                    meaning = sense.meaning,
                                    partOfSpeech = sense.partOfSpeech,
                                    examples = sense.examples.map { EditableExample(it.id, it.text) }
                                        .ifEmpty { listOf(EditableExample(newKey())) },
                                    provenance = sense.provenance,
                                )
                            },
                            notes = entry.notes,
                            selectedTagIds = entry.tags.mapTo(mutableSetOf()) { it.id },
                        )
                    }
                }
                if (entry != null) refreshDictionaryLanguageOptions()
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
        val options = sourceLanguage?.let { source ->
            descriptors
                .flatMap(DictionaryProviderDescriptor::supportedLanguagePairs)
                .filter { it.sourceLanguage == source }
                .distinct()
                .sortedWith(
                    compareBy({ it.resultLanguage.value }, { it.resultKind.name }),
                )
                .map { pair ->
                    DictionaryLanguagePairOption(
                        key = pair.stableKey(),
                        languagePair = pair,
                        providerCount = descriptors.count { pair in it.supportedLanguagePairs },
                    )
                }
        }.orEmpty()
        val selectedKey = state.selectedDictionaryLanguageOptionKey
            ?.takeIf { key -> options.any { it.key == key } }
            ?: options.firstOrNull()?.key
        val message = when {
            state.languageTag.isBlank() -> null
            sourceLanguage == null -> "유효한 BCP 47 원문 언어 태그를 입력하면 사전을 검색합니다."
            options.isEmpty() -> "현재 원문 언어를 지원하는 사전 공급원이 없습니다."
            else -> null
        }
        mutableUiState.update {
            it.copy(
                dictionaryLanguageOptions = options,
                selectedDictionaryLanguageOptionKey = selectedKey,
                dictionarySuggestionGroups = emptyList(),
                dictionarySuggestionMessage = message,
                dictionaryReference = null,
            )
        }
        scheduleDictionarySuggestions()
    }

    private fun selectDictionaryLanguagePair(key: String) {
        val state = mutableUiState.value
        if (state.dictionaryLanguageOptions.none { it.key == key }) return
        if (state.selectedDictionaryLanguageOptionKey == key) return
        mutableUiState.update {
            it.copy(
                selectedDictionaryLanguageOptionKey = key,
                dictionarySuggestionGroups = emptyList(),
                dictionarySuggestionMessage = null,
                dictionaryReference = null,
            )
        }
        scheduleDictionarySuggestions()
    }

    private fun scheduleDictionarySuggestions() {
        val state = mutableUiState.value
        val pair = state.dictionaryLanguageOptions
            .firstOrNull { it.key == state.selectedDictionaryLanguageOptionKey }
            ?.languagePair
        val request = DictionarySuggestionRequest(
            query = state.headword.trim(),
            languagePair = pair,
        )
        if (request == dictionarySuggestionRequests.value) return
        mutableUiState.update {
            it.copy(
                isDictionarySearchInProgress = false,
                dictionarySuggestionGroups = emptyList(),
                dictionarySuggestionMessage = when {
                    request.query.isBlank() -> it.dictionarySuggestionMessage
                    pair == null -> it.dictionarySuggestionMessage
                    else -> null
                },
            )
        }
        dictionarySuggestionRequests.value = request
    }

    private suspend fun searchDictionarySuggestions(request: DictionarySuggestionRequest) {
        val languagePair = requireNotNull(request.languagePair)
        val query = DictionaryQuery(
            text = request.query,
            languagePair = languagePair,
            resultLimit = DICTIONARY_RESULT_LIMIT,
        )
        val descriptors = dictionaryProviderRegistry.descriptorsSupporting(query)
        if (descriptors.isEmpty()) {
            if (dictionarySuggestionRequests.value == request) {
                mutableUiState.update {
                    it.copy(
                        isDictionarySearchInProgress = false,
                        dictionarySuggestionGroups = emptyList(),
                        dictionarySuggestionMessage = "선택한 언어 조합을 지원하는 사전이 없습니다.",
                    )
                }
            }
            return
        }

        mutableUiState.update {
            it.copy(isDictionarySearchInProgress = true, dictionarySuggestionMessage = null)
        }
        val groups = coroutineScope {
            descriptors.mapNotNull { descriptor ->
                dictionaryProviderRegistry.find(descriptor.id)?.let { provider ->
                    async { searchProviderSafely(descriptor, provider, query) }
                }
            }.map { it.await() }
        }
        if (dictionarySuggestionRequests.value != request) return
        mutableUiState.update {
            it.copy(
                isDictionarySearchInProgress = false,
                dictionarySuggestionGroups = groups,
                dictionarySuggestionMessage = null,
            )
        }
    }

    private fun searchProvider(
        descriptor: DictionaryProviderDescriptor,
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
        )
        is DictionarySearchResult.Failure -> DictionarySuggestionGroup(
            providerId = descriptor.id,
            providerName = descriptor.displayName,
            message = result.error.toSuggestionMessage(descriptor.displayName),
        )
    }

    private suspend fun searchProviderSafely(
        descriptor: DictionaryProviderDescriptor,
        provider: DictionaryProvider,
        query: DictionaryQuery,
    ): DictionarySuggestionGroup = try {
        searchProvider(descriptor, provider.search(query))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        DictionarySuggestionGroup(
            providerId = descriptor.id,
            providerName = descriptor.displayName,
            message = error.message ?: "${descriptor.displayName} 검색에 실패했습니다.",
        )
    }

    private fun selectDictionarySuggestion(entry: ExternalDictionaryEntry) {
        when (
            val mapping = DictionaryEntryDraftMapper.map(
                entry = entry,
                importedAtEpochMillis = timeProvider.currentTimeMillis(),
            )
        ) {
            is DictionaryEntryDraftMappingResult.Ready -> {
                val seededSenses = mapping.seed.draft.senses
                    .takeIf { senses -> senses.any { it.hasContent() } }
                    ?.let(::toEditableSenses)
                    .orEmpty()
                mutableUiState.update { state ->
                    val acceptsReading = !state.isReadingUserEdited && state.reading.isBlank() &&
                        mapping.seed.draft.reading.isNotBlank()
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
                        dictionaryReference = mapping.seed.transientEntry,
                    )
                }
            }
            is DictionaryEntryDraftMappingResult.ReferenceOnly -> mutableUiState.update {
                it.copy(dictionaryReference = mapping.transientEntry)
            }
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
                    )
                },
                notes = state.notes,
                tagIds = state.selectedTagIds,
                reading = state.reading,
                readingProvenance = state.readingProvenance,
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

    private fun toEditableSenses(senses: List<VocabularySenseDraft>): List<EditableSense> =
        senses.map { sense ->
            EditableSense(
                key = newKey(),
                meaning = sense.meaning,
                partOfSpeech = sense.partOfSpeech,
                examples = sense.examples.map { example ->
                    EditableExample(key = newKey(), text = example)
                }.ifEmpty { listOf(EditableExample(newKey())) },
                provenance = sense.provenance,
            )
        }.ifEmpty { listOf(EditableSense(newKey())) }

    private fun VocabularySenseDraft.hasContent(): Boolean =
        meaning.isNotBlank() || partOfSpeech.isNotBlank() || examples.any(String::isNotBlank)

    private fun EditableSense.isBlankPlaceholder(): Boolean =
        provenance == null && meaning.isBlank() && partOfSpeech.isBlank() &&
            examples.all { it.text.isBlank() }

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
    }
}
