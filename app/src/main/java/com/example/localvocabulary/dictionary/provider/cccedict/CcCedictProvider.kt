package com.example.localvocabulary.dictionary.provider.cccedict

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
class CcCedictProvider @Inject internal constructor(
    private val dataSource: CcCedictDataSource,
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

        val form = if (sourceLanguage == CC_CEDICT_SIMPLIFIED_CHINESE) {
            CcCedictHeadwordForm.SIMPLIFIED
        } else {
            CcCedictHeadwordForm.TRADITIONAL
        }
        return when (
            val result = dataSource.exactLookup(
                query = query.text,
                headwordForm = form,
                resultLimit = query.resultLimit,
            )
        ) {
            is CcCedictLookupResult.Matches -> DictionarySearchResult.Success(
                DictionarySearchPage(
                    entries = result.records.map { record ->
                        record.toExternalEntry(descriptor, query.languagePair, result.datasetVersion)
                    },
                    isTruncated = result.records.size < result.totalMatchCount,
                ),
            )
            CcCedictLookupResult.NoMatch -> DictionarySearchResult.Failure(
                DictionaryProviderError.NoResult,
            )
            CcCedictLookupResult.DatasetUnavailable -> DictionarySearchResult.Failure(
                DictionaryProviderError.LocalDatasetUnavailable,
            )
            is CcCedictLookupResult.MalformedDataset -> DictionarySearchResult.Failure(
                DictionaryProviderError.MalformedProviderData(result.detail),
            )
        }
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = when (val availability =
        dataSource.availability()
    ) {
        CcCedictDatasetAvailability.Available -> ProviderAvailability.Available
        CcCedictDatasetAvailability.Unavailable -> ProviderAvailability.Unavailable(
            DictionaryProviderError.LocalDatasetUnavailable,
        )
        is CcCedictDatasetAvailability.Invalid -> ProviderAvailability.Unavailable(
            DictionaryProviderError.MalformedProviderData(availability.detail),
        )
    }

    companion object {
        const val STABLE_PROVIDER_ID = "cc-cedict"
        const val RELEASE_ID = "2026-08-22T08:27:42Z"
        const val RELEASE_ENTRY_COUNT = 124_889L

        private val ENGLISH = Bcp47LanguageTag.requireValid("en")
        private val SUPPORTED_SOURCE_LANGUAGES = setOf(
            CC_CEDICT_SIMPLIFIED_CHINESE,
            CC_CEDICT_TRADITIONAL_CHINESE,
        )
        private val SUPPORTED_LANGUAGE_PAIRS = SUPPORTED_SOURCE_LANGUAGES.mapTo(linkedSetOf()) {
            DictionaryLanguagePair(
                sourceLanguage = it,
                resultLanguage = ENGLISH,
                resultKind = DictionaryResultKind.TRANSLATION,
            )
        }
        private val DESCRIPTOR = DictionaryProviderDescriptor(
            id = DictionaryProviderId(STABLE_PROVIDER_ID),
            displayName = "CC-CEDICT",
            supportedLanguagePairs = SUPPORTED_LANGUAGE_PAIRS,
            access = DictionaryAccess.LOCAL_DATASET,
            capabilities = setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.ALTERNATE_WRITTEN_FORMS,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.READING,
            ),
            attribution = DictionaryAttribution(
                sourceName = "CC-CEDICT",
                sourceUrl = "https://cc-cedict.org/editor/editor.php?handler=Download",
                officialIdentifier = "CC-CEDICT",
                licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
                licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                attributionNotice =
                    "CC-CEDICT data from MDBG, licensed under CC BY-SA 4.0.",
                licenseShortName = "CC BY-SA 4.0",
                usagePolicy = DictionaryUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                    cachePolicy = DictionaryCachePolicy.PERMITTED,
                    vocabularyImportMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                    note = "Attribution and ShareAlike obligations must accompany copied " +
                        "CC-CEDICT content. Imported senses preserve source, license, dataset " +
                        "version, imported fields, and modification state in Room and backup.",
                ),
            ),
            dataset = DictionaryDatasetMetadata(
                artifactName = CC_CEDICT_ARTIFACT_NAME,
                releaseId = RELEASE_ID,
                releasedAt = RELEASE_ID,
                entryCount = RELEASE_ENTRY_COUNT,
                format = "CC-CEDICT v1 UTF-8, GZip",
                releasePageUrl = "https://www.mdbg.net/chinese/dictionary?page=cc-cedict",
            ),
        )
    }
}

internal val CC_CEDICT_SIMPLIFIED_CHINESE = Bcp47LanguageTag.requireValid("zh-Hans")
internal val CC_CEDICT_TRADITIONAL_CHINESE = Bcp47LanguageTag.requireValid("zh-Hant")
