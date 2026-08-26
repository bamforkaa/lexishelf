package com.example.localvocabulary.core.model

import java.util.IllformedLocaleException
import java.util.Locale

data class LanguageDisplayName(
    val name: String,
    val languageTag: String,
)

object LanguageDisplayNameResolver {
    fun resolve(rawTag: String): LanguageDisplayName {
        val canonical = canonicalize(rawTag) ?: rawTag.trim()
        val catalogEntry = AppLanguageCatalog.createEntry(canonical)
        return LanguageDisplayName(
            name = catalogEntry?.nativeName ?: canonical,
            languageTag = canonical,
        )
    }

    private fun canonicalize(rawTag: String): String? = try {
        Locale.Builder().setLanguageTag(rawTag.trim()).build().toLanguageTag()
            .takeUnless { it == "und" }
    } catch (_: IllformedLocaleException) {
        null
    }
}
