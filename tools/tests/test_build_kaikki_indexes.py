import gzip
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from tools.build_kaikki_indexes import build_indexes, normalized_exact_key
from tools.kaikki_language_config import KAIKKI_INDEX_SCHEMA_VERSION


FIXTURE_ENTRIES = (
    {
        "word": "Wasser",
        "lang": "German",
        "lang_code": "de",
        "pos": "noun",
        "sounds": [{"ipa": "/ˈvasɐ/"}, {"audio": "ignored.ogg"}],
        "forms": [
            {"form": "Wassers", "tags": ["genitive", "singular"]},
            {"form": "de-ndecl", "tags": ["inflection-template"]},
        ],
        "senses": [
            {
                "id": "en-Wasser-de-noun-1",
                "glosses": ["water", "a body of water"],
                "tags": ["neuter"],
                "examples": [
                    {
                        "type": "example",
                        "text": "Das Wasser ist kalt.",
                        "english": "The water is cold.",
                    },
                    {
                        "type": "example",
                        "text": "Das Wasser kocht.",
                        "english": "The water is boiling.",
                    },
                    {
                        "type": "example",
                        "text": "Wasser marsch!",
                        "english": "Turn on the water!",
                    },
                    {
                        "type": "quotation",
                        "text": "An externally attributed quotation",
                        "ref": "External publication",
                    },
                ],
            },
            {"id": "en-Wasser-de-noun-2", "glosses": ["urine"]},
        ],
    },
    {
        "word": "Wasser",
        "lang": "German",
        "lang_code": "de",
        "pos": "name",
        "etymology_number": 2,
        "senses": [{"id": "en-Wasser-de-name-1", "glosses": ["a surname"]}],
    },
    {
        "word": "água",
        "lang": "Portuguese",
        "lang_code": "pt",
        "pos": "noun",
        "senses": [{"id": "en-agua-pt-noun-1", "glosses": ["water"]}],
    },
    {"word": "redirect", "lang_code": "de", "redirect": "Wasser"},
    {"word": "ignored", "lang_code": "en", "senses": [{"glosses": ["ignored"]}]},
)


