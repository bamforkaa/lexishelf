package com.example.localvocabulary.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreSettingsRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reviewLimitsSurviveRecreationAndUpdateTogether() = runBlocking {
        val repository = DataStoreSettingsRepository(context)
        val previous = repository.settings.first().reviewLimits
        try {
            val limits = com.example.localvocabulary.review.domain.ReviewLimits(7, 20)
            repository.setReviewLimits(limits)
            assertEquals(limits, DataStoreSettingsRepository(context).settings.first().reviewLimits)
        } finally { repository.setReviewLimits(previous) }
    }

    @Test
    fun canonicalCustomLanguageSurvivesRepositoryRecreationWithoutBuiltInDuplicates() = runBlocking {
        val repository = DataStoreSettingsRepository(context)

        repository.addUserLanguageTag("NL")
        repository.addUserLanguageTag("nl")
        repository.addUserLanguageTag("EN")

        val recreatedRepository = DataStoreSettingsRepository(context)
        val restored = recreatedRepository.settings.first()
        assertEquals(1, restored.userLanguageTags.count { it == "nl" })
        assertFalse("en" in restored.userLanguageTags)
    }

    @Test
    fun invalidLanguageIsRejectedWithoutChangingPersistentCatalog() = runBlocking {
        val repository = DataStoreSettingsRepository(context)
        val before = repository.settings.first().userLanguageTags

        val failure = runCatching { repository.addUserLanguageTag("not_a_tag") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(before, repository.settings.first().userLanguageTags)
    }

    @Test
    fun savingUnknownDefaultLanguageAlsoAddsItToReusableCatalog() = runBlocking {
        val repository = DataStoreSettingsRepository(context)

        repository.setDefaultLanguageTag("pt-br")

        val restored = DataStoreSettingsRepository(context).settings.first()
        assertEquals("pt-BR", restored.defaultLanguageTag)
        assertTrue("pt-BR" in restored.userLanguageTags)
    }
}
