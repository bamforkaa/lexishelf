package com.example.localvocabulary.feature.wordeditor

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryCachePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryContentField
import com.example.localvocabulary.dictionary.domain.DictionaryExample
import com.example.localvocabulary.dictionary.domain.DictionaryFieldUsagePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryPermission
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionaryResultKind
import com.example.localvocabulary.dictionary.domain.DictionaryUsagePolicy
import com.example.localvocabulary.dictionary.domain.DictionaryVocabularyImportMode
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionarySuggestionSynthesizerTest {
    @Test
    fun `identical Korean meaning from rich and fallback providers is one candidate`() {
        val groups = listOf(
            group("panlex", "PanLex", entry("panlex", "먹다", "ko", sourceEntryId = "pl-1")),
            group(
                "korean-basic-dictionary",
                "한국어기초사전",
                entry(
                    "korean-basic-dictionary",
                    "먹다",
                    "ko",
                    partOfSpeech = "동사",
                    sourceEntryId = "kr-1",
                ),
            ),
        )

        val candidate = DictionarySuggestionSynthesizer.synthesize(groups)
            .single().candidates.single()

        assertEquals(listOf("한국어기초사전", "PanLex"), candidate.sources.map { it.providerName })
        assertEquals("korean-basic-dictionary", candidate.primarySource.providerId.value)
        assertEquals("동사", candidate.primaryEntry.senses.single().partOfSpeech)
    }

    @Test
    fun `identical English meaning merges after NFC trim and whitespace normalization`() {
        val decomposed = "  cafe\u0301   au   lait "
        val groups = listOf(
            group("source-b", "Source B", entry("source-b", decomposed, "en", sourceEntryId = "b")),
            group("source-a", "Source A", entry("source-a", "café au lait", "en", sourceEntryId = "a")),
        )

        val candidate = DictionarySuggestionSynthesizer.synthesize(groups)
            .single().candidates.single()

        assertEquals(2, candidate.sources.size)
        assertEquals("source-a", candidate.primarySource.providerId.value)
    }

    @Test
    fun `case and genuinely different meanings remain separate`() {
        val groups = listOf(
            group("a", "A", entry("a", "water", "en", sourceEntryId = "1")),
            group("b", "B", entry("b", "Water", "en", sourceEntryId = "2")),
            group("c", "C", entry("c", "the water", "en", sourceEntryId = "3")),
        )

        val candidates = DictionarySuggestionSynthesizer.synthesize(groups).single().candidates

        assertEquals(listOf("water", "Water", "the water"), candidates.map { it.displayMeaning })
    }

    @Test
    fun `conflicting POS and same-provider homographs are not merged`() {
        val groups = listOf(
            group(
                "specialist",
                "Specialist",
                entry("specialist", "lead", "en", "noun", "entry-1", "sense-1"),
                entry("specialist", "lead", "en", "verb", "entry-2", "sense-2"),
            ),
            group("fallback", "Fallback", entry("fallback", "lead", "en", sourceEntryId = "entry-3")),
        )

        val candidates = DictionarySuggestionSynthesizer.synthesize(groups).single().candidates

        assertEquals(3, candidates.size)
        assertTrue(candidates.all { it.sources.size == 1 })
        assertEquals(setOf("noun", "verb", null), candidates.map { it.primaryEntry.senses.single().partOfSpeech }.toSet())
    }

    @Test
    fun `Korean group precedes English and stable key ignores input order`() {
        val korean = group("panlex", "PanLex", entry("panlex", "물", "ko", sourceEntryId = "pl"))
        val english = group("kaikki", "Kaikki", entry("kaikki", "water", "en", "noun", "ka"))

        val first = DictionarySuggestionSynthesizer.synthesize(listOf(english, korean))
        val second = DictionarySuggestionSynthesizer.synthesize(listOf(korean, english))

        assertEquals(listOf("ko", "en"), first.map { it.resultLanguage?.value })
        assertEquals(
            first.flatMap { it.candidates }.associate { it.displayMeaning to it.key },
            second.flatMap { it.candidates }.associate { it.displayMeaning to it.key },
        )
    }

    @Test
    fun `importable Kaikki example makes Kaikki deterministic primary and stays source owned`() {
        val groups = listOf(
            group("fallback", "Fallback", entry("fallback", "water", "en", "noun", "fb")),
            group(
                "kaikki",
                "Kaikki / Wiktionary",
                entry(
                    providerId = "kaikki",
                    meaning = "water",
                    resultLanguage = "en",
                    partOfSpeech = "noun",
                    sourceEntryId = "ka",
                    examples = listOf("Das Wasser ist kalt."),
                ),
            ),
        )

        val candidate = DictionarySuggestionSynthesizer.synthesize(groups)
            .single().candidates.single()

        assertEquals("kaikki", candidate.primarySource.providerId.value)
        assertEquals(listOf("Das Wasser ist kalt."), candidate.primaryEntry.senses.single().examples.map { it.text })
        assertEquals(listOf("Kaikki / Wiktionary", "Fallback"), candidate.sources.map { it.providerName })
    }

    @Test
    fun `reading and pronunciation remain distinct source fields`() {
        val reading = entry(
            providerId = "jmdict",
            meaning = "to eat",
            resultLanguage = "en",
            sourceEntryId = "jm",
        ).copy(
            linguisticFeatures = DictionaryLinguisticFeatures(
                reading = DictionaryReading("たべる"),
            ),
        )
        val pronunciation = entry(
            providerId = "kaikki",
            meaning = "to eat",
            resultLanguage = "en",
            sourceEntryId = "ka",
        ).copy(
            linguisticFeatures = DictionaryLinguisticFeatures(
                pronunciations = listOf(com.example.localvocabulary.dictionary.domain.DictionaryPronunciation("/taberu/")),
            ),
        )

        val candidate = DictionarySuggestionSynthesizer.synthesize(
            listOf(group("jmdict", "JMdict", reading), group("kaikki", "Kaikki", pronunciation)),
        ).single().candidates.single()

        assertEquals("jmdict", candidate.primarySource.providerId.value)
        assertEquals("たべる", candidate.primaryEntry.linguisticFeatures.reading?.text)
        assertEquals("/taberu/", candidate.sources.single { it.providerId.value == "kaikki" }
            .entry.linguisticFeatures.pronunciations.single().text)
    }

    @Test
    fun `CC CEDICT pinyin remains a reading on the deterministic primary source`() {
        val ccCedict = entry(
            providerId = "cc-cedict",
            meaning = "hello",
            resultLanguage = "en",
            sourceEntryId = "cc",
        ).copy(
            linguisticFeatures = DictionaryLinguisticFeatures(
                reading = DictionaryReading("ni3 hao3"),
            ),
        )
        val fallback = entry(
            providerId = "fallback",
            meaning = "hello",
            resultLanguage = "en",
            sourceEntryId = "fb",
        )

        val candidate = DictionarySuggestionSynthesizer.synthesize(
            listOf(group("fallback", "Fallback", fallback), group("cc-cedict", "CC-CEDICT", ccCedict)),
        ).single().candidates.single()

        assertEquals("cc-cedict", candidate.primarySource.providerId.value)
        assertEquals("ni3 hao3", candidate.primaryEntry.linguisticFeatures.reading?.text)
        assertTrue(candidate.primaryEntry.linguisticFeatures.pronunciations.isEmpty())
    }

    @Test
    fun `provider role ordering is deterministic when raw group order changes`() {
        val rich = group(
            "korean-basic-dictionary",
            "한국어기초사전",
            entry("korean-basic-dictionary", "먹다", "ko", sourceEntryId = "kr"),
        )
        val fallback = group(
            "panlex",
            "PanLex",
            entry("panlex", "섭취하다", "ko", sourceEntryId = "pl"),
        )

        val first = DictionarySuggestionSynthesizer.synthesize(listOf(fallback, rich))
            .single().candidates.map { it.displayMeaning }
        val second = DictionarySuggestionSynthesizer.synthesize(listOf(rich, fallback))
            .single().candidates.map { it.displayMeaning }

        assertEquals(listOf("먹다", "섭취하다"), first)
        assertEquals(first, second)
    }

    @Test
    fun `supporting source set participates in stable synthesized identity`() {
        val primary = group("a", "A", entry("a", "water", "en", sourceEntryId = "1"))
        val supporting = group("b", "B", entry("b", "water", "en", sourceEntryId = "2"))

        val oneSourceKey = DictionarySuggestionSynthesizer.synthesize(listOf(primary))
            .single().candidates.single().key
        val twoSourceKey = DictionarySuggestionSynthesizer.synthesize(listOf(primary, supporting))
            .single().candidates.single().key

        assertNotEquals(oneSourceKey, twoSourceKey)
    }

    private fun group(
        providerId: String,
        providerName: String,
        vararg entries: ExternalDictionaryEntry,
    ) = DictionarySuggestionGroup(
        providerId = DictionaryProviderId(providerId),
        providerName = providerName,
        entries = entries.toList(),
        languagePair = pair(entries.first().senses.first().meanings.first().language.value),
    )

    private fun entry(
        providerId: String,
        meaning: String,
        resultLanguage: String,
        partOfSpeech: String? = null,
        sourceEntryId: String,
        sourceSenseId: String = "sense-1",
        examples: List<String> = emptyList(),
    ) = ExternalDictionaryEntry(
        providerId = DictionaryProviderId(providerId),
        sourceEntryId = sourceEntryId,
        headword = "query",
        sourceLanguage = Bcp47LanguageTag.requireValid("de"),
        linguisticFeatures = DictionaryLinguisticFeatures(),
        senses = listOf(
            ExternalDictionarySense(
                meanings = listOf(
                    DictionaryMeaning(
                        text = meaning,
                        language = Bcp47LanguageTag.requireValid(resultLanguage),
                        kind = DictionaryResultKind.TRANSLATION,
                    ),
                ),
                partOfSpeech = partOfSpeech,
                examples = examples.map {
                    DictionaryExample(it, Bcp47LanguageTag.requireValid("de"))
                },
                sourceSenseId = sourceSenseId,
            ),
        ),
        attribution = attribution(providerId),
    )

    private fun pair(resultLanguage: String) = DictionaryLanguagePair(
        sourceLanguage = Bcp47LanguageTag.requireValid("de"),
        resultLanguage = Bcp47LanguageTag.requireValid(resultLanguage),
        resultKind = DictionaryResultKind.TRANSLATION,
    )

    private fun attribution(providerId: String) = DictionaryAttribution(
        sourceName = providerId,
        sourceUrl = "https://example.test/$providerId",
        officialIdentifier = providerId,
        licenseName = "Test license",
        licenseUrl = "https://example.test/license",
        attributionNotice = "Test",
        usagePolicy = DictionaryUsagePolicy(
            localPersistence = DictionaryPermission.PERMITTED,
            redistribution = DictionaryPermission.PERMITTED,
            cachePolicy = DictionaryCachePolicy.PERMITTED,
            vocabularyImportMode = DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS,
            fieldOverrides = mapOf(
                DictionaryContentField.EXAMPLE to DictionaryFieldUsagePolicy(
                    DictionaryPermission.PERMITTED,
                    DictionaryPermission.PERMITTED,
                ),
            ),
        ),
    )
}
