package com.example.localvocabulary.dictionary.reference

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalDictionaryIntentFactoryTest {
    @Test
    fun createsUserInitiatedActionViewOnlyForHttpUris() {
        val intent = ExternalDictionaryIntentFactory.create(
            "https://ja.dict.naver.com/#/search?query=%E9%A3%9F%E3%81%B9%E3%82%8B",
        )
        assertEquals(Intent.ACTION_VIEW, intent?.action)
        assertEquals("https", intent?.data?.scheme)
        assertNull(ExternalDictionaryIntentFactory.create("file:///data/private"))
        assertNull(ExternalDictionaryIntentFactory.create("not a uri"))
    }
}
