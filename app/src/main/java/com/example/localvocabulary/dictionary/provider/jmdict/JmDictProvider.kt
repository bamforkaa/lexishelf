package com.example.localvocabulary.dictionary.provider.jmdict

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
class JmDictProvider @Inject internal constructor(
    private val lookup: JmDictLookup,
) : DictionaryProvider {
    override val descriptor: DictionaryProviderDescriptor = DESCRIPTOR

    override suspend fun search(query: DictionaryQuery): DictionarySearchResult {
        if (query.languagePair.sourceLanguage != JAPANESE) {
            return DictionarySearchResult.Failure(
                DictionaryProviderError.UnsupportedSourceLanguage(query.languagePair.sourceLanguage),
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
        return when (val result = lookup.exactLookup(query.text, query.resultLimit)) {
            is JmDictLookupResult.Matches -> DictionarySearchResult.Success(
                DictionarySearchPage(entries = result.records.map { it.toExternalEntry(descriptor) }),
            )
            JmDictLookupResult.NoMatch -> DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
            JmDictLookupResult.DatasetUnavailable -> DictionarySearchResult.Failure(
                DictionaryProviderError.LocalDatasetUnavailable,
            )
            is JmDictLookupResult.MalformedDataset -> DictionarySearchResult.Failure(
                DictionaryProviderError.MalformedProviderData(result.detail),
            )
        }
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = when (val result = lookup.availability()) {
        JmDictDatasetAvailability.Available -> ProviderAvailability.Available
        JmDictDatasetAvailability.Unavailable -> ProviderAvailability.Unavailable(
            DictionaryProviderError.LocalDatasetUnavailable,
        )
        is JmDictDatasetAvailability.Invalid -> ProviderAvailability.Unavailable(
            DictionaryProviderError.MalformedProviderData(result.detail),
        )
    }

    companion object {
        const val STABLE_PROVIDER_ID = "jmdict"
        const val INDEXED_ENTRY_COUNT = 218_551L
        val LANGUAGE_PAIR = DictionaryLanguagePair(
            sourceLanguage = JAPANESE,
            resultLanguage = ENGLISH,
            resultKind = DictionaryResultKind.TRANSLATION,
        )
        private val DESCRIPTOR = DictionaryProviderDescriptor(
            id = DictionaryProviderId(STABLE_PROVIDER_ID),
            displayName = "JMdict",
            supportedLanguagePairs = setOf(LANGUAGE_PAIR),
            access = DictionaryAccess.LOCAL_DATASET,
            capabilities = setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.ALTERNATE_WRITTEN_FORMS,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.READING,
                DictionaryCapability.PART_OF_SPEECH,
            ),
            attribution = DictionaryAttribution(
                sourceName = "JMdict / Electronic Dictionary Research and Development Group",
                sourceUrl = "https://www.edrdg.org/jmdict/j_jmdict.html",
                officialIdentifier = "JMdict",
                licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
                licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                attributionNotice = "JMdict by the Electronic Dictionary Research and Development Group, CC BY-SA 4.0.",
                licenseShortName = "CC BY-SA 4.0",
                usagePolicy = DictionaryUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                    cachePolicy = DictionaryCachePolicy.PERMITTED,
                    vocabularyImportMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
                    note = "Attribution and ShareAlike apply; bundled data must have a documented regular update procedure.",
                ),
            ),
            dataset = DictionaryDatasetMetadata(
                artifactName = JMDICT_ARTIFACT_NAME,
                releaseId = JMDICT_RELEASE_ID,
                releasedAt = JMDICT_RELEASE_ID,
                entryCount = INDEXED_ENTRY_COUNT,
                format = "Read-only SQLite exact index built from official JMdict_e XML",
                releasePageUrl = "https://ftp.edrdg.org/pub/Nihongo/00INDEX.html",
            ),
        )
    }
}
