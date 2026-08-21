package com.example.localvocabulary.vocabulary.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.localvocabulary.core.database.VocabularyDatabase
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
        repository = RoomTagRepository(database.tagDao())
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
        assertEquals(SaveTagResult.NameConflict, duplicate)
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
            listOf(VocabularyTag(created.id, "Renamed Tag")),
            repository.observeTags().first(),
        )
    }
}
