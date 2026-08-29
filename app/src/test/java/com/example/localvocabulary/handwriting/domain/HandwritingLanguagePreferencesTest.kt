package com.example.localvocabulary.handwriting.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HandwritingLanguagePreferencesTest {
    @Test
    fun `language suggestions preserve source priority and canonicalize duplicates`() {
        assertEquals(
            listOf("ja", "ko", "zh-Hani", "de", "en"),
            prioritizeHandwritingLanguageTags(
                contextLanguageTag = "JA",
                recentLanguageTags = listOf("ko", "ja"),
                installedLanguageTags = listOf("zh-Hani", "ko"),
                vocabularyLanguageTags = listOf("de", "en", "invalid_tag"),
            ),
        )
    }

    @Test
    fun `recent selections move to front stay unique and remain bounded`() {
        assertEquals(
            listOf("ja", "en", "ko"),
            updateRecentHandwritingLanguageTags(
                current = listOf("en", "ja", "ko", "en"),
                selectedLanguageTag = "ja",
                limit = 3,
            ),
        )
    }
}
