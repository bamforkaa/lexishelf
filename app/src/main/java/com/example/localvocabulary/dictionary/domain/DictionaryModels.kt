package com.example.localvocabulary.dictionary.domain

import java.util.IllformedLocaleException
import java.util.Locale

@JvmInline
value class DictionaryProviderId(val value: String) {
    init {
        require(PROVIDER_ID.matches(value)) {
            "Provider ID must be a stable lowercase identifier"
        }
    }

    private companion object {
        val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    }
}

@JvmInline
value class Bcp47LanguageTag private constructor(val value: String) {
    companion object {
        fun parse(rawTag: String): Bcp47LanguageTag? {
            val candidate = rawTag.trim()
            if (candidate.isEmpty() || '_' in candidate) return null
            return try {
                Locale.Builder()
                    .setLanguageTag(candidate)
                    .build()
                    .toLanguageTag()
                    .takeUnless { it == "und" }
                    ?.let(::Bcp47LanguageTag)
            } catch (_: IllformedLocaleException) {
                null
            }
        }

        fun requireValid(rawTag: String): Bcp47LanguageTag =
            requireNotNull(parse(rawTag)) { "Invalid BCP 47 language tag: $rawTag" }
    }
}

data class DictionaryProviderDescriptor(
    val id: DictionaryProviderId,
    val displayName: String,
    val supportedLanguagePairs: Set<DictionaryLanguagePair>,
    val access: DictionaryAccess,
    val capabilities: Set<DictionaryCapability>,
    val attribution: DictionaryAttribution,
    val dataset: DictionaryDatasetMetadata? = null,
) {
    init {
        require(displayName.isNotBlank()) { "Provider display name must not be blank" }
        require(supportedLanguagePairs.isNotEmpty()) {
            "Provider must declare at least one supported language pair"
        }
    }

    fun supports(query: DictionaryQuery): Boolean = query.languagePair in supportedLanguagePairs
}

enum class DictionaryAccess {
    ONLINE,
    LOCAL_DATASET,
}

enum class DictionaryCapability {
    EXACT_LOOKUP,
    ALTERNATE_WRITTEN_FORMS,
    MONOLINGUAL_DEFINITIONS,
    TRANSLATIONS,
    PRONUNCIATION_TEXT,
    AUDIO,
    READING,
    TRANSLITERATION,
    EXAMPLE_SENTENCES,
    PART_OF_SPEECH,
    GRAMMATICAL_GENDER,
    INFLECTION,
    ETYMOLOGY,
}

data class DictionaryDatasetMetadata(
    val artifactName: String,
    val releaseId: String,
    val releasedAt: String?,
    val entryCount: Long?,
    val format: String,
    val releasePageUrl: String,
) {
    init {
        require(artifactName.isNotBlank()) { "Dataset artifact name must not be blank" }
        require(releaseId.isNotBlank()) { "Dataset release ID must not be blank" }
        require(releasedAt == null || releasedAt.isNotBlank()) {
            "Dataset release date must not be blank"
        }
        require(entryCount == null || entryCount >= 0) { "Dataset entry count must not be negative" }
        require(format.isNotBlank()) { "Dataset format must not be blank" }
        require(releasePageUrl.isNotBlank()) { "Dataset release page URL must not be blank" }
    }
}

enum class DictionaryResultKind {
    MONOLINGUAL_DEFINITION,
    TRANSLATION,
}

data class DictionaryLanguagePair(
    val sourceLanguage: Bcp47LanguageTag,
    val resultLanguage: Bcp47LanguageTag,
    val resultKind: DictionaryResultKind,
)

enum class DictionaryPermission {
    UNKNOWN,
    PERMITTED,
    PROHIBITED,
}

enum class DictionaryContentField {
    HEADWORD,
    MONOLINGUAL_DEFINITION,
    TRANSLATION,
    PART_OF_SPEECH,
    EXAMPLE,
    READING,
    PRONUNCIATION_TEXT,
    AUDIO,
    TRANSLITERATION,
    GRAMMATICAL_GENDER,
    INFLECTION,
    ETYMOLOGY,
}

