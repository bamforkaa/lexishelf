#!/usr/bin/env python3
"""Audit learner-facing usage/grammar label coverage in pinned Kaikki data."""

from __future__ import annotations

import argparse
import gzip
import json
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import BinaryIO, Iterable, Mapping

from tools.dataset_paths import dataset_paths, dataset_root_summary
from tools.kaikki_language_config import (
    KAIKKI_SOURCE_FILE,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)
from tools.kaikki_usage_label_policy import (
    LABEL_CATEGORY_BY_CODE,
    normalize_sense_labels,
    unknown_sense_tags,
)

_SAMPLE_LIMIT = 3


@dataclass
class MutableLanguageAudit:
    language: str
    raw_entry_count: int = 0
    indexed_entry_count: int = 0
    sense_count: int = 0
    labeled_sense_count: int = 0
    label_counts: Counter[str] = field(default_factory=Counter)
    label_pos_counts: dict[str, Counter[str]] = field(
        default_factory=lambda: defaultdict(Counter)
    )
    raw_sense_tags: Counter[str] = field(default_factory=Counter)
    unknown_sense_tags: Counter[str] = field(default_factory=Counter)
    source_field_counts: Counter[str] = field(default_factory=Counter)


def audit_stream(
    source: Path,
    selected_languages: Iterable[str],
    audits: dict[str, MutableLanguageAudit],
    samples: dict[str, list[dict[str, object]]],
    samples_by_language: dict[str, dict[str, list[dict[str, object]]]],
) -> None:
    selected = set(selected_languages)
    with _open_jsonl(source) as stream:
        for raw_line in stream:
            entry = json.loads(raw_line)
            language = entry.get("lang_code")
            if language not in selected:
                continue
            audit = audits[language]
            audit.raw_entry_count += 1
            _count_source_fields(entry, audit.source_field_counts, "entry")
            raw_senses = entry.get("senses")
            if not isinstance(raw_senses, list):
                continue
            indexed_senses = []
            for order, sense in enumerate(raw_senses):
                if not isinstance(sense, dict):
                    continue
                glosses = _strings(sense.get("glosses"))
                if not glosses:
                    continue
                indexed_senses.append((order, sense, glosses))
            if not indexed_senses:
                continue
            audit.indexed_entry_count += 1
            pos = _text(entry.get("pos")) or "(none)"
            for order, sense, glosses in indexed_senses:
                audit.sense_count += 1
                _count_source_fields(sense, audit.source_field_counts, "sense")
                tags = _strings(sense.get("tags"))
                audit.raw_sense_tags.update(tags)
                audit.unknown_sense_tags.update(unknown_sense_tags(tags))
                labels = normalize_sense_labels(tags)
                if not labels:
                    continue
                audit.labeled_sense_count += 1
                audit.label_counts.update(labels)
                for label in labels:
                    audit.label_pos_counts[label][pos] += 1
                    if len(samples[label]) < _SAMPLE_LIMIT:
                        samples[label].append(
                            _sample(language, entry, pos, order, glosses, tags, labels)
                        )
                    language_samples = samples_by_language[language].setdefault(label, [])
                    if len(language_samples) < _SAMPLE_LIMIT:
                        language_samples.append(
                            _sample(language, entry, pos, order, glosses, tags, labels)
                        )


