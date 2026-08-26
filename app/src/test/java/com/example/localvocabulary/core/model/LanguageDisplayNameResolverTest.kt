package com.example.localvocabulary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LanguageDisplayNameResolverTest {
    @Test
    fun `known BCP47 tags display human readable names without changing identity`() {
        assertEquals(LanguageDisplayName("English", "en"), LanguageDisplayNameResolver.resolve("EN"))
        assertEquals(LanguageDisplayName("日本語", "ja"), LanguageDisplayNameResolver.resolve("ja"))
        assertEquals(
            LanguageDisplayName("中文（简体）", "zh-Hans"),
            LanguageDisplayNameResolver.resolve("zh-hans"),
        )
        assertEquals(
            LanguageDisplayName("中文（繁體）", "zh-Hant"),
            LanguageDisplayNameResolver.resolve("zh-hant"),
        )
    }

    @Test
    fun `unknown valid BCP47 tag uses platform metadata without changing identity`() {
        val display = LanguageDisplayNameResolver.resolve("pt-br")

        assertEquals("pt-BR", display.languageTag)
        assertNotEquals("pt-BR", display.name)
    }
}
