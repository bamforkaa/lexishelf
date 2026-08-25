#!/usr/bin/env python3
"""Build a compact exact-lookup SQLite index from the official JMdict_e XML."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import sqlite3
import time
import unicodedata
import xml.etree.ElementTree as ET
from contextlib import closing
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Iterator


INDEX_SCHEMA_VERSION = 1
OFFICIAL_SOURCE_URL = "https://www.edrdg.org/jmdict/j_jmdict.html"
OFFICIAL_DOWNLOAD_URL = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"
LICENSE_NAME = "Creative Commons Attribution-ShareAlike 4.0 International"
LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"
_CREATED = re.compile(rb"JMdict created:\s*(\d{4}-\d{2}-\d{2})")
_WHITESPACE = re.compile(r"\s+")


@dataclass(frozen=True)
class BuildStats:
    release_id: str
    source_sha256: str
    entry_count: int
    lookup_key_count: int
    sense_count: int
    conversion_seconds: float
    generated_database_bytes: int


def normalized_exact_key(value: str) -> str:
    return _WHITESPACE.sub(" ", unicodedata.normalize("NFC", value).strip()).casefold()


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _open_xml(path: Path) -> BinaryIO:
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rb")
    return path.open("rb")


def _release_id(path: Path) -> str:
    with _open_xml(path) as source:
        header = source.read(256 * 1024)
    match = _CREATED.search(header)
    if match is None:
        raise ValueError("JMdict creation date comment is missing")
    return match.group(1).decode("ascii")


def _texts(parent: ET.Element, name: str) -> list[str]:
    return [text for node in parent.findall(name) if (text := (node.text or "").strip())]


def _optional_attributes(node: ET.Element) -> dict[str, str]:
    return {key: value for key, value in node.attrib.items() if value}


def _entry_payload(entry: ET.Element) -> tuple[dict[str, object], list[tuple[str, int, int]]]:
    ent_seq = (entry.findtext("ent_seq") or "").strip()
    if not ent_seq.isdigit():
        raise ValueError("JMdict entry has an invalid ent_seq")

    writings: list[dict[str, object]] = []
    readings: list[dict[str, object]] = []
    lookup_keys: list[tuple[str, int, int]] = []
    for order, node in enumerate(entry.findall("k_ele")):
        text = (node.findtext("keb") or "").strip()
        if not text:
            raise ValueError(f"JMdict entry {ent_seq} has an empty keb")
        priorities = _texts(node, "ke_pri")
        writings.append({"t": text, "i": _texts(node, "ke_inf"), "p": priorities})
        lookup_keys.append((normalized_exact_key(text), 0, order if not priorities else -order - 1))

    for order, node in enumerate(entry.findall("r_ele")):
        text = (node.findtext("reb") or "").strip()
        if not text:
            raise ValueError(f"JMdict entry {ent_seq} has an empty reb")
        priorities = _texts(node, "re_pri")
        readings.append(
            {
                "t": text,
                "n": node.find("re_nokanji") is not None,
                "x": _texts(node, "re_restr"),
                "i": _texts(node, "re_inf"),
                "p": priorities,
            }
        )
        lookup_keys.append((normalized_exact_key(text), 1, order if not priorities else -order - 1))

    senses: list[dict[str, object]] = []
    inherited_pos: list[str] = []
    for order, node in enumerate(entry.findall("sense")):
        explicit_pos = _texts(node, "pos")
        if explicit_pos:
            inherited_pos = explicit_pos
        glosses = []
        for gloss in node.findall("gloss"):
            text = (gloss.text or "").strip()
            if text:
                attributes = _optional_attributes(gloss)
                glosses.append(
                    {
                        "t": text,
                        "l": attributes.pop("{http://www.w3.org/XML/1998/namespace}lang", "eng"),
                        "a": attributes,
                    }
                )
        sources = []
        for source in node.findall("lsource"):
            text = (source.text or "").strip()
            sources.append({"t": text, "a": _optional_attributes(source)})
        senses.append(
            {
                "o": order,
                "k": _texts(node, "stagk"),
                "r": _texts(node, "stagr"),
                "p": list(inherited_pos),
                "xp": _texts(node, "xref"),
                "a": _texts(node, "ant"),
                "f": _texts(node, "field"),
                "m": _texts(node, "misc"),
                "i": _texts(node, "s_inf"),
                "l": sources,
                "d": _texts(node, "dial"),
                "g": glosses,
            }
        )

    if not readings:
        raise ValueError(f"JMdict entry {ent_seq} has no reading")
    return {"e": ent_seq, "k": writings, "r": readings, "s": senses}, lookup_keys


def iter_entries(path: Path) -> Iterator[tuple[dict[str, object], list[tuple[str, int, int]]]]:
    with _open_xml(path) as source:
        for _event, element in ET.iterparse(source, events=("end",)):
            if element.tag == "entry":
                yield _entry_payload(element)
                element.clear()


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
            ent_seq INTEGER PRIMARY KEY NOT NULL,
            entry_order INTEGER NOT NULL UNIQUE,
            payload BLOB NOT NULL
        );

        CREATE TABLE lookup_keys (
            normalized_key TEXT NOT NULL,
            ent_seq INTEGER NOT NULL,
            match_kind INTEGER NOT NULL,
            element_order INTEGER NOT NULL,
            has_priority INTEGER NOT NULL,
            PRIMARY KEY(normalized_key, ent_seq, match_kind, element_order),
            FOREIGN KEY(ent_seq) REFERENCES entries(ent_seq) ON DELETE CASCADE
        ) WITHOUT ROWID;
        """
    )


