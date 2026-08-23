import gzip
import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from tools.build_jmdict_index import build_index, normalized_exact_key


FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE JMdict [
<!ELEMENT JMdict (entry*)>
<!ENTITY n "noun (common) (futsuumeishi)">
<!ENTITY v1 "Ichidan verb">
]>
<!-- JMdict created: 2026-08-23 -->
<JMdict>
  <entry><ent_seq>1001</ent_seq>
    <k_ele><keb>食べる</keb><ke_inf>common spelling</ke_inf><ke_pri>ichi1</ke_pri></k_ele>
    <k_ele><keb>喰べる</keb></k_ele>
    <r_ele><reb>たべる</reb><re_restr>食べる</re_restr><re_pri>ichi1</re_pri></r_ele>
    <r_ele><reb>くべる</reb><re_restr>喰べる</re_restr></r_ele>
    <sense><stagk>食べる</stagk><stagr>たべる</stagr><pos>&v1;</pos>
      <xref>食う・1</xref><ant>吐く・1</ant><field>food term</field><misc>common</misc>
      <s_inf>literal use</s_inf><lsource xml:lang="eng">eat</lsource><dial>Kansai-ben</dial>
      <gloss>to eat</gloss><gloss>to consume</gloss></sense>
    <sense><gloss>to live on</gloss></sense>
  </entry>
  <entry><ent_seq>1002</ent_seq>
    <r_ele><reb>こんにちは</reb><re_nokanji/></r_ele>
    <sense><pos>&n;</pos><gloss>hello</gloss></sense>
  </entry>
</JMdict>"""


class JmDictIndexBuilderTest(unittest.TestCase):
    def write_fixture(self, root: Path, text: str = FIXTURE) -> Path:
        source = root / "JMdict_e.gz"
        with gzip.open(source, "wt", encoding="utf-8") as stream:
            stream.write(text)
        return source

    def test_preserves_writings_readings_restrictions_pos_senses_and_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "jmdict.db"
            stats = build_index(self.write_fixture(root), output)

            self.assertEqual("2026-08-23", stats.release_id)
            self.assertEqual(2, stats.entry_count)
            self.assertEqual(3, stats.sense_count)
            with closing(sqlite3.connect(output)) as database:
                row = database.execute(
                    """
                    SELECT e.ent_seq, l.match_kind, l.has_priority, e.payload
                    FROM lookup_keys l JOIN entries e USING(ent_seq)
                    WHERE l.normalized_key = ?
                    """,
                    (normalized_exact_key(" 食べる "),),
                ).fetchone()
                metadata = dict(database.execute("SELECT key, value FROM metadata"))

            payload = json.loads(row[3])
            self.assertEqual((1001, 0, 1), row[:3])
            self.assertEqual(["食べる", "喰べる"], [item["t"] for item in payload["k"]])
            self.assertEqual(["食べる"], payload["r"][0]["x"])
            self.assertTrue(payload["r"][1]["x"])
            self.assertEqual(["食べる"], payload["s"][0]["k"])
            self.assertEqual(["たべる"], payload["s"][0]["r"])
            self.assertEqual(["Ichidan verb"], payload["s"][0]["p"])
            self.assertEqual(["Ichidan verb"], payload["s"][1]["p"])
            self.assertEqual(["to eat", "to consume"], [g["t"] for g in payload["s"][0]["g"]])
            self.assertEqual(["食う・1"], payload["s"][0]["xp"])
            self.assertEqual(["吐く・1"], payload["s"][0]["a"])
            self.assertEqual(["food term"], payload["s"][0]["f"])
            self.assertEqual(["common"], payload["s"][0]["m"])
            self.assertEqual(["literal use"], payload["s"][0]["i"])
            self.assertEqual("eat", payload["s"][0]["l"][0]["t"])
            self.assertEqual(["Kansai-ben"], payload["s"][0]["d"])
            self.assertEqual("2", metadata["entry_count"])

    def test_kana_only_entry_and_unicode_normalization_are_indexed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "jmdict.db"
            build_index(self.write_fixture(root), output)
            with closing(sqlite3.connect(output)) as database:
                rows = database.execute(
                    "SELECT ent_seq, match_kind FROM lookup_keys WHERE normalized_key = ?",
                    ("こんにちは",),
                ).fetchall()
            self.assertEqual([(1002, 1)], rows)
            self.assertEqual("café", normalized_exact_key(" CAFE\u0301 "))

    def test_malformed_input_does_not_leave_partial_database(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "jmdict.db"
            with self.assertRaises(Exception):
                build_index(self.write_fixture(root, FIXTURE[:-20]), output)
            self.assertFalse(output.exists())
            self.assertFalse(output.with_name("jmdict.db.tmp").exists())


if __name__ == "__main__":
    unittest.main()
