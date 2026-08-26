package com.example.localvocabulary.vocabulary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.core.database.VocabularyDatabase
import com.example.localvocabulary.core.database.dao.SenseWrite
import com.example.localvocabulary.core.database.entity.VocabularyEntryEntity
import com.example.localvocabulary.vocabulary.domain.SaveTagResult
import com.example.localvocabulary.vocabulary.domain.VocabularyTag
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTagRepositoryTest {
    private lateinit var database: VocabularyDatabase
    private lateinit var repository: RoomTagRepository

    @Before
    fun createRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VocabularyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var nextId = 1
        repository = RoomTagRepository(database.tagDao()) { "tag-${nextId++}" }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun duplicatePolicyIgnoresCaseAndRepeatedWhitespace() = runTest {
        val first = repository.save(null, "  Study   List  ")
        val duplicate = repository.save(null, "study list")

        assertEquals(SaveTagResult.Saved(1), first)
        assertEquals(SaveTagResult.NameConflict(existingId = 1), duplicate)
        assertEquals(listOf("Study List"), repository.observeTags().first().map { it.name })
    }

    @Test
    fun updatingMissingTagReturnsNotFound() = runTest {
        assertEquals(SaveTagResult.NotFound, repository.save(999, "Missing"))
    }

    @Test
    fun updatingTagKeepsItsIdentityAndNormalizesTheNewName() = runTest {
        val created = repository.save(null, "Original") as SaveTagResult.Saved

        val updated = repository.save(created.id, "  Renamed   Tag  ")

        assertEquals(SaveTagResult.Saved(created.id), updated)
        assertEquals(
            listOf(VocabularyTag(created.id, "tag-1", "Renamed Tag")),
            repository.observeTags().first(),
        )
    }

    @Test
    fun tagSummariesCountRelationsWithoutLoadingVocabularyEntries() = runTest {
        val firstTag = repository.save(null, "JLPT N2") as SaveTagResult.Saved
        val secondTag = repository.save(null, "음식") as SaveTagResult.Saved
        database.vocabularyDao().saveEntry(
            entry = entry("食べる"),
            senses = listOf(SenseWrite("먹다", "동사", emptyList())),
            tagIds = setOf(firstTag.id, secondTag.id),
        )
        database.vocabularyDao().saveEntry(
            entry = entry("勉強"),
            senses = listOf(SenseWrite("공부", "명사", emptyList())),
            tagIds = setOf(firstTag.id),
        )

        val summaries = repository.observeTagSummaries().first()

        assertEquals(
            mapOf("JLPT N2" to 2, "음식" to 1),
            summaries.associate { it.tag.name to it.entryCount },
        )
    }

    private fun entry(headword: String) = VocabularyEntryEntity(
        backupId = "entry-$headword",
        headword = headword,
        languageTag = "ja",
        notes = "",
        createdAtEpochMillis = 1,
        modifiedAtEpochMillis = 1,
    )
}
