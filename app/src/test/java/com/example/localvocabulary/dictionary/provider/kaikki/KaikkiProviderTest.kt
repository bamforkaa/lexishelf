package com.example.localvocabulary.dictionary.provider.kaikki

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryContentField
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryPronunciationNotation
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.DictionarySenseLabelType
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaikkiProviderTest {
    @Test
    fun `descriptor is one stable local provider with English fallback pairs`() {
        val descriptor = provider().descriptor

        assertEquals("kaikki", descriptor.id.value)
        assertEquals(DictionaryAccess.LOCAL_DATASET, descriptor.access)
        assertEquals(KAIKKI_SOURCE_LANGUAGES.size, descriptor.supportedLanguagePairs.size)
        KAIKKI_SOURCE_LANGUAGES.forEach { language ->
            assertTrue(descriptor.supports(query("word", language.value, "en")))
        }
        assertTrue(DictionaryCapability.PART_OF_SPEECH in descriptor.capabilities)
        assertTrue(DictionaryCapability.PRONUNCIATION_TEXT in descriptor.capabilities)
        assertTrue(DictionaryCapability.INFLECTION in descriptor.capabilities)
        assertTrue(DictionaryCapability.MORPHOLOGY_LOOKUP in descriptor.capabilities)
        assertTrue(DictionaryCapability.EXAMPLE_SENTENCES in descriptor.capabilities)
        assertTrue(DictionaryCapability.SENSE_LABELS in descriptor.capabilities)
        assertEquals(KaikkiProvider.INDEXED_ENTRY_COUNT, descriptor.dataset?.entryCount)
        assertEquals(
            DictionaryPermission.PERMITTED,
            descriptor.attribution.usagePolicy.fieldPolicy(DictionaryContentField.EXAMPLE)
                .localPersistence,
        )
    }

    @Test
    fun `rich result imports pronunciation and gender while forms stay transient`() = runTest {
        val lookup = RecordingLookup(
            KaikkiLookupResult.Matches(listOf(match()), isTruncated = false),
        )
        val result = provider(lookup).search(query("Wasser", "de", "en"))
            as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals("Wasser", entry.headword)
        assertEquals(listOf("water", "a body of water"), entry.senses.single().meanings.map { it.text })
        assertEquals("adjective", entry.senses.single().partOfSpeech)
        assertEquals("adj", entry.senses.single().sourcePartOfSpeech)
        assertEquals("neuter", entry.senses.single().grammaticalGender)
        assertEquals(
            listOf(
                DictionarySenseLabelType.INFORMAL,
                DictionarySenseLabelType.TRANSITIVE,
            ),
            entry.senses.single().labels.map { it.type },
        )
        assertEquals(3, entry.senses.single().availableExampleCount)
        assertEquals(
            listOf("Das Wasser ist kalt.", "Das Wasser kocht."),
            entry.senses.single().examples.map { it.text },
        )
        assertEquals(listOf("/ˈvasɐ/"), entry.linguisticFeatures.pronunciations.map { it.text })
        assertEquals(
            DictionaryPronunciationNotation.IPA,
            entry.linguisticFeatures.pronunciations.single().notation,
        )
        assertEquals(9, entry.linguisticFeatures.totalInflectionCount)
        assertEquals("de", lookup.lastLanguage)
        assertEquals("https://en.wiktionary.org/wiki/Wasser", entry.attribution.sourceUrl)

        val mapping = DictionaryEntryDraftMapper.map(entry, 1_000)
            as DictionaryEntryDraftMappingResult.Ready
        val sense = mapping.seed.draft.senses.single()
        assertEquals("water; a body of water", sense.meaning)
        assertEquals("adjective", sense.partOfSpeech)
        assertFalse(sense.meaning.contains("informal"))
        assertFalse(sense.partOfSpeech.contains("transitive"))
        assertTrue(mapping.seed.draft.notes.isEmpty())
        assertEquals(
            listOf("Das Wasser ist kalt.", "Das Wasser kocht."),
            sense.examples,
        )
        assertEquals("kaikki", sense.provenance?.providerId)
        assertEquals("en-Wasser-de-adj-1", sense.provenance?.sourceSenseId)
        val provenance = requireNotNull(sense.provenance)
        assertTrue(
            com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField.EXAMPLES in
                provenance.importedFields,
        )
        assertEquals("neuter", sense.grammaticalGender?.displayValue())
        assertTrue(
            com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField
                .GRAMMATICAL_GENDER in provenance.importedFields,
        )
        val pronunciation = mapping.seed.draft.pronunciations.single()
        assertEquals("/ˈvasɐ/", pronunciation.value)
        assertEquals("de", pronunciation.languageTag)
        assertEquals("kaikki", pronunciation.provenance?.providerId)
        assertTrue(
            com.example.localvocabulary.vocabulary.domain.ImportedDictionaryField.PRONUNCIATION in
                requireNotNull(pronunciation.provenance).importedFields,
        )
        assertTrue(
            DictionaryContentField.PRONUNCIATION_TEXT in mapping.seed.copiedProviderFields,
        )
        assertTrue(
            DictionaryContentField.GRAMMATICAL_GENDER in mapping.seed.copiedProviderFields,
        )
        assertFalse(DictionaryContentField.INFLECTION in mapping.seed.copiedProviderFields)
        assertEquals(
            entry.senses.single().labels,
            mapping.seed.transientEntry.senses.single().labels,
        )
        assertFalse(provenance.modifiedAfterImport)
    }

    @Test
    fun `enPR is typed as phonetic rather than IPA`() = runTest {
        val source = match().copy(
            entry = match().entry.copy(pronunciations = listOf("enPR: wô-tər")),
        )
        val entry = (
            provider(RecordingLookup(KaikkiLookupResult.Matches(listOf(source), false)))
                .search(query("Wasser", "de", "en")) as DictionarySearchResult.Success
            ).page.entries.single()

        assertEquals(
            DictionaryPronunciationNotation.PHONETIC,
            entry.linguisticFeatures.pronunciations.single().notation,
        )
        assertEquals("wô-tər", entry.linguisticFeatures.pronunciations.single().text)
    }

    @Test
    fun `unsupported pair no result and missing language pack remain structured`() = runTest {
        val unsupported = provider().search(query("water", "en", "de"))
            as DictionarySearchResult.Failure
        val noResult = provider(RecordingLookup(KaikkiLookupResult.NoMatch))
            .search(query("missing", "de", "en")) as DictionarySearchResult.Failure
        val unavailable = provider(RecordingLookup(KaikkiLookupResult.DatasetUnavailable))
            .search(query("Wasser", "de", "en")) as DictionarySearchResult.Failure

        assertTrue(unsupported.error is DictionaryProviderError.UnsupportedSourceLanguage)
        assertEquals(DictionaryProviderError.NoResult, noResult.error)
        assertEquals(DictionaryProviderError.LocalDatasetUnavailable, unavailable.error)
    }

    @Test
    fun `sense without retained example still imports normally`() = runTest {
        val sourceMatch = match()
        val withoutExamples = sourceMatch.copy(
            entry = sourceMatch.entry.copy(
                senses = listOf(
                    sourceMatch.entry.senses.single().copy(
                        retainedExamples = emptyList(),
                        availableExampleCount = 0,
                    ),
                ),
            ),
        )
        val result = provider(
            RecordingLookup(
                KaikkiLookupResult.Matches(listOf(withoutExamples), isTruncated = false),
            ),
        ).search(query("Wasser", "de", "en")) as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        val mapped = DictionaryEntryDraftMapper.map(entry, 1_000)
            as DictionaryEntryDraftMappingResult.Ready

        assertEquals("water; a body of water", mapped.seed.draft.senses.single().meaning)
        assertTrue(mapped.seed.draft.senses.single().examples.isEmpty())
    }

    @Test
    fun `usage labels remain on their source sense and unknown codes are ignored`() = runTest {
        val source = match()
        val first = source.entry.senses.single()
        val result = provider(
            RecordingLookup(
                KaikkiLookupResult.Matches(
                    listOf(
                        source.copy(
                            entry = source.entry.copy(
                                senses = listOf(
                                    first.copy(
                                        usageLabels = listOf("informal", "unknown", "informal"),
                                    ),
                                    first.copy(
                                        order = 1,
                                        sourceSenseId = "neutral-sense",
                                        glosses = listOf("neutral meaning"),
                                        usageLabels = emptyList(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    isTruncated = false,
                ),
            ),
        ).search(query("Wasser", "de", "en")) as DictionarySearchResult.Success

        assertEquals(
            listOf(DictionarySenseLabelType.INFORMAL),
            result.page.entries.single().senses[0].labels.map { it.type },
        )
        assertTrue(result.page.entries.single().senses[1].labels.isEmpty())
    }

    @Test
    fun `reviewed raw label codes map to provider neutral types`() = runTest {
        val source = match()
        val reviewedCodes = listOf(
            "formal",
            "informal",
            "colloquial",
            "slang",
            "vulgar",
            "offensive",
            "derogatory",
            "literary",
            "archaic",
            "obsolete",
            "dated",
            "rare",
            "transitive",
            "intransitive",
            "countable",
            "uncountable",
            "auxiliary",
            "impersonal",
            "regional",
            "dialectal",
        )
        val result = provider(
            RecordingLookup(
                KaikkiLookupResult.Matches(
                    records = listOf(
                        source.copy(
                            entry = source.entry.copy(
                                senses = listOf(
                                    source.entry.senses.single().copy(
                                        usageLabels = reviewedCodes,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    isTruncated = false,
                ),
            ),
        ).search(query("Wasser", "de", "en")) as DictionarySearchResult.Success

        assertEquals(
            DictionarySenseLabelType.entries,
            result.page.entries.single().senses.single().labels.map { it.type },
        )
    }

    private fun provider(
        lookup: KaikkiLookup = RecordingLookup(KaikkiLookupResult.NoMatch),
    ) = KaikkiProvider(lookup)

    private fun query(text: String, source: String, result: String) = DictionaryQuery(
        text = text,
        languagePair = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid(source),
            resultLanguage = Bcp47LanguageTag.requireValid(result),
            resultKind = DictionaryResultKind.TRANSLATION,
        ),
    )

    private fun match() = KaikkiMatch(
        sourceEntryId = "enw-de-entry",
        entry = KaikkiEntryRecord(
            headword = "Wasser",
            rawPartOfSpeech = "adj",
            pronunciations = listOf("/ˈvasɐ/"),
            retainedForms = listOf(KaikkiFormRecord("Wassers", "genitive")),
            totalFormCount = 9,
            senses = listOf(
                KaikkiSenseRecord(
                    order = 0,
                    sourceSenseId = "en-Wasser-de-adj-1",
                    glosses = listOf("water", "a body of water"),
                    retainedExamples = listOf(
                        "Das Wasser ist kalt.",
                        "Das Wasser kocht.",
                    ),
                    availableExampleCount = 3,
                    grammaticalGender = "neuter",
                    usageLabels = listOf("transitive", "informal", "transitive"),
                ),
            ),
        ),
    )

    private class RecordingLookup(private val result: KaikkiLookupResult) : KaikkiLookup {
        var lastLanguage: String? = null

        override suspend fun exactLookup(
            query: String,
            sourceLanguageTag: String,
            resultLimit: Int,
        ): KaikkiLookupResult {
            lastLanguage = sourceLanguageTag
            return result
        }

        override suspend fun availability(): KaikkiDatasetAvailability =
            KaikkiDatasetAvailability.Available
    }
}
