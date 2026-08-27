#!/usr/bin/env python3
"""Measure raw and semantically selected Kaikki forms from filtered sources."""

from __future__ import annotations

import argparse
import gzip
import json
import math
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

from tools.dataset_paths import DatasetPaths, dataset_paths, dataset_root_summary
from tools.kaikki_form_selection import normalize_kaikki_forms, select_kaikki_forms
from tools.kaikki_language_config import KAIKKI_SUPPORTED_LANGUAGE_TAGS


@dataclass(frozen=True)
class FormSample:
    lemma: str
    part_of_speech: str
    raw_count: int
    raw_preview: tuple[str, ...]
    selected: tuple[str, ...]


@dataclass(frozen=True)
class LanguageFormReport:
    language_tag: str
    indexed_entries: int
    entries_with_raw_forms: int
    raw_average: float
    raw_p95: int
    raw_max: int
    selected_average: float
    selected_p95: int
    selected_max: int
    selected_zero_entries: int
    selected_one_to_three_entries: int
    selected_four_to_eight_entries: int
    selected_nine_or_more_entries: int
    maximum_raw_sample: FormSample | None


def inspect_filtered_source(path: Path, language_tag: str) -> LanguageFormReport:
    indexed_entries = 0
    raw_counts: list[int] = []
    selected_counts: list[int] = []
    maximum_sample: FormSample | None = None
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        for line in stream:
            entry = json.loads(line)
            if not _is_indexable(entry):
                continue
            indexed_entries += 1
            lemma = _text(entry.get("word"))
            part_of_speech = _text(entry.get("pos"))
            selection = select_kaikki_forms(
                language_tag=language_tag,
                part_of_speech=part_of_speech,
                lemma=lemma,
                raw_forms=entry.get("forms"),
            )
            if selection.raw_form_count == 0:
                continue
            raw_counts.append(selection.raw_form_count)
            selected_counts.append(len(selection.forms))
            if maximum_sample is None or selection.raw_form_count > maximum_sample.raw_count:
                normalized = normalize_kaikki_forms(entry.get("forms"))
                maximum_sample = FormSample(
                    lemma=lemma,
                    part_of_speech=part_of_speech,
                    raw_count=selection.raw_form_count,
                    raw_preview=tuple(
                        f"{form.text} [{', '.join(sorted(form.tags))}]"
                        for form in normalized[:12]
                    ),
                    selected=tuple(
                        f"{form.label}: {form.text}" for form in selection.forms
                    ),
                )
    return LanguageFormReport(
        language_tag=language_tag,
        indexed_entries=indexed_entries,
        entries_with_raw_forms=len(raw_counts),
        raw_average=_average(raw_counts),
        raw_p95=_p95(raw_counts),
        raw_max=max(raw_counts, default=0),
        selected_average=_average(selected_counts),
        selected_p95=_p95(selected_counts),
        selected_max=max(selected_counts, default=0),
        selected_zero_entries=sum(count == 0 for count in selected_counts),
        selected_one_to_three_entries=sum(1 <= count <= 3 for count in selected_counts),
        selected_four_to_eight_entries=sum(4 <= count <= 8 for count in selected_counts),
        selected_nine_or_more_entries=sum(count >= 9 for count in selected_counts),
        maximum_raw_sample=maximum_sample,
    )


def build_report(root: Path, languages: Iterable[str]) -> list[LanguageFormReport]:
    source = DatasetPaths(root.resolve(), "kaikki").source / "filtered"
    reports: list[LanguageFormReport] = []
    for language_tag in languages:
        path = source / f"{language_tag}.jsonl.gz"
        if not path.is_file():
            raise FileNotFoundError(f"Filtered Kaikki source is missing: {path}")
        report = inspect_filtered_source(path, language_tag)
        reports.append(report)
        print(
            f"Inspected {language_tag}: {report.indexed_entries:,} entries, "
            f"raw max {report.raw_max:,}, selected max {report.selected_max}",
            flush=True,
        )
    return reports


def _is_indexable(entry: object) -> bool:
    if not isinstance(entry, dict) or not _text(entry.get("word")):
        return False
    senses = entry.get("senses")
    return isinstance(senses, list) and any(
        isinstance(sense, dict)
        and isinstance(sense.get("glosses"), list)
        and any(_text(gloss) for gloss in sense["glosses"])
        for sense in senses
    )


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _average(values: list[int]) -> float:
    return round(sum(values) / len(values), 4) if values else 0.0


def _p95(values: list[int]) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * 0.95) - 1)]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--languages", nargs="+", default=list(KAIKKI_SUPPORTED_LANGUAGE_TAGS))
    parser.add_argument("--root", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    arguments = parser.parse_args()
    paths = (
        DatasetPaths(arguments.root.resolve(), "kaikki")
        if arguments.root is not None
        else dataset_paths("kaikki")
    )
    print(dataset_root_summary(paths.root), flush=True)
    reports = build_report(paths.root, arguments.languages)
    rendered = json.dumps([asdict(report) for report in reports], ensure_ascii=False, indent=2)
    if arguments.output is None:
        print(rendered)
    else:
        arguments.output.parent.mkdir(parents=True, exist_ok=True)
        arguments.output.write_text(rendered + "\n", encoding="utf-8")
        print(f"Wrote {arguments.output}", flush=True)


if __name__ == "__main__":
    main()
