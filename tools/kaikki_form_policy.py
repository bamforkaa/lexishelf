"""Reviewed Kaikki form policies for learner display and morphology lookup.

Display selection is intentionally small and semantic. Morphology lookup is a
separate, broader policy, but still accepts only source-attested grammatical
inflections. Neither policy guesses a lemma.
"""

from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata
from typing import Iterable, Mapping


DISPLAY_FORM_LIMIT = 8
MAX_MORPHOLOGY_FORM_LENGTH = 128

_WHITESPACE = re.compile(r"\s+")
_HAS_LETTER_OR_NUMBER = re.compile(r"\w", re.UNICODE)
_TECHNICAL_PSEUDO_FORMS = frozenset({"form only", "metaphonic"})
_INTERNAL_TAGS = frozenset(
    {
        "table-tags",
        "inflection-template",
        "class",
        "error-unknown-tag",
        "error-misspelling",
        "error-unrecognized-form",
    }
)
_NON_INFLECTION_TAGS = frozenset(
    {
        "alternative",
        "alternative-spelling",
        "archaic",
        "canonical",
        "classifier",
        "dated",
        "dialectal",
        "derived",
        "derivational",
        "han-nom",
        "historical",
        "misspelling",
        "nonstandard",
        "obsolete",
        "rare",
        "reconstructed",
        "reduplication",
        "romanization",
        "root",
        "transliteration",
    }
)


@dataclass(frozen=True)
class SelectedForm:
    form: str
    label: str


@dataclass(frozen=True)
class _DisplayRule:
    label: str
    required: frozenset[str]
    forbidden: frozenset[str] = frozenset()

    def matches(self, tags: frozenset[str]) -> bool:
        return self.required <= tags and not self.forbidden.intersection(tags)


def _rule(label: str, *required: str, forbidden: Iterable[str] = ()) -> _DisplayRule:
    return _DisplayRule(label, frozenset(required), frozenset(forbidden))


# Rules are deliberately conservative. A language/POS combination omitted here
# displays zero forms rather than exposing an ambiguous raw table.
_DISPLAY_RULES: dict[tuple[str, str], tuple[_DisplayRule, ...]] = {
    ("de", "noun"): (
        _rule("plural", "plural", forbidden=("genitive", "dative", "accusative")),
        _rule("genitive singular", "genitive", "singular"),
    ),
    ("de", "verb"): (
        _rule("present · 3rd singular", "present", "third-person", "singular"),
        _rule("past", "past", forbidden=("participle", "subjunctive")),
        _rule("past participle", "past", "participle"),
    ),
    ("de", "adj"): (
        _rule("comparative", "comparative"),
        _rule("superlative", "superlative"),
    ),
    ("nl", "noun"): (_rule("plural", "plural"),),
    ("nl", "verb"): (
        _rule("past", "past", forbidden=("participle",)),
        _rule("past participle", "past", "participle"),
    ),
    ("nl", "adj"): (
        _rule("comparative", "comparative"),
        _rule("superlative", "superlative"),
    ),
    ("sv", "noun"): (
        _rule("plural", "indefinite", "plural"),
        _rule("definite singular", "definite", "singular"),
        _rule("definite plural", "definite", "plural"),
    ),
    ("sv", "verb"): (
        _rule("past", "past", forbidden=("participle",)),
        _rule("supine", "supine"),
        _rule("past participle", "past", "participle"),
    ),
    ("sv", "adj"): (
        _rule("comparative", "comparative"),
        _rule("superlative", "superlative"),
    ),
    ("pt", "noun"): (_rule("plural", "plural"),),
    ("pt", "adj"): (
        _rule("feminine", "feminine", "singular"),
        _rule("plural", "masculine", "plural"),
        _rule("feminine plural", "feminine", "plural"),
        _rule("comparative", "comparative"),
        _rule("superlative", "superlative"),
    ),
    ("pt", "verb"): (
        _rule("present · 1st singular", "present", "first-person", "singular"),
        _rule("preterite · 3rd singular", "preterite", "third-person", "singular"),
        _rule("past participle", "past", "participle"),
        _rule("gerund", "gerund"),
    ),
    ("pl", "noun"): (
        _rule("nominative plural", "nominative", "plural"),
        _rule("genitive singular", "genitive", "singular"),
    ),
    ("cs", "noun"): (
        _rule("nominative plural", "nominative", "plural"),
        _rule("genitive singular", "genitive", "singular"),
    ),
    ("uk", "noun"): (
        _rule("nominative plural", "nominative", "plural"),
        _rule("genitive singular", "genitive", "singular"),
    ),
    ("pl", "verb"): (
        _rule("present · 3rd singular", "present", "third-person", "singular"),
        _rule("past · 3rd singular", "past", "third-person", "singular"),
        _rule("imperative", "imperative", forbidden=("plural",)),
    ),
    ("cs", "verb"): (
        _rule("present · 3rd singular", "present", "third-person", "singular"),
        _rule("past · 3rd singular", "past", "third-person", "singular"),
        _rule("imperative", "imperative", forbidden=("plural",)),
    ),
    ("uk", "verb"): (
        _rule("present · 3rd singular", "present", "third-person", "singular"),
        _rule("past · masculine", "past", "masculine", "singular"),
        _rule("imperative", "imperative", forbidden=("plural",)),
    ),
    ("pl", "adj"): (_rule("comparative", "comparative"), _rule("superlative", "superlative")),
    ("cs", "adj"): (_rule("comparative", "comparative"), _rule("superlative", "superlative")),
    ("uk", "adj"): (_rule("comparative", "comparative"), _rule("superlative", "superlative")),
    ("hi", "noun"): (
        _rule("direct plural", "direct", "plural"),
        _rule("oblique singular", "oblique", "singular"),
        _rule("oblique plural", "oblique", "plural"),
    ),
    ("tr", "noun"): (_rule("plural", "plural", forbidden=("possessive",)),),
    # The reviewed 2026-08-05 Turkish conjugation rows contain person/number tags
    # that do not agree with their surface text (for example, ``gittim`` is tagged
    # third-person). Keep those attested surfaces searchable, but do not present a
    # learner-facing grammatical label that the source metadata cannot support.
    ("id", "noun"): (_rule("plural", "plural"),),
    ("id", "verb"): (_rule("active", "active"), _rule("passive", "passive")),
    ("id", "adj"): (_rule("comparative", "comparative"), _rule("superlative", "superlative")),
    ("en", "noun"): (_rule("plural", "plural"),),
    ("en", "verb"): (
        _rule("present · 3rd singular", "present", "third-person", "singular"),
        _rule("past", "past", forbidden=("participle",)),
        _rule("past participle", "past", "participle"),
        _rule("present participle", "present", "participle"),
    ),
    ("en", "adj"): (_rule("comparative", "comparative"), _rule("superlative", "superlative")),
}