def create_report(
    filtered_directory: Path,
    languages: Iterable[str],
    english_source: Path | None = None,
) -> dict[str, object]:
    selected = tuple(dict.fromkeys(languages))
    audits = {language: MutableLanguageAudit(language) for language in selected}
    samples: dict[str, list[dict[str, object]]] = defaultdict(list)
    samples_by_language: dict[str, dict[str, list[dict[str, object]]]] = defaultdict(dict)
    for language in selected:
        source = filtered_directory / f"{language}.jsonl.gz"
        if not source.is_file():
            raise FileNotFoundError(f"Filtered Kaikki source is missing: {source}")
        audit_stream(source, (language,), audits, samples, samples_by_language)
    if english_source is not None:
        audits["en"] = MutableLanguageAudit("en")
        audit_stream(english_source, ("en",), audits, samples, samples_by_language)

    language_reports = []
    global_raw_tags: Counter[str] = Counter()
    global_unknown_tags: Counter[str] = Counter()
    for language, audit in audits.items():
        global_raw_tags.update(audit.raw_sense_tags)
        global_unknown_tags.update(audit.unknown_sense_tags)
        coverage = (
            audit.labeled_sense_count / audit.sense_count * 100
            if audit.sense_count
            else 0.0
        )
        language_reports.append(
            {
                "language": language,
                "rawEntryCount": audit.raw_entry_count,
                "indexedEntryCount": audit.indexed_entry_count,
                "senseCount": audit.sense_count,
                "labeledSenseCount": audit.labeled_sense_count,
                "coveragePercent": round(coverage, 4),
                "normalizedLabels": dict(audit.label_counts.most_common()),
                "labelPartOfSpeech": {
                    label: dict(counter.most_common())
                    for label, counter in audit.label_pos_counts.items()
                },
                "topRawSenseTags": audit.raw_sense_tags.most_common(50),
                "topUnknownSenseTags": audit.unknown_sense_tags.most_common(50),
                "sourceFieldCounts": dict(audit.source_field_counts),
            }
        )
    return {
        "taxonomy": LABEL_CATEGORY_BY_CODE,
        "languages": language_reports,
        "globalTopRawSenseTags": global_raw_tags.most_common(50),
        "globalTopRejectedOrDeferredSenseTags": global_unknown_tags.most_common(50),
        "samples": {label: values for label, values in samples.items()},
        "samplesByLanguage": samples_by_language,
    }


def _sample(
    language: str,
    entry: Mapping[str, object],
    pos: str,
    order: int,
    glosses: list[str],
    tags: list[str],
    labels: tuple[str, ...],
) -> dict[str, object]:
    return {
        "language": language,
        "headword": _text(entry.get("word")),
        "partOfSpeech": pos,
        "senseOrder": order,
        "gloss": glosses[0],
        "rawTags": tags,
        "normalizedLabels": list(labels),
    }


def _count_source_fields(
    value: Mapping[str, object],
    counter: Counter[str],
    prefix: str,
) -> None:
    for field_name in ("tags", "raw_tags", "topics", "categories"):
        field_value = value.get(field_name)
        if isinstance(field_value, list) and field_value:
            counter[f"{prefix}.{field_name}"] += 1
    if prefix == "entry":
        forms = value.get("forms")
        if isinstance(forms, list) and any(
            isinstance(form, dict) and _strings(form.get("tags")) for form in forms
        ):
            counter["form.tags"] += 1


def _open_jsonl(path: Path) -> BinaryIO:
    return gzip.open(path, "rb") if path.suffix.lower() == ".gz" else path.open("rb")


def _strings(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [item.strip() for item in value if isinstance(item, str) and item.strip()]


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def parse_args() -> argparse.Namespace:
    defaults = dataset_paths("kaikki")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--filtered-directory",
        type=Path,
        default=defaults.source / "filtered",
    )
    parser.add_argument(
        "--languages",
        nargs="+",
        default=KAIKKI_SUPPORTED_LANGUAGE_TAGS,
        choices=KAIKKI_SUPPORTED_LANGUAGE_TAGS,
    )
    parser.add_argument("--include-english", action="store_true")
    parser.add_argument(
        "--english-source",
        type=Path,
        default=defaults.source / KAIKKI_SOURCE_FILE,
    )
    parser.add_argument("--output", type=Path, required=True)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    print(dataset_root_summary(dataset_paths("kaikki").root))
    report = create_report(
        args.filtered_directory,
        args.languages,
        args.english_source if args.include_english else None,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Usage-label audit: {args.output}")
    for language in report["languages"]:
        print(
            f"{language['language']}: {language['labeledSenseCount']}/"
            f"{language['senseCount']} senses ({language['coveragePercent']}%)"
        )


if __name__ == "__main__":
    main()
