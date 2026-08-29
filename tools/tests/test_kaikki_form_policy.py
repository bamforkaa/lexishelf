import unittest

from tools.kaikki_form_policy import (
    DISPLAY_FORM_LIMIT,
    normalize_key,
    select_display_forms,
    select_morphology_forms,
)


class KaikkiFormPolicyTest(unittest.TestCase):
    def test_german_display_is_semantic_deduplicated_and_bounded(self):
        forms = [
            {"form": "Häuser", "tags": ["nominative", "plural"]},
            {"form": "Häuser", "tags": ["nominative", "plural"]},
            {"form": "Hauses", "tags": ["genitive", "singular"]},
            {"form": "Haus", "tags": ["nominative", "singular"]},
        ] + [
            {"form": f"Haus-{index}", "tags": ["dative", "plural"]}
            for index in range(100)
        ]

        selected = select_display_forms("de", "noun", "Haus", forms)

        self.assertEqual([("Häuser", "plural"), ("Hauses", "genitive singular")], [
            (item.form, item.label) for item in selected
        ])
        self.assertLessEqual(len(selected), DISPLAY_FORM_LIMIT)

    def test_unknown_or_non_inflecting_language_can_display_zero(self):
        forms = [{"form": "sà-wàt-dii", "tags": ["romanization"]}]
        self.assertEqual([], select_display_forms("th", "verb", "สวัสดี", forms))
        self.assertEqual([], select_display_forms("xx", "noun", "word", forms))

    def test_morphology_is_broader_than_display_but_excludes_non_inflections(self):
        forms = [
            {"form": "ging", "tags": ["past", "singular"]},
            {"form": "gegangen", "tags": ["past", "participle"]},
            {"form": "gehen", "tags": ["canonical"]},
            {"form": "gɛən", "tags": ["romanization"]},
            {"form": "Gehung", "tags": ["derived"]},
            {"form": "-", "tags": ["past"]},
        ]

        self.assertEqual(
            ["ging", "gegangen"],
            select_morphology_forms("de", "verb", "gehen", forms),
        )

    def test_source_technical_pseudo_form_is_neither_displayed_nor_indexed(self):
        forms = [
            {"form": "metaphonic", "tags": ["plural"]},
            {"form": "form only", "tags": ["superlative"]},
        ]
        self.assertEqual([], select_display_forms("pt", "noun", "osso", forms))
        self.assertEqual([], select_morphology_forms("pt", "noun", "osso", forms))

    def test_english_all_sense_abbreviation_lemma_is_not_morphology_eligible(self):
        senses = [
            {"glosses": ["Abbreviation of interstate."], "tags": ["abbreviation", "alt-of"]},
            {"glosses": ["Abbreviation of instruction."], "tags": ["abbreviation", "alt-of"]},
        ]

        self.assertEqual(
            [],
            select_morphology_forms(
                "en",
                "noun",
                "I",
                [{"form": "Is", "tags": ["plural"]}],
                raw_senses=senses,
            ),
        )

    def test_english_all_sense_noncanonical_lemma_is_not_morphology_eligible(self):
        senses = [
            {"glosses": ["Obsolete spelling of eat."], "tags": ["alt-of", "obsolete"]},
        ]

        self.assertEqual(
            [],
            select_morphology_forms(
                "en",
                "verb",
                "eate",
                [{"form": "ate", "tags": ["past"]}],
                raw_senses=senses,
            ),
        )

    def test_english_mixed_lemma_keeps_valid_lexical_sense_and_other_languages_are_unchanged(self):
        mixed_senses = [
            {"glosses": ["A lexical noun."]},
            {"glosses": ["An abbreviation."], "tags": ["abbreviation"]},
        ]
        forms = [{"form": "axes", "tags": ["plural"]}]

        self.assertEqual(
            ["axes"],
            select_morphology_forms("en", "noun", "axis", forms, raw_senses=mixed_senses),
        )
        self.assertEqual(
            ["Häuser"],
            select_morphology_forms(
                "de",
                "noun",
                "Haus",
                [{"form": "Häuser", "tags": ["plural"]}],
                raw_senses=[{"tags": ["abbreviation"]}],
            ),
        )

    def test_morphology_normalizes_duplicates_and_keeps_homograph_work_for_database(self):
        forms = [
            {"form": "CAFE\u0301S", "tags": ["plural"]},
            {"form": "CAFÉS", "tags": ["plural"]},
        ]
        self.assertEqual(["CAFÉS"], select_morphology_forms("pt", "noun", "café", forms))

    def test_vietnamese_thai_and_derivational_indonesian_forms_are_not_indexed(self):
        self.assertEqual(
            [],
            select_morphology_forms(
                "vi", "noun", "nước", [{"form": "水", "tags": ["CJK"]}],
            ),
        )
        self.assertEqual(
            [],
            select_morphology_forms(
                "th", "noun", "น้ำ", [{"form": "náam", "tags": ["romanization"]}],
            ),
        )
        self.assertEqual(
            [],
            select_morphology_forms(
                "id", "noun", "ajar", [{"form": "pengajaran", "tags": ["derived"]}],
            ),
        )

    def test_turkish_pathological_combinations_are_filtered(self):
        forms = [
            {"form": "gider", "tags": ["aorist", "third-person", "singular"]},
            {"form": "gidiyor", "tags": ["present", "continuative", "third-person", "singular"]},
            {"form": "gidecek", "tags": ["future", "third-person", "singular"]},
            {"form": "gidemezmişsiniz", "tags": ["aorist", "negative", "potential", "evidential", "second-person", "plural"]},
        ]
        self.assertEqual(
            ["gider", "gidiyor"],
            select_morphology_forms("tr", "verb", "gitmek", forms),
        )

    def test_turkish_conjugation_person_tags_are_not_used_as_display_labels(self):
        forms = [
            {"form": "gittim", "tags": ["past", "singular", "third-person"]},
            {"form": "giderim", "tags": ["aorist", "singular", "third-person"]},
        ]

        self.assertEqual([], select_display_forms("tr", "verb", "gitmek", forms))

    def test_turkish_dotted_and_dotless_i_use_language_aware_case_pairs(self):
        self.assertEqual("ı", normalize_key("I", "tr"))
        self.assertEqual("i", normalize_key("İ", "tr"))
        self.assertEqual("i", normalize_key("i", "tr"))
        self.assertEqual("ı", normalize_key("ı", "tr"))
        self.assertEqual(
            ["İngilizceleştirir"],
            select_morphology_forms(
                "tr",
                "verb",
                "İngilizceleştirmek",
                [{"form": "İngilizceleştirir", "tags": ["aorist", "third-person"]}],
            ),
        )

    def test_german_adjective_index_keeps_principal_degree_not_full_declension(self):
        forms = [
            {"form": "freier", "tags": ["comparative"]},
            {"form": "freiere", "tags": ["comparative", "feminine", "nominative", "singular"]},
            {"form": "am freiesten", "tags": ["superlative"]},
        ]
        self.assertEqual(
            ["freier", "am freiesten"],
            select_morphology_forms("de", "adj", "frei", forms),
        )


if __name__ == "__main__":
    unittest.main()
