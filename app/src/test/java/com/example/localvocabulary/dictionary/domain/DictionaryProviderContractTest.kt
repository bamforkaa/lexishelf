package com.example.localvocabulary.dictionary.domain

import com.example.localvocabulary.backup.data.KotlinxBackupSerializer
import com.example.localvocabulary.backup.data.toBackupV2
import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMapper
import com.example.localvocabulary.dictionary.importer.DictionaryEntryDraftMappingResult
import com.example.localvocabulary.dictionary.registry.DefaultDictionaryProviderRegistry
import com.example.localvocabulary.dictionary.testing.FakeDictionaryProvider
import com.example.localvocabulary.vocabulary.domain.ValidatedVocabularyDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyEntry
import com.example.localvocabulary.vocabulary.domain.VocabularyEntryValidator
import com.example.localvocabulary.vocabulary.domain.VocabularyRepository
import com.example.localvocabulary.vocabulary.domain.VocabularySenseDraft
import com.example.localvocabulary.vocabulary.domain.VocabularyValidationResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryProviderContractTest {
    @Test
    fun `BCP 47 value preserves script and region distinctions`() {
        val simplifiedChinese = language("zh-Hans")
        val traditionalChinese = language("zh-Hant")

        assertNotEquals(simplifiedChinese, traditionalChinese)
        assertEquals("en-GB", language("EN-gb").value)
        assertNull(Bcp47LanguageTag.parse("en_US"))
    }

    @Test
    fun `supported language pair returns paged common domain result`() = runTest {
        val provider = provider()

        val result = provider.search(query()) as DictionarySearchResult.Success

        assertEquals(1, result.page.entries.size)
        assertEquals("test-next-page", result.page.nextContinuationToken)
        assertTrue(result.page.isTruncated)
        assertEquals(FakeDictionaryProvider.SUPPORTED_QUERY, result.page.entries.single().headword)
    }

    @Test
    fun `unsupported source and result languages are distinct failures`() = runTest {
        val provider = provider()
        val unsupportedSource = provider.search(
            query(source = language("de")),
        ) as DictionarySearchResult.Failure
        val unsupportedResult = provider.search(
            query(result = language("ko")),
        ) as DictionarySearchResult.Failure

        assertTrue(unsupportedSource.error is DictionaryProviderError.UnsupportedSourceLanguage)
        assertTrue(unsupportedResult.error is DictionaryProviderError.UnsupportedResultLanguage)
    }

    @Test
    fun `descriptor exposes online local and optional linguistic capabilities`() = runTest {
        val online = provider(access = DictionaryAccess.ONLINE)
        val local = provider(
            id = "test.local",
            access = DictionaryAccess.LOCAL_DATASET,
        )
        val entry = (
            online.search(query()) as DictionarySearchResult.Success
            ).page.entries.single()

        assertEquals(DictionaryAccess.ONLINE, online.descriptor.access)
        assertEquals(DictionaryAccess.LOCAL_DATASET, local.descriptor.access)
        assertTrue(DictionaryCapability.READING in online.descriptor.capabilities)
        assertTrue(DictionaryCapability.AUDIO in online.descriptor.capabilities)
        assertNotNull(entry.linguisticFeatures.reading)
        assertNotNull(entry.linguisticFeatures.pronunciations.single().audio)
        assertTrue(entry.linguisticFeatures.transliterations.isNotEmpty())
        assertTrue(entry.linguisticFeatures.inflections.isNotEmpty())
        assertNotNull(entry.linguisticFeatures.etymology)
        assertNotNull(entry.senses.single().grammaticalGender)
    }

    @Test
    fun `provider ID is stable persistable and validated`() {
        val first = DictionaryProviderId("org.example.dictionary")
        val restored = DictionaryProviderId(first.value)

        assertEquals(first, restored)
        assertEquals("org.example.dictionary", first.value)
        assertTrue(runCatching { DictionaryProviderId("Display Name") }.isFailure)
    }

    @Test
    fun `registry discovers providers and filters by exact language pair`() {
        val traditional = provider(id = "test.traditional")
        val simplifiedPair = pair(source = language("zh-Hans"))
        val simplified = provider(
            id = "test.simplified",
            supportedPairs = setOf(simplifiedPair),
        )
        val registry = DefaultDictionaryProviderRegistry(listOf(traditional, simplified))

        assertEquals(
            listOf("test.simplified", "test.traditional"),
            registry.descriptors().map { it.id.value },
        )
        assertSame(traditional, registry.find(traditional.descriptor.id))
        assertEquals(
            listOf("test.traditional"),
            registry.descriptorsSupporting(query()).map { it.id.value },
        )
        assertTrue(
            runCatching {
                DefaultDictionaryProviderRegistry(listOf(traditional, traditional))
            }.isFailure,
        )
    }

    @Test
    fun `fake provider DTO is mapped to common domain types at the boundary`() = runTest {
        val result = provider().search(query()) as DictionarySearchResult.Success
        val entry = result.page.entries.single()

        assertEquals(
            "com.example.localvocabulary.dictionary.domain",
            entry::class.java.packageName,
        )
        assertEquals(
            "com.example.localvocabulary.dictionary.domain",
            entry.senses.single()::class.java.packageName,
        )
        assertFalse(entry::class.java.name.contains("FakeProviderEntryDto"))
    }

    @Test
    fun `permitted external result maps to editable draft with source attribution`() = runTest {
        val provider = provider(persistence = DictionaryPermission.PERMITTED)
        val entry = (
            provider.search(query()) as DictionarySearchResult.Success
            ).page.entries.single()

        val mapped = DictionaryEntryDraftMapper.map(entry, 1_000) as DictionaryEntryDraftMappingResult.Ready

        assertEquals(FakeDictionaryProvider.SUPPORTED_QUERY, mapped.seed.draft.headword)
        assertEquals("ja", mapped.seed.draft.languageTag)
        assertEquals("test definition", mapped.seed.draft.senses.single().meaning)
        assertEquals(provider.descriptor.id, mapped.seed.source.providerId)
        assertEquals("fake-entry-1", mapped.seed.source.sourceEntryId)
        assertEquals(provider.descriptor.attribution, mapped.seed.source.attribution)
        assertSame(entry, mapped.seed.transientEntry)
        assertEquals(
            setOf(
                DictionaryContentField.HEADWORD,
                DictionaryContentField.TRANSLATION,
                DictionaryContentField.PART_OF_SPEECH,
                DictionaryContentField.EXAMPLE,
            ),
            mapped.seed.copiedProviderFields,
        )
    }

    @Test
    fun `validated user edits remain independent from refreshed provider result`() = runTest {
        val provider = provider(persistence = DictionaryPermission.PERMITTED)
        val initialEntry = (
            provider.search(query()) as DictionarySearchResult.Success
            ).page.entries.single()
        val initial = DictionaryEntryDraftMapper.map(initialEntry, 1_000) as DictionaryEntryDraftMappingResult.Ready
        val userEditedDraft = initial.seed.draft.copy(
            senses = initial.seed.draft.senses.map { it.copy(meaning = "사용자가 수정한 뜻") },
            notes = "사용자 메모",
        )
        val validated = VocabularyEntryValidator.validate(userEditedDraft)

        provider.updateDefinition("provider refreshed definition")
        val refreshedEntry = (
            provider.search(query()) as DictionarySearchResult.Success
            ).page.entries.single()
        val refreshed = DictionaryEntryDraftMapper.map(refreshedEntry, 2_000) as DictionaryEntryDraftMappingResult.Ready

        assertTrue(validated is VocabularyValidationResult.Valid)
        val savedInput = (validated as VocabularyValidationResult.Valid).draft
        assertEquals("사용자가 수정한 뜻", savedInput.senses.single().meaning)
        assertEquals("사용자 메모", savedInput.notes)
        assertEquals("provider refreshed definition", refreshed.seed.draft.senses.single().meaning)
        assertEquals("사용자가 수정한 뜻", userEditedDraft.senses.single().meaning)
        assertEquals(initial.seed.source, refreshed.seed.source)
    }

    @Test
    fun `unknown and prohibited results remain visible but only create manual drafts`() = runTest {
        listOf(DictionaryPermission.UNKNOWN, DictionaryPermission.PROHIBITED).forEach { permission ->
            val provider = provider(persistence = permission)
            val entry = entryFrom(provider)

            assertEquals("test definition", entry.senses.single().meanings.single().text)
            val mapped = DictionaryEntryDraftMapper.map(entry, 1_000)
                as DictionaryEntryDraftMappingResult.ReferenceOnly

            assertSame(entry, mapped.transientEntry)
            assertEquals(provider.descriptor.attribution, mapped.source.attribution)
            assertEquals("", mapped.manualDraft.headword)
            assertEquals("ja", mapped.manualDraft.languageTag)
            assertEquals("", mapped.manualDraft.senses.single().meaning)
            assertTrue(mapped.manualDraft.senses.single().examples.isEmpty())
        }
    }

    @Test
    fun `prohibited source still allows user authored vocabulary and excludes it from backup`() = runTest {
        val entry = entryFrom(provider(persistence = DictionaryPermission.PROHIBITED))
        val mapped = DictionaryEntryDraftMapper.map(entry, 1_000)
            as DictionaryEntryDraftMappingResult.ReferenceOnly
        val userDraft = mapped.manualDraft.copy(
            headword = "manual-headword",
            senses = listOf(
                VocabularySenseDraft(
                    meaning = "my own meaning",
                    partOfSpeech = "my label",
                    examples = listOf("my own example"),
                ),
            ),
            notes = "my own note",
        )
        val validated = VocabularyEntryValidator.validate(userDraft) as VocabularyValidationResult.Valid
        val repository = RecordingVocabularyRepository()

        repository.save(validated.draft)
        val json = KotlinxBackupSerializer().encode(repository.savedDraft!!.toBackup())

        assertEquals("my own meaning", repository.savedDraft!!.senses.single().meaning)
        assertTrue(json.contains("my own meaning"))
        assertFalse(json.contains(entry.headword))
        assertFalse(json.contains("test definition"))
        assertFalse(json.contains("test example"))
        assertFalse(json.contains("test etymology"))
        assertFalse(json.contains("Test dictionary source"))
    }

    @Test
    fun `only provider fields permitted for persistence and export enter the draft`() = runTest {
        val provider = provider(
            persistence = DictionaryPermission.PROHIBITED,
            redistribution = DictionaryPermission.PROHIBITED,
            fieldOverrides = mapOf(
                DictionaryContentField.HEADWORD to DictionaryFieldUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                ),
                DictionaryContentField.TRANSLATION to DictionaryFieldUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PERMITTED,
                ),
                DictionaryContentField.PART_OF_SPEECH to DictionaryFieldUsagePolicy(
                    localPersistence = DictionaryPermission.PERMITTED,
                    redistribution = DictionaryPermission.PROHIBITED,
                ),
                DictionaryContentField.EXAMPLE to DictionaryFieldUsagePolicy(
                    localPersistence = DictionaryPermission.UNKNOWN,
                    redistribution = DictionaryPermission.PERMITTED,
                ),
            ),
        )

        val mapped = DictionaryEntryDraftMapper.map(entryFrom(provider), 1_000)
            as DictionaryEntryDraftMappingResult.Ready

        assertEquals(FakeDictionaryProvider.SUPPORTED_QUERY, mapped.seed.draft.headword)
        assertEquals("test definition", mapped.seed.draft.senses.single().meaning)
        assertEquals("", mapped.seed.draft.senses.single().partOfSpeech)
        assertTrue(mapped.seed.draft.senses.single().examples.isEmpty())
        assertEquals(
            setOf(DictionaryContentField.HEADWORD, DictionaryContentField.TRANSLATION),
            mapped.seed.copiedProviderFields,
        )
    }

    @Test
    fun `locally permitted but non-redistributable content stays reference only`() = runTest {
        val entry = entryFrom(
            provider(
                persistence = DictionaryPermission.PERMITTED,
                redistribution = DictionaryPermission.PROHIBITED,
            ),
        )

        val mapped = DictionaryEntryDraftMapper.map(entry, 1_000)

        assertTrue(mapped is DictionaryEntryDraftMappingResult.ReferenceOnly)
    }

    private suspend fun entryFrom(provider: FakeDictionaryProvider): ExternalDictionaryEntry =
        (provider.search(query()) as DictionarySearchResult.Success).page.entries.single()

    private fun provider(
        id: String = "test.dictionary",
        access: DictionaryAccess = DictionaryAccess.ONLINE,
        supportedPairs: Set<DictionaryLanguagePair> = setOf(pair()),
        persistence: DictionaryPermission = DictionaryPermission.PERMITTED,
        redistribution: DictionaryPermission = DictionaryPermission.PERMITTED,
        fieldOverrides: Map<DictionaryContentField, DictionaryFieldUsagePolicy> = emptyMap(),
    ) = FakeDictionaryProvider(
        providerId = id,
        access = access,
        supportedLanguagePairs = supportedPairs,
        usagePolicy = DictionaryUsagePolicy(
            localPersistence = persistence,
            redistribution = redistribution,
            cachePolicy = DictionaryCachePolicy.SESSION_ONLY,
            vocabularyImportMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            fieldOverrides = fieldOverrides,
            note = "test only",
        ),
    )

    private fun query(
        source: Bcp47LanguageTag = language("ja"),
        result: Bcp47LanguageTag = language("en"),
    ) = DictionaryQuery(
        text = FakeDictionaryProvider.SUPPORTED_QUERY,
        languagePair = pair(source, result),
        resultLimit = 10,
    )

    private fun pair(
        source: Bcp47LanguageTag = language("ja"),
        result: Bcp47LanguageTag = language("en"),
    ) = DictionaryLanguagePair(
        sourceLanguage = source,
        resultLanguage = result,
        resultKind = DictionaryResultKind.TRANSLATION,
    )

    private fun language(tag: String) = Bcp47LanguageTag.requireValid(tag)

    private fun ValidatedVocabularyDraft.toBackup() = VocabularyBackupV2(
        format = BACKUP_FORMAT_ID,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = 1,
        tags = emptyList(),
        entries = listOf(
            BackupEntryV2(
                stableId = "manual-entry",
                headword = headword,
                languageTag = languageTag,
                senses = senses.map { sense ->
                    BackupSenseV2(
                        sense.meaning,
                        sense.partOfSpeech,
                        sense.examples,
                        sense.provenance?.toBackupV2(),
                    )
                },
                notes = notes,
                tagStableIds = emptyList(),
                createdAtEpochMillis = 1,
                modifiedAtEpochMillis = 1,
            ),
        ),
    )

    private class RecordingVocabularyRepository : VocabularyRepository {
        var savedDraft: ValidatedVocabularyDraft? = null

        override fun observeEntries(
            query: String,
            tagId: Long?,
        ) = flowOf<List<VocabularyEntry>>(emptyList())

        override fun observeEntry(id: Long) = flowOf<VocabularyEntry?>(null)

        override suspend fun save(draft: ValidatedVocabularyDraft): Long {
            savedDraft = draft
            return 1
        }

        override suspend fun delete(id: Long) = Unit
    }
}
