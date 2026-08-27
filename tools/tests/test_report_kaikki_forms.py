import gzip
import json
import tempfile
import unittest
from pathlib import Path

from tools.report_kaikki_forms import inspect_filtered_source


class KaikkiFormReportTest(unittest.TestCase):
    def test_report_measures_raw_and_selected_distribution(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "de.jsonl.gz"
            entries = [
                {
                    "word": "Haus",
                    "pos": "noun",
                    "senses": [{"glosses": ["house"]}],
                    "forms": [
                        {"form": "Häuser", "tags": ["plural"]},
                        {"form": "Hauses", "tags": ["genitive", "singular"]},
                        {"form": "Häuschen", "tags": ["diminutive"]},
                    ],
                },
                {
                    "word": "und",
                    "pos": "conj",
                    "senses": [{"glosses": ["and"]}],
                },
                {"word": "redirect", "forms": [{"form": "x", "tags": ["plural"]}]},
            ]
            with gzip.open(source, "wt", encoding="utf-8") as stream:
                for entry in entries:
                    stream.write(json.dumps(entry, ensure_ascii=False) + "\n")

            report = inspect_filtered_source(source, "de")

            self.assertEqual(2, report.indexed_entries)
            self.assertEqual(1, report.entries_with_raw_forms)
            self.assertEqual(3, report.raw_max)
            self.assertEqual(2, report.selected_max)
            self.assertEqual(1, report.selected_one_to_three_entries)
            self.assertEqual("Haus", report.maximum_raw_sample.lemma)


if __name__ == "__main__":
    unittest.main()
