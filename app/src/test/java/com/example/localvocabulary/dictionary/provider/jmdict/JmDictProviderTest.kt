package com.example.localvocabulary.dictionary.provider.jmdict

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JmDictProviderTest {
    @Test
    fun `descriptor is stable local ja to en and permits attributed persistence`() {
        val descriptor = provider().descriptor

        assertEquals("jmdict", descriptor.id.value)
        assertEquals(DictionaryAccess.LOCAL_DATASET, descriptor.access)
        assertEquals(setOf(JmDictProvider.LANGUAGE_PAIR), descriptor.supportedLanguagePairs)
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.localPersistence)
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.redistribution)
        assertEquals("CC BY-SA 4.0", descriptor.attribution.licenseShortName)
        assertEquals(JMDICT_RELEASE_ID, descriptor.dataset?.releaseId)
    }

    @Test
    fun `kanji match preserves compatible reading pos sense order and restrictions`() = runTest {
        val result = provider(match(writtenOrder = 0)).search(query("食べる"))
            as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals("食べる", entry.headword)
        assertEquals(listOf("喰べる"), entry.alternateWrittenForms.map { it.text })
        assertEquals("たべる", entry.linguisticFeatures.reading?.text)
        assertEquals(listOf("to eat", "to consume"), entry.senses.first().meanings.map { it.text })
        assertEquals("Ichidan verb; transitive verb", entry.senses.first().partOfSpeech)
        assertEquals(setOf("食べる"), entry.senses.first().writtenFormRestrictions)
        assertEquals(setOf("たべる"), entry.senses.first().readingRestrictions)
        assertEquals(listOf("to live on"), entry.senses.last().meanings.map { it.text })
    }

    @Test
    fun `reading match selects its permitted writing and excludes incompatible senses`() = runTest {
        val result = provider(match(readingOrder = 1)).search(query("くべる"))
            as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals("喰べる", entry.headword)
        assertEquals("くべる", entry.linguisticFeatures.reading?.text)
        assertEquals(listOf("to live on"), entry.senses.flatMap { it.meanings }.map { it.text })
    }

    @Test
    fun `kana only entry stays kana only`() = runTest {
        val kana = JmDictEntryRecord(
            entrySequence = "200",
            readings = listOf(JmDictReading("こんにちは", hasNoWrittenForm = true)),
            senses = listOf(JmDictSense(order = 0, glosses = listOf(JmDictGloss("hello")))),
        )
        val result = provider(JmDictMatch(kana, JmDictMatchKind.READING, 0))
            .search(query("こんにちは")) as DictionarySearchResult.Success

        assertEquals("こんにちは", result.page.entries.single().headword)
        assertTrue(result.page.entries.single().linguisticFeatures.reading!!.appliesWithoutWrittenForm)
    }

    @Test
    fun `explicit Use copies reading POS selected sense and provenance through generic mapper`() = runTest {
        val external = (
            provider(match(writtenOrder = 0)).search(query("食べる")) as DictionarySearchResult.Success
            ).page.entries.single().let { it.copy(senses = listOf(it.senses.first())) }

        val mapped = DictionaryEntryDraftMapper.map(external, 1_000)
            as DictionaryEntryDraftMappingResult.Ready
        val sense = mapped.seed.draft.senses.single()

        assertEquals("たべる", mapped.seed.draft.reading)
        assertEquals("to eat; to consume", sense.meaning)
        assertEquals("Ichidan verb; transitive verb", sense.partOfSpeech)
        assertEquals("jmdict", sense.provenance?.providerId)
        assertEquals("100", sense.provenance?.sourceEntryId)
        assertEquals("100:1", sense.provenance?.sourceSenseId)
        assertEquals(
            setOf(ImportedDictionaryField.READING),
            mapped.seed.draft.readingProvenance?.importedFields,
        )
        assertFalse(sense.provenance!!.modifiedAfterImport)
    }

    @Test
    fun `unsupported pair no result unavailable and malformed are structured failures`() = runTest {
        val unsupported = provider().search(
            query("食べる").copy(
                languagePair = JmDictProvider.LANGUAGE_PAIR.copy(
                    resultLanguage = Bcp47LanguageTag.requireValid("ko"),
                ),
            ),
        ) as DictionarySearchResult.Failure
        val noResult = provider(result = JmDictLookupResult.NoMatch).search(query("不存在"))
            as DictionarySearchResult.Failure
        val unavailable = provider(result = JmDictLookupResult.DatasetUnavailable).search(query("食べる"))
            as DictionarySearchResult.Failure
        val malformed = provider(result = JmDictLookupResult.MalformedDataset("bad payload"))
            .search(query("食べる")) as DictionarySearchResult.Failure

        assertTrue(unsupported.error is DictionaryProviderError.UnsupportedResultLanguage)
        assertEquals(DictionaryProviderError.NoResult, noResult.error)
        assertEquals(DictionaryProviderError.LocalDatasetUnavailable, unavailable.error)
        assertEquals(DictionaryProviderError.MalformedProviderData("bad payload"), malformed.error)
    }

    private fun provider(
        match: JmDictMatch? = null,
        result: JmDictLookupResult = match?.let { JmDictLookupResult.Matches(listOf(it)) }
            ?: JmDictLookupResult.NoMatch,
    ) = JmDictProvider(FakeLookup(result))

    private fun query(text: String) = DictionaryQuery(text, JmDictProvider.LANGUAGE_PAIR)

    private fun match(writtenOrder: Int? = null, readingOrder: Int? = null): JmDictMatch = JmDictMatch(
        entry = JmDictEntryRecord(
            entrySequence = "100",
            writtenForms = listOf(JmDictWrittenForm("食べる"), JmDictWrittenForm("喰べる")),
            readings = listOf(
                JmDictReading("たべる", writtenFormRestrictions = listOf("食べる")),
                JmDictReading("くべる", writtenFormRestrictions = listOf("喰べる")),
            ),
            senses = listOf(
                JmDictSense(
                    order = 0,
                    writtenFormRestrictions = listOf("食べる"),
                    readingRestrictions = listOf("たべる"),
                    partOfSpeech = listOf("Ichidan verb", "transitive verb"),
                    glosses = listOf(JmDictGloss("to eat"), JmDictGloss("to consume")),
                ),
                JmDictSense(order = 1, glosses = listOf(JmDictGloss("to live on"))),
            ),
        ),
        matchKind = if (writtenOrder != null) JmDictMatchKind.WRITTEN_FORM else JmDictMatchKind.READING,
        matchedElementOrder = writtenOrder ?: requireNotNull(readingOrder),
    )

    private class FakeLookup(private val result: JmDictLookupResult) : JmDictLookup {
        override suspend fun exactLookup(query: String, resultLimit: Int?) = result
        override suspend fun availability() = JmDictDatasetAvailability.Available
    }
}
