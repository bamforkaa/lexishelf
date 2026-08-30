import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.report_kaikki_usage_labels import create_report


class KaikkiUsageLabelReportTest(unittest.TestCase):
    def test_counts_only_gloss_senses_and_keeps_sense_boundaries(self):
        entries = [
            {
                "word": "sehen",
                "lang_code": "de",
                "pos": "verb",
                "tags": ["strong"],
                "forms": [{"form": "sah", "tags": ["past"]}],
                "senses": [
                    {
                        "glosses": ["to see"],
                        "tags": ["informal", "transitive", "form-of"],
                        "raw_tags": ["with an object"],
                        "topics": ["vision"],
                    },
                    {"glosses": ["to look"], "tags": ["intransitive"]},
                    {"tags": ["slang"]},
                ],
            }
        ]
        with tempfile.TemporaryDirectory() as directory:
            filtered = Path(directory)
            with gzip.open(filtered / "de.jsonl.gz", "wt", encoding="utf-8") as stream:
                for entry in entries:
                    stream.write(json.dumps(entry) + "\n")

            report = create_report(filtered, ("de",))

        language = report["languages"][0]
        self.assertEqual(1, language["indexedEntryCount"])
        self.assertEqual(2, language["senseCount"])
        self.assertEqual(2, language["labeledSenseCount"])
        self.assertEqual(1, language["normalizedLabels"]["informal"])
        self.assertEqual(1, language["normalizedLabels"]["transitive"])
        self.assertEqual(1, language["normalizedLabels"]["intransitive"])
        self.assertNotIn("form-of", language["normalizedLabels"])
        self.assertEqual(1, language["sourceFieldCounts"]["form.tags"])
        self.assertEqual(1, language["sourceFieldCounts"]["sense.raw_tags"])
        self.assertEqual(1, language["sourceFieldCounts"]["sense.topics"])


if __name__ == "__main__":
    unittest.main()
