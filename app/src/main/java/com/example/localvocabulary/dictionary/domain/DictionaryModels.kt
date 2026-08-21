package com.example.localvocabulary.dictionary.domain

data class DictionaryProviderDescriptor(
    val id: String,
    val displayName: String,
    val supportedSourceLanguageTags: Set<String>,
    val supportedDefinitionLanguageTags: Set<String>,
    val access: DictionaryAccess,
    val capabilities: Set<DictionaryCapability>,
    val attribution: DictionaryAttribution,
)

enum class DictionaryAccess { ONLINE, OFFLINE }

enum class DictionaryCapability {
    EXACT_LOOKUP,
    PRONUNCIATION,
    AUDIO,
    READING,
    TRANSLITERATION,
    GRAMMATICAL_GENDER,
    INFLECTION,
    ETYMOLOGY,
    EXAMPLE_SENTENCES,
    TRANSLATIONS,
    MONOLINGUAL_DEFINITIONS,
}

data class DictionaryAttribution(
    val sourceName: String,
    val sourceUrl: String,
    val licenseName: String?,
    val licenseUrl: String?,
    val notice: String,
)

data class DictionaryQuery(
    val text: String,
    val sourceLanguageTag: String,
    val definitionLanguageTag: String,
)

data class ExternalDictionaryEntry(
    val providerId: String,
    val sourceEntryId: String?,
    val headword: String,
    val sourceLanguageTag: String,
    val reading: String?,
    val senses: List<ExternalDictionarySense>,
    val attribution: DictionaryAttribution,
)

data class ExternalDictionarySense(
    val definition: String,
    val partOfSpeech: String?,
    val examples: List<String>,
)

sealed interface DictionarySearchResult {
    data class Success(val entries: List<ExternalDictionaryEntry>) : DictionarySearchResult
    data class Failure(val error: DictionaryProviderError) : DictionarySearchResult
}

sealed interface DictionaryProviderError {
    data object Unavailable : DictionaryProviderError
    data object MissingCredentials : DictionaryProviderError
    data object NetworkFailure : DictionaryProviderError
    data object RateLimited : DictionaryProviderError
    data class InvalidResponse(val detail: String) : DictionaryProviderError
    data class UnsupportedLanguagePair(
        val sourceLanguageTag: String,
        val definitionLanguageTag: String,
    ) : DictionaryProviderError
}

sealed interface ProviderAvailability {
    data object Available : ProviderAvailability
    data class Unavailable(val reason: String) : ProviderAvailability
}
