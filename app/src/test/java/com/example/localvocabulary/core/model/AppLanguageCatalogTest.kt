package com.example.localvocabulary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageCatalogTest {
    @Test
    fun `catalog contains current provider and common application languages`() {
        val tags = AppLanguageCatalog.entries.mapTo(mutableSetOf()) { it.languageTag }

        assertTrue(
            tags.containsAll(
                setOf(
                    "en", "ko", "ja", "zh-Hans", "zh-Hant", "de", "fr", "es", "ru",
                    "ar", "hi", "pl", "mn", "vi", "th", "id", "la",
                ),
            ),
        )
        assertNotNull(AppLanguageCatalog.find("zh-Hans"))
        assertNotNull(AppLanguageCatalog.find("zh-Hant"))
    }

    @Test
    fun `search matches English name native name and BCP47 code`() {
        assertEquals(listOf("ja"), AppLanguageCatalog.search("jap").map { it.languageTag })
        assertEquals(listOf("ja"), AppLanguageCatalog.search("日本").map { it.languageTag })
        assertTrue(AppLanguageCatalog.search("ja").any { it.languageTag == "ja" })
        assertEquals(
            setOf("zh-Hans", "zh-Hant"),
            AppLanguageCatalog.search("Chinese").mapTo(mutableSetOf()) { it.languageTag },
        )
    }

    @Test
    fun `user language metadata is generated and canonical duplicates are removed`() {
        val userEntries = AppLanguageCatalog.userEntries(setOf("NL", "nl", "EN"))

        assertEquals(listOf("nl"), userEntries.map { it.languageTag })
        assertEquals(listOf("nl"), AppLanguageCatalog.search("Dutch", userEntries).map { it.languageTag })
        assertEquals(listOf("nl"), AppLanguageCatalog.search("Neder", userEntries).map { it.languageTag })
        assertEquals(listOf("nl"), AppLanguageCatalog.search("nl", userEntries).map { it.languageTag })
    }

    @Test
    fun `unknown current valid language remains available without joining built in catalog`() {
        val current = AppLanguageCatalog.currentEntry("pt-br", emptyList())

        assertEquals("pt-BR", current?.languageTag)
        assertTrue(current?.englishName?.contains("Portuguese") == true)
        assertTrue(current?.englishName?.contains("Brazil") == true)
        assertEquals(null, AppLanguageCatalog.find("pt-BR"))
    }
}
