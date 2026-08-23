package com.example.localvocabulary.dictionary.provider.koreanbasic

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
class KoreanBasicDictionaryProvider @Inject internal constructor(
    private val dataSource: KoreanBasicDictionaryLookup,
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
                    resultLanguage = query.languagePair.resultLanguage,
                    resultKind = query.languagePair.resultKind,
                ),
            )
        }

        return when (
            val result = dataSource.exactLookup(
                query = query.text,
                sourceLanguageTag = sourceLanguage.value,
                resultLanguageTag = query.languagePair.resultLanguage.value,
            )
        ) {
            is KoreanBasicDictionaryLookupResult.Matches -> {
                val allEntries = result.records.toExternalEntries(
                    queryText = query.text,
                    descriptor = descriptor,
                    languagePair = query.languagePair,
                )
                val entries = query.resultLimit?.let(allEntries::take) ?: allEntries
                DictionarySearchResult.Success(
                    DictionarySearchPage(
                        entries = entries,
                        isTruncated = entries.size < allEntries.size,
                    ),
                )
            }
            KoreanBasicDictionaryLookupResult.NoMatch -> DictionarySearchResult.Failure(
                DictionaryProviderError.NoResult,
            )
            KoreanBasicDictionaryLookupResult.DatasetUnavailable ->
                DictionarySearchResult.Failure(DictionaryProviderError.LocalDatasetUnavailable)
            is KoreanBasicDictionaryLookupResult.MalformedDataset ->
                DictionarySearchResult.Failure(
                    DictionaryProviderError.MalformedProviderData(result.detail),
                )
        }
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = when (val availability =
        dataSource.availability()
    ) {
        KoreanBasicDictionaryDatasetAvailability.Available -> ProviderAvailability.Available
        KoreanBasicDictionaryDatasetAvailability.Unavailable -> ProviderAvailability.Unavailable(
            DictionaryProviderError.LocalDatasetUnavailable,
        )
        is KoreanBasicDictionaryDatasetAvailability.Invalid -> ProviderAvailability.Unavailable(
            DictionaryProviderError.MalformedProviderData(availability.detail),
        )
    }

    companion object {
        const val STABLE_PROVIDER_ID = "korean-basic-dictionary"
        const val INDEXED_ENTRY_COUNT = 56_555L

        private val FOREIGN_LANGUAGES = listOf(
            "en",
            "ja",
            "fr",
            "es",
            "ar",
            "mn",
            "vi",
            "th",
            "id",
            "ru",
            "zh",
        ).map(Bcp47LanguageTag::requireValid)
        private val SUPPORTED_SOURCE_LANGUAGES = FOREIGN_LANGUAGES.toSet() + KOREAN_LANGUAGE
        private val SUPPORTED_LANGUAGE_PAIRS = buildSet {
            FOREIGN_LANGUAGES.forEach { foreignLanguage ->
                add(
                    DictionaryLanguagePair(
                        sourceLanguage = KOREAN_LANGUAGE,
                        resultLanguage = foreignLanguage,
                        resultKind = DictionaryResultKind.TRANSLATION,
                    ),
                )
                add(
                    DictionaryLanguagePair(
                        sourceLanguage = foreignLanguage,
                        resultLanguage = KOREAN_LANGUAGE,
                        resultKind = DictionaryResultKind.TRANSLATION,
                    ),
                )
            }
        }
        private val DESCRIPTOR = DictionaryProviderDescriptor(
            id = DictionaryProviderId(STABLE_PROVIDER_ID),
            displayName = "한국어기초사전",
            supportedLanguagePairs = SUPPORTED_LANGUAGE_PAIRS,
            access = DictionaryAccess.LOCAL_DATASET,
            capabilities = setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.PART_OF_SPEECH,
            ),
            attribution = DictionaryAttribution(
                sourceName = "한국어기초사전 - 국립국어원 제공",
                sourceUrl = "https://krdict.korean.go.kr/",
                officialIdentifier = "한국어기초사전 - 국립국어원 제공",
                licenseName = "Creative Commons Attribution-ShareAlike 2.0 Korea",
                licenseUrl = "https://creativecommons.org/licenses/by-sa/2.0/kr/",
                attributionNotice =
                    "한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.",
                licenseShortName = "CC BY-SA 2.0 KR",
                usagePolicy = DictionaryUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                    cachePolicy = DictionaryCachePolicy.PERMITTED,
                    vocabularyImportMode =
                        DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                    note = "Imported text retains source, license, dataset version, stable " +
                        "entry/sense identifiers, imported fields, and modification state. " +
                        "Audio, image, video, and pronunciation media are excluded.",
                ),
            ),
            dataset = DictionaryDatasetMetadata(
                artifactName = KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME,
                releaseId = KOREAN_BASIC_DICTIONARY_RELEASE_ID,
                releasedAt = KOREAN_BASIC_DICTIONARY_RELEASE_ID,
                entryCount = INDEXED_ENTRY_COUNT,
                format = "Read-only SQLite index built from official full JSON",
                releasePageUrl = "https://krdict.korean.go.kr/download/downloadPopup",
            ),
        )
    }
}

internal val KOREAN_LANGUAGE = Bcp47LanguageTag.requireValid("ko")
