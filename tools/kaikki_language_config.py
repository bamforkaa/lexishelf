"""Reviewed Kaikki language scope shared by conversion and pack building."""

from __future__ import annotations

KAIKKI_DATASET_RELEASE_ID = "enwiktionary-2026-08-05"
KAIKKI_EXTRACTION_DATE = "2026-08-23"
KAIKKI_SOURCE_FILE = (
    "raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz"
)
KAIKKI_SOURCE_URL = "https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz"
KAIKKI_SOURCE_SHA256 = "e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65"
KAIKKI_INDEX_SCHEMA_VERSION = 3

# This broader set is measured before selecting the production batch. Keep it
# explicit so an analysis run never silently grows when Kaikki adds languages.
KAIKKI_CANDIDATE_LANGUAGE_TAGS = (
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
    "fr",
    "es",
    "ru",
    "ar",
    "vi",
    "th",
    "id",
)

# Initial production batch selected from measured 2026-08-05 coverage and
# per-language pack sizes. Candidate analysis remains broader and explicit.
KAIKKI_SUPPORTED_LANGUAGE_TAGS = (
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

# English is intentionally morphology-only. It resolves source-attested forms
# such as ``went -> go`` without shipping the much larger full English index.
KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS = ("en",)


def kaikki_language_pairs(
    languages: tuple[str, ...] = KAIKKI_SUPPORTED_LANGUAGE_TAGS,
) -> tuple[tuple[str, str, str], ...]:
    return tuple((language, "en", "TRANSLATION") for language in languages)
