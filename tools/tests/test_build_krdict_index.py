import json
import sqlite3
import tempfile
import unittest
import zipfile
from contextlib import closing
from pathlib import Path

from tools.build_krdict_index import build_index, normalized_exact_key, reverse_keys


def feature(att, val):
    return {"att": att, "val": val}


class KoreanBasicDictionaryIndexBuilderTest(unittest.TestCase):
    def test_normalization_and_explicit_alternative_keys(self):
        self.assertEqual("café", normalized_exact_key("  CAFE\u0301 "))
        self.assertIn("eat", reverse_keys("en", "eat; consume"))
        self.assertIn("食べる", reverse_keys("ja", "たべる【食べる】。くう【食う・喰う】"))
        self.assertIn("たべる", reverse_keys("ja", "たべる【食べる】。くう【食う・喰う】"))

    def test_official_shape_builds_forward_and_reverse_rows_deterministically(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive_path = root / "official.zip"
            output_path = root / "index.db"
            document = {
                "LexicalResource": {
                    "GlobalInformation": {
                        "feat": [
                            feature("label", "한국어기초사전 - 국립국어원 제공"),
                            feature("creationDate", "2026/08/19 12:38:21"),
                        ]
                    },
                    "Lexicon": {
                        "LexicalEntry": [
                            {
                                "att": "id",
                                "val": "58272",
                                "feat": [feature("partOfSpeech", "동사")],
                                "Lemma": {"feat": feature("writtenForm", "먹다")},
                                "Sense": [
                                    {
                                        "att": "id",
                                        "val": "1",
                                        "feat": [feature("definition", "음식을 입에 넣어 삼키다.")],
                                        "Equivalent": [
                                            {
                                                "feat": [
                                                    feature("language", "영어"),
                                                    feature("lemma", "eat; consume"),
                                                    feature("definition", "To consume food."),
                                                ]
                                            },
                                            {
                                                "feat": [
                                                    feature("language", "일본어"),
                                                    feature("lemma", "たべる【食べる】"),
                                                ]
                                            },
                                        ],
                                    }
                                ],
                            },
                            {
                                "att": "id",
                                "val": "58272",
                                "feat": feature("lexicalUnit", "관용구"),
                                "Lemma": [
                                    {"feat": feature("writtenForm", "밥을 먹다")},
                                    {"feat": feature("variant", "밥을 먹고, 밥을 먹으면")},
                                ],
                            },
                            {"att": "id", "val": "broken", "Lemma": {}},
                        ]
                    },
                }
            }
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("1_2_20260819.json", json.dumps(document, ensure_ascii=False))

            stats = build_index(archive_path, output_path)

            self.assertEqual("2026-08-19", stats.release_id)
            self.assertEqual(2, stats.entry_count)
            self.assertEqual(1, stats.sense_count)
            self.assertEqual(2, stats.translation_count)
            self.assertEqual(1, stats.skipped_record_count)
            with closing(sqlite3.connect(output_path)) as database:
                forward = database.execute(
                    """
                    SELECT e.headword, e.part_of_speech, s.official_sense_id, t.display_term
                    FROM entries e
                    JOIN senses s USING(entry_pk)
                    JOIN translations t USING(sense_pk)
                    WHERE e.normalized_headword = ? AND t.language_tag = ?
                    ORDER BY e.entry_pk, s.sense_order, t.translation_order
                    """,
                    ("먹다", "en"),
                ).fetchall()
                reverse = database.execute(
                    """
                    SELECT e.headword, t.display_term
                    FROM reverse_keys r
                    JOIN translations t USING(translation_pk)
                    JOIN senses s USING(sense_pk)
                    JOIN entries e USING(entry_pk)
                    WHERE r.language_tag = ? AND r.normalized_term = ?
                    """,
                    ("ja", "食べる"),
                ).fetchall()
                metadata = dict(database.execute("SELECT key, value FROM metadata"))
                entry_ids = database.execute(
                    "SELECT official_entry_id, headword FROM entries ORDER BY entry_pk",
                ).fetchall()

            self.assertEqual([("먹다", "동사", "1", "eat; consume")], forward)
            self.assertEqual([("먹다", "たべる【食べる】")], reverse)
            self.assertEqual(
                [("58272", "먹다"), ("58272", "밥을 먹다")],
                entry_ids,
            )
            self.assertEqual("2026-08-19", metadata["release_id"])
            self.assertEqual("1", metadata["schema_version"])


if __name__ == "__main__":
    unittest.main()
