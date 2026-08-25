package com.example.localvocabulary.dictionary.reference

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ExternalDictionaryReference(
    val providerId: String,
    val providerName: String,
    val destinationName: String,
    val uri: String,
)

interface ExternalDictionaryReferenceProvider {
    fun resolve(
        sourceLanguage: Bcp47LanguageTag,
        headword: String,
    ): ExternalDictionaryReference?
}

class NaverDictionaryLinkProvider : ExternalDictionaryReferenceProvider {
    override fun resolve(
        sourceLanguage: Bcp47LanguageTag,
        headword: String,
    ): ExternalDictionaryReference? {
        val query = headword.trim().takeIf(String::isNotEmpty) ?: return null
        val language = sourceLanguage.value.substringBefore('-').lowercase()
        val destination = DESTINATIONS[language] ?: return null
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val uri = "${destination.baseUrl}#/search?query=$encodedQuery"
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != "https" || parsed.host !in ALLOWED_HOSTS) return null
        return ExternalDictionaryReference(
            providerId = PROVIDER_ID,
            providerName = "NAVER Dictionary",
            destinationName = destination.displayName,
            uri = uri,
        )
    }

    private data class Destination(val displayName: String, val baseUrl: String)

    companion object {
        const val PROVIDER_ID = "naver-dictionary-reference"

        private val ALLOWED_HOSTS = setOf(
            "en.dict.naver.com",
            "ja.dict.naver.com",
            "zh.dict.naver.com",
            "dict.naver.com",
        )

        private val DESTINATIONS = mapOf(
            "en" to Destination("네이버 영어사전", "https://en.dict.naver.com/"),
            "ja" to Destination("네이버 일본어사전", "https://ja.dict.naver.com/"),
            "zh" to Destination("네이버 중국어사전", "https://zh.dict.naver.com/"),
            "fr" to Destination("네이버 프랑스어사전", "https://dict.naver.com/frkodict/"),
            "de" to Destination("네이버 독일어사전", "https://dict.naver.com/dekodict/"),
            "es" to Destination("네이버 스페인어사전", "https://dict.naver.com/eskodict/"),
            "ru" to Destination("네이버 러시아어사전", "https://dict.naver.com/rukodict/"),
            "ar" to Destination("네이버 아랍어사전", "https://dict.naver.com/arkodict/"),
            "hi" to Destination("네이버 힌디어사전", "https://dict.naver.com/hikodict/"),
            "pl" to Destination("네이버 폴란드어사전", "https://dict.naver.com/plkodict/"),
            "mn" to Destination("네이버 몽골어사전", "https://dict.naver.com/mnkodict/"),
            "la" to Destination("네이버 라틴어사전", "https://dict.naver.com/lakodict/"),
        )
    }
}

