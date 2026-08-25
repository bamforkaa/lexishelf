package com.example.localvocabulary.dictionary.reference

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaverDictionaryLinkProviderTest {
    private val provider = NaverDictionaryLinkProvider()

    @Test
    fun `supported language resolves official destination with encoded headword`() {
        val result = provider.resolve(language("ja"), "食べる 物")!!
        assertEquals("네이버 일본어사전", result.destinationName)
        assertTrue(result.uri.startsWith("https://ja.dict.naver.com/#/search?query="))
        assertTrue(result.uri.contains("%20"))
        assertTrue(result.uri.contains("%E9%A3%9F"))
    }

    @Test
    fun `Chinese script variants use verified Chinese destination`() {
        assertTrue(provider.resolve(language("zh-Hans"), "你好")!!.uri.startsWith("https://zh.dict.naver.com/"))
        assertTrue(provider.resolve(language("zh-Hant"), "你好")!!.uri.startsWith("https://zh.dict.naver.com/"))
    }

    @Test
    fun `unsupported language and empty headword do not create links`() {
        assertNull(provider.resolve(language("ko"), "단어"))
        assertNull(provider.resolve(language("en"), "  "))
    }

    private fun language(tag: String) = Bcp47LanguageTag.requireValid(tag)
}