data class DictionaryFieldUsagePolicy(
    val localPersistence: DictionaryPermission,
    val redistribution: DictionaryPermission,
)

enum class DictionaryCachePolicy {
    UNKNOWN,
    NOT_PERMITTED,
    SESSION_ONLY,
    PROVIDER_DEFINED,
    PERMITTED,
}

enum class DictionaryVocabularyImportMode {
    REFERENCE_ONLY,
    COPY_EXPORTABLE_FIELDS,
}

data class DictionaryUsagePolicy(
    val localPersistence: DictionaryPermission = DictionaryPermission.UNKNOWN,
    val redistribution: DictionaryPermission = DictionaryPermission.UNKNOWN,
    val cachePolicy: DictionaryCachePolicy = DictionaryCachePolicy.UNKNOWN,
    val vocabularyImportMode: DictionaryVocabularyImportMode =
        DictionaryVocabularyImportMode.REFERENCE_ONLY,
    val fieldOverrides: Map<DictionaryContentField, DictionaryFieldUsagePolicy> = emptyMap(),
    val note: String? = null,
) {
    init {
        require(cachePolicy != DictionaryCachePolicy.PROVIDER_DEFINED || !note.isNullOrBlank()) {
            "A provider-defined cache policy requires a note"
        }
    }

    fun fieldPolicy(field: DictionaryContentField): DictionaryFieldUsagePolicy =
        fieldOverrides[field] ?: DictionaryFieldUsagePolicy(localPersistence, redistribution)

    // Every persisted vocabulary text is currently eligible for JSON backup export.
    fun permitsExportableVocabularyCopy(field: DictionaryContentField): Boolean =
        vocabularyImportMode == DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS &&
            fieldPolicy(field).let { policy ->
                policy.localPersistence == DictionaryPermission.PERMITTED &&
                    policy.redistribution == DictionaryPermission.PERMITTED
            }
}

data class DictionaryAttribution(
    val sourceName: String,
    val sourceUrl: String?,
    val officialIdentifier: String?,
    val licenseName: String?,
    val licenseUrl: String?,
    val attributionNotice: String?,
    val licenseShortName: String? = null,
    val usagePolicy: DictionaryUsagePolicy = DictionaryUsagePolicy(),
) {
    init {
        require(sourceName.isNotBlank()) { "Dictionary source name must not be blank" }
        require(!sourceUrl.isNullOrBlank() || !officialIdentifier.isNullOrBlank()) {
            "Dictionary source URL or official identifier is required"
        }
    }
}

data class DictionaryQuery(
    val text: String,
    val languagePair: DictionaryLanguagePair,
    val resultLimit: Int? = null,
    val continuationToken: String? = null,
) {
    init {
        require(text.isNotBlank()) { "Dictionary query must not be blank" }
        require(resultLimit == null || resultLimit > 0) { "Result limit must be positive" }
        require(continuationToken == null || continuationToken.isNotBlank()) {
            "Continuation token must not be blank"
        }
    }
}

data class ExternalDictionaryEntry(
    val providerId: DictionaryProviderId,
    val sourceEntryId: String?,
    val datasetVersion: String? = null,
    val headword: String,
    val sourceLanguage: Bcp47LanguageTag,
    val headwordScriptCode: String? = null,
    val alternateWrittenForms: List<DictionaryWrittenForm> = emptyList(),
    val linguisticFeatures: DictionaryLinguisticFeatures = DictionaryLinguisticFeatures(),
    val senses: List<ExternalDictionarySense>,
    val attribution: DictionaryAttribution,
)

data class DictionaryWrittenForm(
    val text: String,
    val language: Bcp47LanguageTag,
    val scriptCode: String? = null,
)

