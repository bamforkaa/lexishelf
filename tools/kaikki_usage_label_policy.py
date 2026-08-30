"""Reviewed learner-facing Kaikki sense-label whitelist.

Only normalized Wiktextract ``sense.tags`` values listed here may cross the
converter boundary. Raw tags, categories, topics, and form tags intentionally
remain outside this policy.
"""

from __future__ import annotations

from collections.abc import Iterable


LABEL_CATEGORY_BY_CODE = {
    "formal": "register",
    "informal": "register",
    "colloquial": "register",
    "slang": "register",
    "vulgar": "register",
    "offensive": "register",
    "derogatory": "register",
    "literary": "register",
    "archaic": "temporal",
    "obsolete": "temporal",
    "dated": "temporal",
    "rare": "temporal",
    "transitive": "grammar",
    "intransitive": "grammar",
    "countable": "grammar",
    "uncountable": "grammar",
    "auxiliary": "grammar",
    "impersonal": "grammar",
    "regional": "region",
    "dialectal": "region",
}

_LABEL_ORDER = tuple(LABEL_CATEGORY_BY_CODE)
_LABEL_RANK = {code: index for index, code in enumerate(_LABEL_ORDER)}


def normalize_sense_labels(raw_tags: object) -> tuple[str, ...]:
    """Return deduplicated known labels in deterministic learner-facing order."""
    if not isinstance(raw_tags, list):
        return ()
    selected = {
        tag
        for tag in raw_tags
        if isinstance(tag, str) and tag in LABEL_CATEGORY_BY_CODE
    }
    return tuple(sorted(selected, key=_LABEL_RANK.__getitem__))


def unknown_sense_tags(raw_tags: object) -> Iterable[str]:
    if not isinstance(raw_tags, list):
        return ()
    return (
        tag
        for tag in raw_tags
        if isinstance(tag, str) and tag not in LABEL_CATEGORY_BY_CODE
    )
