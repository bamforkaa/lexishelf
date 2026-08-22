package com.example.localvocabulary.dictionary.provider.cccedict

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
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationResult
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CcCedictProviderTest {
    @Test
    fun `descriptor has stable local ID exact language pairs capabilities and license policy`() = runTest {
        val provider = provider()
        val descriptor = provider.descriptor

        assertEquals("cc-cedict", descriptor.id.value)
        assertEquals(DictionaryAccess.LOCAL_DATASET, descriptor.access)
        assertTrue(descriptor.supports(query("中国", "zh-Hans", "en")))
        assertTrue(descriptor.supports(query("中國", "zh-Hant", "en")))
        assertEquals(
            setOf(
                DictionaryCapability.EXACT_LOOKUP,
                DictionaryCapability.ALTERNATE_WRITTEN_FORMS,
                DictionaryCapability.TRANSLATIONS,
                DictionaryCapability.READING,
            ),
            descriptor.capabilities,
        )
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.localPersistence)
        assertEquals(DictionaryPermission.PERMITTED, descriptor.attribution.usagePolicy.redistribution)
        assertEquals(
            DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            descriptor.attribution.usagePolicy.vocabularyImportMode,
        )
        assertEquals(CcCedictProvider.RELEASE_ID, descriptor.dataset?.releaseId)
        assertEquals(CC_CEDICT_ARTIFACT_NAME, descriptor.dataset?.artifactName)
        assertTrue(descriptor.attribution.attributionNotice!!.contains("CC BY-SA 4.0"))
        assertEquals("CC BY-SA 4.0", descriptor.attribution.licenseShortName)
    }

    @Test
    fun `provider maps simplified traditional pinyin and English definitions to common models`() = runTest {
        val result = provider().search(query("词典", "zh-Hans", "en"))
            as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals("词典", entry.headword)
        assertEquals("詞典", entry.alternateWrittenForms.single().text)
        assertEquals("zh-Hant", entry.alternateWrittenForms.single().language.value)
        assertEquals("ci2dian3", entry.linguisticFeatures.reading?.text)
        assertEquals(listOf("dictionary", "lexicon"), entry.senses.single().meanings.map { it.text })
        assertEquals("com.example.localvocabulary.dictionary.domain", entry::class.java.packageName)
        assertFalse(entry::class.java.name.contains("CcCedict"))
        assertTrue(entry.senses.single().examples.isEmpty())
        assertEquals(null, entry.senses.single().partOfSpeech)
    }

    @Test
    fun `unsupported source and result language pairs return structured failures`() = runTest {
        val provider = provider()
        val sourceFailure = provider.search(query("辞書", "ja", "en"))
            as DictionarySearchResult.Failure
        val resultFailure = provider.search(query("中国", "zh-Hans", "fr"))
            as DictionarySearchResult.Failure

        assertTrue(sourceFailure.error is DictionaryProviderError.UnsupportedSourceLanguage)
        assertTrue(resultFailure.error is DictionaryProviderError.UnsupportedResultLanguage)
    }

    @Test
    fun `CC definition maps to editable senses with reusable provenance`() = runTest {
        val provider = provider()
        val entry = (
            provider.search(query("中国", "zh-Hans", "en")) as DictionarySearchResult.Success
            ).page.entries.single()
        val mapping = DictionaryEntryDraftMapper.map(entry, 1_000)
            as DictionaryEntryDraftMappingResult.Ready

        assertEquals("中国", mapping.seed.transientEntry.headword)
        assertEquals(listOf("China", "Middle Kingdom"), mapping.seed.draft.senses.map { it.meaning })
        assertTrue(mapping.seed.draft.senses.all { it.partOfSpeech.isEmpty() })
        val provenance = mapping.seed.draft.senses.first().provenance
        assertNotNull(provenance)
        assertEquals("cc-cedict", provenance?.providerId)
        assertEquals(CcCedictProvider.RELEASE_ID, provenance?.datasetVersion)
        assertFalse(provenance!!.modifiedAfterImport)
        val userDraft = mapping.seed.draft.copy(
            senses = listOf(
                mapping.seed.draft.senses.first().copy(meaning = "내가 수정한 뜻"),
                VocabularySenseDraft("내가 작성한 뜻", "", emptyList()),
            ),
            notes = "내 메모",
        )
        val validated = VocabularyEntryValidator.validate(userDraft)

        assertTrue(validated is VocabularyValidationResult.Valid)
        val senses = (validated as VocabularyValidationResult.Valid).draft.senses
        assertEquals("내가 수정한 뜻", senses.first().meaning)
        assertNotNull(senses.first().provenance)
        assertEquals(null, senses.last().provenance)
        assertNotNull(mapping.seed.source.attribution.licenseUrl)
    }

    @Test
    fun `missing asset is reported as local dataset unavailable`() = runTest {
        val provider = provider(CcCedictDatasetSource { CcCedictDatasetOpenResult.Missing })
        val result = provider.search(query("中国", "zh-Hans", "en"))
            as DictionarySearchResult.Failure

        assertEquals(DictionaryProviderError.LocalDatasetUnavailable, result.error)
    }

    private fun kotlinx.coroutines.test.TestScope.provider(
        source: CcCedictDatasetSource = fixtureSource(),
    ) = CcCedictProvider(
        CcCedictDataSource(
            datasetSource = source,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        ),
    )

    private fun fixtureSource() = CcCedictDatasetSource {
        CcCedictDatasetOpenResult.Opened(
            requireNotNull(
                javaClass.getResourceAsStream("/cccedict/cccedict_fixture.u8"),
            ).bufferedReader(Charsets.UTF_8),
        )
    }

    private fun query(
        text: String,
        source: String,
        result: String,
    ) = DictionaryQuery(
        text = text,
        languagePair = DictionaryLanguagePair(
            sourceLanguage = Bcp47LanguageTag.requireValid(source),
            resultLanguage = Bcp47LanguageTag.requireValid(result),
            resultKind = DictionaryResultKind.TRANSLATION,
        ),
    )
}
