#!/usr/bin/env python3
"""Build compact per-language exact-lookup indexes from official Kaikki JSONL."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import sqlite3
import time
import unicodedata
from contextlib import ExitStack, closing
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import BinaryIO, Iterable, Mapping

from tools.dataset_paths import dataset_paths, dataset_root_summary
from tools.kaikki_language_config import (
    KAIKKI_CANDIDATE_LANGUAGE_TAGS,
    KAIKKI_DATASET_RELEASE_ID,
    KAIKKI_EXTRACTION_DATE,
    KAIKKI_INDEX_SCHEMA_VERSION,
    KAIKKI_SOURCE_FILE,
    KAIKKI_SOURCE_SHA256,
    KAIKKI_SOURCE_URL,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)

_WHITESPACE = re.compile(r"\s+")
_GENDER_TAGS = frozenset(
    {"masculine", "feminine", "neuter", "common-gender"}
)
_INTERNAL_FORM_TAGS = frozenset({"table-tags", "inflection-template"})
_MAX_PRONUNCIATIONS = 8
_MAX_FORMS = 24
_MAX_EXAMPLES_PER_SENSE = 2


@dataclass(frozen=True)
class LanguageBuildStats:
    language_tag: str
    raw_entry_count: int
    headword_count: int
    indexed_entry_count: int
    sense_count: int
    entries_with_pos: int
    entries_with_pronunciation: int
    entries_with_forms: int
    entries_with_examples: int
    entries_with_gender: int
    compressed_source_bytes: int
    generated_database_bytes: int


@dataclass(frozen=True)
class BuildReport:
    release_id: str
    extraction_date: str
    source_sha256: str
    source_bytes: int
    conversion_seconds: float
    languages: tuple[LanguageBuildStats, ...]


@dataclass
class _MutableStats:
    raw_entry_count: int = 0
    indexed_entry_count: int = 0
    sense_count: int = 0
    entries_with_pos: int = 0
    entries_with_pronunciation: int = 0
    entries_with_forms: int = 0
    entries_with_examples: int = 0
    entries_with_gender: int = 0


@dataclass
class _LanguageOutput:
    tag: str
    database: sqlite3.Connection
    temporary_database: Path
    final_database: Path
    filtered_source: BinaryIO
    temporary_filtered_source: Path
    final_filtered_source: Path
    stats: _MutableStats
    entry_order: int = 0


def normalized_exact_key(value: str) -> str:
    return _WHITESPACE.sub(
        " ", unicodedata.normalize("NFC", value).strip()
    ).lower()


def source_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_indexes(
    source: Path,
    generated_directory: Path,
    filtered_source_directory: Path,
    languages: Iterable[str],
    expected_source_sha256: str | None = None,
) -> BuildReport:
    selected = tuple(dict.fromkeys(languages))
    if not selected:
        raise ValueError("At least one Kaikki language is required")
    unsupported = sorted(set(selected) - set(KAIKKI_CANDIDATE_LANGUAGE_TAGS))
    if unsupported:
        raise ValueError(f"Unreviewed Kaikki languages: {', '.join(unsupported)}")
    if not source.is_file():
        raise FileNotFoundError(f"Kaikki source is missing: {source}")

    started = time.perf_counter()
    digest = source_sha256(source)
    if expected_source_sha256 is not None and digest != expected_source_sha256:
        raise ValueError(
            "Kaikki source SHA-256 does not match the reviewed artifact: "
            f"expected {expected_source_sha256}, found {digest}"
        )
    generated_directory.mkdir(parents=True, exist_ok=True)
    filtered_source_directory.mkdir(parents=True, exist_ok=True)

    with ExitStack() as stack:
        outputs = {
            language: _open_language_output(
                stack,
                language,
                generated_directory,
                filtered_source_directory,
                digest,
            )
            for language in selected
        }
        try:
            with _open_jsonl(source) as stream:
                for line_number, raw_line in enumerate(stream, start=1):
                    try:
                        raw_entry = json.loads(raw_line)
                    except (UnicodeDecodeError, json.JSONDecodeError) as error:
                        raise ValueError(
                            f"Malformed Kaikki JSON at line {line_number}"
                        ) from error
                    language = raw_entry.get("lang_code")
                    output = outputs.get(language) if isinstance(language, str) else None
                    if output is None:
                        continue
                    output.filtered_source.write(raw_line)
                    output.stats.raw_entry_count += 1
                    _insert_entry(output, raw_entry)

            language_stats = tuple(
                _finish_language_output(output) for output in outputs.values()
            )
        except BaseException:
            for output in outputs.values():
                output.database.rollback()
                output.database.close()
                output.filtered_source.close()
                output.temporary_database.unlink(missing_ok=True)
                output.temporary_filtered_source.unlink(missing_ok=True)
            raise

    return BuildReport(
        release_id=KAIKKI_DATASET_RELEASE_ID,
        extraction_date=KAIKKI_EXTRACTION_DATE,
        source_sha256=digest,
        source_bytes=source.stat().st_size,
        conversion_seconds=time.perf_counter() - started,
        languages=language_stats,
    )


def _open_jsonl(path: Path) -> BinaryIO:
    return gzip.open(path, "rb") if path.suffix.lower() == ".gz" else path.open("rb")


def _open_language_output(
    stack: ExitStack,
    language: str,
    generated_directory: Path,
    filtered_source_directory: Path,
    source_digest: str,
) -> _LanguageOutput:
    final_database = generated_directory / f"{language}.db"
    temporary_database = final_database.with_suffix(".db.tmp")
    temporary_database.unlink(missing_ok=True)
    database = sqlite3.connect(temporary_database)
    _configure_database(database)
    database.executemany(
        "INSERT INTO metadata(key, value) VALUES (?, ?)",
        (
            ("release_id", KAIKKI_DATASET_RELEASE_ID),
            ("extraction_date", KAIKKI_EXTRACTION_DATE),
            ("language_tag", language),
            ("result_language_tag", "en"),
            ("source_sha256", source_digest),
        ),
    )
    database.commit()
    database.execute("BEGIN")
    final_filtered_source = filtered_source_directory / f"{language}.jsonl.gz"
    temporary_filtered_source = filtered_source_directory / f"{language}.jsonl.gz.tmp"
    temporary_filtered_source.unlink(missing_ok=True)
    filtered_source = stack.enter_context(
        gzip.open(temporary_filtered_source, "wb", compresslevel=6)
    )
    return _LanguageOutput(
        tag=language,
        database=database,
        temporary_database=temporary_database,
        final_database=final_database,
        filtered_source=filtered_source,
        temporary_filtered_source=temporary_filtered_source,
        final_filtered_source=final_filtered_source,
        stats=_MutableStats(),
    )


def _configure_database(database: sqlite3.Connection) -> None:
    database.executescript(
        f"""
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;
        PRAGMA locking_mode = EXCLUSIVE;
        PRAGMA user_version = {KAIKKI_INDEX_SCHEMA_VERSION};

        CREATE TABLE metadata (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE entries (
            entry_id TEXT PRIMARY KEY NOT NULL,
            normalized_headword TEXT NOT NULL,
            headword TEXT NOT NULL,
            entry_order INTEGER NOT NULL,
            payload BLOB NOT NULL
        ) WITHOUT ROWID;
        """
    )


def _insert_entry(output: _LanguageOutput, raw_entry: Mapping[str, object]) -> None:
    if raw_entry.get("lang_code") != output.tag:
        raise ValueError(
            f"Kaikki language marker does not match parsed entry for {output.tag}"
        )
    headword = _text(raw_entry.get("word"))
    if not headword:
        return
    raw_senses = raw_entry.get("senses")
    if not isinstance(raw_senses, list):
        return

    entry_tags = _strings(raw_entry.get("tags"))
    senses: list[dict[str, object]] = []
    has_examples = False
    has_gender = False
    for sense_order, raw_sense in enumerate(raw_senses):
        if not isinstance(raw_sense, dict):
            continue
        glosses = _strings(raw_sense.get("glosses"))
        if not glosses:
            continue
        examples, example_count = _examples(raw_sense.get("examples"))
        gender = _gender(entry_tags + _strings(raw_sense.get("tags")))
        has_examples = has_examples or example_count > 0
        has_gender = has_gender or gender is not None
        senses.append(
            {
                "o": sense_order,
                "i": _text(raw_sense.get("id")),
                "g": glosses,
                "e": examples,
                "x": example_count,
                "d": gender,
            }
        )
    if not senses:
        return

    pronunciations = _pronunciations(raw_entry.get("sounds"))
    forms, form_count = _forms(raw_entry.get("forms"), headword)
    raw_pos = _text(raw_entry.get("pos"))
    entry_id = _stable_entry_id(
        output.tag,
        headword,
        raw_pos,
        raw_entry,
        senses,
        output.entry_order,
    )
    payload = {
        "w": headword,
        "p": raw_pos,
        "n": pronunciations,
        "f": forms,
        "fc": form_count,
        "s": senses,
    }
    output.database.execute(
        """
        INSERT INTO entries(entry_id, normalized_headword, headword, entry_order, payload)
        VALUES (?, ?, ?, ?, ?)
        """,
        (
            entry_id,
            normalized_exact_key(headword),
            headword,
            output.entry_order,
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode(),
        ),
    )
    output.entry_order += 1
    stats = output.stats
    stats.indexed_entry_count += 1
    stats.sense_count += len(senses)
    stats.entries_with_pos += int(bool(raw_pos))
    stats.entries_with_pronunciation += int(bool(pronunciations))
    stats.entries_with_forms += int(form_count > 0)
    stats.entries_with_examples += int(has_examples)
    stats.entries_with_gender += int(has_gender)


def _stable_entry_id(
    language: str,
    headword: str,
    raw_pos: str,
    raw_entry: Mapping[str, object],
    senses: list[dict[str, object]],
    entry_order: int,
) -> str:
    identity = {
        "l": language,
        "w": headword,
        "p": raw_pos,
        "e": raw_entry.get("etymology_number"),
        "s": [sense["i"] for sense in senses],
        "o": entry_order,
    }
    digest = hashlib.sha256(
        json.dumps(identity, ensure_ascii=False, sort_keys=True).encode()
    ).hexdigest()
    return f"enw-{language}-{digest[:24]}"


def _pronunciations(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    result: list[str] = []
    seen: set[str] = set()
    for sound in value:
        if not isinstance(sound, dict):
            continue
        for field, prefix in (("ipa", ""), ("enpr", "enPR: "), ("zh-pron", "")):
            text = _text(sound.get(field))
            display = f"{prefix}{text}" if text else ""
            if display and display not in seen:
                seen.add(display)
                result.append(display)
                if len(result) == _MAX_PRONUNCIATIONS:
                    return result
    return result


def _forms(value: object, headword: str) -> tuple[list[dict[str, str]], int]:
    if not isinstance(value, list):
        return [], 0
    retained: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    total = 0
    for raw_form in value:
        if not isinstance(raw_form, dict):
            continue
        form = _text(raw_form.get("form"))
        tags = _strings(raw_form.get("tags"))
        if not form or form == "-" or _INTERNAL_FORM_TAGS.intersection(tags):
            continue
        label = ", ".join(tags)
        key = (form, label)
        if key in seen:
            continue
        seen.add(key)
        total += 1
        if len(retained) < _MAX_FORMS and not (form == headword and not label):
            retained.append({"f": form, "l": label})
    return retained, total


def _examples(value: object) -> tuple[list[str], int]:
    if not isinstance(value, list):
        return [], 0
    retained: list[str] = []
    total = 0
    for example in value:
        if not isinstance(example, dict):
            continue
        # This array also contains attributed quotations/fair-use material.
        # Keep only contributor-authored usage examples for the selected
        # CC BY-SA reuse path.
        if example.get("type") != "example" or "ref" in example:
            continue
        text = _text(example.get("text"))
        if not text:
            continue
        total += 1
        if len(retained) < _MAX_EXAMPLES_PER_SENSE:
            retained.append(text)
    return retained, total


def _gender(tags: list[str]) -> str | None:
    values = sorted(_GENDER_TAGS.intersection(tags))
    return ", ".join(values) if values else None


def _strings(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [text for item in value if (text := _text(item))]


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _finish_language_output(output: _LanguageOutput) -> LanguageBuildStats:
    database = output.database
    database.execute(
        "CREATE INDEX entries_exact_lookup ON entries(normalized_headword, entry_order)"
    )
    metadata = (
        ("raw_entry_count", str(output.stats.raw_entry_count)),
        ("entry_count", str(output.stats.indexed_entry_count)),
        ("sense_count", str(output.stats.sense_count)),
    )
    database.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata)
    database.commit()
    headword_count = database.execute(
        "SELECT COUNT(DISTINCT headword) FROM entries"
    ).fetchone()[0]
    database.execute("VACUUM")
    database.close()
    output.filtered_source.close()
    output.final_database.unlink(missing_ok=True)
    output.temporary_database.replace(output.final_database)
    output.final_filtered_source.unlink(missing_ok=True)
    output.temporary_filtered_source.replace(output.final_filtered_source)
    stats = output.stats
    return LanguageBuildStats(
        language_tag=output.tag,
        raw_entry_count=stats.raw_entry_count,
        headword_count=headword_count,
        indexed_entry_count=stats.indexed_entry_count,
        sense_count=stats.sense_count,
        entries_with_pos=stats.entries_with_pos,
        entries_with_pronunciation=stats.entries_with_pronunciation,
        entries_with_forms=stats.entries_with_forms,
        entries_with_examples=stats.entries_with_examples,
        entries_with_gender=stats.entries_with_gender,
        compressed_source_bytes=output.final_filtered_source.stat().st_size,
        generated_database_bytes=output.final_database.stat().st_size,
    )


def _report_json(report: BuildReport) -> str:
    return json.dumps(asdict(report), ensure_ascii=False, indent=2)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--languages",
        nargs="+",
        choices=KAIKKI_CANDIDATE_LANGUAGE_TAGS,
        default=None,
        help="Explicit reviewed languages; defaults to the production selection",
    )
    parser.add_argument(
        "--analyze-candidates",
        action="store_true",
        help="Build every reviewed candidate index instead of the production selection",
    )
    arguments = parser.parse_args()
    languages = (
        KAIKKI_CANDIDATE_LANGUAGE_TAGS
        if arguments.analyze_candidates
        else tuple(arguments.languages or KAIKKI_SUPPORTED_LANGUAGE_TAGS)
    )
    paths = dataset_paths("kaikki")
    print(dataset_root_summary(paths.root))
    source = paths.source / KAIKKI_SOURCE_FILE
    report = build_indexes(
        source=source,
        generated_directory=paths.generated,
        filtered_source_directory=paths.source / "filtered",
        languages=languages,
        expected_source_sha256=KAIKKI_SOURCE_SHA256,
    )
    report_path = paths.generated / "kaikki-build-report.json"
    report_path.write_text(_report_json(report), encoding="utf-8")
    print(_report_json(report))
    print(f"Official source: {KAIKKI_SOURCE_URL}")
    print(f"Report: {report_path}")


if __name__ == "__main__":
    main()
