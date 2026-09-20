package com.example.localvocabulary.dictionary.provider.koreanbasic

import com.example.localvocabulary.dictionary.domain.DictionaryLookupKind

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.DictionaryVocabularyImportMode
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KoreanBasicDictionaryProviderTest {
    @Test
    fun `descriptor exposes stable local provider all official language pairs and license`() {
        val descriptor = provider().descriptor

        assertEquals("korean-basic-dictionary", descriptor.id.value)
        assertEquals(DictionaryAccess.LOCAL_DATASET, descriptor.access)
        assertEquals(22, descriptor.supportedLanguagePairs.size)
        OFFICIAL_FOREIGN_LANGUAGES.forEach { language ->
            assertTrue(descriptor.supports(query("말", "ko", language)))
            assertTrue(descriptor.supports(query("word", language, "ko")))
        }
        assertEquals(
            setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.PART_OF_SPEECH,
            ),
            descriptor.capabilities,
        )
        assertEquals(
            DictionaryPermission.PERMITTED,
            descriptor.attribution.usagePolicy.localPersistence,
        )
        assertEquals(
            DictionaryPermission.PERMITTED,
            descriptor.attribution.usagePolicy.redistribution,
        )
        assertEquals(
            DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            descriptor.attribution.usagePolicy.vocabularyImportMode,
        )
        assertEquals("CC BY-SA 2.0 KR", descriptor.attribution.licenseShortName)
        assertEquals(KOREAN_BASIC_DICTIONARY_RELEASE_ID, descriptor.dataset?.releaseId)
        assertEquals(
            KOREAN_BASIC_DICTIONARY_ARTIFACT_NAME,
            descriptor.dataset?.artifactName,
        )
    }

    @Test
    fun `foreign exact lookup maps English Japanese Chinese and Russian to Korean`() = runTest {
        val cases = listOf(
            Triple("eat", "en", "eat; consume"),
            Triple("食べる", "ja", "たべる【食べる】"),
            Triple("吃", "zh", "吃"),
            Triple("есть", "ru", "есть"),
        )

        cases.forEachIndexed { index, (text, language, officialTerm) ->
            val lookup = RecordingLookup(
                KoreanBasicDictionaryLookupResult.Matches(
                    listOf(record(translationTerm = officialTerm, entryOrder = index.toLong())),
                ),
            )
            val result = provider(lookup).search(query(text, language, "ko"))
                as DictionarySearchResult.Success
            val entry = result.page.entries.single()

            assertEquals(text, entry.headword)
            assertEquals("먹다", entry.senses.single().meanings.single().text)
            assertEquals(language, entry.sourceLanguage.value)
            assertEquals("com.example.localvocabulary.dictionary.domain", entry::class.java.packageName)
            assertFalse(entry::class.java.name.contains("KoreanBasic"))
            assertEquals(Triple(text, language, "ko"), lookup.lastRequest)
        }
    }

    @Test
    fun `Korean lookup preserves sense and translation ordering and optional POS`() = runTest {
        val records = listOf(
            record(officialSenseId = "1", translationTerm = "eat", translationOrder = 0),
            record(officialSenseId = "1", translationTerm = "consume", translationOrder = 1),
            record(
                officialSenseId = "2",
                translationTerm = "take",
                senseOrder = 1,
                translationOrder = 0,
                partOfSpeech = null,
            ),
        )
        val result = provider(
            RecordingLookup(KoreanBasicDictionaryLookupResult.Matches(records)),
        ).search(query("먹다", "ko", "en")) as DictionarySearchResult.Success
        val senses = result.page.entries.single().senses

        assertEquals(listOf("eat", "consume"), senses.first().meanings.map { it.text })
        assertEquals("동사", senses.first().partOfSpeech)
        assertEquals("1", senses.first().sourceSenseId)
        assertEquals(listOf("take"), senses.last().meanings.map { it.text })
        assertEquals(null, senses.last().partOfSpeech)
        assertEquals("2", senses.last().sourceSenseId)
    }

    @Test
    fun `reverse lookup keeps multiple candidates deterministic and removes duplicate sense rows`() = runTest {
        val records = listOf(
            record(officialEntryId = "10", koreanHeadword = "먹다", entryOrder = 1),
            record(
                officialEntryId = "10",
                koreanHeadword = "먹다",
                entryOrder = 1,
                translationOrder = 1,
            ),
            record(officialEntryId = "20", koreanHeadword = "들다", entryOrder = 2),
        )
        val result = provider(
            RecordingLookup(KoreanBasicDictionaryLookupResult.Matches(records)),
        ).search(query("eat", "en", "ko")) as DictionarySearchResult.Success

        assertEquals(
            listOf("먹다", "들다"),
            result.page.entries.map { it.senses.single().meanings.single().text },
        )
        assertFalse(result.page.isTruncated)
    }

    @Test
    fun `result limit truncates mapped candidates rather than source rows`() = runTest {
        val records = listOf(
            record(officialEntryId = "10", koreanHeadword = "먹다", entryOrder = 1),
            record(officialEntryId = "20", koreanHeadword = "들다", entryOrder = 2),
        )
        val limitedQuery = query("eat", "en", "ko").copy(resultLimit = 1)
        val result = provider(
            RecordingLookup(KoreanBasicDictionaryLookupResult.Matches(records)),
        ).search(limitedQuery) as DictionarySearchResult.Success

        assertEquals(1, result.page.entries.size)
        assertTrue(result.page.isTruncated)
    }

    @Test
    fun `unsupported pair no result and unavailable dataset are structured failures`() = runTest {
        val unsupportedSource = provider().search(query("词", "zh-Hans", "ko"))
            as DictionarySearchResult.Failure
        val unsupportedResult = provider().search(query("말", "ko", "de"))
            as DictionarySearchResult.Failure
        val noResult = provider(
            RecordingLookup(KoreanBasicDictionaryLookupResult.NoMatch),
        ).search(query("absent", "en", "ko")) as DictionarySearchResult.Failure
        val unavailable = provider(
            RecordingLookup(KoreanBasicDictionaryLookupResult.DatasetUnavailable),
        ).search(query("eat", "en", "ko")) as DictionarySearchResult.Failure

        assertTrue(unsupportedSource.error is DictionaryProviderError.UnsupportedSourceLanguage)
        assertTrue(unsupportedResult.error is DictionaryProviderError.UnsupportedResultLanguage)
        assertEquals(DictionaryProviderError.NoResult, noResult.error)
        assertEquals(DictionaryProviderError.LocalDatasetUnavailable, unavailable.error)
    }

    @Test
    fun `Use maps Korean meaning through generic draft and preserves official provenance IDs`() = runTest {
        val entry = (
            provider(
                RecordingLookup(
                    KoreanBasicDictionaryLookupResult.Matches(listOf(record())),
                ),
            ).search(query("eat", "en", "ko")) as DictionarySearchResult.Success
            ).page.entries.single()

        val mapping = DictionaryEntryDraftMapper.map(entry, 1_000)
            as DictionaryEntryDraftMappingResult.Ready
        val provenance = mapping.seed.draft.senses.single().provenance

        assertEquals("eat", mapping.seed.draft.headword)
        assertEquals("먹다", mapping.seed.draft.senses.single().meaning)
        assertEquals("", mapping.seed.draft.senses.single().partOfSpeech)
        assertEquals(
            DictionaryLookupKind.REVERSE_TRANSLATION,
            entry.lookupKind,
        )
        assertNotNull(provenance)
        assertEquals("korean-basic-dictionary", provenance?.providerId)
        assertEquals("100:먹다", provenance?.sourceEntryId)
        assertEquals("1", provenance?.sourceSenseId)
        assertEquals(KOREAN_BASIC_DICTIONARY_RELEASE_ID, provenance?.datasetVersion)
        assertFalse(provenance!!.modifiedAfterImport)
    }

    private fun provider(
        lookup: KoreanBasicDictionaryLookup = RecordingLookup(
            KoreanBasicDictionaryLookupResult.NoMatch,
        ),
    ) = KoreanBasicDictionaryProvider(lookup)

    private fun query(text: String, source: String, result: String) = DictionaryQuery(
        text = text,
        languagePair = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid(source),
            resultLanguage = Bcp47LanguageTag.requireValid(result),
            resultKind = DictionaryResultKind.TRANSLATION,
        ),
    )

    private fun record(
        officialEntryId: String = "100",
        officialSenseId: String = "1",
        koreanHeadword: String = "먹다",
        partOfSpeech: String? = "동사",
        translationTerm: String = "eat; consume",
        entryOrder: Long = 1,
        senseOrder: Int = 0,
        translationOrder: Int = 0,
    ) = KoreanBasicDictionaryRecord(
        officialEntryId = officialEntryId,
        officialSenseId = officialSenseId,
        koreanHeadword = koreanHeadword,
        partOfSpeech = partOfSpeech,
        translationTerm = translationTerm,
        entryOrder = entryOrder,
        senseOrder = senseOrder,
        translationOrder = translationOrder,
    )

    private class RecordingLookup(
        private val result: KoreanBasicDictionaryLookupResult,
    ) : KoreanBasicDictionaryLookup {
        var lastRequest: Triple<String, String, String>? = null

        override suspend fun exactLookup(
            query: String,
            sourceLanguageTag: String,
            resultLanguageTag: String,
        ): KoreanBasicDictionaryLookupResult {
            lastRequest = Triple(query, sourceLanguageTag, resultLanguageTag)
            return result
        }

        override suspend fun availability(): KoreanBasicDictionaryDatasetAvailability =
            KoreanBasicDictionaryDatasetAvailability.Available
    }

    private companion object {
        val OFFICIAL_FOREIGN_LANGUAGES = listOf(
            "en", "ja", "fr", "es", "ar", "mn", "vi", "th", "id", "ru", "zh",
        )
    }
}
