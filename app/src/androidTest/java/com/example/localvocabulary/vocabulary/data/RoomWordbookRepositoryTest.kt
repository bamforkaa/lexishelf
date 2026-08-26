package com.example.localvocabulary.vocabulary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.core.common.StableIdGenerator
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.vocabulary.domain.SaveWordbookResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWordbookRepositoryTest {
    private lateinit var database: VocabularyDatabase
    private lateinit var repository: RoomWordbookRepository
    private var nextId = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VocabularyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomWordbookRepository(
            database.wordbookDao(),
            StableIdGenerator { "wordbook-${++nextId}" },
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun createRenameDuplicateAndDeleteFollowWordbookIdentityPolicy() = runTest {
        val created = repository.save(null, "  JLPT   N2 ") as SaveWordbookResult.Saved
        assertEquals("JLPT N2", repository.observeWordbooks().first().single().name)

        assertEquals(
            SaveWordbookResult.NameConflict(created.id),
            repository.save(null, "jlpt n2"),
        )
        assertEquals(SaveWordbookResult.Saved(created.id), repository.save(created.id, "일본어 N2"))
        assertEquals("일본어 N2", repository.observeWordbooks().first().single().name)

        repository.delete(created.id)
        assertTrue(repository.observeWordbooks().first().isEmpty())
    }

    @Test
    fun batchMembershipUpdatesAreAtomicIdempotentAndPreserveVocabulary() = runTest {
        val wordbookId = (repository.save(null, "TOEIC 950") as SaveWordbookResult.Saved).id
        val firstEntryId = insertEntry("long", "기다랗다")
        val secondEntryId = insertEntry("soul", "영혼")

        assertEquals(2, repository.addEntries(wordbookId, setOf(firstEntryId, secondEntryId)))
        assertEquals(0, repository.addEntries(wordbookId, setOf(firstEntryId, secondEntryId)))
        assertEquals(
            setOf(firstEntryId, secondEntryId),
            repository.observeEntryIds(wordbookId).first(),
        )
        assertEquals(2, repository.observeWordbookSummaries().first().single().entryCount)

        assertEquals(1, repository.removeEntries(wordbookId, setOf(firstEntryId)))

        assertEquals(setOf(secondEntryId), repository.observeEntryIds(wordbookId).first())
        assertEquals(1, repository.observeWordbookSummaries().first().single().entryCount)
        val preserved = database.vocabularyDao().observeEntry(firstEntryId).first()
        assertEquals("long", preserved?.entry?.headword)
        assertEquals("기다랗다", preserved?.senses?.single()?.sense?.meaning)
    }

    private suspend fun insertEntry(headword: String, meaning: String): Long =
        database.vocabularyDao().saveEntry(
            entry = VocabularyEntryEntity(
                backupId = "entry-$headword",
                headword = headword,
                languageTag = "en",
                notes = "",
                createdAtEpochMillis = 1,
                modifiedAtEpochMillis = 1,
            ),
            senses = listOf(SenseWrite(meaning, "", emptyList())),
            tagIds = emptySet(),
        )
}
