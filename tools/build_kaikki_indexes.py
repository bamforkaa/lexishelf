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
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import BinaryIO, Iterable, Mapping

from tools.dataset_paths import dataset_paths, dataset_root_summary
from tools.kaikki_language_config import (
    KAIKKI_CANDIDATE_LANGUAGE_TAGS,
    KAIKKI_DATASET_RELEASE_ID,
    KAIKKI_EXTRACTION_DATE,
    KAIKKI_INDEX_SCHEMA_VERSION,
    KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS,
    KAIKKI_SOURCE_FILE,
    KAIKKI_SOURCE_SHA256,
    KAIKKI_SOURCE_URL,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)
from tools.kaikki_form_policy import (
    morphology_lemma_rejection_reason,
    select_display_forms,
    select_morphology_forms,
)

_WHITESPACE = re.compile(r"\s+")
_GENDER_TAGS = frozenset(
    {"masculine", "feminine", "neuter", "common-gender"}
)
_MAX_PRONUNCIATIONS = 8
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
    selected_display_form_count: int
    morphology_row_count: int
    rejected_morphology_entry_count: int
    rejected_morphology_row_count: int
    morphology_rejection_counts: dict[str, int]
    unique_morphology_surface_count: int
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
    selected_display_form_count: int = 0
    morphology_row_count: int = 0
    rejected_morphology_entry_count: int = 0
    rejected_morphology_row_count: int = 0
    morphology_rejection_counts: dict[str, int] = field(default_factory=dict)


@dataclass
class _LanguageOutput:
    tag: str
    database: sqlite3.Connection
    temporary_database: Path
    final_database: Path
    filtered_source: BinaryIO | None
    temporary_filtered_source: Path | None
    final_filtered_source: Path | None
    stats: _MutableStats
    morphology_only: bool = False
    entry_order: int = 0


