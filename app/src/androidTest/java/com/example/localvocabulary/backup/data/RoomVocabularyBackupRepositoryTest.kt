package com.example.localvocabulary.backup.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.backup.domain.BACKUP_FORMAT_ID
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.backup.domain.BackupDictionaryProvenanceV2
import com.example.localvocabulary.backup.domain.BackupEntryV2
import com.example.localvocabulary.backup.domain.BackupImportedFieldV2
import com.example.localvocabulary.backup.domain.BackupSenseV2
import com.example.localvocabulary.backup.domain.BackupTagV1
import com.example.localvocabulary.backup.domain.CURRENT_BACKUP_SCHEMA_VERSION
import com.example.localvocabulary.backup.domain.ValidatedBackup
import com.example.localvocabulary.backup.domain.VocabularyBackupV2
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.dao.SenseDictionaryProvenanceWrite
import com.example.localvocabulary.core.database.dao.PronunciationWrite
import com.example.localvocabulary.core.database.entity.TagEntity
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.core.database.entity.WordbookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomVocabularyBackupRepositoryTest {
    private lateinit var database: VocabularyDatabase
    private lateinit var repository: RoomVocabularyBackupRepository
    private val serializer = KotlinxBackupSerializer()

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VocabularyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomVocabularyBackupRepository(database, TimeProvider { 1_000 })
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun completeExportImportRoundTripPreservesUnicodeOrderRelationsAndTimestamps() = runTest {
        val sharedTagId = insertTag("tag-shared", "Shared")
        val japaneseTagId = insertTag("tag-japanese", "日本語")
        val studyWordbookId = insertWordbook("wordbook-jlpt", "JLPT N2")
        val travelWordbookId = insertWordbook("wordbook-travel", "여행")
        insertEntry(
            stableId = "entry-japanese",
            headword = "辞書",
            languageTag = "ja",
            senses = listOf(
                SenseWrite(
                    "사전 / 詞典",
                    "名詞",
                    listOf("彼は辞書を引いた。", "他查了词典。"),
                    provenanceWrite(modified = true).copy(
                        importedFields = setOf("MEANING", "GRAMMATICAL_GENDER"),
                    ),
                    grammaticalGender = "NEUTER",
                ),
                SenseWrite("lexicon", "noun", listOf("café naïve façade")),
            ),
            notes = "ملاحظة عربية",
            tagIds = setOf(sharedTagId, japaneseTagId),
            createdAt = 100,
            modifiedAt = 200,
            reading = "じしょ",
            readingProvenance = provenanceWrite(modified = true).copy(
                providerId = "jmdict",
                sourceEntryId = "1358280",
                sourceSenseId = "1358280:1",
                sourceName = "JMdict",
                datasetVersion = "2026-08-23",
                importedFields = setOf("READING"),
            ),
            wordbookIds = setOf(studyWordbookId, travelWordbookId),
            pronunciations = listOf(
                PronunciationWrite(
                    stableId = "pronunciation-ja-1",
                    notation = "IPA",
                    value = "/dʑiɕo/",
                    languageTag = "ja",
                    provenance = provenanceWrite(modified = true).copy(
                        providerId = "kaikki",
                        sourceEntryId = "enw-ja-entry",
                        sourceSenseId = null,
                        sourceName = "English Wiktionary via Kaikki/Wiktextract",
                        importedFields = setOf("PRONUNCIATION"),
                    ),
                ),
            ),
        )
        insertEntry(
            stableId = "entry-arabic",
            headword = "مُعْجَم",
            languageTag = "ar",
            senses = listOf(SenseWrite("dictionary", "اسم", listOf("فَتَحَ الْمُعْجَمَ."))),
            notes = "中文备注",
            tagIds = setOf(sharedTagId),
            createdAt = 300,
            modifiedAt = 400,
        )

        val exportedJson = serializer.encode(repository.createBackup())
        val validated = decode(exportedJson)
        repository.importBackup(validated, BackupConflictPolicy.REPLACE_ALL)
        val reExportedJson = serializer.encode(repository.createBackup())

        assertEquals(exportedJson, reExportedJson)
        val entries = database.vocabularyDao().getAllEntries()
        assertEquals(2, entries.size)
        val japanese = entries.single { it.entry.backupId == "entry-japanese" }
        val senses = japanese.senses.sortedBy { it.sense.sortOrder }
        assertEquals(listOf("사전 / 詞典", "lexicon"), senses.map { it.sense.meaning })
        assertEquals(
            listOf("彼は辞書を引いた。", "他查了词典。"),
            senses.first().examples.sortedBy { it.sortOrder }.map { it.text },
        )
        assertEquals(100L, japanese.entry.createdAtEpochMillis)
        assertEquals(200L, japanese.entry.modifiedAtEpochMillis)
        assertEquals("じしょ", japanese.entry.reading)
        assertEquals("cc-cedict", senses.first().provenance?.providerId)
        assertEquals(
            setOf("MEANING", "GRAMMATICAL_GENDER"),
            senses.first().provenanceFields.map { it.field }.toSet(),
        )
        assertEquals("NEUTER", senses.first().sense.grammaticalGender)
        assertEquals("/dʑiɕo/", japanese.pronunciations.single().pronunciation.value)
        assertEquals("pronunciation-ja-1", japanese.pronunciations.single().pronunciation.stableId)
        assertEquals("kaikki", japanese.pronunciations.single().provenance?.providerId)
        assertEquals("jmdict", japanese.entryProvenance.single().providerId)
        assertEquals("READING", japanese.entryProvenance.single().field)
        assertTrue(senses.first().provenance!!.modifiedAfterImport)
        assertEquals(null, senses.last().provenance)
        assertEquals(2, database.tagDao().getAll().size)
        assertEquals(2, database.wordbookDao().getAll().size)
        assertEquals(
            listOf("JLPT N2", "여행"),
            japanese.wordbooks.map { it.name }.sorted(),
        )
        assertEquals(
            2,
            entries.count { entry -> entry.tags.any { it.backupId == "tag-shared" } },
        )
    }

    @Test
    fun emptyDatabaseExportImportsAsEmptyBackup() = runTest {
        val exported = repository.createBackup()
        val result = repository.importBackup(
            decode(serializer.encode(exported)),
            BackupConflictPolicy.REPLACE_ALL,
        )

        assertTrue(exported.entries.isEmpty())
        assertTrue(exported.tags.isEmpty())
        assertEquals(0, result.createdEntryCount)
        assertTrue(database.vocabularyDao().getAllEntries().isEmpty())
    }

    @Test
    fun schemaV1BackupImportsWithUserAuthoredSemanticsAndNoProvenance() = runTest {
        val v1Json =
            """
            {
              "format": "$BACKUP_FORMAT_ID",
              "schemaVersion": 1,
              "exportedAtEpochMillis": 500,
              "tags": [],
              "entries": [{
                "stableId": "entry-v1",
                "headword": "legacy",
                "languageTag": "en",
                "senses": [{"meaning":"old meaning","partOfSpeech":"noun","examples":[]}],
                "notes": "old note",
                "tagStableIds": [],
                "createdAtEpochMillis": 100,
                "modifiedAtEpochMillis": 200
              }]
            }
            """.trimIndent()

        repository.importBackup(decode(v1Json), BackupConflictPolicy.MERGE_BY_STABLE_ID)

        val stored = database.vocabularyDao().getAllEntries().single()
        assertEquals("legacy", stored.entry.headword)
        assertEquals("old meaning", stored.senses.single().sense.meaning)
        assertEquals(null, stored.senses.single().provenance)
    }

    @Test
    fun validationFailureLeavesExistingDatabaseUnchanged() = runTest {
        insertEntry(
            stableId = "entry-existing",
            headword = "existing",
            languageTag = "en",
            senses = listOf(SenseWrite("meaning", "", emptyList())),
        )
        val invalid = backup(
            entries = listOf(backupEntry("entry-invalid", "invalid", languageTag = "en_US")),
        )

        val decoded = serializer.decode(serializer.encode(invalid))

        assertTrue(decoded is BackupDecodeResult.Failure)
        assertEquals("existing", database.vocabularyDao().getAllEntries().single().entry.headword)
    }

    @Test
    fun databaseFailureRollsBackReplaceImport() = runTest {
        val oldTagId = insertTag("tag-old", "Old")
        insertEntry(
            stableId = "entry-old",
            headword = "old",
            languageTag = "en",
            senses = listOf(SenseWrite("old meaning", "", emptyList())),
            tagIds = setOf(oldTagId),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_backup_import
            BEFORE INSERT ON senses
            WHEN NEW.meaning = 'force-failure'
            BEGIN
                SELECT RAISE(ABORT, 'forced test failure');
            END
            """.trimIndent(),
        )
        val incoming = decode(
            serializer.encode(
                backup(
                    tags = listOf(BackupTagV1("tag-new", "New")),
                    entries = listOf(
                        backupEntry("entry-new", "new", meaning = "force-failure", tagIds = listOf("tag-new")),
                    ),
                ),
            ),
        )

        val failure = runCatching {
            repository.importBackup(incoming, BackupConflictPolicy.REPLACE_ALL)
        }.exceptionOrNull()

        assertNotNull(failure)
        val stored = database.vocabularyDao().getAllEntries().single()
        assertEquals("entry-old", stored.entry.backupId)
        assertEquals("old meaning", stored.senses.single().sense.meaning)
        assertEquals("Old", database.tagDao().getAll().single().name)
    }

    @Test
    fun mergePolicyUpdatesMatchingStableIdAndKeepsUnmatchedEntries() = runTest {
        insertEntry(
            stableId = "entry-conflict",
            headword = "old",
            languageTag = "en",
            senses = listOf(SenseWrite("old meaning", "", emptyList())),
        )
        insertEntry(
            stableId = "entry-keep",
            headword = "keep",
            languageTag = "en",
            senses = listOf(SenseWrite("keep meaning", "", emptyList())),
        )
        val incoming = decode(
            serializer.encode(
                backup(
                    entries = listOf(
                        backupEntry("entry-conflict", "updated", meaning = "updated meaning"),
                        backupEntry("entry-new", "new", meaning = "new meaning"),
                    ),
                ),
            ),
        )

        val preview = repository.previewImport(incoming, BackupConflictPolicy.MERGE_BY_STABLE_ID)
        val result = repository.importBackup(incoming, BackupConflictPolicy.MERGE_BY_STABLE_ID)

        assertEquals(1, preview.conflictCount)
        assertEquals(1, preview.newEntryCount)
        assertEquals(1, preview.updatedEntryCount)
        assertEquals(0, preview.existingEntryRemovalCount)
        assertEquals(1, result.createdEntryCount)
        assertEquals(1, result.updatedEntryCount)
        val entries = database.vocabularyDao().getAllEntries()
        assertEquals(3, entries.size)
        assertEquals("updated", entries.single { it.entry.backupId == "entry-conflict" }.entry.headword)
        assertEquals("keep", entries.single { it.entry.backupId == "entry-keep" }.entry.headword)
    }

    @Test
    fun replacePolicyReportsAndRemovesAllExistingEntries() = runTest {
        insertEntry("entry-one", "one", "en", listOf(SenseWrite("one", "", emptyList())))
        insertEntry("entry-two", "two", "en", listOf(SenseWrite("two", "", emptyList())))
        val incoming = decode(
            serializer.encode(backup(entries = listOf(backupEntry("entry-three", "three")))),
        )

        val preview = repository.previewImport(incoming, BackupConflictPolicy.REPLACE_ALL)
        val result = repository.importBackup(incoming, BackupConflictPolicy.REPLACE_ALL)

        assertEquals(2, preview.existingEntryRemovalCount)
        assertEquals(1, preview.newEntryCount)
        assertEquals(2, result.removedEntryCount)
        assertEquals("entry-three", database.vocabularyDao().getAllEntries().single().entry.backupId)
    }

    @Test
    fun mergeReusesExistingNormalizedTagInsteadOfCreatingDuplicateRow() = runTest {
        insertTag("tag-existing", "Shared Tag")
        val incoming = decode(
            serializer.encode(
                backup(
                    tags = listOf(BackupTagV1("tag-from-file", "  shared   tag ")),
                    entries = listOf(
                        backupEntry(
                            stableId = "entry-new",
                            headword = "word",
                            tagIds = listOf("tag-from-file"),
                        ),
                    ),
                ),
            ),
        )

        repository.importBackup(incoming, BackupConflictPolicy.MERGE_BY_STABLE_ID)

        assertEquals(1, database.tagDao().getAll().size)
        assertEquals("tag-existing", database.tagDao().getAll().single().backupId)
        assertEquals("Shared Tag", database.vocabularyDao().getAllEntries().single().tags.single().name)
    }

    private suspend fun insertTag(stableId: String, name: String): Long = database.tagDao().insert(
        TagEntity(
            backupId = stableId,
            name = name,
            normalizedName = name.lowercase(),
        ),
    )

    private suspend fun insertWordbook(
        stableId: String,
        name: String,
    ): Long = database.wordbookDao().insert(
        WordbookEntity(
            backupId = stableId,
            name = name,
            normalizedName = name.lowercase(),
        ),
    )

    private suspend fun insertEntry(
        stableId: String,
        headword: String,
        languageTag: String,
        senses: List<SenseWrite>,
        notes: String = "",
        tagIds: Set<Long> = emptySet(),
        createdAt: Long = 10,
        modifiedAt: Long = 20,
        reading: String = "",
        readingProvenance: SenseDictionaryProvenanceWrite? = null,
        wordbookIds: Set<Long> = emptySet(),
        pronunciations: List<PronunciationWrite> = emptyList(),
    ): Long = database.vocabularyDao().saveEntry(
        entry = VocabularyEntryEntity(
            backupId = stableId,
            headword = headword,
            languageTag = languageTag,
            notes = notes,
            createdAtEpochMillis = createdAt,
            modifiedAtEpochMillis = modifiedAt,
            reading = reading,
        ),
        senses = senses,
        tagIds = tagIds,
        readingProvenance = readingProvenance,
        wordbookIds = wordbookIds,
        pronunciations = pronunciations,
    )

    private fun decode(json: String): ValidatedBackup =
        (serializer.decode(json) as BackupDecodeResult.Success).backup

    private fun backup(
        entries: List<BackupEntryV2>,
        tags: List<BackupTagV1> = emptyList(),
    ) = VocabularyBackupV2(
        format = BACKUP_FORMAT_ID,
        schemaVersion = CURRENT_BACKUP_SCHEMA_VERSION,
        exportedAtEpochMillis = 1_000,
        tags = tags,
        entries = entries,
    )

    private fun backupEntry(
        stableId: String,
        headword: String,
        languageTag: String = "en",
        meaning: String = "meaning",
        tagIds: List<String> = emptyList(),
    ) = BackupEntryV2(
        stableId = stableId,
        headword = headword,
        languageTag = languageTag,
        senses = listOf(BackupSenseV2(meaning, "noun", listOf("example"))),
        notes = "note",
        tagStableIds = tagIds,
        createdAtEpochMillis = 100,
        modifiedAtEpochMillis = 200,
    )

    private fun provenanceWrite(modified: Boolean = false) =
        SenseDictionaryProvenanceWrite(
            providerId = "cc-cedict",
            sourceEntryId = "詞典|词典|ci2dian3",
            sourceSenseId = "0",
            sourceName = "CC-CEDICT",
            sourceUrl = "https://cc-cedict.org/editor/editor.php?handler=Download",
            licenseName = "Creative Commons Attribution-ShareAlike 4.0 International",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            datasetVersion = "2026-08-22T08:27:42Z",
            importedFields = setOf("MEANING"),
            importedAtEpochMillis = 150,
            modifiedAfterImport = modified,
        )
}
