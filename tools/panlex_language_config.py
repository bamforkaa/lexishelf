"""Reviewed PanLex varieties included in the Korean fallback index."""

from __future__ import annotations


PANLEX_KOREAN_LANGUAGE_TAG = "ko"
PANLEX_KOREAN_VARIETY_UID = "kor-000"
PANLEX_PINNED_RELEASE_ID = "2019-09-01"
PANLEX_PINNED_ARCHIVE_SHA256 = (
    "e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be"
)

# This is the canonical build-time language list. Keep one explicitly reviewed
# PanLex variety per BCP 47 language tag; never infer a macrolanguage variety.
PANLEX_FOREIGN_LANGUAGE_VARIETIES = (
    ("de", "deu-000"),
    ("hi", "hin-000"),
    ("pl", "pol-000"),
    ("la", "lat-000"),
    ("nl", "nld-000"),
    ("pt", "por-000"),
    ("it", "ita-000"),
    ("tr", "tur-000"),
    ("cs", "ces-000"),
    ("sv", "swe-000"),
    ("fi", "fin-000"),
    ("uk", "ukr-000"),
)

PANLEX_FOREIGN_LANGUAGE_TAGS = tuple(
    language_tag for language_tag, _ in PANLEX_FOREIGN_LANGUAGE_VARIETIES
)
PANLEX_BCP47_TO_VARIETY_UID = {
    PANLEX_KOREAN_LANGUAGE_TAG: PANLEX_KOREAN_VARIETY_UID,
    **dict(PANLEX_FOREIGN_LANGUAGE_VARIETIES),
}


def panlex_language_pairs() -> tuple[tuple[str, str, str], ...]:
    """Return the exact bidirectional pairs exposed by the generated index."""
    return tuple(
        pair
        for language_tag in PANLEX_FOREIGN_LANGUAGE_TAGS
        for pair in (
            (language_tag, PANLEX_KOREAN_LANGUAGE_TAG, "TRANSLATION"),
            (PANLEX_KOREAN_LANGUAGE_TAG, language_tag, "TRANSLATION"),
        )
    )
