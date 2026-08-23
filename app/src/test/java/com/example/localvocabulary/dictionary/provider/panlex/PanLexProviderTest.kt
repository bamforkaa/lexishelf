package com.example.localvocabulary.dictionary.provider.panlex

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
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryDatasetAvailability
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryLookup
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryLookupResult
import com.example.localvocabulary.dictionary.provider.koreanbasic.KoreanBasicDictionaryProvider
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanLexProviderTest {
    @Test
    fun `descriptor is stable local CC0 provider with reviewed bidirectional pairs`() {
        val descriptor = provider().descriptor

        assertEquals("panlex", descriptor.id.value)
        assertEquals(DictionaryAccess.LOCAL_DATASET, descriptor.access)
        assertEquals(8, descriptor.supportedLanguagePairs.size)
        listOf("de", "hi", "pl", "la").forEach { language ->
            assertTrue(descriptor.supports(query("term", language, "ko")))
            assertTrue(descriptor.supports(query("말", "ko", language)))
        }
        assertEquals(
            setOf(DictionaryCapability.EXACT_LOOKUP, DictionaryCapability.TRANSLATIONS),
            descriptor.capabilities,
        )
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.localPersistence)
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.redistribution)
        assertEquals(
            DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            descriptor.attribution.usagePolicy.vocabularyImportMode,
        )
        assertEquals("CC0-1.0", descriptor.attribution.licenseShortName)
        assertEquals(PANLEX_RELEASE_ID, descriptor.dataset?.releaseId)
        assertEquals(PanLexProvider.INDEXED_RELATION_COUNT, descriptor.dataset?.entryCount)
    }

    @Test
    fun `direct relation maps to common result and exportable editor provenance`() = runTest {
        val lookup = RecordingLookup(
            PanLexLookupResult.Matches(listOf(record()), isTruncated = false),
        )
        val result = provider(lookup).search(query("Wasser", "de", "ko"))
            as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals("Wasser", entry.headword)
        assertEquals("물", entry.senses.single().meanings.single().text)
        assertEquals("de", entry.sourceLanguage.value)
        assertEquals("ex:11->ex:22", entry.sourceEntryId)
        assertEquals("mn:33:src:44", entry.senses.single().sourceSenseId)
        assertEquals("com.example.localvocabulary.dictionary.domain", entry::class.java.packageName)
        assertEquals(LookupRequest("Wasser", "de", "ko", 20), lookup.lastRequest)

        val mapping = DictionaryEntryDraftMapper.map(entry, importedAtEpochMillis = 1_000)
            as DictionaryEntryDraftMappingResult.Ready
        val sense = mapping.seed.draft.senses.single()
        assertEquals("Wasser", mapping.seed.draft.headword)
        assertEquals("물", sense.meaning)
        assertEquals("panlex", sense.provenance?.providerId)
        assertEquals("ex:11->ex:22", sense.provenance?.sourceEntryId)
        assertEquals("mn:33:src:44", sense.provenance?.sourceSenseId)
        assertEquals(PANLEX_RELEASE_ID, sense.provenance?.datasetVersion)
        assertFalse(sense.provenance!!.modifiedAfterImport)
    }

    @Test
    fun `Korean reverse relation uses foreign target and source IDs in query direction`() = runTest {
        val reverseRecord = record(
            sourceExpressionId = 22,
            sourceText = "물",
            targetExpressionId = 11,
            targetText = "Wasser",
        )
        val result = provider(
            RecordingLookup(PanLexLookupResult.Matches(listOf(reverseRecord), false)),
        ).search(query("물", "ko", "de")) as DictionarySearchResult.Success

        val entry = result.page.entries.single()
        assertEquals("물", entry.headword)
        assertEquals("Wasser", entry.senses.single().meanings.single().text)
        assertEquals("ex:22->ex:11", entry.sourceEntryId)
    }

    @Test
    fun `source ranking order truncation and requested limit pass through unchanged`() = runTest {
        val records = listOf(
            record(targetExpressionId = 1, targetText = "첫째", translationQuality = 20),
            record(targetExpressionId = 2, targetText = "둘째", translationQuality = 10),
        )
        val lookup = RecordingLookup(PanLexLookupResult.Matches(records, isTruncated = true))
        val result = provider(lookup).search(query("Haus", "de", "ko").copy(resultLimit = 2))
            as DictionarySearchResult.Success

        assertEquals(listOf("첫째", "둘째"), result.page.entries.map { it.senses.single().meanings.single().text })
        assertTrue(result.page.isTruncated)
        assertEquals(2, lookup.lastRequest?.limit)
    }

    @Test
    fun `unsupported pair and dataset outcomes are structured failures`() = runTest {
        val unsupportedSource = provider().search(query("word", "en", "ko"))
            as DictionarySearchResult.Failure
        val unsupportedResult = provider().search(query("Wasser", "de", "en"))
            as DictionarySearchResult.Failure
        val noResult = provider(RecordingLookup(PanLexLookupResult.NoMatch))
            .search(query("missing", "de", "ko")) as DictionarySearchResult.Failure
        val unavailable = provider(RecordingLookup(PanLexLookupResult.DatasetUnavailable))
            .search(query("Wasser", "de", "ko")) as DictionarySearchResult.Failure

        assertTrue(unsupportedSource.error is DictionaryProviderError.UnsupportedSourceLanguage)
        assertTrue(unsupportedResult.error is DictionaryProviderError.UnsupportedResultLanguage)
        assertEquals(DictionaryProviderError.NoResult, noResult.error)
        assertEquals(DictionaryProviderError.LocalDatasetUnavailable, unavailable.error)
    }

    @Test
    fun `registry discovers PanLex without changing Korean Basic Dictionary`() {
        val panLex = provider()
        val koreanBasic = KoreanBasicDictionaryProvider(NoMatchKoreanBasicLookup)
        val registry = DefaultDictionaryProviderRegistry(listOf(koreanBasic, panLex))

        assertEquals(
            listOf("panlex"),
            registry.descriptorsSupporting(query("Haus", "de", "ko")).map { it.id.value },
        )
        assertEquals(
            listOf("korean-basic-dictionary"),
            registry.descriptorsSupporting(query("house", "en", "ko")).map { it.id.value },
        )
        assertEquals(
            listOf("korean-basic-dictionary", "panlex"),
            registry.descriptors().map { it.id.value },
        )
    }

    private fun provider(
        lookup: PanLexLookup = RecordingLookup(PanLexLookupResult.NoMatch),
    ) = PanLexProvider(lookup)

    private fun query(text: String, source: String, result: String) = DictionaryQuery(
        text = text,
        languagePair = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid(source),
            resultLanguage = Bcp47LanguageTag.requireValid(result),
            resultKind = DictionaryResultKind.TRANSLATION,
        ),
    )

    private fun record(
        sourceExpressionId: Long = 11,
        sourceText: String = "Wasser",
        targetExpressionId: Long = 22,
        targetText: String = "물",
        translationQuality: Int = 17,
    ) = PanLexRelationRecord(
        sourceExpressionId = sourceExpressionId,
        sourceText = sourceText,
        targetExpressionId = targetExpressionId,
        targetText = targetText,
        representativeMeaningId = 33,
        representativeSourceId = 44,
        sourceAttestationCount = 3,
        sourceGroupCount = 2,
        translationQuality = translationQuality,
    )

    private class RecordingLookup(
        private val result: PanLexLookupResult,
    ) : PanLexLookup {
        var lastRequest: LookupRequest? = null

        override suspend fun exactLookup(
            query: String,
            sourceLanguageTag: String,
            resultLanguageTag: String,
            resultLimit: Int,
        ): PanLexLookupResult {
            lastRequest = LookupRequest(query, sourceLanguageTag, resultLanguageTag, resultLimit)
            return result
        }

        override suspend fun availability(): PanLexDatasetAvailability =
            PanLexDatasetAvailability.Available
    }

    private data class LookupRequest(
        val query: String,
        val source: String,
        val result: String,
        val limit: Int,
    )

    private data object NoMatchKoreanBasicLookup : KoreanBasicDictionaryLookup {
        override suspend fun exactLookup(
            query: String,
            sourceLanguageTag: String,
            resultLanguageTag: String,
        ): KoreanBasicDictionaryLookupResult = KoreanBasicDictionaryLookupResult.NoMatch

        override suspend fun availability(): KoreanBasicDictionaryDatasetAvailability =
            KoreanBasicDictionaryDatasetAvailability.Available
    }
}