class KaikkiIndexBuilderTest(unittest.TestCase):
    def write_fixture(self, root: Path) -> Path:
        source = root / "raw.jsonl.gz"
        with gzip.open(source, "wt", encoding="utf-8") as stream:
            for entry in FIXTURE_ENTRIES:
                stream.write(json.dumps(entry, ensure_ascii=False))
                stream.write("\n")
        return source

    def test_builds_independent_compact_indexes_and_preserves_rich_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = build_indexes(
                self.write_fixture(root),
                root / "generated",
                root / "filtered",
                ("de", "pt"),
            )

            de = report.languages[0]
            self.assertEqual(3, de.raw_entry_count)
            self.assertEqual(1, de.headword_count)
            self.assertEqual(2, de.indexed_entry_count)
            self.assertEqual(3, de.sense_count)
            self.assertEqual(1, de.entries_with_pronunciation)
            self.assertEqual(1, de.entries_with_forms)
            self.assertEqual(1, de.entries_with_examples)
            self.assertEqual(1, de.entries_with_gender)
            self.assertGreater(de.compressed_source_bytes, 0)
            self.assertGreater(de.generated_database_bytes, 0)

            with closing(sqlite3.connect(root / "generated" / "de.db")) as database:
                schema_version = database.execute("PRAGMA user_version").fetchone()[0]
                quick_check = database.execute("PRAGMA quick_check").fetchone()[0]
                rows = database.execute(
                    "SELECT entry_id, payload FROM entries WHERE normalized_headword = ? ORDER BY entry_order",
                    (normalized_exact_key(" wasser "),),
                ).fetchall()
                metadata = dict(database.execute("SELECT key, value FROM metadata"))
            self.assertEqual(KAIKKI_INDEX_SCHEMA_VERSION, schema_version)
            self.assertEqual("ok", quick_check)
            self.assertEqual(2, len(rows), "homographs must remain separate")
            first = json.loads(rows[0][1])
            self.assertEqual("noun", first["p"])
            self.assertEqual(["/ˈvasɐ/"], first["n"])
            self.assertEqual("Wassers", first["f"][0]["f"])
            self.assertEqual("genitive singular", first["f"][0]["l"])
            self.assertEqual(1, first["fc"])
            self.assertEqual(["water", "a body of water"], first["s"][0]["g"])
            self.assertEqual(3, first["s"][0]["x"])
            self.assertEqual(
                ["Das Wasser ist kalt.", "Das Wasser kocht."],
                first["s"][0]["e"],
                "only the first two source-ordered examples should be retained",
            )
            self.assertNotIn("quotation", " ".join(first["s"][0]["e"]))
            self.assertEqual("neuter", first["s"][0]["d"])
            self.assertEqual("de", metadata["language_tag"])
            with closing(sqlite3.connect(root / "generated" / "de.db")) as database:
                morphology = database.execute(
                    "SELECT form, lemma FROM morphology_forms WHERE normalized_form = ?",
                    (normalized_exact_key("Wassers"),),
                ).fetchall()
            self.assertEqual([("Wassers", "Wasser")], morphology)

    def test_builds_compact_english_morphology_only_index(self):
        entries = (
            {
                "word": "be",
                "lang_code": "en",
                "pos": "verb",
                "forms": [
                    {"form": "is", "tags": ["present", "third-person", "singular"]},
                    {"form": "are", "tags": ["present", "plural"]},
                    {"form": "was", "tags": ["past", "singular"]},
                ],
                "senses": [{"glosses": ["To exist."]}],
            },
            {
                "word": "I",
                "lang_code": "en",
                "pos": "noun",
                "etymology_number": "3",
                "forms": [{"form": "Is", "tags": ["plural"]}],
                "senses": [
                    {
                        "glosses": ["Abbreviation of interstate."],
                        "tags": ["abbreviation", "alt-of"],
                    },
                    {
                        "glosses": ["Abbreviation of instruction."],
                        "tags": ["abbreviation", "alt-of"],
                    },
                ],
            },
            {
                "word": "axis",
                "lang_code": "en",
                "pos": "noun",
                "forms": [{"form": "axes", "tags": ["plural"]}],
                "senses": [{"glosses": ["A line around which something rotates."]}],
            },
            {
                "word": "axe",
                "lang_code": "en",
                "pos": "noun",
                "forms": [{"form": "axes", "tags": ["plural"]}],
                "senses": [{"glosses": ["A cutting tool."]}],
            },
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "raw.jsonl.gz"
            with gzip.open(source, "wt", encoding="utf-8") as stream:
                for entry in entries:
                    stream.write(json.dumps(entry) + "\n")

            report = build_indexes(
                source,
                root / "generated",
                root / "filtered",
                (),
                morphology_only_languages=("en",),
            )

            database_path = root / "generated" / "en-morphology.db"
            self.assertTrue(database_path.is_file())
            self.assertFalse((root / "filtered" / "en.jsonl.gz").exists())
            with closing(sqlite3.connect(database_path)) as database:
                mappings = database.execute(
                    "SELECT form, lemma FROM morphology_forms ORDER BY entry_order, form_order"
                ).fetchall()
                entry_count = database.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
            self.assertEqual(
                [
                    ("is", "be"),
                    ("are", "be"),
                    ("was", "be"),
                    ("axes", "axis"),
                    ("axes", "axe"),
                ],
                mappings,
            )
            self.assertEqual(0, entry_count)
            self.assertEqual(5, report.languages[0].morphology_row_count)
            self.assertEqual(1, report.languages[0].rejected_morphology_entry_count)
            self.assertEqual(1, report.languages[0].rejected_morphology_row_count)
            self.assertEqual({"nonlexical": 1}, report.languages[0].morphology_rejection_counts)

    def test_unicode_normalization_and_malformed_json_are_safe(self):
        self.assertEqual("café", normalized_exact_key(" CAFE\u0301 "))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "bad.jsonl.gz"
            with gzip.open(source, "wb") as stream:
                stream.write(b'{"lang_code":"de",not-json}\n')
            with self.assertRaisesRegex(ValueError, "Malformed Kaikki JSON"):
                build_indexes(source, root / "generated", root / "filtered", ("de",))
            self.assertFalse((root / "generated" / "de.db").exists())
            self.assertFalse((root / "generated" / "de.db.tmp").exists())
            self.assertFalse((root / "filtered" / "de.jsonl.gz").exists())
            self.assertFalse((root / "filtered" / "de.jsonl.gz.tmp").exists())

    def test_turkish_exact_key_uses_dotted_and_dotless_i_pairs(self):
        self.assertEqual("ı", normalized_exact_key("I", "tr"))
        self.assertEqual("i", normalized_exact_key("İ", "tr"))
        self.assertEqual("i", normalized_exact_key("i", "tr"))
        self.assertEqual("ı", normalized_exact_key("ı", "tr"))

    def test_validation_failure_keeps_previous_generated_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generated = root / "generated"
            filtered = root / "filtered"
            generated.mkdir()
            filtered.mkdir()
            previous_database = generated / "de.db"
            previous_source = filtered / "de.jsonl.gz"
            previous_database.write_bytes(b"previous database")
            previous_source.write_bytes(b"previous filtered source")
            malformed = root / "bad.jsonl.gz"
            with gzip.open(malformed, "wb") as stream:
                stream.write(b'{"lang_code":"de","word":"partial","senses":[]}' + b"\n")
                stream.write(b'{not-json}' + b"\n")

            with self.assertRaisesRegex(ValueError, "Malformed Kaikki JSON"):
                build_indexes(malformed, generated, filtered, ("de",))

            self.assertEqual(b"previous database", previous_database.read_bytes())
            self.assertEqual(b"previous filtered source", previous_source.read_bytes())
            self.assertFalse((generated / "de.db.tmp").exists())
            self.assertFalse((filtered / "de.jsonl.gz.tmp").exists())

    def test_unreviewed_language_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "Unreviewed Kaikki languages"):
                build_indexes(
                    self.write_fixture(root),
                    root / "generated",
                    root / "filtered",
                    ("xx",),
                )

    def test_expected_source_checksum_is_enforced_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "SHA-256 does not match"):
                build_indexes(
                    self.write_fixture(root),
                    root / "generated",
                    root / "filtered",
                    ("de",),
                    expected_source_sha256="0" * 64,
                )
            self.assertFalse((root / "generated").exists())


if __name__ == "__main__":
    unittest.main()
