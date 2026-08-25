#!/usr/bin/env python3
"""Build the app's read-only Korean Basic Dictionary index from the official JSON ZIP."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sqlite3
import unicodedata
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Iterator


INDEX_SCHEMA_VERSION = 1
OFFICIAL_SOURCE_URL = "https://krdict.korean.go.kr/"
OFFICIAL_DOWNLOAD_URL = "https://krdict.korean.go.kr/download/downloadPopup"
LICENSE_NAME = "Creative Commons Attribution-ShareAlike 2.0 Korea"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/2.0/kr/"

LANGUAGE_TAGS = {
    "영어": "en",
    "일본어": "ja",
    "프랑스어": "fr",
    "스페인어": "es",
    "아랍어": "ar",
    "몽골어": "mn",
    "베트남어": "vi",
    "타이어": "th",
    "인도네시아어": "id",
    "러시아어": "ru",
    "중국어": "zh",
}

_WHITESPACE = re.compile(r"\s+")
_GENERIC_ALTERNATIVE = re.compile(r"[;,，；、،]")
_JAPANESE_SEGMENT = re.compile(r"。")
_JAPANESE_BRACKETS = re.compile(r"[【〖]([^】〗]+)[】〗]")


@dataclass(frozen=True)
class BuildStats:
    release_id: str
    entry_count: int
    sense_count: int
    translation_count: int
    reverse_key_count: int
    skipped_record_count: int


def listify(value: Any) -> list[Any]:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def feature_map(node: Any) -> dict[str, str]:
    if not isinstance(node, dict):
        return {}
    result: dict[str, str] = {}
    for feature in listify(node.get("feat")):
        if not isinstance(feature, dict):
            continue
        name = feature.get("att")
        value = feature.get("val")
        if isinstance(name, str) and isinstance(value, str) and name not in result:
            result[name] = value
    return result


def first_feature_value(node: Any, name: str) -> str | None:
    for item in listify(node):
        value = feature_map(item).get(name)
        if value:
            return value
    return None


def normalized_exact_key(value: str) -> str:
    normalized = unicodedata.normalize("NFC", value)
    return _WHITESPACE.sub(" ", normalized.strip()).lower()


def reverse_keys(language_tag: str, display_term: str) -> tuple[str, ...]:
    """Return only explicitly written equivalents; never generate a translation."""
    candidates = {display_term}
    candidates.update(_GENERIC_ALTERNATIVE.split(display_term))
    if language_tag == "ja":
        segments = _JAPANESE_SEGMENT.split(display_term)
        candidates.update(segments)
        for segment in segments:
            bracket_values = _JAPANESE_BRACKETS.findall(segment)
            candidates.add(_JAPANESE_BRACKETS.sub("", segment))
            for value in bracket_values:
                candidates.update(re.split(r"[・,，]", value))

    normalized = {
        normalized_exact_key(candidate)
        for candidate in candidates
        if candidate and normalized_exact_key(candidate)
    }
    return tuple(sorted(normalized))


def _creation_date(document: dict[str, Any]) -> str:
    features = feature_map(document.get("LexicalResource", {}).get("GlobalInformation", {}))
    raw = features.get("creationDate", "")
    match = re.fullmatch(r"(\d{4})/(\d{2})/(\d{2})(?:\s.*)?", raw)
    if not match:
        raise ValueError(f"Unsupported or missing creationDate: {raw!r}")
    return "-".join(match.groups())


def _lexical_entries(document: dict[str, Any]) -> list[Any]:
    resource = document.get("LexicalResource")
    if not isinstance(resource, dict):
        raise ValueError("Missing LexicalResource object")
    lexicon = resource.get("Lexicon")
    if not isinstance(lexicon, dict):
        raise ValueError("Missing Lexicon object")
    entries = lexicon.get("LexicalEntry")
    if not isinstance(entries, list):
        raise ValueError("LexicalEntry must be an array")
    return entries


def _ordered_json_entries(archive: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    entries = [entry for entry in archive.infolist() if entry.filename.lower().endswith(".json")]
    if not entries:
        raise ValueError("Official archive contains no JSON files")

    def order(entry: zipfile.ZipInfo) -> tuple[int, str]:
        prefix = entry.filename.split("_", 1)[0]
        return (int(prefix) if prefix.isdigit() else 2**31 - 1, entry.filename)

    return sorted(entries, key=order)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _configure_database(database: sqlite3.Connection) -> None:
    database.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA temp_store = MEMORY;
        PRAGMA locking_mode = EXCLUSIVE;

        CREATE TABLE metadata (
            key TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        ) WITHOUT ROWID;

        CREATE TABLE entries (
            entry_pk INTEGER PRIMARY KEY NOT NULL,
            official_entry_id TEXT NOT NULL,
            headword TEXT NOT NULL,
            normalized_headword TEXT NOT NULL,
            part_of_speech TEXT
        );

        CREATE TABLE senses (
            sense_pk INTEGER PRIMARY KEY NOT NULL,
            entry_pk INTEGER NOT NULL,
            official_sense_id TEXT NOT NULL,
            sense_order INTEGER NOT NULL,
            FOREIGN KEY (entry_pk) REFERENCES entries(entry_pk) ON DELETE CASCADE,
            UNIQUE (entry_pk, official_sense_id)
        );

        CREATE TABLE translations (
            translation_pk INTEGER PRIMARY KEY NOT NULL,
            sense_pk INTEGER NOT NULL,
            language_tag TEXT NOT NULL,
            translation_order INTEGER NOT NULL,
            display_term TEXT NOT NULL,
            FOREIGN KEY (sense_pk) REFERENCES senses(sense_pk) ON DELETE CASCADE,
            UNIQUE (sense_pk, language_tag, translation_order)
        );

        CREATE TABLE reverse_keys (
            language_tag TEXT NOT NULL,
            normalized_term TEXT NOT NULL,
            translation_pk INTEGER NOT NULL,
            PRIMARY KEY (language_tag, normalized_term, translation_pk),
            FOREIGN KEY (translation_pk) REFERENCES translations(translation_pk) ON DELETE CASCADE
        ) WITHOUT ROWID;
        """
    )


