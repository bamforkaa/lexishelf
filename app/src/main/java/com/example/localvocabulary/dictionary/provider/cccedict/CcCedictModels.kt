package com.example.localvocabulary.dictionary.provider.cccedict

import java.io.BufferedReader

internal enum class CcCedictFormat {
    V1,
    V2,
}

internal data class CcCedictRecord(
    val traditional: String,
    val simplified: String,
    val pinyin: String,
    val senses: List<List<String>>,
    val format: CcCedictFormat,
    val sourceLineNumber: Int,
) {
    val stableSourceId: String = "$traditional|$simplified|$pinyin"
}

internal data class CcCedictParseIssue(
    val lineNumber: Int,
    val line: String,
    val reason: String,
)

internal data class CcCedictParseReport(
    val records: List<CcCedictRecord>,
    val blankLineCount: Int,
    val commentLineCount: Int,
    val issues: List<CcCedictParseIssue>,
)

internal sealed interface CcCedictDatasetOpenResult {
    data class Opened(val reader: BufferedReader) : CcCedictDatasetOpenResult
    data object Missing : CcCedictDatasetOpenResult
    data class Failed(val detail: String?) : CcCedictDatasetOpenResult
}

internal fun interface CcCedictDatasetSource {
    fun open(): CcCedictDatasetOpenResult

    fun activeIdentity(): String? = null

    fun datasetVersion(): String? = null
}

internal enum class CcCedictHeadwordForm {
    SIMPLIFIED,
    TRADITIONAL,
}

internal sealed interface CcCedictLookupResult {
    data class Matches(
        val records: List<CcCedictRecord>,
        val totalMatchCount: Int,
        val skippedMalformedLineCount: Int,
        val datasetVersion: String? = CcCedictProvider.RELEASE_ID,
    ) : CcCedictLookupResult

    data object NoMatch : CcCedictLookupResult
    data object DatasetUnavailable : CcCedictLookupResult
    data class MalformedDataset(val detail: String?) : CcCedictLookupResult
}

internal sealed interface CcCedictDatasetAvailability {
    data object Available : CcCedictDatasetAvailability
    data object Unavailable : CcCedictDatasetAvailability
    data class Invalid(val detail: String?) : CcCedictDatasetAvailability
}
