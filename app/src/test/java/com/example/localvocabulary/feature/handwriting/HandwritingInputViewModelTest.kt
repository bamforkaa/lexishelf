package com.example.localvocabulary.feature.handwriting

import com.example.localvocabulary.feature.wordlist.MainDispatcherRule
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HandwritingInputViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `installed model recognizes once after stroke-end debounce`() = runTest {
        val service = FakeRecognitionService()
        val viewModel = viewModel(service = service)

        openReadyCanvas(viewModel)
        viewModel.onAction(HandwritingInputAction.StrokeStarted(point(1)))
        viewModel.onAction(HandwritingInputAction.StrokeContinued(point(2)))
        viewModel.onAction(HandwritingInputAction.StrokeEnded(point(3)))
        advanceTimeBy(HandwritingInputViewModel.RECOGNITION_DEBOUNCE_MILLIS - 1)
        runCurrent()
        assertTrue(service.recognizedInks.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(1, service.recognizedInks.size)
        assertEquals(listOf("食", "事"), viewModel.uiState.value.candidates.map { it.text })
        assertFalse(viewModel.uiState.value.isRecognizing)
    }

    @Test
    fun `new stroke cancels stale recognition result`() = runTest {
        val firstResult = CompletableDeferred<HandwritingRecognitionResult>()
        val service = FakeRecognitionService(recognitionGates = ArrayDeque(listOf(firstResult)))
        val viewModel = viewModel(service = service)

        openReadyCanvas(viewModel)
        drawStroke(viewModel, 1)
        advanceTimeBy(HandwritingInputViewModel.RECOGNITION_DEBOUNCE_MILLIS)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRecognizing)

        viewModel.onAction(HandwritingInputAction.StrokeStarted(point(10)))
        firstResult.complete(
            HandwritingRecognitionResult.Success(listOf(HandwritingCandidate("stale"))),
        )
        runCurrent()

        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        assertFalse(viewModel.uiState.value.isRecognizing)
    }

    @Test
    fun `undo clear and empty ink never leave stale candidates or crash`() = runTest {
        val service = FakeRecognitionService()
        val viewModel = viewModel(service = service)
        openReadyCanvas(viewModel)

        drawStroke(viewModel, 1)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.candidates.isNotEmpty())

        viewModel.onAction(HandwritingInputAction.UndoLastStroke)
        assertTrue(viewModel.uiState.value.ink.isEmpty)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
        viewModel.onAction(HandwritingInputAction.UndoLastStroke)
        viewModel.onAction(HandwritingInputAction.Clear)
        assertTrue(viewModel.uiState.value.ink.isEmpty)
    }

    @Test
    fun `missing model downloads on demand then enables local recognition`() = runTest {
        val service = FakeRecognitionService(modelCheck = HandwritingModelCheckResult.Missing)
        val viewModel = viewModel(service = service)

        viewModel.onAction(HandwritingInputAction.Open("ja"))
        advanceUntilIdle()
        assertEquals(HandwritingModelUiState.ModelMissing, viewModel.uiState.value.modelState)

        viewModel.onAction(HandwritingInputAction.DownloadModel)
        runCurrent()
        assertEquals(HandwritingModelUiState.Ready, viewModel.uiState.value.modelState)
        assertEquals(1, service.downloadedModels.size)
    }

    @Test
    fun `download failure and unsupported language remain non-blocking states`() = runTest {
        val service = FakeRecognitionService(
            modelCheck = HandwritingModelCheckResult.Missing,
            downloadResult = HandwritingModelDownloadResult.Failed,
        )
        val viewModel = viewModel(service = service)
        viewModel.onAction(HandwritingInputAction.Open("ja"))
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.DownloadModel)
        advanceUntilIdle()
        assertEquals(
            HandwritingModelUiState.ModelOperationFailed,
            viewModel.uiState.value.modelState,
        )

        val unsupported = HandwritingInputViewModel(UnsupportedResolver, service)
        unsupported.onAction(HandwritingInputAction.Open("qaa"))
        assertEquals(
            HandwritingModelUiState.UnsupportedLanguage,
            unsupported.uiState.value.modelState,
        )
    }

    @Test
    fun `candidate is only marked after explicit acceptance and clear removes it`() = runTest {
        val viewModel = viewModel(service = FakeRecognitionService())
        openReadyCanvas(viewModel)
        drawStroke(viewModel, 1)
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.selectedCandidate)
        viewModel.onAction(HandwritingInputAction.CandidateAccepted("食"))
        assertEquals("食", viewModel.uiState.value.selectedCandidate)
        viewModel.onAction(HandwritingInputAction.Clear)
        assertEquals(null, viewModel.uiState.value.selectedCandidate)
        assertTrue(viewModel.uiState.value.candidates.isEmpty())
    }

    @Test
    fun `ink can be written before a recognition language is selected`() = runTest {
        val service = FakeRecognitionService()
        val viewModel = viewModel(service)

        viewModel.onAction(HandwritingInputAction.Open())
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(300f, 240f))
        drawStroke(viewModel, 1)
        advanceUntilIdle()

        assertEquals(HandwritingModelUiState.NoLanguageSelected, viewModel.uiState.value.modelState)
        assertEquals(1, viewModel.uiState.value.ink.strokes.size)
        assertTrue(service.recognizedInks.isEmpty())
    }

    @Test
    fun `selecting and switching language reuses the same ink`() = runTest {
        val service = FakeRecognitionService()
        val preferences = FakeLanguagePreferences(listOf("en"))
        val viewModel = viewModel(service, preferences)
        viewModel.onAction(HandwritingInputAction.Open(vocabularyLanguageTags = listOf("ko")))
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(300f, 240f))
        drawStroke(viewModel, 1)

        viewModel.onAction(HandwritingInputAction.LanguageSelected("ja"))
        advanceUntilIdle()
        val originalInk = viewModel.uiState.value.ink
        assertEquals("ja", service.recognizedModels.last().requestedLanguageTag)

        viewModel.onAction(HandwritingInputAction.LanguageSelected("ko"))
        advanceUntilIdle()

        assertEquals(originalInk, viewModel.uiState.value.ink)
        assertEquals(listOf("ja", "ko"), service.recognizedModels.map { it.requestedLanguageTag })
        assertEquals("ko", preferences.recorded.last())
    }

    @Test
    fun `stale result from previously selected language is ignored`() = runTest {
        val staleResult = CompletableDeferred<HandwritingRecognitionResult>()
        val service = FakeRecognitionService(
            recognitionGates = ArrayDeque(listOf(staleResult)),
        )
        val viewModel = viewModel(service)
        viewModel.onAction(HandwritingInputAction.Open())
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(300f, 240f))
        drawStroke(viewModel, 1)
        viewModel.onAction(HandwritingInputAction.LanguageSelected("ja"))
        advanceTimeBy(HandwritingInputViewModel.RECOGNITION_DEBOUNCE_MILLIS)
        runCurrent()

        viewModel.onAction(HandwritingInputAction.LanguageSelected("ko"))
        staleResult.complete(
            HandwritingRecognitionResult.Success(listOf(HandwritingCandidate("stale"))),
        )
        advanceUntilIdle()

        assertEquals("ko", viewModel.uiState.value.selectedLanguageTag)
        assertEquals(listOf("食", "事"), viewModel.uiState.value.candidates.map { it.text })
        assertTrue(viewModel.uiState.value.candidates.none { it.text == "stale" })
    }

    @Test
    fun `model download completion recognizes the ink already on canvas`() = runTest {
        val service = FakeRecognitionService(modelCheck = HandwritingModelCheckResult.Missing)
        val viewModel = viewModel(service)
        viewModel.onAction(HandwritingInputAction.Open())
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(300f, 240f))
        drawStroke(viewModel, 1)

        viewModel.onAction(HandwritingInputAction.LanguageSelected("ja"))
        advanceUntilIdle()
        assertTrue(service.recognizedInks.isEmpty())
        viewModel.onAction(HandwritingInputAction.DownloadModel)
        advanceUntilIdle()

        assertEquals(1, service.recognizedInks.size)
        assertEquals(1, viewModel.uiState.value.ink.strokes.size)
    }

    @Test
    fun `context recent installed and vocabulary languages have deterministic priority`() = runTest {
        val service = FakeRecognitionService(
            installed = listOf(HandwritingModel("zh-Hani", "zh-Hani")),
        )
        val preferences = FakeLanguagePreferences(listOf("ko", "ja"))
        val viewModel = viewModel(service, preferences)

        viewModel.onAction(
            HandwritingInputAction.Open(
                contextLanguageTag = "ja",
                vocabularyLanguageTags = listOf("de", "en"),
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf("ja", "ko", "zh-Hani", "de", "en"),
            viewModel.uiState.value.languageOptions,
        )
    }

    @Test
    fun `writing area resize transforms existing local coordinates`() = runTest {
        val viewModel = viewModel(FakeRecognitionService())
        openReadyCanvas(viewModel)
        viewModel.onAction(HandwritingInputAction.StrokeStarted(point(10)))
        viewModel.onAction(HandwritingInputAction.StrokeEnded(point(20)))
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(600f, 120f))

        val points = viewModel.uiState.value.ink.strokes.single().points
        assertEquals(20f, points.first().x)
        assertEquals(5f, points.first().y)
        assertEquals(40f, points.last().x)
        assertEquals(10f, points.last().y)
    }

    private suspend fun TestScope.openReadyCanvas(viewModel: HandwritingInputViewModel) {
        viewModel.onAction(HandwritingInputAction.Open("ja", preContext = "食べ"))
        advanceUntilIdle()
        viewModel.onAction(HandwritingInputAction.WritingAreaChanged(300f, 240f))
    }

    private fun drawStroke(viewModel: HandwritingInputViewModel, start: Long) {
        viewModel.onAction(HandwritingInputAction.StrokeStarted(point(start)))
        viewModel.onAction(HandwritingInputAction.StrokeEnded(point(start + 1)))
    }

    private fun viewModel(
        service: FakeRecognitionService,
        preferences: HandwritingLanguagePreferences = FakeLanguagePreferences(),
    ) = HandwritingInputViewModel(
        languageResolver = SupportedResolver,
        recognitionService = service,
        languagePreferences = preferences,
    )

    private fun point(timestamp: Long) = HandwritingPoint(
        x = timestamp.toFloat(),
        y = timestamp.toFloat(),
        timestampMillis = timestamp,
    )

    private object SupportedResolver : HandwritingLanguageResolver {
        override fun resolve(languageTag: String) = HandwritingLanguageResolution.Supported(
            HandwritingModel(languageTag, languageTag),
        )
    }

    private object UnsupportedResolver : HandwritingLanguageResolver {
        override fun resolve(languageTag: String) = HandwritingLanguageResolution.Unsupported
    }
}

