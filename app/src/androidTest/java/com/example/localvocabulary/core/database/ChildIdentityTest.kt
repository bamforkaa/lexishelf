package com.example.localvocabulary.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.backup.data.KotlinxBackupSerializer
import com.example.localvocabulary.backup.data.RoomVocabularyBackupRepository
import com.example.localvocabulary.backup.domain.BackupConflictPolicy
import com.example.localvocabulary.backup.domain.BackupDecodeResult
import com.example.localvocabulary.core.common.TimeProvider
import com.example.localvocabulary.core.database.dao.ExampleWrite
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChildIdentityTest {
    private lateinit var database: VocabularyDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), VocabularyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() = database.close()

    @Test
    fun contentEditsReorderAndRemovalPreserveSurvivingLongAndStableIds() = runTest {
        val dao = database.vocabularyDao()
        val first = SenseWrite("기대하다", "", listOf(
            ExampleWrite("I look forward to it.", stableId = "a", meaning = "기대됩니다.",
                origin = "CAPTURED", sourceTitle = "Podcast", sourceUrl = "https://example.com",
                sourceLocator = "12:35", capturedAt = 100),
            ExampleWrite("My sentence.", stableId = "b", origin = "USER"),
        ), stableId = "first")
        val second = SenseWrite("second", "", emptyList(), stableId = "second")
        val entryId = dao.saveEntry(entry(), listOf(first, second), emptySet())
        val initial = requireNotNull(dao.observeEntry(entryId).first())
        val senseIds = initial.senses.associate { it.sense.stableId to it.sense.id }
        val exampleIds = initial.senses.flatMap { it.examples }.associate { it.stableId to it.id }
        val edited = first.copy(meaning = "edited meaning", examples = first.examples.reversed().map {
            it.copy(text = it.text + " edited", meaning = "해석 수정")
        })
        dao.saveEntry(initial.entry, listOf(second, edited), emptySet())
        val reordered = requireNotNull(dao.observeEntry(entryId).first())
        assertEquals(senseIds, reordered.senses.associate { it.sense.stableId to it.sense.id })
        assertEquals(exampleIds, reordered.senses.flatMap { it.examples }.associate { it.stableId to it.id })
        assertEquals(listOf("second", "first"), reordered.senses.sortedBy { it.sense.sortOrder }.map { it.sense.stableId })
        val contexts = reordered.senses.single { it.sense.stableId == "first" }.examples.sortedBy { it.sortOrder }
        assertEquals(listOf("b", "a"), contexts.map { it.stableId })
        assertEquals("12:35", contexts.last().sourceLocator)
        assertEquals("해석 수정", contexts.last().meaning)
        dao.saveEntry(initial.entry, listOf(edited.copy(examples = listOf(edited.examples.last()))), emptySet())
        assertNull(dao.findSense("second"))
        assertNull(dao.findExample("b"))
        assertEquals(senseIds["first"], dao.findSense("first")?.id)
        assertEquals(exampleIds["a"], dao.findExample("a")?.id)
    }

    @Test
    fun v6MergeKeepsChildRowsAndReplaceRoundTripsAllMetadata() = runTest {
        val dao = database.vocabularyDao()
        val id = dao.saveEntry(entry(), listOf(SenseWrite("meaning", "", listOf(
            ExampleWrite("context", "example", "해석", "CAPTURED", "title", "https://example.com", "p.17", 100),
        ), stableId = "sense")), emptySet())
        val repository = RoomVocabularyBackupRepository(database, TimeProvider { 300 })
        val serializer = KotlinxBackupSerializer()
        val before = repository.createBackup()
        val validated = (serializer.decode(serializer.encode(before)) as BackupDecodeResult.Success).backup
        val senseId = dao.findSense("sense")!!.id
        val exampleId = dao.findExample("example")!!.id
        repository.importBackup(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID)
        assertEquals(id, dao.findEntryByBackupId("entry")!!.id)
        assertEquals(senseId, dao.findSense("sense")!!.id)
        assertEquals(exampleId, dao.findExample("example")!!.id)
        repository.importBackup(validated, BackupConflictPolicy.REPLACE_ALL)
        assertEquals(before, repository.createBackup())
    }

    @Test
    fun foreignChildIdentityAbortsWholeMerge() = runTest {
        val dao = database.vocabularyDao()
        dao.saveEntry(entry(), listOf(SenseWrite("original", "", emptyList(), stableId = "owned")), emptySet())
        val repository = RoomVocabularyBackupRepository(database, TimeProvider { 300 })
        val serializer = KotlinxBackupSerializer()
        val before = repository.createBackup()
        val conflicting = before.copy(entries = listOf(before.entries.single().copy(stableId = "other-entry")))
        val validated = (serializer.decode(serializer.encode(conflicting)) as BackupDecodeResult.Success).backup
        assertTrue(runCatching { repository.importBackup(validated, BackupConflictPolicy.MERGE_BY_STABLE_ID) }.isFailure)
        assertEquals(before, repository.createBackup())
    }

    private fun entry() = VocabularyEntryEntity(
        backupId = "entry", headword = "I look forward to working with you on this project.",
        languageTag = "en", notes = "note", createdAtEpochMillis = 100, modifiedAtEpochMillis = 200,
    )
}
