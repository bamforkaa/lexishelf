package com.example.localvocabulary.dictionary.provider.jmdict

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class JmDictEntryRecord(
    @SerialName("e") val entrySequence: String,
    @SerialName("k") val writtenForms: List<JmDictWrittenForm> = emptyList(),
    @SerialName("r") val readings: List<JmDictReading>,
    @SerialName("s") val senses: List<JmDictSense>,
)

@Serializable
internal data class JmDictWrittenForm(
    @SerialName("t") val text: String,
    @SerialName("i") val information: List<String> = emptyList(),
    @SerialName("p") val priorityMarkers: List<String> = emptyList(),
)

@Serializable
internal data class JmDictReading(
    @SerialName("t") val text: String,
    @SerialName("n") val hasNoWrittenForm: Boolean = false,
    @SerialName("x") val writtenFormRestrictions: List<String> = emptyList(),
    @SerialName("i") val information: List<String> = emptyList(),
    @SerialName("p") val priorityMarkers: List<String> = emptyList(),
)

@Serializable
internal data class JmDictSense(
    @SerialName("o") val order: Int,
    @SerialName("k") val writtenFormRestrictions: List<String> = emptyList(),
    @SerialName("r") val readingRestrictions: List<String> = emptyList(),
    @SerialName("p") val partOfSpeech: List<String> = emptyList(),
    @SerialName("xp") val crossReferences: List<String> = emptyList(),
    @SerialName("a") val antonyms: List<String> = emptyList(),
    @SerialName("f") val fields: List<String> = emptyList(),
    @SerialName("m") val miscellaneous: List<String> = emptyList(),
    @SerialName("i") val information: List<String> = emptyList(),
    @SerialName("l") val loanwordSources: List<JmDictAttributedText> = emptyList(),
    @SerialName("d") val dialects: List<String> = emptyList(),
    @SerialName("g") val glosses: List<JmDictGloss> = emptyList(),
)

@Serializable
internal data class JmDictAttributedText(
    @SerialName("t") val text: String = "",
    @SerialName("a") val attributes: Map<String, String> = emptyMap(),
)

@Serializable
internal data class JmDictGloss(
    @SerialName("t") val text: String,
    @SerialName("l") val languageCode: String = "eng",
    @SerialName("a") val attributes: Map<String, String> = emptyMap(),
)

internal enum class JmDictMatchKind { WRITTEN_FORM, READING }

internal data class JmDictMatch(
    val entry: JmDictEntryRecord,
    val matchKind: JmDictMatchKind,
    val matchedElementOrder: Int,
)

internal sealed interface JmDictLookupResult {
    data class Matches(val records: List<JmDictMatch>) : JmDictLookupResult
    data object NoMatch : JmDictLookupResult
    data object DatasetUnavailable : JmDictLookupResult
    data class MalformedDataset(val detail: String?) : JmDictLookupResult
}

internal sealed interface JmDictDatasetAvailability {
    data object Available : JmDictDatasetAvailability
    data object Unavailable : JmDictDatasetAvailability
    data class Invalid(val detail: String?) : JmDictDatasetAvailability
}

internal interface JmDictLookup {
    suspend fun exactLookup(query: String, resultLimit: Int?): JmDictLookupResult
    suspend fun availability(): JmDictDatasetAvailability
}