private class FakeRecognitionService(
    var modelCheck: HandwritingModelCheckResult = HandwritingModelCheckResult.Installed,
    var downloadResult: HandwritingModelDownloadResult = HandwritingModelDownloadResult.Success,
    private val recognitionGates: ArrayDeque<CompletableDeferred<HandwritingRecognitionResult>> =
        ArrayDeque(),
    private val installed: List<HandwritingModel> = emptyList(),
) : HandwritingRecognitionService {
    val recognizedInks = mutableListOf<HandwritingInk>()
    val recognizedModels = mutableListOf<HandwritingModel>()
    val downloadedModels = mutableListOf<HandwritingModel>()

    override suspend fun installedModels(): List<HandwritingModel> = installed

    override suspend fun checkModel(model: HandwritingModel) = modelCheck

    override suspend fun downloadModel(model: HandwritingModel): HandwritingModelDownloadResult {
        downloadedModels += model
        return downloadResult
    }

    override suspend fun deleteModel(model: HandwritingModel) =
        HandwritingModelDownloadResult.Success

    override suspend fun recognize(
        model: HandwritingModel,
        ink: HandwritingInk,
        writingArea: HandwritingWritingArea,
        preContext: String,
    ): HandwritingRecognitionResult {
        recognizedInks += ink
        recognizedModels += model
        return recognitionGates.removeFirstOrNull()?.await()
            ?: HandwritingRecognitionResult.Success(
                listOf(HandwritingCandidate("食"), HandwritingCandidate("事")),
            )
    }
}

private class FakeLanguagePreferences(initial: List<String> = emptyList()) :
    HandwritingLanguagePreferences {
    private val recent = MutableStateFlow(initial)
    override val recentLanguageTags = recent
    val recorded = mutableListOf<String>()

    override suspend fun recordLanguage(languageTag: String) {
        recorded += languageTag
        recent.value = listOf(languageTag) + recent.value.filterNot { it == languageTag }
    }
}
