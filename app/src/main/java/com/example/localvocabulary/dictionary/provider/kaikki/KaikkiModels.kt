package com.example.localvocabulary.dictionary.provider.kaikki

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class KaikkiEntryRecord(
    @SerialName("w") val headword: String,
    @SerialName("p") val rawPartOfSpeech: String = "",
    @SerialName("n") val pronunciations: List<String> = emptyList(),
    @SerialName("f") val retainedForms: List<KaikkiFormRecord> = emptyList(),
    @SerialName("fc") val totalFormCount: Int = retainedForms.size,
    @SerialName("s") val senses: List<KaikkiSenseRecord>,
)

@Serializable
internal data class KaikkiFormRecord(
    @SerialName("f") val form: String,
    @SerialName("l") val label: String = "",
)

@Serializable
internal data class KaikkiSenseRecord(
    @SerialName("o") val order: Int,
    @SerialName("i") val sourceSenseId: String = "",
    @SerialName("g") val glosses: List<String>,
    @SerialName("e") val retainedExamples: List<String> = emptyList(),
    @SerialName("x") val availableExampleCount: Int = 0,
    @SerialName("d") val grammaticalGender: String? = null,
)

internal data class KaikkiMatch(
    val sourceEntryId: String,
    val entry: KaikkiEntryRecord,
)

internal sealed interface KaikkiLookupResult {
    data class Matches(
        val records: List<KaikkiMatch>,
        val isTruncated: Boolean,
        val datasetVersion: String = KAIKKI_RELEASE_ID,
    ) : KaikkiLookupResult
    data object NoMatch : KaikkiLookupResult
    data object DatasetUnavailable : KaikkiLookupResult
    data class MalformedDataset(val detail: String?) : KaikkiLookupResult
}

internal sealed interface KaikkiDatasetAvailability {
    data object Available : KaikkiDatasetAvailability
    data object Unavailable : KaikkiDatasetAvailability
    data class Invalid(val detail: String?) : KaikkiDatasetAvailability
}

internal interface KaikkiLookup {
    suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLimit: Int,
    ): KaikkiLookupResult

    suspend fun availability(): KaikkiDatasetAvailability
}
