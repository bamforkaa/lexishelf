#!/usr/bin/env python3
"""Report metadata retained by the generated Kaikki exact-lookup indexes."""

from __future__ import annotations

import argparse
import json
import math
import sqlite3
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

from tools.dataset_paths import DatasetPaths, dataset_paths, dataset_root_summary
from tools.kaikki_language_config import KAIKKI_SUPPORTED_LANGUAGE_TAGS


@dataclass(frozen=True)
class FieldCounts:
    present_entries: int
    value_count: int
    average_per_entry: float
    p95_per_present_entry: int
    max_per_entry: int


@dataclass(frozen=True)
class LanguageMetadataReport:
    language_tag: str
    entry_count: int
    ipa: FieldCounts
    other_textual_pronunciation: FieldCounts
    gender: FieldCounts
    retained_forms: FieldCounts
    available_forms: FieldCounts
    retained_examples: FieldCounts
    available_examples: FieldCounts
    entries_with_labeled_forms: int


def _field_counts(counts: list[int], entry_count: int) -> FieldCounts:
    present = sorted(count for count in counts if count > 0)
    percentile_index = max(0, math.ceil(len(present) * 0.95) - 1)
    return FieldCounts(
        present_entries=len(present),
        value_count=sum(counts),
        average_per_entry=(sum(counts) / entry_count) if entry_count else 0.0,
        p95_per_present_entry=present[percentile_index] if present else 0,
        max_per_entry=max(counts, default=0),
    )


def inspect_database(path: Path, language_tag: str) -> LanguageMetadataReport:
    uri = f"file:{path.as_posix()}?mode=ro"
    connection = sqlite3.connect(uri, uri=True)
    ipa_counts: list[int] = []
    other_pronunciation_counts: list[int] = []
    gender_counts: list[int] = []
    retained_form_counts: list[int] = []
    available_form_counts: list[int] = []
    retained_example_counts: list[int] = []
    available_example_counts: list[int] = []
    entries_with_labeled_forms = 0
    try:
        for (raw_payload,) in connection.execute(
            "SELECT payload FROM entries ORDER BY entry_order"
        ):
            payload = json.loads(raw_payload)
            pronunciations = payload.get("n") or []
            # The current converter prefixes enPR while IPA is stored verbatim.
            # zh-pron is not applicable to the reviewed twelve-language batch.
            other_count = sum(
                1 for value in pronunciations if str(value).startswith("enPR: ")
            )
            other_pronunciation_counts.append(other_count)
            ipa_counts.append(len(pronunciations) - other_count)

            forms = payload.get("f") or []
            retained_form_counts.append(len(forms))
            available_form_counts.append(int(payload.get("fc") or 0))
            entries_with_labeled_forms += int(
                any(str(form.get("l") or "").strip() for form in forms)
            )

            senses = payload.get("s") or []
            gender_counts.append(
                sum(1 for sense in senses if str(sense.get("d") or "").strip())
            )
            retained_example_counts.append(
                sum(len(sense.get("e") or []) for sense in senses)
            )
            available_example_counts.append(
                sum(int(sense.get("x") or 0) for sense in senses)
            )
    finally:
        connection.close()

    entry_count = len(ipa_counts)
    return LanguageMetadataReport(
        language_tag=language_tag,
        entry_count=entry_count,
        ipa=_field_counts(ipa_counts, entry_count),
        other_textual_pronunciation=_field_counts(
            other_pronunciation_counts, entry_count
        ),
        gender=_field_counts(gender_counts, entry_count),
        retained_forms=_field_counts(retained_form_counts, entry_count),
        available_forms=_field_counts(available_form_counts, entry_count),
        retained_examples=_field_counts(retained_example_counts, entry_count),
        available_examples=_field_counts(available_example_counts, entry_count),
        entries_with_labeled_forms=entries_with_labeled_forms,
    )


def build_report(root: Path, languages: Iterable[str]) -> list[LanguageMetadataReport]:
    generated = DatasetPaths(root.resolve(), "kaikki").generated
    reports: list[LanguageMetadataReport] = []
    for language_tag in languages:
        database = generated / f"{language_tag}.db"
        if not database.is_file():
            raise FileNotFoundError(f"Generated Kaikki database is missing: {database}")
        report = inspect_database(database, language_tag)
        reports.append(report)
        print(
            f"Inspected {language_tag}: {report.entry_count:,} entries",
            flush=True,
        )
    return reports


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--languages",
        nargs="+",
        default=list(KAIKKI_SUPPORTED_LANGUAGE_TAGS),
    )
    parser.add_argument("--root", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=None)
    args = parser.parse_args()

    paths = (
        DatasetPaths(args.root.resolve(), "kaikki")
        if args.root is not None
        else dataset_paths("kaikki")
    )
    print(dataset_root_summary(paths.root), flush=True)
    reports = build_report(paths.root, args.languages)
    rendered = json.dumps(
        [asdict(report) for report in reports],
        ensure_ascii=False,
        indent=2,
    )
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
        print(f"Wrote {args.output}", flush=True)
    else:
        print(rendered)


if __name__ == "__main__":
    main()
