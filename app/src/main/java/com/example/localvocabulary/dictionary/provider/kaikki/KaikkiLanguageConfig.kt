package com.example.localvocabulary.dictionary.provider.kaikki

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag

internal val KAIKKI_SOURCE_LANGUAGE_TAG_VALUES = listOf(
    "de",
    "hi",
    "pl",
    "nl",
    "pt",
    "tr",
    "cs",
    "sv",
    "uk",
    "vi",
    "th",
    "id",
)

internal val KAIKKI_SOURCE_LANGUAGES =
    KAIKKI_SOURCE_LANGUAGE_TAG_VALUES.map(Bcp47LanguageTag::requireValid)

internal val KAIKKI_ENGLISH_LANGUAGE = Bcp47LanguageTag.requireValid("en")