def build_index(input_zip: Path, output_database: Path) -> BuildStats:
    if not input_zip.is_file():
        raise FileNotFoundError(input_zip)
    output_database.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_database.with_name(output_database.name + ".tmp")
    if temporary.exists():
        temporary.unlink()

    database = sqlite3.connect(temporary)
    release_id: str | None = None
    entry_count = sense_count = translation_count = skipped_count = 0
    source_order = 0
    try:
        database.execute("PRAGMA foreign_keys = ON")
        _configure_database(database)
        with zipfile.ZipFile(input_zip) as archive:
            for zip_entry in _ordered_json_entries(archive):
                with archive.open(zip_entry) as raw:
                    document = json.load(raw)
                document_release = _creation_date(document)
                if release_id is None:
                    release_id = document_release
                elif release_id != document_release:
                    raise ValueError("Official archive contains mixed creation dates")

                for entry in _lexical_entries(document):
                    source_order += 1
                    if not isinstance(entry, dict):
                        skipped_count += 1
                        continue
                    entry_id = entry.get("val")
                    headword = first_feature_value(entry.get("Lemma"), "writtenForm")
                    if not isinstance(entry_id, str) or not entry_id or not headword:
                        skipped_count += 1
                        continue
                    headword = headword.strip()
                    entry_features = feature_map(entry)
                    database.execute(
                        """
                        INSERT INTO entries(
                            entry_pk, official_entry_id, headword, normalized_headword,
                            part_of_speech
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        (
                            source_order,
                            entry_id,
                            headword,
                            normalized_exact_key(headword),
                            entry_features.get("partOfSpeech"),
                        ),
                    )
                    entry_count += 1

                    for sense_order, sense in enumerate(listify(entry.get("Sense"))):
                        if not isinstance(sense, dict):
                            skipped_count += 1
                            continue
                        sense_id = sense.get("val")
                        if not isinstance(sense_id, str) or not sense_id:
                            skipped_count += 1
                            continue
                        sense_count += 1
                        sense_pk = sense_count
                        database.execute(
                            """
                            INSERT INTO senses(
                                sense_pk, entry_pk, official_sense_id, sense_order
                            )
                            VALUES (?, ?, ?, ?)
                            """,
                            (sense_pk, source_order, sense_id, sense_order),
                        )

                        language_orders: dict[str, int] = {}
                        for equivalent in listify(sense.get("Equivalent")):
                            equivalent_features = feature_map(equivalent)
                            language_tag = LANGUAGE_TAGS.get(equivalent_features.get("language", ""))
                            term = equivalent_features.get("lemma", "").strip()
                            if not language_tag or not term:
                                skipped_count += 1
                                continue
                            translation_order = language_orders.get(language_tag, 0)
                            language_orders[language_tag] = translation_order + 1
                            translation_count += 1
                            translation_pk = translation_count
                            database.execute(
                                """
                                INSERT INTO translations(
                                    translation_pk, sense_pk, language_tag, translation_order,
                                    display_term
                                ) VALUES (?, ?, ?, ?, ?)
                                """,
                                (
                                    translation_pk,
                                    sense_pk,
                                    language_tag,
                                    translation_order,
                                    term,
                                ),
                            )
                            database.executemany(
                                """
                                INSERT OR IGNORE INTO reverse_keys(
                                    language_tag, normalized_term, translation_pk
                                ) VALUES (?, ?, ?)
                                """,
                                (
                                    (language_tag, key, translation_pk)
                                    for key in reverse_keys(language_tag, term)
                                ),
                            )
                database.commit()

        if release_id is None:
            raise ValueError("Official archive contains no release metadata")

        database.executescript(
            """
            CREATE INDEX index_entries_headword
                ON entries(normalized_headword, entry_pk);
            CREATE INDEX index_senses_entry
                ON senses(entry_pk, sense_order);
            CREATE INDEX index_translations_language
                ON translations(sense_pk, language_tag, translation_order);
            CREATE INDEX index_reverse_exact
                ON reverse_keys(language_tag, normalized_term);
            PRAGMA user_version = 1;
            """
        )
        reverse_key_count = database.execute("SELECT COUNT(*) FROM reverse_keys").fetchone()[0]
        metadata = {
            "schema_version": str(INDEX_SCHEMA_VERSION),
            "source_name": "한국어기초사전 - 국립국어원 제공",
            "source_url": OFFICIAL_SOURCE_URL,
            "download_url": OFFICIAL_DOWNLOAD_URL,
            "release_id": release_id,
            "entry_count": str(entry_count),
            "sense_count": str(sense_count),
            "translation_count": str(translation_count),
            "reverse_key_count": str(reverse_key_count),
            "license_name": LICENSE_NAME,
            "license_url": LICENSE_URL,
            "source_archive_sha256": _sha256(input_zip),
        }
        database.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            sorted(metadata.items()),
        )
        database.commit()
        database.execute("VACUUM")
        database.close()
        os.replace(temporary, output_database)
        return BuildStats(
            release_id=release_id,
            entry_count=entry_count,
            sense_count=sense_count,
            translation_count=translation_count,
            reverse_key_count=reverse_key_count,
            skipped_record_count=skipped_count,
        )
    except BaseException:
        database.close()
        if temporary.exists():
            temporary.unlink()
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_zip", type=Path, nargs="?")
    parser.add_argument("output_database", type=Path, nargs="?")
    args = parser.parse_args()
    if (args.input_zip is None) != (args.output_database is None):
        parser.error("provide both input_zip and output_database, or neither")
    from tools.dataset_paths import dataset_paths, dataset_root_summary

    paths = dataset_paths("korean-basic")
    print(dataset_root_summary(paths.root))
    if args.input_zip is None:
        input_zip = paths.source / "korean-basic-dictionary-json.zip"
        output_database = paths.generated / "korean_basic_dictionary.db"
    else:
        input_zip = args.input_zip
        output_database = args.output_database
    stats = build_index(input_zip.resolve(), output_database.resolve())
    print(json.dumps(stats.__dict__, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
