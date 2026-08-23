import csv
import io
import json
import sqlite3
import tempfile
import unittest
import zipfile
from contextlib import closing
from pathlib import Path

from tools.build_panlex_index import (
    BCP47_TO_PANLEX_UID,
    PANLEX_LICENSE_NAME,
    build_index,
    normalized_exact_key,
)


LICENSE = """PanLex is a project of The Long Now Foundation, and provides CSV and JSON
snapshots of the PanLex Database, PanLex Lite, and the PanLex Swadesh Lists
(“the Data”) under the CC0 1.0 Universal License. You can copy, modify, and
distribute the Data, even for commercial purposes, all without asking permission.
"""


def csv_text(fieldnames, rows):
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fieldnames)
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue()


def write_snapshot(path: Path, *, malformed_expr=False, license_text=LICENSE):
    root = "panlex-20190901-csv/"
    tables = {
        "langvar.csv": csv_text(
            ["id", "lang_code", "var_code"],
            [
                {"id": 1, "lang_code": "kor", "var_code": 0},
                {"id": 2, "lang_code": "deu", "var_code": 0},
                {"id": 3, "lang_code": "hin", "var_code": 0},
                {"id": 4, "lang_code": "deu", "var_code": 1},
            ],
        ),
        "expr.csv": csv_text(
            ["id", "langvar", "txt", "txt_degr"],
            [
                {"id": 10, "langvar": 1, "txt": "물", "txt_degr": "물"},
                {"id": 11, "langvar": 1, "txt": "수", "txt_degr": "수"},
                {"id": 12, "langvar": 1, "txt": "카페", "txt_degr": "카페"},
                {
                    "id": "invalid" if malformed_expr else 20,
                    "langvar": 2,
                    "txt": "Wasser",
                    "txt_degr": "wasser",
                },
                {"id": 21, "langvar": 2, "txt": "Cafe\u0301", "txt_degr": "cafe"},
                {"id": 22, "langvar": 4, "txt": "Dialekt", "txt_degr": "dialekt"},
                {"id": 30, "langvar": 3, "txt": "पानी", "txt_degr": "पानी"},
            ],
        ),
        "denotation.csv": csv_text(
            ["id", "meaning", "expr"],
            [
                {"id": 1, "meaning": 100, "expr": 10},
                {"id": 2, "meaning": 100, "expr": 20},
                {"id": 3, "meaning": 101, "expr": 10},
                {"id": 4, "meaning": 101, "expr": 20},
                {"id": 5, "meaning": 102, "expr": 10},
                {"id": 6, "meaning": 102, "expr": 20},
                {"id": 7, "meaning": 103, "expr": 11},
                {"id": 8, "meaning": 103, "expr": 20},
                {"id": 9, "meaning": 104, "expr": 12},
                {"id": 10, "meaning": 104, "expr": 21},
                {"id": 11, "meaning": 105, "expr": 10},
                {"id": 12, "meaning": 105, "expr": 30},
                {"id": 13, "meaning": 106, "expr": 10},
                {"id": 14, "meaning": 106, "expr": 22},
            ],
        ),
        "meaning.csv": csv_text(
            ["id", "source"],
            [
                {"id": 100, "source": 1},
                {"id": 101, "source": 2},
                {"id": 102, "source": 3},
                {"id": 103, "source": 4},
                {"id": 104, "source": 5},
                {"id": 105, "source": 6},
                {"id": 106, "source": 7},
            ],
        ),
        "source.csv": csv_text(
            ["id", "quality", "grp"],
            [
                {"id": 1, "quality": 8, "grp": 10},
                {"id": 2, "quality": 7, "grp": 20},
                {"id": 3, "quality": 9, "grp": 20},
                {"id": 4, "quality": 5, "grp": 40},
                {"id": 5, "quality": 6, "grp": 50},
                {"id": 6, "quality": 4, "grp": 60},
                {"id": 7, "quality": 9, "grp": 70},
            ],
        ),
    }
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(root, "")
        for name, content in tables.items():
            archive.writestr(root + name, content)
        archive.writestr(root + "LICENSE.txt", license_text)


