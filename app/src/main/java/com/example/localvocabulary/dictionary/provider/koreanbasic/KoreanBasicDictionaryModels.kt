package com.example.localvocabulary.dictionary.provider.koreanbasic

internal data class KoreanBasicDictionaryRecord(
    val officialEntryId: String,
    val officialSenseId: String,
    val koreanHeadword: String,
    val partOfSpeech: String?,
    val translationTerm: String,
    val entryOrder: Long,
    val senseOrder: Int,
    val translationOrder: Int,
) {
    val stableSourceEntryId: String = "$officialEntryId:$koreanHeadword"
}

internal sealed interface KoreanBasicDictionaryLookupResult {
    data class Matches(val records: List<KoreanBasicDictionaryRecord>) :
        KoreanBasicDictionaryLookupResult

    data object NoMatch : KoreanBasicDictionaryLookupResult
    data object DatasetUnavailable : KoreanBasicDictionaryLookupResult
    data class MalformedDataset(val detail: String?) : KoreanBasicDictionaryLookupResult
}

internal sealed interface KoreanBasicDictionaryDatasetAvailability {
    data object Available : KoreanBasicDictionaryDatasetAvailability
    data object Unavailable : KoreanBasicDictionaryDatasetAvailability
    data class Invalid(val detail: String?) : KoreanBasicDictionaryDatasetAvailability
}

internal interface KoreanBasicDictionaryLookup {
    suspend fun exactLookup(
        query: String,
        sourceLanguageTag: String,
        resultLanguageTag: String,
    ): KoreanBasicDictionaryLookupResult

    suspend fun availability(): KoreanBasicDictionaryDatasetAvailability
}
