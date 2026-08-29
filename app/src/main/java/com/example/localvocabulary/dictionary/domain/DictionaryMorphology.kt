package com.example.localvocabulary.dictionary.domain

data class DictionaryMorphologyQuery(
    val surface: String,
    val sourceLanguage: Bcp47LanguageTag,
    val resultLimit: Int = 5,
) {
    init {
        require(surface.isNotBlank()) { "Morphology surface must not be blank" }
        require(resultLimit > 0) { "Morphology result limit must be positive" }
    }
}

data class DictionaryLemmaCandidate(
    val surface: String,
    val lemma: String,
    val sourceLanguage: Bcp47LanguageTag,
    val resolverProviderId: DictionaryProviderId,
    val sourceEntryId: String?,
)

sealed interface DictionaryMorphologyResult {
    data class Resolved(
        val candidates: List<DictionaryLemmaCandidate>,
        val isTruncated: Boolean = false,
    ) :
        DictionaryMorphologyResult {
        init {
            require(candidates.isNotEmpty()) { "Use NoResult for an empty morphology lookup" }
        }
    }

    data object NoResult : DictionaryMorphologyResult
    data object DatasetUnavailable : DictionaryMorphologyResult
    data class MalformedDataset(val detail: String?) : DictionaryMorphologyResult
}

/** Source-attested form-to-lemma lookup. Implementations must not guess lemmas. */
interface DictionaryMorphologyResolver {
    val providerId: DictionaryProviderId
    val displayName: String
    val supportedSourceLanguages: Set<Bcp47LanguageTag>

    suspend fun resolve(query: DictionaryMorphologyQuery): DictionaryMorphologyResult
}

object NoOpDictionaryMorphologyResolver : DictionaryMorphologyResolver {
    override val providerId = DictionaryProviderId("none")
    override val displayName = ""
    override val supportedSourceLanguages: Set<Bcp47LanguageTag> = emptySet()

    override suspend fun resolve(query: DictionaryMorphologyQuery) =
        DictionaryMorphologyResult.NoResult
}
