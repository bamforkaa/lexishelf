package com.example.localvocabulary.feature.handwriting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.handwriting.domain.HandwritingCandidate
import com.example.localvocabulary.handwriting.domain.HandwritingInk
import com.example.localvocabulary.handwriting.domain.HandwritingLanguagePreferences
import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolution
import com.example.localvocabulary.handwriting.domain.HandwritingLanguageResolver
import com.example.localvocabulary.handwriting.domain.HandwritingModel
import com.example.localvocabulary.handwriting.domain.HandwritingModelCheckResult
import com.example.localvocabulary.handwriting.domain.HandwritingModelDownloadResult
import com.example.localvocabulary.handwriting.domain.HandwritingPoint
import com.example.localvocabulary.handwriting.domain.HandwritingRecognitionResult
import com.example.localvocabulary.handwriting.domain.HandwritingRecognitionService
import com.example.localvocabulary.handwriting.domain.HandwritingWritingArea
import com.example.localvocabulary.handwriting.domain.prioritizeHandwritingLanguageTags
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HandwritingModelUiState {
    data object NoLanguageSelected : HandwritingModelUiState
    data object Resolving : HandwritingModelUiState
    data object UnsupportedLanguage : HandwritingModelUiState
    data object InvalidLanguageTag : HandwritingModelUiState
    data object ModelMissing : HandwritingModelUiState
    data object Downloading : HandwritingModelUiState
    data object Ready : HandwritingModelUiState
    data object ModelOperationFailed : HandwritingModelUiState
}

data class HandwritingInputUiState(
    val isOpen: Boolean = false,
    val contextLanguageTag: String? = null,
    val selectedLanguageTag: String? = null,
    val languageOptions: List<String> = emptyList(),
    val installedModelLanguageTags: Set<String> = emptySet(),
    val modelLanguageTag: String? = null,
    val modelState: HandwritingModelUiState = HandwritingModelUiState.NoLanguageSelected,
    val ink: HandwritingInk = HandwritingInk(),
    val writingArea: HandwritingWritingArea? = null,
    val candidates: List<HandwritingCandidate> = emptyList(),
    val selectedCandidate: String? = null,
    val isRecognizing: Boolean = false,
    val recognitionFailed: Boolean = false,
)

sealed interface HandwritingInputAction {
    data class Open(
        val contextLanguageTag: String? = null,
        val preContext: String = "",
        val vocabularyLanguageTags: List<String> = emptyList(),
    ) : HandwritingInputAction
    data object Close : HandwritingInputAction
    data class LanguageSelected(val languageTag: String) : HandwritingInputAction
    data class WritingAreaChanged(val width: Float, val height: Float) : HandwritingInputAction
    data class StrokeStarted(val point: HandwritingPoint) : HandwritingInputAction
    data class StrokeContinued(val point: HandwritingPoint) : HandwritingInputAction
    data class StrokeEnded(val point: HandwritingPoint? = null) : HandwritingInputAction
    data object UndoLastStroke : HandwritingInputAction
    data object Clear : HandwritingInputAction
    data object DownloadModel : HandwritingInputAction
    data class CandidateAccepted(val text: String) : HandwritingInputAction
}

