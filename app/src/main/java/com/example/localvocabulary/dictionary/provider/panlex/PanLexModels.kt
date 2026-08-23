package com.example.localvocabulary.dictionary.provider.panlex

internal data class PanLexRelationRecord(
    val sourceExpressionId: Long,
    val sourceText: String,
    val targetExpressionId: Long,
    val targetText: String,
    val representativeMeaningId: Long,
    val representativeSourceId: Long,
    val sourceAttestationCount: Int,
    val sourceGroupCount: Int,
    val translationQuality: Int,
)

internal sealed interface PanLexLookupResult {
    data class Matches(
        val records: List<PanLexRelationRecord>,
        val isTruncated: Boolean,
    ) : PanLexLookupResult

    data object NoMatch : PanLexLookupResult
    data object DatasetUnavailable : PanLexLookupResult
    data class MalformedDataset(val detail: String?) : PanLexLookupResult
}

internal sealed interface PanLexDatasetAvailability {
    data object Available : PanLexDatasetAvailability
    data object Unavailable : PanLexDatasetAvailability
    data class Invalid(val detail: String?) : PanLexDatasetAvailability
}

internal interface PanLexLookup {
    suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
        resultLimit: Int,
    ): PanLexLookupResult

    suspend fun availability(): PanLexDatasetAvailability
}