class PanLexIndexBuilderTest(unittest.TestCase):
    def test_normalization_is_nfc_trimmed_whitespace_collapsed_and_case_insensitive(self):
        self.assertEqual("café au lait", normalized_exact_key("  CAFE\u0301   au lait "))

    def test_builds_direct_relations_ranking_variety_mapping_and_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot = root / "panlex-20190901-csv.zip"
            output = root / "panlex.db"
            write_snapshot(snapshot)

            stats = build_index(snapshot, output, ("de", "hi"))

            self.assertEqual("2019-09-01", stats.release_id)
            self.assertEqual(3, stats.coverage["de"].direct_relation_count)
            self.assertEqual(1, stats.coverage["hi"].direct_relation_count)
            self.assertEqual(("de", "hi"), stats.selected_language_tags)
            self.assertGreater(stats.generated_database_bytes, 0)
            with closing(sqlite3.connect(output)) as database:
                water = database.execute(
                    """
                    SELECT korean_text, source_attestation_count, source_group_count,
                           translation_quality, representative_meaning_id,
                           representative_source_id
                    FROM relations
                    WHERE foreign_language_tag='de' AND normalized_foreign='wasser'
                    ORDER BY translation_quality DESC, korean_text
                    """
                ).fetchall()
                self.assertEqual(("물", 3, 2, 17, 102, 3), water[0])
                self.assertEqual("수", water[1][0])
                self.assertEqual(
                    [("de", BCP47_TO_PANLEX_UID["de"]), ("hi", BCP47_TO_PANLEX_UID["hi"]),
                     ("ko", BCP47_TO_PANLEX_UID["ko"])],
                    database.execute(
                        "SELECT language_tag, panlex_uid FROM language_varieties ORDER BY language_tag"
                    ).fetchall(),
                )
                metadata = dict(database.execute("SELECT key, value FROM metadata"))
                self.assertEqual(PANLEX_LICENSE_NAME, metadata["license_name"])
                self.assertEqual("distance-1 same-source meaning co-denotations only", metadata["translation_policy"])
                self.assertEqual("4", metadata["relation_count"])
                self.assertEqual(64, len(metadata["source_archive_sha256"]))
                self.assertEqual(1, database.execute("PRAGMA user_version").fetchone()[0])

    def test_unicode_multiple_translations_and_unselected_variety_are_preserved_correctly(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot = root / "panlex-20190901-csv.zip"
            output = root / "panlex.db"
            write_snapshot(snapshot)
            build_index(snapshot, output, ("de",))

            with closing(sqlite3.connect(output)) as database:
                self.assertEqual(
                    [("Cafe\u0301", "카페", "café")],
                    database.execute(
                        """
                        SELECT foreign_text, korean_text, normalized_foreign FROM relations
                        WHERE foreign_language_tag='de' AND normalized_foreign='café'
                        """
                    ).fetchall(),
                )
                self.assertEqual(
                    0,
                    database.execute("SELECT COUNT(*) FROM relations WHERE foreign_text='Dialekt'").fetchone()[0],
                )

    def test_malformed_record_removes_partial_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot = root / "panlex-20190901-csv.zip"
            output = root / "panlex.db"
            write_snapshot(snapshot, malformed_expr=True)

            with self.assertRaisesRegex(ValueError, "Invalid expr.csv.id"):
                build_index(snapshot, output, ("de",))

            self.assertFalse(output.exists())
            self.assertFalse(output.with_name(output.name + ".tmp").exists())
            self.assertFalse(output.with_name(output.name + ".stage").exists())

    def test_unreviewed_variety_and_unverified_license_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            snapshot = root / "panlex-20190901-csv.zip"
            output = root / "panlex.db"
            write_snapshot(snapshot, license_text="unknown")

            with self.assertRaisesRegex(ValueError, "CC0 grant"):
                build_index(snapshot, output, ("de",))
            with self.assertRaisesRegex(ValueError, "No reviewed PanLex variety mapping"):
                build_index(snapshot, output, ("xx",))


if __name__ == "__main__":
    unittest.main()