@HiltViewModel
class HandwritingInputViewModel @Inject constructor(
    private val languageResolver: HandwritingLanguageResolver,
    private val recognitionService: HandwritingRecognitionService,
    private val languagePreferences: HandwritingLanguagePreferences = EmptyLanguagePreferences,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HandwritingInputUiState())
    val uiState: StateFlow<HandwritingInputUiState> = mutableUiState.asStateFlow()

    private var activeModel: HandwritingModel? = null
    private var modelJob: Job? = null
    private var languageOptionsJob: Job? = null
    private var recognitionJob: Job? = null
    private var recognitionGeneration = 0L
    private var sessionGeneration = 0L
    private var recognitionPreContext = ""

    fun onAction(action: HandwritingInputAction) {
        when (action) {
            is HandwritingInputAction.Open -> open(
                contextLanguageTag = action.contextLanguageTag,
                preContext = action.preContext,
                vocabularyLanguageTags = action.vocabularyLanguageTags,
            )
            HandwritingInputAction.Close -> close()
            is HandwritingInputAction.LanguageSelected -> selectLanguage(
                action.languageTag,
                rememberSelection = true,
            )
            is HandwritingInputAction.WritingAreaChanged -> updateWritingArea(action)
            is HandwritingInputAction.StrokeStarted -> beginStroke(action.point)
            is HandwritingInputAction.StrokeContinued -> continueStroke(action.point)
            is HandwritingInputAction.StrokeEnded -> endStroke(action.point)
            HandwritingInputAction.UndoLastStroke -> undoLastStroke()
            HandwritingInputAction.Clear -> clearInk()
            HandwritingInputAction.DownloadModel -> downloadModel()
            is HandwritingInputAction.CandidateAccepted -> acceptCandidate(action.text)
        }
    }

    private fun open(
        contextLanguageTag: String?,
        preContext: String,
        vocabularyLanguageTags: List<String>,
    ) {
        cancelPendingOperations()
        sessionGeneration += 1
        activeModel = null
        recognitionPreContext = preContext
        val canonicalContext = contextLanguageTag
            ?.let { Bcp47LanguageTag.parse(it)?.value ?: it.trim() }
            ?.takeIf(String::isNotEmpty)
        mutableUiState.value = HandwritingInputUiState(
            isOpen = true,
            contextLanguageTag = canonicalContext,
            languageOptions = prioritizeHandwritingLanguageTags(
                contextLanguageTag = canonicalContext,
                recentLanguageTags = emptyList(),
                installedLanguageTags = emptyList(),
                vocabularyLanguageTags = vocabularyLanguageTags,
            ),
        )
        loadLanguageOptions(
            generation = sessionGeneration,
            contextLanguageTag = canonicalContext,
            vocabularyLanguageTags = vocabularyLanguageTags,
        )
        canonicalContext?.let { selectLanguage(it, rememberSelection = false) }
    }

    private fun loadLanguageOptions(
        generation: Long,
        contextLanguageTag: String?,
        vocabularyLanguageTags: List<String>,
    ) {
        languageOptionsJob = viewModelScope.launch {
            val recent = languagePreferences.recentLanguageTags.first()
            val installed = recognitionService.installedModels()
            if (generation != sessionGeneration || !mutableUiState.value.isOpen) return@launch
            mutableUiState.update { state ->
                state.copy(
                    languageOptions = prioritizeHandwritingLanguageTags(
                        contextLanguageTag = contextLanguageTag,
                        recentLanguageTags = listOfNotNull(state.selectedLanguageTag) + recent,
                        installedLanguageTags = installed.map(HandwritingModel::requestedLanguageTag),
                        vocabularyLanguageTags = vocabularyLanguageTags,
                    ),
                    installedModelLanguageTags = installed
                        .map(HandwritingModel::modelLanguageTag)
                        .toSet(),
                )
            }
        }
    }

    private fun close() {
        cancelPendingOperations()
        sessionGeneration += 1
        activeModel = null
        recognitionPreContext = ""
        mutableUiState.value = HandwritingInputUiState()
    }

    private fun selectLanguage(languageTag: String, rememberSelection: Boolean) {
        if (!mutableUiState.value.isOpen) return
        modelJob?.cancel()
        modelJob = null
        invalidateRecognition()
        activeModel = null
        val displayTag = Bcp47LanguageTag.parse(languageTag)?.value ?: languageTag.trim()
        mutableUiState.update { state ->
            state.copy(
                selectedLanguageTag = displayTag,
                languageOptions = prioritizeHandwritingLanguageTags(
                    contextLanguageTag = state.contextLanguageTag,
                    recentLanguageTags = listOf(displayTag) + state.languageOptions,
                    installedLanguageTags = emptyList(),
                    vocabularyLanguageTags = emptyList(),
                ),
                modelLanguageTag = null,
                modelState = HandwritingModelUiState.Resolving,
                candidates = emptyList(),
                selectedCandidate = null,
                recognitionFailed = false,
            )
        }
        when (val resolution = languageResolver.resolve(displayTag)) {
            HandwritingLanguageResolution.InvalidLanguageTag -> mutableUiState.update {
                it.copy(modelState = HandwritingModelUiState.InvalidLanguageTag)
            }
            HandwritingLanguageResolution.Unsupported -> mutableUiState.update {
                it.copy(modelState = HandwritingModelUiState.UnsupportedLanguage)
            }
            is HandwritingLanguageResolution.Supported -> {
                activeModel = resolution.model
                mutableUiState.update {
                    it.copy(modelLanguageTag = resolution.model.modelLanguageTag)
                }
                if (rememberSelection) {
                    viewModelScope.launch { languagePreferences.recordLanguage(displayTag) }
                }
                checkModel(resolution.model)
            }
        }
    }

    private fun checkModel(model: HandwritingModel) {
        modelJob = viewModelScope.launch {
            val result = recognitionService.checkModel(model)
            if (activeModel != model || !mutableUiState.value.isOpen) return@launch
            val modelState = when (result) {
                HandwritingModelCheckResult.Installed -> HandwritingModelUiState.Ready
                HandwritingModelCheckResult.Missing -> HandwritingModelUiState.ModelMissing
                HandwritingModelCheckResult.Failed -> HandwritingModelUiState.ModelOperationFailed
            }
            mutableUiState.update { state ->
                state.copy(
                    modelState = modelState,
                    installedModelLanguageTags = if (
                        result == HandwritingModelCheckResult.Installed
                    ) {
                        state.installedModelLanguageTags + model.modelLanguageTag
                    } else {
                        state.installedModelLanguageTags
                    },
                )
            }
            if (modelState == HandwritingModelUiState.Ready) scheduleRecognition()
        }
    }

    private fun updateWritingArea(action: HandwritingInputAction.WritingAreaChanged) {
        if (action.width <= 0f || action.height <= 0f) return
        val oldArea = mutableUiState.value.writingArea
        val newArea = HandwritingWritingArea(action.width, action.height)
        if (oldArea == newArea) return
        if (oldArea != null && !mutableUiState.value.ink.isEmpty) invalidateRecognition()
        mutableUiState.update { state ->
            val transformedInk = if (oldArea != null && !state.ink.isEmpty) {
                state.ink.scaled(
                    scaleX = newArea.width / oldArea.width,
                    scaleY = newArea.height / oldArea.height,
                )
            } else {
                state.ink
            }
            state.copy(
                writingArea = newArea,
                ink = transformedInk,
                candidates = if (oldArea != null) emptyList() else state.candidates,
                selectedCandidate = if (oldArea != null) null else state.selectedCandidate,
            )
        }
        if (oldArea != null) scheduleRecognition()
    }

    private fun beginStroke(point: HandwritingPoint) {
        if (!mutableUiState.value.isOpen) return
        invalidateRecognition()
        mutableUiState.update {
            it.copy(
                ink = it.ink.begin(point),
                candidates = emptyList(),
                selectedCandidate = null,
                recognitionFailed = false,
            )
        }
    }

    private fun continueStroke(point: HandwritingPoint) {
        if (mutableUiState.value.ink.activePoints.isEmpty()) return
        mutableUiState.update { it.copy(ink = it.ink.append(point)) }
    }

    private fun endStroke(point: HandwritingPoint?) {
        if (mutableUiState.value.ink.activePoints.isEmpty()) return
        invalidateRecognition()
        mutableUiState.update { it.copy(ink = it.ink.end(point)) }
        scheduleRecognition()
    }

    private fun undoLastStroke() {
        invalidateRecognition()
        mutableUiState.update {
            it.copy(
                ink = it.ink.undoLastStroke(),
                candidates = emptyList(),
                selectedCandidate = null,
                recognitionFailed = false,
            )
        }
        scheduleRecognition()
    }

    private fun clearInk() {
        invalidateRecognition()
        mutableUiState.update {
            it.copy(
                ink = it.ink.clear(),
                candidates = emptyList(),
                selectedCandidate = null,
                recognitionFailed = false,
            )
        }
    }

    private fun downloadModel() {
        val model = activeModel ?: return
        if (mutableUiState.value.modelState == HandwritingModelUiState.Downloading) return
        modelJob?.cancel()
        mutableUiState.update { it.copy(modelState = HandwritingModelUiState.Downloading) }
        modelJob = viewModelScope.launch {
            val result = recognitionService.downloadModel(model)
            if (activeModel != model || !mutableUiState.value.isOpen) return@launch
            when (result) {
                HandwritingModelDownloadResult.Success -> {
                    mutableUiState.update {
                        it.copy(
                            modelState = HandwritingModelUiState.Ready,
                            installedModelLanguageTags =
                                it.installedModelLanguageTags + model.modelLanguageTag,
                        )
                    }
                    scheduleRecognition()
                }
                HandwritingModelDownloadResult.Failed -> mutableUiState.update {
                    it.copy(modelState = HandwritingModelUiState.ModelOperationFailed)
                }
            }
        }
    }

    private fun acceptCandidate(text: String) {
        if (mutableUiState.value.candidates.none { it.text == text }) return
        mutableUiState.update { it.copy(selectedCandidate = text) }
    }

    private fun scheduleRecognition() {
        val state = mutableUiState.value
        val model = activeModel ?: return
        val writingArea = state.writingArea ?: return
        if (
            state.modelState != HandwritingModelUiState.Ready ||
            state.ink.strokes.isEmpty() ||
            state.ink.activePoints.isNotEmpty()
        ) return

        val generation = recognitionGeneration
        val ink = state.ink
        recognitionJob = viewModelScope.launch {
            delay(RECOGNITION_DEBOUNCE_MILLIS)
            if (!isCurrent(generation, model)) return@launch
            mutableUiState.update { it.copy(isRecognizing = true, recognitionFailed = false) }
            val result = recognitionService.recognize(
                model = model,
                ink = ink,
                writingArea = writingArea,
                preContext = recognitionPreContext,
            )
            if (!isCurrent(generation, model)) return@launch
            mutableUiState.update { current ->
                when (result) {
                    is HandwritingRecognitionResult.Success -> current.copy(
                        candidates = result.candidates,
                        selectedCandidate = null,
                        isRecognizing = false,
                        recognitionFailed = false,
                    )
                    HandwritingRecognitionResult.Failed -> current.copy(
                        candidates = emptyList(),
                        selectedCandidate = null,
                        isRecognizing = false,
                        recognitionFailed = true,
                    )
                }
            }
        }
    }

    private fun invalidateRecognition() {
        recognitionGeneration += 1
        recognitionJob?.cancel()
        recognitionJob = null
        mutableUiState.update { it.copy(isRecognizing = false) }
    }

    private fun cancelPendingOperations() {
        invalidateRecognition()
        modelJob?.cancel()
        modelJob = null
        languageOptionsJob?.cancel()
        languageOptionsJob = null
    }

    private fun isCurrent(generation: Long, model: HandwritingModel): Boolean =
        generation == recognitionGeneration &&
            activeModel == model &&
            mutableUiState.value.isOpen

    companion object {
        const val RECOGNITION_DEBOUNCE_MILLIS = 350L
    }
}

private object EmptyLanguagePreferences : HandwritingLanguagePreferences {
    override val recentLanguageTags = flowOf(emptyList<String>())
    override suspend fun recordLanguage(languageTag: String) = Unit
}
