#!/usr/bin/env python3
"""Build a compact Korean PanLex exact-lookup index from an official CSV snapshot."""

from __future__ import annotations

import argparse
import base64
from contextlib import closing
import csv
import hashlib
import io
import json
import os
import re
import sqlite3
import unicodedata
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterator, Sequence


INDEX_SCHEMA_VERSION = 1
PANLEX_SOURCE_URL = "https://panlex.org/"
PANLEX_SNAPSHOT_URL = "https://panlex.org/snapshot/"
PANLEX_LICENSE_NAME = "CC0 1.0 Universal"
PANLEX_LICENSE_URL = "https://creativecommons.org/publicdomain/zero/1.0/"
KOREAN_LANGUAGE_TAG = "ko"
KOREAN_VARIETY_UID = "kor-000"
DEFAULT_LANGUAGE_TAGS = ("de", "hi", "pl", "la")
BCP47_TO_PANLEX_UID = {
    "ko": KOREAN_VARIETY_UID,
    "de": "deu-000",
    "hi": "hin-000",
    "pl": "pol-000",
    "la": "lat-000",
    "en": "eng-000",
    "ja": "jpn-000",
    "zh": "cmn-000",
    "fr": "fra-000",
    "es": "spa-000",
    "ru": "rus-000",
    "ar": "arb-000",
    "mn": "khk-000",
    "vi": "vie-000",
    "th": "tha-000",
    "id": "ind-000",
}

_SNAPSHOT_ROOT = re.compile(r"panlex-(\d{4})(\d{2})(\d{2})-csv/$")
_REQUIRED_TABLE_COLUMNS = {
    "langvar.csv": {"id", "lang_code", "var_code"},
    "expr.csv": {"id", "langvar", "txt"},
    "denotation.csv": {"meaning", "expr"},
    "meaning.csv": {"id", "source"},
    "source.csv": {"id", "quality", "grp"},
}
_REQUIRED_LICENSE_PHRASES = (
    "CC0 1.0 Universal License",
    "copy, modify, and",
    "distribute the Data, even for commercial purposes",
)


@dataclass(frozen=True)
class PanLexCoverage:
    direct_relation_count: int
    distinct_foreign_expressions: int
    distinct_korean_expressions: int


@dataclass(frozen=True)
class PanLexBuildStats:
    release_id: str
    source_archive_bytes: int
    source_uncompressed_bytes: int
    source_archive_sha256: str
    selected_language_tags: tuple[str, ...]
    coverage: dict[str, PanLexCoverage]
    generated_database_bytes: int


def normalized_exact_key(value: str) -> str:
    normalized = unicodedata.normalize("NFC", value)
    return " ".join(normalized.strip().split()).lower()


def _validate_language_tags(language_tags: Sequence[str]) -> tuple[str, ...]:
    normalized = tuple(dict.fromkeys(tag.strip().lower() for tag in language_tags))
    if not normalized:
        raise ValueError("At least one foreign language is required")
    if KOREAN_LANGUAGE_TAG in normalized:
        raise ValueError("Korean is implicit and must not be selected as a foreign language")
    unsupported = sorted(set(normalized) - BCP47_TO_PANLEX_UID.keys())
    if unsupported:
        raise ValueError(f"No reviewed PanLex variety mapping for: {', '.join(unsupported)}")
    return normalized


def _snapshot_metadata(archive: zipfile.ZipFile) -> tuple[str, str, int]:
    roots = {name.split("/", 1)[0] + "/" for name in archive.namelist() if "/" in name}
    matching_roots = [(root, _SNAPSHOT_ROOT.fullmatch(root)) for root in roots]
    matching_roots = [(root, match) for root, match in matching_roots if match is not None]
    if len(matching_roots) != 1:
        raise ValueError("Snapshot must contain one panlex-YYYYMMDD-csv directory")
    root, match = matching_roots[0]
    assert match is not None
    release_id = "-".join(match.groups())
    missing = [name for name in (*_REQUIRED_TABLE_COLUMNS, "LICENSE.txt") if root + name not in archive.namelist()]
    if missing:
        raise ValueError(f"Snapshot is missing required files: {', '.join(missing)}")
    license_text = archive.read(root + "LICENSE.txt").decode("utf-8")
    if any(phrase not in license_text for phrase in _REQUIRED_LICENSE_PHRASES):
        raise ValueError("Snapshot LICENSE.txt does not contain the reviewed PanLex CC0 grant")
    return root, release_id, sum(item.file_size for item in archive.infolist())


