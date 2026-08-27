package com.example.localvocabulary.dictionary.provider.kaikki

import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryCachePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryContentField
import com.example.localvocabulary.dictionary.domain.DictionaryDatasetMetadata
import com.example.localvocabulary.dictionary.domain.DictionaryFieldUsagePolicy
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
class KaikkiProvider @Inject internal constructor(
    private val dataSource: KaikkiLookup,
) : DictionaryProvider {
    override val descriptor: DictionaryProviderDescriptor = DESCRIPTOR

    override suspend fun search(query: DictionaryQuery): DictionarySearchResult {
        val sourceLanguage = query.languagePair.sourceLanguage
        if (sourceLanguage !in KAIKKI_SOURCE_LANGUAGES) {
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
                resultLimit = resultLimit,
            )
        ) {
            is KaikkiLookupResult.Matches -> DictionarySearchResult.Success(
                DictionarySearchPage(
                    entries = result.records.map { match ->
                        match.toExternalEntry(
                            descriptor = descriptor,
                            sourceLanguageTag = sourceLanguage.value,
                            datasetVersion = result.datasetVersion,
                        )
                    },
                    isTruncated = result.isTruncated,
                ),
            )
            KaikkiLookupResult.NoMatch ->
                DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
            KaikkiLookupResult.DatasetUnavailable ->
                DictionarySearchResult.Failure(DictionaryProviderError.LocalDatasetUnavailable)
            is KaikkiLookupResult.MalformedDataset -> DictionarySearchResult.Failure(
                DictionaryProviderError.MalformedProviderData(result.detail),
            )
        }
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = when (
        val availability = dataSource.availability()
    ) {
        KaikkiDatasetAvailability.Available -> ProviderAvailability.Available
        KaikkiDatasetAvailability.Unavailable -> ProviderAvailability.Unavailable(
            DictionaryProviderError.LocalDatasetUnavailable,
        )
        is KaikkiDatasetAvailability.Invalid -> ProviderAvailability.Unavailable(
            DictionaryProviderError.MalformedProviderData(availability.detail),
        )
    }

    companion object {
        const val STABLE_PROVIDER_ID = "kaikki"
        const val DEFAULT_RESULT_LIMIT = 20
        const val MAX_RESULT_LIMIT = 100
        const val INDEXED_ENTRY_COUNT = 1_793_612L

        private val SUPPORTED_LANGUAGE_PAIRS = KAIKKI_SOURCE_LANGUAGES.mapTo(linkedSetOf()) {
            sourceLanguage ->
            DictionaryLanguagePair(
                sourceLanguage = sourceLanguage,
                resultLanguage = KAIKKI_ENGLISH_LANGUAGE,
                resultKind = DictionaryResultKind.TRANSLATION,
            )
        }
        private val PROHIBITED_AUDIO_POLICY = DictionaryFieldUsagePolicy(
            localPersistence = DictionaryPermission.PROHIBITED,
            redistribution = DictionaryPermission.PROHIBITED,
        )
        private val DESCRIPTOR = DictionaryProviderDescriptor(
            id = DictionaryProviderId(STABLE_PROVIDER_ID),
            displayName = "Kaikki / Wiktionary",
            supportedLanguagePairs = SUPPORTED_LANGUAGE_PAIRS,
            access = DictionaryAccess.LOCAL_DATASET,
            capabilities = setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.PART_OF_SPEECH,
                DictionaryCapability.EXAMPLE_SENTENCES,
                DictionaryCapability.PRONUNCIATION_TEXT,
                DictionaryCapability.GRAMMATICAL_GENDER,
                DictionaryCapability.INFLECTION,
            ),
            attribution = DictionaryAttribution(
                sourceName = "English Wiktionary via Kaikki/Wiktextract",
                sourceUrl = "https://en.wiktionary.org/",
                officialIdentifier = "Kaikki English Wiktionary extraction $KAIKKI_RELEASE_ID",
                licenseName =
                    "CC BY-SA 4.0 reuse of dual-licensed Wiktionary text (also GFDL 1.1+)",
                licenseUrl = "https://en.wiktionary.org/wiki/Wiktionary:Copyrights",
                attributionNotice =
                    "Definitions and structured text come from English Wiktionary, extracted " +
                        "by Wiktextract and distributed through Kaikki.org. This app uses the " +
                        "CC BY-SA 4.0 option; attribution and ShareAlike apply.",
                licenseShortName = "CC BY-SA 4.0",
                usagePolicy = DictionaryUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                    cachePolicy = DictionaryCachePolicy.PERMITTED,
                    vocabularyImportMode =
                        DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                    fieldOverrides = mapOf(
                        DictionaryContentField.AUDIO to PROHIBITED_AUDIO_POLICY,
                    ),
                    note = "Glosses, normalized POS, grammatical gender, textual pronunciation, " +
                        "and at most two source-ordered example texts per sense may be explicitly " +
                        "imported with field provenance. Audio/media remain excluded; forms " +
                        "remain transient suggestion metadata.",
                ),
            ),
            dataset = DictionaryDatasetMetadata(
                artifactName = "per-language filtered SQLite indexes",
                releaseId = KAIKKI_RELEASE_ID,
                releasedAt = KAIKKI_EXTRACTION_DATE,
                entryCount = INDEXED_ENTRY_COUNT,
                format = "Read-only compact SQLite built from official Kaikki JSONL",
                releasePageUrl = "https://kaikki.org/dictionary/rawdata.html",
            ),
        )
    }
}