_INFLECTION_MARKERS = frozenset(
    {
        "ablative", "accusative", "active", "adverbial", "aorist", "comparative",
        "conditional", "continuative", "dative", "definite", "direct", "dual",
        "feminine", "first-person", "future", "genitive", "gerund", "imperative",
        "imperfect", "indefinite", "indicative", "instrumental", "locative",
        "masculine", "nominative", "oblique", "passive", "past", "participle",
        "perfect", "plural", "present", "preterite", "second-person", "singular",
        "subjunctive", "superlative", "supine", "third-person", "vocative",
    }
)
_MORPHOLOGY_POS = frozenset({"adj", "adjective", "adv", "adverb", "noun", "proper-noun", "verb"})
_MORPHOLOGY_LANGUAGES = frozenset({"de", "hi", "pl", "nl", "pt", "tr", "cs", "sv", "uk", "id", "en"})
_ENGLISH_NONLEXICAL_LEMMA_TAGS = frozenset(
    {"abbreviation", "acronym", "initialism", "letter", "punctuation", "symbol"}
)
_ENGLISH_NONCANONICAL_LEMMA_TAGS = frozenset(
    {
        "alt-of", "archaic", "dated", "dialectal", "form-of", "historical",
        "misspelling", "nonstandard", "obsolete", "rare",
    }
)
_TURKISH_EXCLUDED = frozenset(
    {
        "causative", "conditional", "evidential", "inferential", "necessitative",
        "negative", "potential", "possessive", "reciprocal", "reflexive",
    }
)
_NOMINAL_CASE_TAGS = frozenset(
    {"ablative", "accusative", "dative", "genitive", "instrumental", "locative", "vocative"}
)
_GERMAN_ADJECTIVE_DECLENSION_TAGS = _NOMINAL_CASE_TAGS | frozenset(
    {
        "feminine", "masculine", "neuter", "plural", "singular", "strong", "weak",
        "mixed", "attributive",
    }
)
_TURKISH_VERB_EXCLUDED = frozenset(
    {"future", "gerund", "participle", "perfect", "subjunctive"}
)


def select_display_forms(
    language_tag: str,
    part_of_speech: str,
    lemma: str,
    raw_forms: object,
) -> list[SelectedForm]:
    rules = _DISPLAY_RULES.get((language_tag, part_of_speech.lower()), ())
    if not rules or not isinstance(raw_forms, list):
        return []
    candidates = _valid_candidates(language_tag, lemma, raw_forms)
    selected: list[SelectedForm] = []
    seen: set[tuple[str, str]] = set()
    for rule in rules:
        for form, tags, _ in candidates:
            if not rule.matches(tags):
                continue
            key = (normalize_key(form, language_tag), rule.label)
            if key not in seen:
                seen.add(key)
                selected.append(SelectedForm(form, rule.label))
                break
        if len(selected) == DISPLAY_FORM_LIMIT:
            break
    return selected


