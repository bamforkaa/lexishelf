#!/usr/bin/env python3
"""Inspect raw Kaikki form metadata without modifying generated artifacts."""

from __future__ import annotations

import argparse
import gzip
import json
import sys
import time
from collections import Counter
from pathlib import Path
from typing import Iterable

from tools.dataset_paths import dataset_paths, dataset_root_summary
from tools.kaikki_language_config import KAIKKI_SUPPORTED_LANGUAGE_TAGS
from tools.kaikki_form_policy import select_display_forms


def _percentile(values: list[int], percentile: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[min(int((len(ordered) - 1) * percentile), len(ordered) - 1)]


def _sample(entry: dict[str, object], form_count: int) -> dict[str, object]:
    forms = entry.get("forms")
    return {
        "word": entry.get("word"),
        "pos": entry.get("pos"),
        "form_count": form_count,
        "forms": forms[:8] if isinstance(forms, list) else [],
    }


def inspect_language(
    source: Path,
    language: str,
    top_count: int = 30,
    include_samples: bool = False,
) -> dict[str, object]:
    form_counts: list[int] = []
    selected_counts: list[int] = []
    display_selection_nanoseconds = 0
    form_keys: Counter[str] = Counter()
    tag_sets: Counter[tuple[str, ...]] = Counter()
    tags: Counter[str] = Counter()
    sense_relation_keys: Counter[str] = Counter()
    samples: dict[str, dict[str, object]] = {}
    entries = 0
    entries_with_forms = 0
    with gzip.open(source, "rt", encoding="utf-8") as stream:
        for line in stream:
            entry = json.loads(line)
            entries += 1
            senses = entry.get("senses")
            if isinstance(senses, list):
                for sense in senses:
                    if not isinstance(sense, dict):
                        continue
                    for key in ("form_of", "alt_of", "compound_of", "derived"):
                        value = sense.get(key)
                        if isinstance(value, list) and value:
                            sense_relation_keys[key] += 1
            raw_forms = entry.get("forms")
            if not isinstance(raw_forms, list):
                continue
            valid_forms = [form for form in raw_forms if isinstance(form, dict)]
            count = len(valid_forms)
            if count == 0:
                continue
            entries_with_forms += 1
            form_counts.append(count)
            pos = str(entry.get("pos") or "unknown")
            selection_started = time.perf_counter_ns()
            selected_counts.append(
                len(select_display_forms(language, pos, str(entry.get("word") or ""), raw_forms))
            )
            display_selection_nanoseconds += time.perf_counter_ns() - selection_started
            samples.setdefault(pos, _sample(entry, count))
            if count > 20:
                samples.setdefault("over_20", _sample(entry, count))
            if count > 100:
                samples.setdefault("over_100", _sample(entry, count))
            if count > 500:
                samples.setdefault("over_500", _sample(entry, count))
            if count == max(form_counts):
                samples["max"] = _sample(entry, count)
            for form in valid_forms:
                form_keys.update(str(key) for key in form)
                raw_tags = form.get("tags")
                normalized_tags = tuple(
                    str(tag).strip()
                    for tag in raw_tags
                    if isinstance(tag, str) and tag.strip()
                ) if isinstance(raw_tags, list) else ()
                tag_sets[normalized_tags] += 1
                tags.update(normalized_tags)
    result = {
        "source": str(source),
        "entries": entries,
        "entries_with_forms": entries_with_forms,
        "form_cardinality": {
            "average_present": sum(form_counts) / len(form_counts) if form_counts else 0.0,
            "p50": _percentile(form_counts, 0.50),
            "p95": _percentile(form_counts, 0.95),
            "p99": _percentile(form_counts, 0.99),
            "max": max(form_counts, default=0),
        },
        "selected_display_cardinality": {
            "average_present": sum(selected_counts) / len(selected_counts)
            if selected_counts else 0.0,
            "p50": _percentile(selected_counts, 0.50),
            "p95": _percentile(selected_counts, 0.95),
            "max": max(selected_counts, default=0),
        },
        "display_selection_time_ms": display_selection_nanoseconds / 1_000_000,
        "display_selection_microseconds_per_form_entry": (
            display_selection_nanoseconds / 1_000 / entries_with_forms
            if entries_with_forms else 0.0
        ),
        "form_object_keys": form_keys.most_common(),
        "top_tags": tags.most_common(top_count),
        "top_tag_sets": [
            {"tags": list(tag_set), "count": count}
            for tag_set, count in tag_sets.most_common(top_count)
        ],
        "sense_relation_keys": dict(sense_relation_keys),
    }
    if include_samples:
        result["samples"] = samples
    return result


def report(
    languages: Iterable[str],
    top_count: int = 30,
    include_samples: bool = False,
) -> list[dict[str, object]]:
    paths = dataset_paths("kaikki")
    print(dataset_root_summary(paths.root))
    reports = []
    for language in languages:
        source = paths.source / "filtered" / f"{language}.jsonl.gz"
        if not source.is_file():
            raise FileNotFoundError(f"Filtered Kaikki source is missing: {source}")
        reports.append(
            {
                "language": language,
                **inspect_language(source, language, top_count, include_samples),
            }
        )
    return reports


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--languages",
        nargs="+",
        default=list(KAIKKI_SUPPORTED_LANGUAGE_TAGS),
    )
    parser.add_argument("--top", type=int, default=30)
    parser.add_argument("--include-samples", action="store_true")
    parser.add_argument("--output", type=Path)
    arguments = parser.parse_args()
    rendered = json.dumps(
        report(
            arguments.languages,
            max(arguments.top, 0),
            arguments.include_samples,
        ),
        ensure_ascii=False,
        indent=2,
    )
    if arguments.output:
        arguments.output.write_text(rendered, encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
