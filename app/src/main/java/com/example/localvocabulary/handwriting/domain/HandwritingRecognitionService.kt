package com.example.localvocabulary.handwriting.domain

sealed interface HandwritingModelCheckResult {
    data object Installed : HandwritingModelCheckResult
    data object Missing : HandwritingModelCheckResult
    data object Failed : HandwritingModelCheckResult
}

sealed interface HandwritingModelDownloadResult {
    data object Success : HandwritingModelDownloadResult
    data object Failed : HandwritingModelDownloadResult
}

sealed interface HandwritingRecognitionResult {
    data class Success(
        val candidates: List<HandwritingCandidate>,
    ) : HandwritingRecognitionResult

    data object Failed : HandwritingRecognitionResult
}

interface HandwritingRecognitionService {
    suspend fun installedModels(): List<HandwritingModel> = emptyList()

    suspend fun checkModel(model: HandwritingModel): HandwritingModelCheckResult

    suspend fun downloadModel(model: HandwritingModel): HandwritingModelDownloadResult

    suspend fun deleteModel(model: HandwritingModel): HandwritingModelDownloadResult

    suspend fun recognize(
        model: HandwritingModel,
        ink: HandwritingInk,
        writingArea: HandwritingWritingArea,
        preContext: String,
    ): HandwritingRecognitionResult
}