def normalized_exact_key(value: str, language_tag: str | None = None) -> str:
    normalized = _WHITESPACE.sub(
        " ", unicodedata.normalize("NFC", value).strip()
    )
    if language_tag and language_tag.split("-", 1)[0].lower() == "tr":
        normalized = normalized.translate(str.maketrans({"I": "ı", "İ": "i"}))
    return normalized.lower()


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
    morphology_only_languages: Iterable[str] = (),
    expected_source_sha256: str | None = None,
) -> BuildReport:
    full_languages = tuple(dict.fromkeys(languages))
    morphology_only = tuple(
        language
        for language in dict.fromkeys(morphology_only_languages)
        if language not in full_languages
    )
    selected = full_languages + morphology_only
    if not selected:
        raise ValueError("At least one Kaikki language is required")
    reviewed = set(KAIKKI_CANDIDATE_LANGUAGE_TAGS) | set(KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS)
    unsupported = sorted(set(selected) - reviewed)
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
                language in morphology_only,
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
                    if output.filtered_source is not None:
                        output.filtered_source.write(raw_line)
                    output.stats.raw_entry_count += 1
                    if output.morphology_only:
                        _insert_morphology_only_entry(output, raw_entry)
                    else:
                        _insert_entry(output, raw_entry)

            language_stats = tuple(
                _finish_language_output(output) for output in outputs.values()
            )
        except BaseException:
            for output in outputs.values():
                output.database.rollback()
                output.database.close()
                if output.filtered_source is not None:
                    output.filtered_source.close()
                output.temporary_database.unlink(missing_ok=True)
                if output.temporary_filtered_source is not None:
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
    morphology_only: bool,
) -> _LanguageOutput:
    database_name = f"{language}-morphology.db" if morphology_only else f"{language}.db"
    final_database = generated_directory / database_name
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
    final_filtered_source = None if morphology_only else filtered_source_directory / f"{language}.jsonl.gz"
    temporary_filtered_source = (
        None if morphology_only else filtered_source_directory / f"{language}.jsonl.gz.tmp"
    )
    if temporary_filtered_source is not None:
        temporary_filtered_source.unlink(missing_ok=True)
    filtered_source = (
        None
        if temporary_filtered_source is None
        else stack.enter_context(gzip.open(temporary_filtered_source, "wb", compresslevel=6))
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
        morphology_only=morphology_only,
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

        CREATE TABLE morphology_forms (
            normalized_form TEXT NOT NULL,
            form TEXT NOT NULL,
            lemma TEXT NOT NULL,
            normalized_lemma TEXT NOT NULL,
            source_entry_id TEXT NOT NULL,
            entry_order INTEGER NOT NULL,
            form_order INTEGER NOT NULL,
            PRIMARY KEY(normalized_form, normalized_lemma, source_entry_id)
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

    raw_pos = _text(raw_entry.get("pos"))
    pronunciations = _pronunciations(raw_entry.get("sounds"))
    selected_forms = select_display_forms(
        output.tag, raw_pos, headword, raw_entry.get("forms")
    )
    morphology_forms = _select_morphology_forms(output, raw_pos, headword, raw_entry)
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
        "f": [{"f": item.form, "l": item.label} for item in selected_forms],
        "fc": len(morphology_forms),
        "s": senses,
    }
    output.database.execute(
        """
        INSERT INTO entries(entry_id, normalized_headword, headword, entry_order, payload)
        VALUES (?, ?, ?, ?, ?)
        """,
        (
            entry_id,
            normalized_exact_key(headword, output.tag),
            headword,
            output.entry_order,
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode(),
        ),
    )
    output.entry_order += 1
    _insert_morphology_forms(
        output, headword, entry_id, output.entry_order - 1, morphology_forms
    )
    stats = output.stats
    stats.indexed_entry_count += 1
    stats.sense_count += len(senses)
    stats.entries_with_pos += int(bool(raw_pos))
    stats.entries_with_pronunciation += int(bool(pronunciations))
    stats.entries_with_forms += int(bool(morphology_forms))
    stats.selected_display_form_count += len(selected_forms)
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


def _insert_morphology_only_entry(
    output: _LanguageOutput,
    raw_entry: Mapping[str, object],
) -> None:
    headword = _text(raw_entry.get("word"))
    raw_pos = _text(raw_entry.get("pos"))
    if not headword:
        return
    forms = _select_morphology_forms(output, raw_pos, headword, raw_entry)
    if not forms:
        return
    source_entry_id = _morphology_source_id(
        output.tag, headword, raw_pos, raw_entry, output.entry_order
    )
    _insert_morphology_forms(output, headword, source_entry_id, output.entry_order, forms)
    output.entry_order += 1
    output.stats.entries_with_forms += 1


def _morphology_source_id(
    language: str,
    headword: str,
    raw_pos: str,
    raw_entry: Mapping[str, object],
    entry_order: int,
) -> str:
    identity = {
        "l": language,
        "w": headword,
        "p": raw_pos,
        "e": raw_entry.get("etymology_number"),
        "o": entry_order,
    }
    digest = hashlib.sha256(
        json.dumps(identity, ensure_ascii=False, sort_keys=True).encode()
    ).hexdigest()
    return f"enw-{language}-morph-{digest[:18]}"


def _insert_morphology_forms(
    output: _LanguageOutput,
    lemma: str,
    source_entry_id: str,
    entry_order: int,
    forms: list[str],
) -> None:
    rows = [
        (
            normalized_exact_key(form, output.tag),
            form,
            lemma,
            normalized_exact_key(lemma, output.tag),
            source_entry_id,
            entry_order,
            form_order,
        )
        for form_order, form in enumerate(forms)
    ]
    output.database.executemany(
        """
        INSERT OR IGNORE INTO morphology_forms(
            normalized_form, form, lemma, normalized_lemma, source_entry_id,
            entry_order, form_order
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    output.stats.morphology_row_count += len(rows)


def _select_morphology_forms(
    output: _LanguageOutput,
    raw_pos: str,
    headword: str,
    raw_entry: Mapping[str, object],
) -> list[str]:
    raw_forms = raw_entry.get("forms")
    raw_senses = raw_entry.get("senses")
    reason = morphology_lemma_rejection_reason(output.tag, raw_senses)
    selected = select_morphology_forms(
        output.tag,
        raw_pos,
        headword,
        raw_forms,
        raw_senses=raw_senses,
    )
    if reason is None:
        return selected
    previously_eligible = select_morphology_forms(
        output.tag,
        raw_pos,
        headword,
        raw_forms,
    )
    if previously_eligible:
        stats = output.stats
        stats.rejected_morphology_entry_count += 1
        stats.rejected_morphology_row_count += len(previously_eligible)
        counts = stats.morphology_rejection_counts
        counts[reason] = counts.get(reason, 0) + len(previously_eligible)
    return selected


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
    database.execute(
        "CREATE INDEX morphology_exact_lookup ON "
        "morphology_forms(normalized_form, entry_order, form_order)"
    )
    metadata = (
        ("raw_entry_count", str(output.stats.raw_entry_count)),
        ("entry_count", str(output.stats.indexed_entry_count)),
        ("sense_count", str(output.stats.sense_count)),
        ("morphology_row_count", str(output.stats.morphology_row_count)),
    )
    database.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata)
    database.commit()
    headword_count = database.execute(
        "SELECT COUNT(DISTINCT headword) FROM entries"
    ).fetchone()[0]
    unique_morphology_surface_count = database.execute(
        "SELECT COUNT(DISTINCT normalized_form) FROM morphology_forms"
    ).fetchone()[0]
    database.execute("VACUUM")
    database.close()
    if output.filtered_source is not None:
        output.filtered_source.close()
    output.final_database.unlink(missing_ok=True)
    output.temporary_database.replace(output.final_database)
    if output.final_filtered_source is not None and output.temporary_filtered_source is not None:
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
        selected_display_form_count=stats.selected_display_form_count,
        morphology_row_count=stats.morphology_row_count,
        rejected_morphology_entry_count=stats.rejected_morphology_entry_count,
        rejected_morphology_row_count=stats.rejected_morphology_row_count,
        morphology_rejection_counts=dict(stats.morphology_rejection_counts),
        unique_morphology_surface_count=unique_morphology_surface_count,
        compressed_source_bytes=(
            output.final_filtered_source.stat().st_size
            if output.final_filtered_source is not None
            else 0
        ),
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
    parser.add_argument(
        "--without-english-morphology",
        action="store_true",
        help="Skip the compact English morphology-only index for a partial regeneration",
    )
    parser.add_argument(
        "--only-english-morphology",
        action="store_true",
        help="Regenerate only the compact English morphology index",
    )
    arguments = parser.parse_args()
    if arguments.only_english_morphology and (
        arguments.languages
        or arguments.analyze_candidates
        or arguments.without_english_morphology
    ):
        parser.error("--only-english-morphology cannot be combined with other build selectors")
    languages = (
        ()
        if arguments.only_english_morphology
        else KAIKKI_CANDIDATE_LANGUAGE_TAGS
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
        morphology_only_languages=(
            () if arguments.without_english_morphology
            else KAIKKI_MORPHOLOGY_ONLY_LANGUAGE_TAGS
        ),
        expected_source_sha256=KAIKKI_SOURCE_SHA256,
    )
    is_full_production_build = (
        tuple(languages) == KAIKKI_SUPPORTED_LANGUAGE_TAGS
        and not arguments.without_english_morphology
    )
    report_suffix = (
        ""
        if is_full_production_build
        else "-en-morphology"
        if arguments.only_english_morphology
        else "-" + "-".join(languages)
    )
    report_path = paths.generated / f"kaikki-build-report{report_suffix}.json"
    report_path.write_text(_report_json(report), encoding="utf-8")
    print(_report_json(report))
    print(f"Official source: {KAIKKI_SOURCE_URL}")
    print(f"Report: {report_path}")


if __name__ == "__main__":
    main()