data class DictionaryLinguisticFeatures(
    val reading: DictionaryReading? = null,
    val alternativeReadings: List<DictionaryReading> = emptyList(),
    val pronunciations: List<DictionaryPronunciation> = emptyList(),
    val transliterations: List<DictionaryTransliteration> = emptyList(),
    val inflections: List<DictionaryInflection> = emptyList(),
    val totalInflectionCount: Int = inflections.size,
    val etymology: String? = null,
) {
    init {
        require(totalInflectionCount >= inflections.size) {
            "Total inflection count cannot be smaller than retained inflections"
        }
    }
}

data class DictionaryReading(
    val text: String,
    val scriptCode: String? = null,
    val writtenFormRestrictions: Set<String> = emptySet(),
    val appliesWithoutWrittenForm: Boolean = false,
)

data class DictionaryPronunciation(
    val text: String? = null,
    val audio: DictionaryAudio? = null,
    val notation: DictionaryPronunciationNotation = DictionaryPronunciationNotation.OTHER,
    val language: Bcp47LanguageTag? = null,
)

enum class DictionaryPronunciationNotation {
    IPA,
    PHONETIC,
    ROMANIZATION,
    OTHER,
}

data class DictionaryAudio(
    val uri: String,
    val mediaType: String? = null,
)

data class DictionaryTransliteration(
    val text: String,
    val scriptCode: String? = null,
    val scheme: String? = null,
)

data class DictionaryInflection(
    val form: String,
    val label: String,
)

data class ExternalDictionarySense(
    val meanings: List<DictionaryMeaning>,
    val partOfSpeech: String? = null,
    val sourcePartOfSpeech: String? = null,
    val grammaticalGender: String? = null,
    val examples: List<DictionaryExample> = emptyList(),
    val availableExampleCount: Int = examples.size,
    val sourceSenseId: String? = null,
    val writtenFormRestrictions: Set<String> = emptySet(),
    val readingRestrictions: Set<String> = emptySet(),
) {
    init {
        require(availableExampleCount >= examples.size) {
            "Available example count cannot be smaller than retained examples"
        }
    }
}

data class DictionaryMeaning(
    val text: String,
    val language: Bcp47LanguageTag,
    val kind: DictionaryResultKind,
)

data class DictionaryExample(
    val text: String,
    val language: Bcp47LanguageTag,
)

data class DictionarySearchPage(
    val entries: List<ExternalDictionaryEntry>,
    val nextContinuationToken: String? = null,
    val isTruncated: Boolean = false,
) {
    init {
        require(entries.isNotEmpty()) { "Use NoResult instead of an empty success page" }
        require(nextContinuationToken == null || nextContinuationToken.isNotBlank()) {
            "Next continuation token must not be blank"
        }
    }
}

sealed interface DictionarySearchResult {
    data class Success(val page: DictionarySearchPage) : DictionarySearchResult
    data class Failure(val error: DictionaryProviderError) : DictionarySearchResult
}

sealed interface DictionaryProviderError {
    data class UnsupportedSourceLanguage(
        val sourceLanguage: Bcp47LanguageTag,
    ) : DictionaryProviderError

    data class UnsupportedResultLanguage(
        val resultLanguage: Bcp47LanguageTag,
        val resultKind: DictionaryResultKind,
    ) : DictionaryProviderError

    data object MissingCredential : DictionaryProviderError
    data object AuthenticationFailed : DictionaryProviderError
    data class RateLimited(val retryAfterSeconds: Long? = null) : DictionaryProviderError
    data object NetworkUnavailable : DictionaryProviderError
    data object ProviderUnavailable : DictionaryProviderError
    data class MalformedProviderData(val detail: String? = null) : DictionaryProviderError
    data object NoResult : DictionaryProviderError
    data object LocalDatasetUnavailable : DictionaryProviderError
    data class Unknown(val detail: String? = null) : DictionaryProviderError
}

sealed interface ProviderAvailability {
    data object Available : ProviderAvailability
    data class Unavailable(val error: DictionaryProviderError) : ProviderAvailability
}
