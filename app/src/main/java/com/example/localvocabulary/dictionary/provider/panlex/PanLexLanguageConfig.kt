package com.example.localvocabulary.dictionary.provider.panlex

import com.example.localvocabulary.dictionary.domain.Bcp47LanguageTag

/** Runtime mirror of the reviewed varieties in tools/panlex_language_config.py. */
internal val PANLEX_FOREIGN_LANGUAGE_TAG_VALUES = listOf(
    "de",
    "hi",
    "pl",
    "la",
    "nl",
    "pt",
    "it",
    "tr",
    "cs",
    "sv",
    "fi",
    "uk",
)

internal val PANLEX_FOREIGN_LANGUAGES =
    PANLEX_FOREIGN_LANGUAGE_TAG_VALUES.map(Bcp47LanguageTag::requireValid)

internal val PANLEX_FOREIGN_LANGUAGE_TAGS_METADATA =
    PANLEX_FOREIGN_LANGUAGE_TAG_VALUES.joinToString(",")