def _csv_rows(
    archive: zipfile.ZipFile,
    root: str,
    table_name: str,
) -> Iterator[tuple[int, dict[str, str]]]:
    with archive.open(root + table_name) as raw:
        reader = csv.DictReader(io.TextIOWrapper(raw, encoding="utf-8", newline=""))
        required = _REQUIRED_TABLE_COLUMNS[table_name]
        actual = set(reader.fieldnames or ())
        missing = sorted(required - actual)
        if missing:
            raise ValueError(f"{table_name} is missing columns: {', '.join(missing)}")
        for row_number, row in enumerate(reader, start=2):
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"Malformed {table_name} record at row {row_number}")
            yield row_number, row  # type: ignore[misc]


def _required_int(row: dict[str, str], field: str, table: str, row_number: int) -> int:
    try:
        return int(row[field])
    except (KeyError, TypeError, ValueError) as error:
        raise ValueError(f"Invalid {table}.{field} at row {row_number}") from error


def _grow(buffer: bytearray, identifier: int) -> None:
    if identifier < 0:
        raise ValueError("PanLex identifiers must not be negative")
    if identifier >= len(buffer):
        buffer.extend(b"\0" * (identifier + 1 - len(buffer)))


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _sha1_base32(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(block)
    return base64.b32encode(digest.digest()).decode("ascii")


def _configure_staging(database: sqlite3.Connection) -> None:
    database.executescript(
        """
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        PRAGMA temp_store=MEMORY;
        PRAGMA locking_mode=EXCLUSIVE;

        CREATE TABLE expressions(
            id INTEGER PRIMARY KEY,
            language_tag TEXT NOT NULL,
            txt TEXT NOT NULL,
            normalized_txt TEXT NOT NULL
        );
        CREATE TABLE selected_denotations(
            meaning INTEGER NOT NULL,
            expr INTEGER NOT NULL
        );
        CREATE TABLE meaning_sources(
            meaning INTEGER PRIMARY KEY,
            source INTEGER NOT NULL
        );
        CREATE TABLE sources(
            source INTEGER PRIMARY KEY,
            source_group INTEGER NOT NULL,
            quality INTEGER NOT NULL
        );
        """
    )


def _resolve_varieties(
    archive: zipfile.ZipFile,
    root: str,
    language_tags: tuple[str, ...],
) -> dict[str, int]:
    wanted = {tag: BCP47_TO_PANLEX_UID[tag] for tag in (KOREAN_LANGUAGE_TAG, *language_tags)}
    resolved: dict[str, int] = {}
    for row_number, row in _csv_rows(archive, root, "langvar.csv"):
        var_code = _required_int(row, "var_code", "langvar.csv", row_number)
        uid = f"{row['lang_code']}-{var_code:03d}"
        for language_tag, wanted_uid in wanted.items():
            if uid == wanted_uid:
                if language_tag in resolved:
                    raise ValueError(f"Duplicate PanLex variety {wanted_uid}")
                resolved[language_tag] = _required_int(row, "id", "langvar.csv", row_number)
    missing = [uid for tag, uid in wanted.items() if tag not in resolved]
    if missing:
        raise ValueError(f"Snapshot is missing reviewed varieties: {', '.join(missing)}")
    return resolved


def _stage_expressions(
    archive: zipfile.ZipFile,
    root: str,
    database: sqlite3.Connection,
    varieties: dict[str, int],
) -> bytearray:
    language_number_by_tag = {
        language_tag: number for number, language_tag in enumerate(varieties, start=1)
    }
    language_tag_by_variety = {identifier: tag for tag, identifier in varieties.items()}
    expression_language = bytearray()
    batch: list[tuple[int, str, str, str]] = []
    for row_number, row in _csv_rows(archive, root, "expr.csv"):
        langvar = _required_int(row, "langvar", "expr.csv", row_number)
        language_tag = language_tag_by_variety.get(langvar)
        if language_tag is None:
            continue
        expression_id = _required_int(row, "id", "expr.csv", row_number)
        text = row["txt"]
        if not text:
            raise ValueError(f"Blank expr.csv.txt at row {row_number}")
        _grow(expression_language, expression_id)
        expression_language[expression_id] = language_number_by_tag[language_tag]
        batch.append((expression_id, language_tag, text, normalized_exact_key(text)))
        if len(batch) >= 50_000:
            database.executemany("INSERT INTO expressions VALUES(?, ?, ?, ?)", batch)
            batch.clear()
    database.executemany("INSERT INTO expressions VALUES(?, ?, ?, ?)", batch)
    database.commit()
    return expression_language


def _stage_denotations(
    archive: zipfile.ZipFile,
    root: str,
    database: sqlite3.Connection,
    expression_language: bytearray,
    varieties: dict[str, int],
) -> set[int]:
    korean_number = tuple(varieties).index(KOREAN_LANGUAGE_TAG) + 1
    korean_meanings: set[int] = set()
    for row_number, row in _csv_rows(archive, root, "denotation.csv"):
        expression_id = _required_int(row, "expr", "denotation.csv", row_number)
        if expression_id < len(expression_language) and expression_language[expression_id] == korean_number:
            korean_meanings.add(_required_int(row, "meaning", "denotation.csv", row_number))

    batch: list[tuple[int, int]] = []
    selected_meanings: set[int] = set()
    for row_number, row in _csv_rows(archive, root, "denotation.csv"):
        meaning_id = _required_int(row, "meaning", "denotation.csv", row_number)
        if meaning_id not in korean_meanings:
            continue
        expression_id = _required_int(row, "expr", "denotation.csv", row_number)
        if expression_id >= len(expression_language) or expression_language[expression_id] == 0:
            continue
        selected_meanings.add(meaning_id)
        batch.append((meaning_id, expression_id))
        if len(batch) >= 100_000:
            database.executemany("INSERT INTO selected_denotations VALUES(?, ?)", batch)
            batch.clear()
    database.executemany("INSERT INTO selected_denotations VALUES(?, ?)", batch)
    database.commit()
    return selected_meanings


def _stage_source_metadata(
    archive: zipfile.ZipFile,
    root: str,
    database: sqlite3.Connection,
    selected_meanings: set[int],
) -> None:
    source_ids: set[int] = set()
    meaning_rows: list[tuple[int, int]] = []
    for row_number, row in _csv_rows(archive, root, "meaning.csv"):
        meaning_id = _required_int(row, "id", "meaning.csv", row_number)
        if meaning_id not in selected_meanings:
            continue
        source_id = _required_int(row, "source", "meaning.csv", row_number)
        source_ids.add(source_id)
        meaning_rows.append((meaning_id, source_id))
        if len(meaning_rows) >= 100_000:
            database.executemany("INSERT INTO meaning_sources VALUES(?, ?)", meaning_rows)
            meaning_rows.clear()
    database.executemany("INSERT INTO meaning_sources VALUES(?, ?)", meaning_rows)

    found_sources: set[int] = set()
    source_rows: list[tuple[int, int, int]] = []
    for row_number, row in _csv_rows(archive, root, "source.csv"):
        source_id = _required_int(row, "id", "source.csv", row_number)
        if source_id not in source_ids:
            continue
        quality = _required_int(row, "quality", "source.csv", row_number)
        if quality not in range(10):
            raise ValueError(f"Invalid source.csv.quality at row {row_number}")
        found_sources.add(source_id)
        source_rows.append(
            (
                source_id,
                _required_int(row, "grp", "source.csv", row_number),
                quality,
            )
        )
    missing_sources = source_ids - found_sources
    if missing_sources:
        raise ValueError(f"Snapshot is missing {len(missing_sources)} referenced sources")
    database.executemany("INSERT INTO sources VALUES(?, ?, ?)", source_rows)
    database.commit()


def _create_direct_relations(database: sqlite3.Connection) -> None:
    database.executescript(
        """
        CREATE INDEX selected_denotations_meaning
            ON selected_denotations(meaning, expr);

        CREATE TABLE direct_relations AS
        SELECT DISTINCT
            k.meaning AS meaning_id,
            ms.source AS panlex_source_id,
            s.source_group,
            s.quality AS source_quality,
            k.expr AS korean_expr_id,
            ke.txt AS korean_text,
            ke.normalized_txt AS normalized_korean,
            fe.language_tag AS foreign_language_tag,
            f.expr AS foreign_expr_id,
            fe.txt AS foreign_text,
            fe.normalized_txt AS normalized_foreign
        FROM selected_denotations k
        JOIN expressions ke ON ke.id=k.expr AND ke.language_tag='ko'
        JOIN selected_denotations f ON f.meaning=k.meaning AND f.expr!=k.expr
        JOIN expressions fe ON fe.id=f.expr AND fe.language_tag!='ko'
        JOIN meaning_sources ms ON ms.meaning=k.meaning
        JOIN sources s ON s.source=ms.source;

        CREATE INDEX direct_relations_pair
            ON direct_relations(foreign_language_tag, foreign_expr_id, korean_expr_id);

        CREATE TABLE pair_source_groups AS
        SELECT foreign_language_tag, foreign_expr_id, korean_expr_id, source_group,
               MAX(source_quality) AS group_quality
        FROM direct_relations
        GROUP BY foreign_language_tag, foreign_expr_id, korean_expr_id, source_group;

        CREATE TABLE pair_scores AS
        SELECT foreign_language_tag, foreign_expr_id, korean_expr_id,
               SUM(group_quality) AS translation_quality,
               COUNT(*) AS source_group_count
        FROM pair_source_groups
        GROUP BY foreign_language_tag, foreign_expr_id, korean_expr_id;

        CREATE TABLE pair_source_counts AS
        SELECT foreign_language_tag, foreign_expr_id, korean_expr_id,
               COUNT(DISTINCT panlex_source_id) AS source_attestation_count
        FROM direct_relations
        GROUP BY foreign_language_tag, foreign_expr_id, korean_expr_id;

        CREATE TABLE representative_relations AS
        SELECT *, ROW_NUMBER() OVER(
            PARTITION BY foreign_language_tag, foreign_expr_id, korean_expr_id
            ORDER BY source_quality DESC, panlex_source_id, meaning_id
        ) AS representative_rank
        FROM direct_relations;
        """
    )


def _create_output_database(
    database: sqlite3.Connection,
    temporary_output: Path,
    release_id: str,
    archive_path: Path,
    archive_sha256: str,
    archive_sha1_base32: str,
    source_uncompressed_bytes: int,
    varieties: dict[str, int],
    language_tags: tuple[str, ...],
) -> dict[str, PanLexCoverage]:
    if temporary_output.exists():
        temporary_output.unlink()
    database.execute("ATTACH DATABASE ? AS output", (str(temporary_output),))
    database.executescript(
        """
        PRAGMA output.journal_mode=OFF;
        PRAGMA output.synchronous=OFF;
        PRAGMA output.page_size=4096;

        CREATE TABLE output.metadata(
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE output.language_varieties(
            language_tag TEXT PRIMARY KEY NOT NULL,
            panlex_uid TEXT NOT NULL,
            panlex_langvar_id INTEGER NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE output.relations(
            foreign_language_tag TEXT NOT NULL,
            foreign_expr_id INTEGER NOT NULL,
            foreign_text TEXT NOT NULL,
            normalized_foreign TEXT NOT NULL,
            korean_expr_id INTEGER NOT NULL,
            korean_text TEXT NOT NULL,
            normalized_korean TEXT NOT NULL,
            representative_meaning_id INTEGER NOT NULL,
            representative_source_id INTEGER NOT NULL,
            source_attestation_count INTEGER NOT NULL,
            source_group_count INTEGER NOT NULL,
            translation_quality INTEGER NOT NULL,
            PRIMARY KEY(foreign_language_tag, foreign_expr_id, korean_expr_id)
        ) WITHOUT ROWID;

        INSERT INTO output.relations
        SELECT r.foreign_language_tag, r.foreign_expr_id, r.foreign_text,
               r.normalized_foreign, r.korean_expr_id, r.korean_text,
               r.normalized_korean, r.meaning_id, r.panlex_source_id,
               c.source_attestation_count, q.source_group_count,
               q.translation_quality
        FROM representative_relations r
        JOIN pair_scores q USING(foreign_language_tag, foreign_expr_id, korean_expr_id)
        JOIN pair_source_counts c USING(foreign_language_tag, foreign_expr_id, korean_expr_id)
        WHERE r.representative_rank=1;

        CREATE INDEX output.index_relations_foreign_exact
            ON relations(
                foreign_language_tag, normalized_foreign,
                translation_quality DESC, source_group_count DESC,
                source_attestation_count DESC, korean_text, korean_expr_id
            );
        CREATE INDEX output.index_relations_korean_exact
            ON relations(
                normalized_korean, foreign_language_tag,
                translation_quality DESC, source_group_count DESC,
                source_attestation_count DESC, foreign_text, foreign_expr_id
            );
        PRAGMA output.user_version=1;
        """
    )

    coverage: dict[str, PanLexCoverage] = {}
    for language_tag in language_tags:
        relation_count, distinct_foreign, distinct_korean = database.execute(
            """
            SELECT COUNT(*), COUNT(DISTINCT foreign_expr_id), COUNT(DISTINCT korean_expr_id)
            FROM output.relations WHERE foreign_language_tag=?
            """,
            (language_tag,),
        ).fetchone()
        coverage[language_tag] = PanLexCoverage(
            direct_relation_count=relation_count,
            distinct_foreign_expressions=distinct_foreign,
            distinct_korean_expressions=distinct_korean,
        )

    relation_count = sum(item.direct_relation_count for item in coverage.values())
    metadata = {
        "schema_version": str(INDEX_SCHEMA_VERSION),
        "source_name": "PanLex",
        "source_url": PANLEX_SOURCE_URL,
        "snapshot_url": PANLEX_SNAPSHOT_URL,
        "release_id": release_id,
        "license_name": PANLEX_LICENSE_NAME,
        "license_url": PANLEX_LICENSE_URL,
        "source_archive_name": archive_path.name,
        "source_archive_bytes": str(archive_path.stat().st_size),
        "source_uncompressed_bytes": str(source_uncompressed_bytes),
        "source_archive_sha256": archive_sha256,
        "source_archive_sha1_base32": archive_sha1_base32,
        "relation_count": str(relation_count),
        "supported_language_tags": ",".join(language_tags),
        "translation_policy": "distance-1 same-source meaning co-denotations only",
        "ranking_policy": "PanLex tr1q source-group quality, then deterministic text order",
    }
    for language_tag, item in coverage.items():
        metadata[f"coverage_{language_tag}"] = json.dumps(item.__dict__, sort_keys=True)
    database.executemany(
        "INSERT INTO output.metadata(key, value) VALUES(?, ?)",
        sorted(metadata.items()),
    )
    database.executemany(
        "INSERT INTO output.language_varieties VALUES(?, ?, ?)",
        (
            (language_tag, BCP47_TO_PANLEX_UID[language_tag], varieties[language_tag])
            for language_tag in (KOREAN_LANGUAGE_TAG, *language_tags)
        ),
    )
    database.commit()
    database.execute("PRAGMA output.optimize")
    database.execute("DETACH DATABASE output")
    # sqlite3.Connection's context manager commits/rolls back but does not close.
    # Close explicitly so Windows releases the file before the atomic replace.
    with closing(sqlite3.connect(temporary_output)) as output:
        output.execute("VACUUM")
    return coverage


def build_index(
    snapshot_path: Path,
    output_database: Path,
    language_tags: Sequence[str] = DEFAULT_LANGUAGE_TAGS,
) -> PanLexBuildStats:
    if not snapshot_path.is_file():
        raise FileNotFoundError(snapshot_path)
    selected_languages = _validate_language_tags(language_tags)
    output_database.parent.mkdir(parents=True, exist_ok=True)
    temporary_output = output_database.with_name(output_database.name + ".tmp")
    staging_path = output_database.with_name(output_database.name + ".stage")
    for generated in (temporary_output, staging_path):
        if generated.exists():
            generated.unlink()

    archive_sha256 = _sha256(snapshot_path)
    archive_sha1_base32 = _sha1_base32(snapshot_path)
    staging = sqlite3.connect(staging_path)
    try:
        _configure_staging(staging)
        with zipfile.ZipFile(snapshot_path) as archive:
            root, release_id, source_uncompressed_bytes = _snapshot_metadata(archive)
            varieties = _resolve_varieties(archive, root, selected_languages)
            expression_language = _stage_expressions(
                archive, root, staging, varieties
            )
            selected_meanings = _stage_denotations(
                archive, root, staging, expression_language, varieties
            )
            _stage_source_metadata(
                archive, root, staging, selected_meanings
            )
        _create_direct_relations(staging)
        coverage = _create_output_database(
            database=staging,
            temporary_output=temporary_output,
            release_id=release_id,
            archive_path=snapshot_path,
            archive_sha256=archive_sha256,
            archive_sha1_base32=archive_sha1_base32,
            source_uncompressed_bytes=source_uncompressed_bytes,
            varieties=varieties,
            language_tags=selected_languages,
        )
        staging.close()
        os.replace(temporary_output, output_database)
        staging_path.unlink()
        return PanLexBuildStats(
            release_id=release_id,
            source_archive_bytes=snapshot_path.stat().st_size,
            source_uncompressed_bytes=source_uncompressed_bytes,
            source_archive_sha256=archive_sha256,
            selected_language_tags=selected_languages,
            coverage=coverage,
            generated_database_bytes=output_database.stat().st_size,
        )
    except BaseException:
        staging.close()
        for generated in (temporary_output, staging_path):
            if generated.exists():
                generated.unlink()
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("snapshot", type=Path, nargs="?")
    parser.add_argument("output_database", type=Path, nargs="?")
    parser.add_argument(
        "--languages",
        default=",".join(DEFAULT_LANGUAGE_TAGS),
        help="Comma-separated reviewed BCP 47 language tags",
    )
    args = parser.parse_args()
    if (args.snapshot is None) != (args.output_database is None):
        parser.error("provide both snapshot and output_database, or neither")
    from tools.dataset_paths import dataset_paths, dataset_root_summary

    paths = dataset_paths("panlex")
    print(dataset_root_summary(paths.root))
    if args.snapshot is None:
        snapshot = paths.source / "panlex-20190901-csv.zip"
        output_database = paths.generated / "panlex_korean_fallback.db"
    else:
        snapshot = args.snapshot
        output_database = args.output_database
    stats = build_index(
        snapshot.resolve(),
        output_database.resolve(),
        args.languages.split(","),
    )
    report = {
        **stats.__dict__,
        "coverage": {key: value.__dict__ for key, value in stats.coverage.items()},
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
