package com.example.localvocabulary.handwriting.data

import android.util.Log
import com.example.localvocabulary.handwriting.domain.HandwritingCandidate
import com.example.localvocabulary.handwriting.domain.HandwritingInk
import com.example.localvocabulary.handwriting.domain.HandwritingModel
import com.example.localvocabulary.handwriting.domain.HandwritingModelCheckResult
import com.example.localvocabulary.handwriting.domain.HandwritingModelDownloadResult
import com.example.localvocabulary.handwriting.domain.HandwritingRecognitionResult
import com.example.localvocabulary.handwriting.domain.HandwritingRecognitionService
import com.example.localvocabulary.handwriting.domain.HandwritingWritingArea
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class MlKitHandwritingRecognitionService @Inject constructor() : HandwritingRecognitionService {
    private val modelManager = RemoteModelManager.getInstance()

    override suspend fun installedModels(): List<HandwritingModel> = runSdkCall(
        block = {
            modelManager.getDownloadedModels(DigitalInkRecognitionModel::class.java).await()
        },
        success = { models ->
            models.map { model ->
                val languageTag = model.modelIdentifier.languageTag
                HandwritingModel(languageTag, languageTag)
            }.sortedBy(HandwritingModel::modelLanguageTag)
        },
        failed = emptyList(),
    )

    override suspend fun checkModel(model: HandwritingModel): HandwritingModelCheckResult =
        runSdkCall(
            block = { modelManager.isModelDownloaded(model.toMlKitModel()).await() },
            success = { installed ->
                if (installed) HandwritingModelCheckResult.Installed
                else HandwritingModelCheckResult.Missing
            },
            failed = HandwritingModelCheckResult.Failed,
        )

    override suspend fun downloadModel(model: HandwritingModel): HandwritingModelDownloadResult =
        runSdkCall(
            block = {
                modelManager.download(
                    model.toMlKitModel(),
                    DownloadConditions.Builder().build(),
                ).await()
            },
            success = { HandwritingModelDownloadResult.Success },
            failed = HandwritingModelDownloadResult.Failed,
        )

    override suspend fun deleteModel(model: HandwritingModel): HandwritingModelDownloadResult =
        runSdkCall(
            block = { modelManager.deleteDownloadedModel(model.toMlKitModel()).await() },
            success = { HandwritingModelDownloadResult.Success },
            failed = HandwritingModelDownloadResult.Failed,
        )

    override suspend fun recognize(
        model: HandwritingModel,
        ink: HandwritingInk,
        writingArea: HandwritingWritingArea,
        preContext: String,
    ): HandwritingRecognitionResult {
        if (ink.strokes.isEmpty()) return HandwritingRecognitionResult.Success(emptyList())
        val mlKitModel = runCatching { model.toMlKitModel() }.getOrElse {
            Log.w(TAG, "Unable to resolve the configured digital ink model", it)
            return HandwritingRecognitionResult.Failed
        }
        val recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(mlKitModel).build(),
        )
        return try {
            val context = RecognitionContext.builder()
                .setWritingArea(WritingArea(writingArea.width, writingArea.height))
                .setPreContext(preContext.takeLast(MAX_PRE_CONTEXT_LENGTH))
                .build()
            val result = recognizer.recognize(MlKitInkMapper.map(ink), context).await()
            HandwritingRecognitionResult.Success(
                result.candidates
                    .map { candidate -> candidate.text.trim() }
                    .filter(String::isNotEmpty)
                    .distinct()
                    .take(MAX_CANDIDATE_COUNT)
                    .map(::HandwritingCandidate),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.w(TAG, "Digital ink recognition failed", exception)
            HandwritingRecognitionResult.Failed
        } finally {
            recognizer.close()
        }
    }

    private fun HandwritingModel.toMlKitModel(): DigitalInkRecognitionModel {
        val identifier = requireNotNull(
            DigitalInkRecognitionModelIdentifier.fromLanguageTag(modelLanguageTag),
        ) { "Unsupported digital ink model: $modelLanguageTag" }
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    private suspend fun <T, R> runSdkCall(
        block: suspend () -> T,
        success: (T) -> R,
        failed: R,
    ): R = try {
        success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        Log.w(TAG, "Digital ink model operation failed", exception)
        failed
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { value ->
            if (continuation.isActive) continuation.resume(value)
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val TAG = "DigitalInkRecognition"
        const val MAX_PRE_CONTEXT_LENGTH = 20
        const val MAX_CANDIDATE_COUNT = 8
    }
}