def select_morphology_forms(
    language_tag: str,
    part_of_speech: str,
    lemma: str,
    raw_forms: object,
    *,
    raw_senses: object = None,
) -> list[str]:
    """Return source-attested inflected surfaces, never guessed derivations."""
    if (
        language_tag not in _MORPHOLOGY_LANGUAGES
        or part_of_speech.lower() not in _MORPHOLOGY_POS
        or not isinstance(raw_forms, list)
        or morphology_lemma_rejection_reason(language_tag, raw_senses) is not None
    ):
        return []
    result: list[str] = []
    seen: set[str] = set()
    for form, tags, _ in _valid_candidates(language_tag, lemma, raw_forms):
        if not tags.intersection(_INFLECTION_MARKERS):
            continue
        if tags.intersection(_NON_INFLECTION_TAGS | _INTERNAL_TAGS):
            continue
        if language_tag == "tr" and tags.intersection(_TURKISH_EXCLUDED):
            continue
        if not _accept_language_morphology(language_tag, part_of_speech.lower(), tags):
            continue
        key = normalize_key(form, language_tag)
        if key in seen:
            continue
        seen.add(key)
        result.append(form)
    return result


def morphology_lemma_rejection_reason(
    language_tag: str,
    raw_senses: object,
) -> str | None:
    """Classify reviewed English entries whose every sense is explicitly low-value.

    Other languages keep their existing policy until their sense metadata has been audited.
    A mixed entry remains eligible so a useful lexical sense is never removed with an alias.
    """
    if language_tag != "en" or not isinstance(raw_senses, list):
        return None
    senses = [sense for sense in raw_senses if isinstance(sense, Mapping)]
    if not senses:
        return None
    sense_tags = [_sense_tags(sense) for sense in senses]
    if all(tags.intersection(_ENGLISH_NONLEXICAL_LEMMA_TAGS) for tags in sense_tags):
        return "nonlexical"
    blocked = _ENGLISH_NONLEXICAL_LEMMA_TAGS | _ENGLISH_NONCANONICAL_LEMMA_TAGS
    if all(tags.intersection(blocked) for tags in sense_tags):
        return "noncanonical"
    return None


def _sense_tags(raw_sense: Mapping[object, object]) -> frozenset[str]:
    raw_tags = raw_sense.get("tags")
    tags = {
        tag.strip().lower()
        for tag in raw_tags
        if isinstance(tag, str) and tag.strip()
    } if isinstance(raw_tags, list) else set()
    if raw_sense.get("alt_of"):
        tags.add("alt-of")
    if raw_sense.get("form_of"):
        tags.add("form-of")
    return frozenset(tags)


def _accept_language_morphology(
    language_tag: str,
    part_of_speech: str,
    tags: frozenset[str],
) -> bool:
    if language_tag == "de" and part_of_speech in {"adj", "adjective"}:
        return (
            bool(tags.intersection({"comparative", "superlative"}))
            and not tags.intersection(_GERMAN_ADJECTIVE_DECLENSION_TAGS)
        )
    if language_tag != "tr":
        return True
    if part_of_speech in {"noun", "proper-noun"}:
        return "plural" in tags and not tags.intersection(_NOMINAL_CASE_TAGS)
    if part_of_speech == "verb":
        if tags.intersection(_TURKISH_VERB_EXCLUDED):
            return False
        return (
            "imperative" in tags
            or "aorist" in tags
            or ("past" in tags and "participle" not in tags)
            or ({"present", "continuative"} <= tags)
        )
    return bool(tags.intersection({"comparative", "superlative"}))


def normalize_key(value: str, language_tag: str | None = None) -> str:
    normalized = _WHITESPACE.sub(" ", unicodedata.normalize("NFC", value).strip())
    if language_tag and language_tag.split("-", 1)[0].lower() == "tr":
        normalized = normalized.translate(str.maketrans({"I": "ı", "İ": "i"}))
    return normalized.lower()


def _valid_candidates(
    language_tag: str,
    lemma: str,
    raw_forms: list[object],
) -> list[tuple[str, frozenset[str], int]]:
    lemma_key = normalize_key(lemma, language_tag)
    candidates: list[tuple[str, frozenset[str], int]] = []
    seen: set[tuple[str, frozenset[str]]] = set()
    for order, raw_form in enumerate(raw_forms):
        if not isinstance(raw_form, Mapping):
            continue
        form_value = raw_form.get("form")
        if not isinstance(form_value, str):
            continue
        form = _WHITESPACE.sub(" ", unicodedata.normalize("NFC", form_value).strip())
        raw_tags = raw_form.get("tags")
        tags = frozenset(
            tag.strip().lower()
            for tag in raw_tags
            if isinstance(tag, str) and tag.strip()
        ) if isinstance(raw_tags, list) else frozenset()
        if (
            not form
            or form == "-"
            or len(form) > MAX_MORPHOLOGY_FORM_LENGTH
            or not _HAS_LETTER_OR_NUMBER.search(form)
            or form.startswith("*")
            or normalize_key(form, language_tag) in _TECHNICAL_PSEUDO_FORMS
            or normalize_key(form, language_tag) == lemma_key
            or tags.intersection(_INTERNAL_TAGS)
        ):
            continue
        key = (normalize_key(form, language_tag), tags)
        if key in seen:
            continue
        seen.add(key)
        candidates.append((form, tags, order))
    return candidates
