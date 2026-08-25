package com.example.localvocabulary.dictionary.provider.cccedict

import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class CcCedictDataSource(
    private val datasetSource: CcCedictDatasetSource,
    private val parser: CcCedictParser = CcCedictParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val loadMutex = Mutex()

    @Volatile
    private var cachedLoad: IdentifiedLoad? = null

    suspend fun exactLookup(
        query: String,
        headwordForm: CcCedictHeadwordForm,
        resultLimit: Int?,
    ): CcCedictLookupResult = withContext(ioDispatcher) {
        when (val load = loadIndex()) {
            is CcCedictLoadResult.Ready -> {
                val matches = load.index.find(query.trim(), headwordForm)
                if (matches.isEmpty()) {
                    CcCedictLookupResult.NoMatch
                } else {
                    CcCedictLookupResult.Matches(
                        records = resultLimit?.let(matches::take) ?: matches,
                        totalMatchCount = matches.size,
                        skippedMalformedLineCount = load.skippedMalformedLineCount,
                        datasetVersion = load.datasetVersion,
                    )
                }
            }
            CcCedictLoadResult.Unavailable -> CcCedictLookupResult.DatasetUnavailable
            is CcCedictLoadResult.Invalid -> CcCedictLookupResult.MalformedDataset(load.detail)
        }
    }

    suspend fun availability(): CcCedictDatasetAvailability = withContext(ioDispatcher) {
        when (val result = datasetSource.open()) {
            is CcCedictDatasetOpenResult.Opened -> {
                result.reader.close()
                CcCedictDatasetAvailability.Available
            }
            CcCedictDatasetOpenResult.Missing -> CcCedictDatasetAvailability.Unavailable
            is CcCedictDatasetOpenResult.Failed -> CcCedictDatasetAvailability.Invalid(result.detail)
        }
    }

    private suspend fun loadIndex(): CcCedictLoadResult {
        val identity = datasetSource.activeIdentity()
        return cachedLoad?.takeIf { it.identity == identity }?.load ?: loadMutex.withLock {
            cachedLoad?.takeIf { it.identity == identity }?.load
                ?: readIndex().also { cachedLoad = IdentifiedLoad(identity, it) }
        }
    }

    private fun readIndex(): CcCedictLoadResult = when (val opened = datasetSource.open()) {
        CcCedictDatasetOpenResult.Missing -> CcCedictLoadResult.Unavailable
        is CcCedictDatasetOpenResult.Failed -> CcCedictLoadResult.Invalid(opened.detail)
        is CcCedictDatasetOpenResult.Opened -> try {
            val report = opened.reader.use(parser::parse)
            if (report.records.isEmpty()) {
                CcCedictLoadResult.Invalid(
                    report.issues.firstOrNull()?.reason ?: "CC-CEDICT dataset contains no records",
                )
            } else {
                CcCedictLoadResult.Ready(
                    index = CcCedictIndex(report.records),
                    skippedMalformedLineCount = report.issues.size,
                    datasetVersion = datasetSource.datasetVersion() ?: CcCedictProvider.RELEASE_ID,
                )
            }
        } catch (error: IOException) {
            CcCedictLoadResult.Invalid(error.message)
        }
    }
}

private data class IdentifiedLoad(
    val identity: String?,
    val load: CcCedictLoadResult,
)

private sealed interface CcCedictLoadResult {
    data class Ready(
        val index: CcCedictIndex,
        val skippedMalformedLineCount: Int,
        val datasetVersion: String?,
    ) : CcCedictLoadResult

    data object Unavailable : CcCedictLoadResult
    data class Invalid(val detail: String?) : CcCedictLoadResult
}

private class CcCedictIndex(records: List<CcCedictRecord>) {
    private val simplified = records.groupBy(CcCedictRecord::simplified)
    private val traditional = records.groupBy(CcCedictRecord::traditional)

    fun find(query: String, form: CcCedictHeadwordForm): List<CcCedictRecord> = when (form) {
        CcCedictHeadwordForm.SIMPLIFIED -> simplified[query]
        CcCedictHeadwordForm.TRADITIONAL -> traditional[query]
    }.orEmpty()
}
