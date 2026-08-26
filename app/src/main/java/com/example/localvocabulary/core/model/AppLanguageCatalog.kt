package com.example.localvocabulary.core.model

import java.util.IllformedLocaleException
import java.util.Locale

data class LanguageCatalogEntry(
    val languageTag: String,
    val nativeName: String,
    val englishName: String,
    val localizedName: String = englishName,
)

object AppLanguageCatalog {
    val entries: List<LanguageCatalogEntry> = listOf(
        LanguageCatalogEntry("en", "English", "English"),
        LanguageCatalogEntry("ko", "한국어", "Korean"),
        LanguageCatalogEntry("ja", "日本語", "Japanese"),
        LanguageCatalogEntry("zh-Hans", "中文（简体）", "Chinese (Simplified)"),
        LanguageCatalogEntry("zh-Hant", "中文（繁體）", "Chinese (Traditional)"),
        LanguageCatalogEntry("de", "Deutsch", "German"),
        LanguageCatalogEntry("fr", "Français", "French"),
        LanguageCatalogEntry("es", "Español", "Spanish"),
        LanguageCatalogEntry("ru", "Русский", "Russian"),
        LanguageCatalogEntry("ar", "العربية", "Arabic"),
        LanguageCatalogEntry("hi", "हिन्दी", "Hindi"),
        LanguageCatalogEntry("pl", "Polski", "Polish"),
        LanguageCatalogEntry("mn", "Монгол", "Mongolian"),
        LanguageCatalogEntry("vi", "Tiếng Việt", "Vietnamese"),
        LanguageCatalogEntry("th", "ไทย", "Thai"),
        LanguageCatalogEntry("id", "Bahasa Indonesia", "Indonesian"),
        LanguageCatalogEntry("la", "Latina", "Latin"),
    )

    fun find(languageTag: String): LanguageCatalogEntry? {
        val canonical = canonicalize(languageTag) ?: return null
        return entries.firstOrNull { it.languageTag == canonical }
    }

    fun userEntries(languageTags: Set<String>): List<LanguageCatalogEntry> = languageTags
        .mapNotNull(::createEntry)
        .filterNot { find(it.languageTag) != null }
        .distinctBy(LanguageCatalogEntry::languageTag)
        .sortedBy { it.englishName.lowercase(Locale.ROOT) }

    fun currentEntry(
        languageTag: String,
        userEntries: List<LanguageCatalogEntry>,
    ): LanguageCatalogEntry? {
        val entry = createEntry(languageTag) ?: return null
        return entry.takeUnless { candidate ->
            find(candidate.languageTag) != null ||
                userEntries.any { it.languageTag == candidate.languageTag }
        }
    }

    fun createEntry(languageTag: String): LanguageCatalogEntry? {
        val canonical = canonicalize(languageTag) ?: return null
        find(canonical)?.let { return it }
        val locale = Locale.forLanguageTag(canonical)
        val nativeName = locale.getDisplayName(locale).usableName(canonical)
        val englishName = locale.getDisplayName(Locale.ENGLISH).usableName(canonical)
        val localizedName = locale.getDisplayName(Locale.getDefault()).usableName(canonical)
        return LanguageCatalogEntry(
            languageTag = canonical,
            nativeName = nativeName,
            englishName = englishName,
            localizedName = localizedName,
        )
    }

    fun search(
        query: String,
        candidates: List<LanguageCatalogEntry> = entries,
    ): List<LanguageCatalogEntry> {
        val term = query.trim().lowercase(Locale.ROOT)
        if (term.isEmpty()) return candidates
        return candidates.filter { entry ->
            entry.languageTag.lowercase(Locale.ROOT).contains(term) ||
                entry.nativeName.lowercase(Locale.ROOT).contains(term) ||
                entry.englishName.lowercase(Locale.ROOT).contains(term) ||
                entry.localizedName.lowercase(Locale.ROOT).contains(term)
        }
    }

    private fun canonicalize(rawTag: String): String? = try {
        val candidate = rawTag.trim()
        if (candidate.isEmpty() || '_' in candidate) return null
        Locale.Builder().setLanguageTag(candidate).build().toLanguageTag()
            .takeUnless { it == "und" }
    } catch (_: IllformedLocaleException) {
        null
    }

    private fun String.usableName(fallback: String): String =
        trim().takeUnless { it.isEmpty() || it.equals("und", ignoreCase = true) } ?: fallback
}
