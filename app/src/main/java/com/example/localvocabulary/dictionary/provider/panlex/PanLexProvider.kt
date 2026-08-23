package com.example.localvocabulary.dictionary.provider.panlex

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryCachePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryDatasetMetadata
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionarySearchPage
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.DictionaryUsagePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryVocabularyImportMode
import com.example.localvocabulary.dictionary.domain.ProviderAvailability
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanLexProvider @Inject internal constructor(
    private val dataSource: PanLexLookup,
) : DictionaryProvider {
    override val descriptor: DictionaryProviderDescriptor = DESCRIPTOR

    override suspend fun search(query: DictionaryQuery): DictionarySearchResult {
        val sourceLanguage = query.languagePair.sourceLanguage
        if (sourceLanguage !in SUPPORTED_SOURCE_LANGUAGES) {
            return DictionarySearchResult.Failure(
                DictionaryProviderError.UnsupportedSourceLanguage(sourceLanguage),
            )
        }
        if (!descriptor.supports(query)) {
            return DictionarySearchResult.Failure(
                DictionaryProviderError.UnsupportedResultLanguage(
                    query.languagePair.resultLanguage,
                    query.languagePair.resultKind,
                ),
            )
        }

        val resultLimit = (query.resultLimit ?: DEFAULT_RESULT_LIMIT).coerceAtMost(MAX_RESULT_LIMIT)
        return when (
            val result = dataSource.exactLookup(
                query = query.text,
                sourceLanguageTag = sourceLanguage.value,
                resultLanguageTag = query.languagePair.resultLanguage.value,
                resultLimit = resultLimit,
            )
        ) {
            is PanLexLookupResult.Matches -> DictionarySearchResult.Success(
                DictionarySearchPage(
                    entries = result.records.map { it.toExternalEntry(descriptor, query.languagePair) },
                    isTruncated = result.isTruncated,
                ),
            )
            PanLexLookupResult.NoMatch ->
                DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
            PanLexLookupResult.DatasetUnavailable ->
                DictionarySearchResult.Failure(DictionaryProviderError.LocalDatasetUnavailable)
            is PanLexLookupResult.MalformedDataset -> DictionarySearchResult.Failure(
                DictionaryProviderError.MalformedProviderData(result.detail),
            )
        }
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = when (
        val availability = dataSource.availability()
    ) {
        PanLexDatasetAvailability.Available -> ProviderAvailability.Available
        PanLexDatasetAvailability.Unavailable -> ProviderAvailability.Unavailable(
            DictionaryProviderError.LocalDatasetUnavailable,
        )
        is PanLexDatasetAvailability.Invalid -> ProviderAvailability.Unavailable(
            DictionaryProviderError.MalformedProviderData(availability.detail),
        )
    }

    companion object {
        const val STABLE_PROVIDER_ID = "panlex"
        const val DEFAULT_RESULT_LIMIT = 20
        const val MAX_RESULT_LIMIT = 100
        const val INDEXED_RELATION_COUNT = 307_530L

        private val FOREIGN_LANGUAGES = listOf("de", "hi", "pl", "la")
            .map(Bcp47LanguageTag::requireValid)
        private val SUPPORTED_SOURCE_LANGUAGES = FOREIGN_LANGUAGES.toSet() + PANLEX_KOREAN_LANGUAGE
        private val SUPPORTED_LANGUAGE_PAIRS = buildSet {
            FOREIGN_LANGUAGES.forEach { foreignLanguage ->
                add(
                    DictionaryLanguagePair(
                        sourceLanguage = foreignLanguage,
                        resultLanguage = PANLEX_KOREAN_LANGUAGE,
                        resultKind = DictionaryResultKind.TRANSLATION,
                    ),
                )
                add(
                    DictionaryLanguagePair(
                        sourceLanguage = PANLEX_KOREAN_LANGUAGE,
                        resultLanguage = foreignLanguage,
                        resultKind = DictionaryResultKind.TRANSLATION,
                    ),
                )
            }
        }
        private val DESCRIPTOR = DictionaryProviderDescriptor(
            id = DictionaryProviderId(STABLE_PROVIDER_ID),
            displayName = "PanLex",
            supportedLanguagePairs = SUPPORTED_LANGUAGE_PAIRS,
            access = DictionaryAccess.LOCAL_DATASET,
            capabilities = setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.TRANSLATIONS,
            ),
            attribution = DictionaryAttribution(
                sourceName = "PanLex",
                sourceUrl = "https://panlex.org/",
                officialIdentifier = "PanLex Database snapshot $PANLEX_RELEASE_ID",
                licenseName = "CC0 1.0 Universal",
                licenseUrl = "https://creativecommons.org/publicdomain/zero/1.0/",
                attributionNotice =
                    "PanLex Database. PanLex recommends citing panlex.org or its 2014 LREC paper.",
                licenseShortName = "CC0-1.0",
                usagePolicy = DictionaryUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                    cachePolicy = DictionaryCachePolicy.PERMITTED,
                    vocabularyImportMode =
                        DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                    note = "Only direct same-meaning translations from reviewed language " +
                        "varieties are indexed. Imported text retains PanLex provenance.",
                ),
            ),
            dataset = DictionaryDatasetMetadata(
                artifactName = PANLEX_ARTIFACT_NAME,
                releaseId = PANLEX_RELEASE_ID,
                releasedAt = PANLEX_RELEASE_ID,
                entryCount = INDEXED_RELATION_COUNT,
                format = "Read-only filtered SQLite index built from official PanLex CSV",
                releasePageUrl = "https://panlex.org/snapshot/",
            ),
        )
    }
}

internal val PANLEX_KOREAN_LANGUAGE = Bcp47LanguageTag.requireValid("ko")