def build_index(input_xml: Path, output_database: Path) -> BuildStats:
    if not input_xml.is_file():
        raise FileNotFoundError(input_xml)
    output_database.parent.mkdir(parents=True, exist_ok=True)
    temporary = output_database.with_name(output_database.name + ".tmp")
    if temporary.exists():
        temporary.unlink()

    started = time.perf_counter()
    release_id = _release_id(input_xml)
    source_sha256 = _sha256(input_xml)
    entry_count = lookup_key_count = sense_count = 0
    try:
        with closing(sqlite3.connect(temporary)) as database:
            database.execute("PRAGMA foreign_keys = ON")
            _configure_database(database)
            for entry_order, (payload, keys) in enumerate(iter_entries(input_xml)):
                ent_seq = str(payload["e"])
                encoded_payload = json.dumps(
                    payload,
                    ensure_ascii=False,
                    separators=(",", ":"),
                ).encode("utf-8")
                database.execute(
                    "INSERT INTO entries(ent_seq, entry_order, payload) VALUES (?, ?, ?)",
                    (ent_seq, entry_order, encoded_payload),
                )
                for key, match_kind, encoded_order in keys:
                    if not key:
                        continue
                    has_priority = int(encoded_order < 0)
                    element_order = -encoded_order - 1 if has_priority else encoded_order
                    inserted = database.execute(
                        """
                        INSERT OR IGNORE INTO lookup_keys(
                            normalized_key, ent_seq, match_kind, element_order, has_priority
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        (key, ent_seq, match_kind, element_order, has_priority),
                    )
                    lookup_key_count += inserted.rowcount
                entry_count += 1
                sense_count += len(payload["s"])

            metadata = {
                "schema_version": str(INDEX_SCHEMA_VERSION),
                "release_id": release_id,
                "source_sha256": source_sha256,
                "entry_count": str(entry_count),
                "source_artifact": input_xml.name,
                "source_url": OFFICIAL_DOWNLOAD_URL,
                "license_name": LICENSE_NAME,
                "license_url": LICENSE_URL,
                "payload_codec": "json-utf8",
            }
            database.executemany("INSERT INTO metadata(key, value) VALUES (?, ?)", metadata.items())
            database.execute("PRAGMA user_version = 1")
            database.execute("ANALYZE")
            database.commit()
        os.replace(temporary, output_database)
    except Exception:
        if temporary.exists():
            temporary.unlink()
        raise

    return BuildStats(
        release_id=release_id,
        source_sha256=source_sha256,
        entry_count=entry_count,
        lookup_key_count=lookup_key_count,
        sense_count=sense_count,
        conversion_seconds=time.perf_counter() - started,
        generated_database_bytes=output_database.stat().st_size,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_xml", type=Path, nargs="?")
    parser.add_argument("output_database", type=Path, nargs="?")
    arguments = parser.parse_args()
    if (arguments.input_xml is None) != (arguments.output_database is None):
        parser.error("provide both input_xml and output_database, or neither")
    from tools.dataset_paths import dataset_paths, dataset_root_summary

    paths = dataset_paths("jmdict")
    print(dataset_root_summary(paths.root))
    if arguments.input_xml is None:
        input_xml = paths.source / "JMdict_e.gz"
        output_database = paths.generated / "jmdict.db"
    else:
        input_xml = arguments.input_xml
        output_database = arguments.output_database
    print(json.dumps(build_index(input_xml.resolve(), output_database.resolve()).__dict__, indent=2))


if __name__ == "__main__":
    main()
