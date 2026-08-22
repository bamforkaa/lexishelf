package com.example.localvocabulary.dictionary.testing

import com.example.localvocabulary.dictionary.domain.DictionaryAccess
import com.example.localvocabulary.dictionary.domain.DictionaryAttribution
import com.example.localvocabulary.dictionary.domain.DictionaryAudio
import com.example.localvocabulary.dictionary.domain.DictionaryCapability
import com.example.localvocabulary.dictionary.domain.DictionaryExample
import com.example.localvocabulary.dictionary.domain.DictionaryInflection
import com.example.localvocabulary.dictionary.domain.DictionaryLanguagePair
import com.example.localvocabulary.dictionary.domain.DictionaryLinguisticFeatures
import com.example.localvocabulary.dictionary.domain.DictionaryMeaning
import com.example.localvocabulary.dictionary.domain.DictionaryPronunciation
import com.example.localvocabulary.dictionary.domain.DictionaryProvider
import com.example.localvocabulary.dictionary.domain.DictionaryProviderDescriptor
import com.example.localvocabulary.dictionary.domain.DictionaryProviderError
import com.example.localvocabulary.dictionary.domain.DictionaryProviderId
import com.example.localvocabulary.dictionary.domain.DictionaryQuery
import com.example.localvocabulary.dictionary.domain.DictionaryReading
import com.example.localvocabulary.dictionary.domain.DictionarySearchPage
import com.example.localvocabulary.dictionary.domain.DictionarySearchResult
import com.example.localvocabulary.dictionary.domain.DictionaryTransliteration
import com.example.localvocabulary.dictionary.domain.DictionaryUsagePolicy
import com.example.localvocabulary.dictionary.domain.ExternalDictionaryEntry
import com.example.localvocabulary.dictionary.domain.ExternalDictionarySense
import com.example.localvocabulary.dictionary.domain.ProviderAvailability

class FakeDictionaryProvider(
    providerId: String,
    access: DictionaryAccess,
    supportedLanguagePairs: Set<DictionaryLanguagePair>,
    usagePolicy: DictionaryUsagePolicy,
) : DictionaryProvider {
    override val descriptor = DictionaryProviderDescriptor(
        id = DictionaryProviderId(providerId),
        displayName = "Test provider $providerId",
        supportedLanguagePairs = supportedLanguagePairs,
        access = access,
        capabilities = setOf(
            DictionaryCapability.EXACT_LOOKUP,
            DictionaryCapability.MONOLINGUAL_DEFINITIONS,
            DictionaryCapability.TRANSLATIONS,
            DictionaryCapability.PRONUNCIATION_TEXT,
            DictionaryCapability.AUDIO,
            DictionaryCapability.READING,
            DictionaryCapability.TRANSLITERATION,
            DictionaryCapability.EXAMPLE_SENTENCES,
            DictionaryCapability.PART_OF_SPEECH,
            DictionaryCapability.GRAMMATICAL_GENDER,
            DictionaryCapability.INFLECTION,
            DictionaryCapability.ETYMOLOGY,
        ),
        attribution = DictionaryAttribution(
            sourceName = "Test dictionary source",
            sourceUrl = "https://example.invalid/dictionary",
            officialIdentifier = "test-source",
            licenseName = "Test-only terms",
            licenseUrl = "https://example.invalid/terms",
            attributionNotice = "Test fixture only",
            usagePolicy = usagePolicy,
        ),
    )

    private var definitionText = "test definition"

    fun updateDefinition(text: String) {
        definitionText = text
    }

    override suspend fun search(query: DictionaryQuery): DictionarySearchResult {
        val matchingSource = descriptor.supportedLanguagePairs.any {
            it.sourceLanguage == query.languagePair.sourceLanguage
        }
        if (!matchingSource) {
            return DictionarySearchResult.Failure(
                DictionaryProviderError.UnsupportedSourceLanguage(
                    query.languagePair.sourceLanguage,
                ),
            )
        }
        if (!descriptor.supports(query)) {
            return DictionarySearchResult.Failure(
                DictionaryProviderError.UnsupportedResultLanguage(
                    resultLanguage = query.languagePair.resultLanguage,
                    resultKind = query.languagePair.resultKind,
                ),
            )
        }
        if (query.text != SUPPORTED_QUERY) {
            return DictionarySearchResult.Failure(DictionaryProviderError.NoResult)
        }

        val dto = FakeProviderEntryDto(
            id = "fake-entry-1",
            word = query.text,
            definition = definitionText,
        )
        return DictionarySearchResult.Success(
            DictionarySearchPage(
                entries = listOf(dto.toDomain(query)),
                nextContinuationToken = "test-next-page",
                isTruncated = true,
            ),
        )
    }

    override suspend fun exactLookup(query: DictionaryQuery): DictionarySearchResult = search(query)

    override suspend fun checkAvailability(): ProviderAvailability = ProviderAvailability.Available

    private fun FakeProviderEntryDto.toDomain(query: DictionaryQuery) = ExternalDictionaryEntry(
        providerId = descriptor.id,
        sourceEntryId = id,
        headword = word,
        sourceLanguage = query.languagePair.sourceLanguage,
        headwordScriptCode = "Hani",
        linguisticFeatures = DictionaryLinguisticFeatures(
            reading = DictionaryReading("じしょ", "Hira"),
            pronunciations = listOf(
                DictionaryPronunciation(
                    text = "dʑiɕo",
                    audio = DictionaryAudio("https://example.invalid/audio", "audio/mpeg"),
                ),
            ),
            transliterations = listOf(DictionaryTransliteration("jisho", "Latn", "Hepburn")),
            inflections = listOf(DictionaryInflection("dictionaries", "plural")),
            etymology = "test etymology",
        ),
        senses = listOf(
            ExternalDictionarySense(
                meanings = listOf(
                    DictionaryMeaning(
                        text = definition,
                        language = query.languagePair.resultLanguage,
                        kind = query.languagePair.resultKind,
                    ),
                ),
                partOfSpeech = "noun",
                grammaticalGender = "test gender",
                examples = listOf(
                    DictionaryExample(
                        text = "test example",
                        language = query.languagePair.sourceLanguage,
                    ),
                ),
            ),
        ),
        attribution = descriptor.attribution,
    )

    private data class FakeProviderEntryDto(
        val id: String,
        val word: String,
        val definition: String,
    )

    companion object {
        const val SUPPORTED_QUERY = "辞書"
    }
}
