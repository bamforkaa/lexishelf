"""Select a small, deterministic set of study-worthy Kaikki word forms.

Kaikki ``forms`` mix principal parts with complete inflection tables,
romanizations, spelling variants, classifiers, and generated errors.  This
module is deliberately precision-first: a form is emitted only when a reviewed
language/POS policy maps its tags to a grammatical slot.
"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from difflib import SequenceMatcher
from typing import Iterable, Mapping


GLOBAL_FORM_SAFETY_CAP = 8

_WHITESPACE = re.compile(r"\s+")
_INTERNAL_TAGS = frozenset({"table-tags", "inflection-template", "class"})
_REJECTED_TAGS = frozenset(
    {
        "alternative",
        "also",
        "archaic",
        "abstract-noun",
        "augmentative",
        "canonical",
        "cjk",
        "classifier",
        "colloquial",
        "dated",
        "derived",
        "dialectal",
        "diminutive",
        "historical",
        "humorous",
        "hán-nôm",
        "informal",
        "ironic",
        "nonstandard",
        "obsolete",
        "rare",
        "romanization",
        "urdu",
    }
)


@dataclass(frozen=True)
class NormalizedKaikkiForm:
    text: str
    tags: frozenset[str]


@dataclass(frozen=True)
class SelectedKaikkiForm:
    text: str
    label: str
    semantic_keys: tuple[str, ...]


@dataclass(frozen=True)
class FormSelectionResult:
    raw_form_count: int
    forms: tuple[SelectedKaikkiForm, ...]


@dataclass(frozen=True)
class FormRule:
    required: frozenset[str]
    forbidden: frozenset[str] = frozenset()
    maximum_extra_tags: int | None = None

    def matches(self, tags: frozenset[str]) -> bool:
        extras = tags - self.required
        return (
            self.required.issubset(tags)
            and not self.forbidden.intersection(tags)
            and (self.maximum_extra_tags is None or len(extras) <= self.maximum_extra_tags)
        )


@dataclass(frozen=True)
class FormSlot:
    key: str
    label: str
    rules: tuple[FormRule, ...]


@dataclass(frozen=True)
class FormSelectionPolicy:
    slots_by_part_of_speech: Mapping[str, tuple[FormSlot, ...]]

    def slots(self, part_of_speech: str) -> tuple[FormSlot, ...]:
        return self.slots_by_part_of_speech.get(part_of_speech, ())


def select_kaikki_forms(
    language_tag: str,
    part_of_speech: str,
    lemma: str,
    raw_forms: object,
    safety_cap: int = GLOBAL_FORM_SAFETY_CAP,
) -> FormSelectionResult:
    """Normalize, semantically select, and deduplicate forms.

    ``raw_form_count`` intentionally describes the cleaned source cardinality,
    not the selected cardinality.  It remains useful as a diagnostic signal and
    is never used as the selection mechanism.
    """
    if safety_cap < 1:
        raise ValueError("Form safety cap must be positive")
    normalized = normalize_kaikki_forms(raw_forms)
    lemma_text = _normalize_text(lemma)
    eligible = tuple(
        form
        for form in normalized
        if form.text != lemma_text and not _is_rejected(form.tags)
    )
    policy = _POLICIES.get(language_tag, _CONSERVATIVE_POLICY)
    slots = policy.slots(part_of_speech.strip().lower())

    # A slot's semantic priority is explicit. Within a slot, prefer the least
    # qualified form; text length and Unicode text are deterministic tie-breakers
    # and do not turn source order or alphabetical order into the policy.
    selected_by_text: dict[str, tuple[list[str], list[str]]] = {}
    for slot in slots:
        candidate = _best_candidate(slot, eligible, lemma_text)
        if candidate is None:
            continue
        existing = selected_by_text.get(candidate.text)
        if existing is not None:
            labels, keys = existing
            if slot.label not in labels:
                labels.append(slot.label)
            if slot.key not in keys:
                keys.append(slot.key)
            continue
        if len(selected_by_text) == safety_cap:
            continue
        selected_by_text[candidate.text] = ([slot.label], [slot.key])

    selected = tuple(
        SelectedKaikkiForm(
            text=text,
            label=" · ".join(labels),
            semantic_keys=tuple(keys),
        )
        for text, (labels, keys) in selected_by_text.items()
    )
    return FormSelectionResult(raw_form_count=len(normalized), forms=selected)


def normalize_kaikki_forms(value: object) -> tuple[NormalizedKaikkiForm, ...]:
    if not isinstance(value, list):
        return ()
    result: list[NormalizedKaikkiForm] = []
    seen: set[tuple[str, frozenset[str]]] = set()
    for raw_form in value:
        if not isinstance(raw_form, dict):
            continue
        text = _normalize_text(raw_form.get("form"))
        tags = _normalize_tags(raw_form.get("tags"))
        if not text or text == "-" or _INTERNAL_TAGS.intersection(tags):
            continue
        key = (text, tags)
        if key in seen:
            continue
        seen.add(key)
        result.append(NormalizedKaikkiForm(text=text, tags=tags))
    return tuple(result)


def _best_candidate(
    slot: FormSlot,
    forms: Iterable[NormalizedKaikkiForm],
    lemma: str,
) -> NormalizedKaikkiForm | None:
    ranked: list[
        tuple[tuple[int, int, int, float, int, int, str, tuple[str, ...]], NormalizedKaikkiForm]
    ] = []
    lemma_words = set(_word_tokens(lemma))
    compact_lemma = "".join(_word_tokens(lemma))
    for form in forms:
        matching_rules = [
            (index, rule)
            for index, rule in enumerate(slot.rules)
            if rule.matches(form.tags)
        ]
        if not matching_rules:
            continue
        rule_index, rule = min(
            matching_rules,
            key=lambda item: (item[0], len(form.tags - item[1].required)),
        )
        extra_tag_count = len(form.tags - rule.required)
        form_words = _word_tokens(form.text)
        shared_word_count = len(lemma_words.intersection(form_words))
        compact_form = "".join(form_words)
        similarity = SequenceMatcher(None, compact_lemma, compact_form).ratio()
        rank = (
            rule_index,
            extra_tag_count,
            -shared_word_count,
            -similarity,
            len(form.tags),
            len(form.text),
            form.text.casefold(),
            tuple(sorted(form.tags)),
        )
        ranked.append((rank, form))
    return min(ranked, key=lambda item: item[0])[1] if ranked else None


def _is_rejected(tags: frozenset[str]) -> bool:
    return bool(_REJECTED_TAGS.intersection(tags)) or any(
        tag.startswith("error-") for tag in tags
    )


def _normalize_text(value: object) -> str:
    if not isinstance(value, str):
        return ""
    return _WHITESPACE.sub(" ", unicodedata.normalize("NFC", value).strip())


def _normalize_tags(value: object) -> frozenset[str]:
    if not isinstance(value, list):
        return frozenset()
    return frozenset(
        normalized
        for item in value
        if isinstance(item, str) and (normalized := _normalize_text(item).lower())
    )


def _word_tokens(value: str) -> tuple[str, ...]:
    return tuple(re.findall(r"\w+", value.casefold(), flags=re.UNICODE))


def _rule(
    *required: str,
    forbidden: Iterable[str] = (),
    maximum_extra_tags: int | None = None,
) -> FormRule:
    return FormRule(
        frozenset(required),
        frozenset(forbidden),
        maximum_extra_tags,
    )


def _slot(key: str, label: str, *rules: FormRule) -> FormSlot:
    return FormSlot(key=key, label=label, rules=rules)


_CASES = frozenset(
    {
        "accusative",
        "dative",
        "genitive",
        "instrumental",
        "locative",
        "nominative",
        "oblique",
        "vocative",
    }
)
_PERSONS = frozenset({"first-person", "second-person", "third-person"})

_BASIC_NOUN = (
    _slot("plural", "plural", _rule("plural", forbidden=_CASES)),
)
_BASIC_ADJECTIVE = (
    _slot("comparative", "comparative", _rule("comparative")),
    _slot("superlative", "superlative", _rule("superlative")),
)
_PAST_PARTICIPLE = _slot(
    "past-participle",
    "past participle",
    _rule("participle", "past"),
)

_GERMAN_POLICY = FormSelectionPolicy(
    {
        "noun": _BASIC_NOUN
        + (_slot("genitive-singular", "genitive singular", _rule("genitive", "singular")),),
        "verb": (
            _slot("present-3sg", "present 3sg", _rule("present", "singular", "third-person")),
            _slot("past", "past", _rule("past", forbidden={"participle"})),
            _PAST_PARTICIPLE,
        ),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_DUTCH_POLICY = FormSelectionPolicy(
    {
        "noun": _BASIC_NOUN,
        "verb": (
            _slot("present-3sg", "present 3sg", _rule("present", "singular", "third-person")),
            _slot(
                "past",
                "past",
                _rule("past", "singular", "main-clause", forbidden={"participle"}),
                _rule("past", "singular", forbidden={"participle"}),
            ),
            _PAST_PARTICIPLE,
        ),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_SWEDISH_POLICY = FormSelectionPolicy(
    {
        "noun": (
            _slot("plural", "plural", _rule("plural", "indefinite", "nominative")),
            _slot("definite-singular", "definite singular", _rule("definite", "singular", "nominative")),
            _slot("definite-plural", "definite plural", _rule("definite", "plural", "nominative")),
        ),
        "verb": (
            _slot("present", "present", _rule("present", forbidden={"passive", "subjunctive"})),
            _slot("preterite", "preterite", _rule("preterite"), _rule("past", forbidden={"passive", "subjunctive"})),
            _slot("supine", "supine", _rule("supine", forbidden={"passive"})),
        ),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_PORTUGUESE_POLICY = FormSelectionPolicy(
    {
        "noun": _BASIC_NOUN,
        "verb": (
            _slot("present-1sg", "present 1sg", _rule("present", "first-person", "singular")),
            _slot("preterite-1sg", "preterite 1sg", _rule("preterite", "first-person", "singular")),
            _PAST_PARTICIPLE,
        ),
        "adj": (
            _slot("feminine-singular", "feminine singular", _rule("feminine", "singular", forbidden=_CASES)),
        )
        + _BASIC_NOUN
        + _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_SLAVIC_POLICY = FormSelectionPolicy(
    {
        "noun": (
            _slot("nominative-plural", "nominative plural", _rule("nominative", "plural")),
            _slot("genitive-singular", "genitive singular", _rule("genitive", "singular")),
            _slot("genitive-plural", "genitive plural", _rule("genitive", "plural")),
        ),
        "verb": (
            _slot(
                "perfective-counterpart",
                "perfective counterpart",
                _rule("perfective", maximum_extra_tags=0),
            ),
            _slot(
                "imperfective-counterpart",
                "imperfective counterpart",
                _rule("imperfective", maximum_extra_tags=0),
            ),
            _slot("present-1sg", "present 1sg", _rule("present", "first-person", "singular")),
            _slot("present-3sg", "present 3sg", _rule("present", "third-person", "singular")),
            _slot("past-masculine", "past masculine singular", _rule("past", "masculine", "singular")),
        ),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_HINDI_POLICY = FormSelectionPolicy(
    {
        "noun": (
            _slot("direct-plural", "direct plural", _rule("direct", "plural")),
            _slot("oblique-singular", "oblique singular", _rule("oblique", "singular")),
            _slot("oblique-plural", "oblique plural", _rule("oblique", "plural")),
        ),
        "verb": (
            _slot("stem", "stem", _rule("stem")),
            _slot("habitual-masculine", "habitual masculine", _rule("habitual", "masculine", "singular")),
            _slot("habitual-feminine", "habitual feminine", _rule("habitual", "feminine", "singular")),
            _slot("perfective-masculine", "perfective masculine", _rule("perfective", "masculine", "singular")),
            _slot("perfective-feminine", "perfective feminine", _rule("perfective", "feminine", "singular")),
        ),
        "adj": (
            _slot("direct-masculine-plural", "masculine plural", _rule("direct", "masculine", "plural")),
            _slot("direct-feminine-singular", "feminine singular", _rule("direct", "feminine", "singular")),
            _slot("direct-feminine-plural", "feminine plural", _rule("direct", "feminine", "plural")),
        ),
    }
)

_TURKISH_POLICY = FormSelectionPolicy(
    {
        "noun": (
            _slot("plural", "plural", _rule("plural", forbidden=_CASES | _PERSONS)),
            _slot("definite-accusative", "definite accusative", _rule("definite", "accusative", forbidden={"possessive"})),
            _slot("dative", "dative", _rule("dative", forbidden={"possessive"})),
            _slot("locative", "locative", _rule("locative", forbidden={"possessive"})),
        ),
        # The reviewed source has shifted person labels in generated Turkish
        # tables. Only Wiktionary's compact principal present form is reliable
        # enough for this precision-first release.
        "verb": (
            _slot("present-3sg", "present 3sg", _rule("present", "singular", "third-person")),
        ),
    }
)

_INDONESIAN_POLICY = FormSelectionPolicy(
    {
        "noun": _BASIC_NOUN,
        "verb": (
            _slot("active", "active", _rule("active")),
            _slot("passive", "passive", _rule("passive")),
        ),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

# No reviewed inflection slots are emitted for Vietnamese or Thai. Their raw
# forms are dominated by script variants, romanization, classifiers, and
# derivations, not inflection paradigms.
_NO_INFLECTION_POLICY = FormSelectionPolicy({})

_CONSERVATIVE_POLICY = FormSelectionPolicy(
    {
        "noun": _BASIC_NOUN,
        "verb": (_PAST_PARTICIPLE,),
        "adj": _BASIC_ADJECTIVE,
        "adv": _BASIC_ADJECTIVE,
    }
)

_POLICIES: Mapping[str, FormSelectionPolicy] = {
    "de": _GERMAN_POLICY,
    "nl": _DUTCH_POLICY,
    "sv": _SWEDISH_POLICY,
    "pt": _PORTUGUESE_POLICY,
    "pl": _SLAVIC_POLICY,
    "cs": _SLAVIC_POLICY,
    "uk": _SLAVIC_POLICY,
    "hi": _HINDI_POLICY,
    "tr": _TURKISH_POLICY,
    "vi": _NO_INFLECTION_POLICY,
    "th": _NO_INFLECTION_POLICY,
    "id": _INDONESIAN_POLICY,
}
