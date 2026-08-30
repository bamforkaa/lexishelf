import unittest

from tools.kaikki_usage_label_policy import normalize_sense_labels


class KaikkiUsageLabelPolicyTest(unittest.TestCase):
    def test_known_labels_are_normalized_in_deterministic_category_order(self):
        self.assertEqual(
            (
                "formal",
                "informal",
                "slang",
                "archaic",
                "transitive",
                "intransitive",
                "countable",
                "uncountable",
                "regional",
            ),
            normalize_sense_labels(
                [
                    "regional",
                    "uncountable",
                    "slang",
                    "transitive",
                    "formal",
                    "countable",
                    "intransitive",
                    "archaic",
                    "informal",
                ]
            ),
        )

    def test_unknown_form_meta_and_duplicate_tags_are_not_exposed(self):
        self.assertEqual(
            ("colloquial", "obsolete"),
            normalize_sense_labels(
                [
                    "form-of",
                    "plural",
                    "colloquial",
                    "unknown-future-tag",
                    "obsolete",
                    "colloquial",
                ]
            ),
        )

    def test_non_list_input_is_empty(self):
        self.assertEqual((), normalize_sense_labels(None))
        self.assertEqual((), normalize_sense_labels("informal"))


if __name__ == "__main__":
    unittest.main()
