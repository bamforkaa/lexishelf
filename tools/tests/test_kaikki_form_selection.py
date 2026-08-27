import unittest

from tools.kaikki_form_selection import (
    GLOBAL_FORM_SAFETY_CAP,
    select_kaikki_forms,
)


def form(text, *tags):
    return {"form": text, "tags": list(tags)}


class KaikkiFormSelectionTest(unittest.TestCase):
    def test_german_principal_parts_ignore_table_order_noise_and_lemma(self):
        raw = [
            form("gehen", "infinitive"),
            form("gehe", "first-person", "present", "singular"),
            form("ging", "past"),
            form("geht", "present", "singular", "third-person"),
            form("gegangen", "participle", "past"),
            form("de-conj", "inflection-template"),
            form("gieng", "alternative", "past"),
        ]

        selected = select_kaikki_forms("de", "verb", "gehen", list(reversed(raw)))

        self.assertEqual(
            [("geht", "present 3sg"), ("ging", "past"), ("gegangen", "past participle")],
            [(item.text, item.label) for item in selected.forms],
        )
        self.assertEqual(6, selected.raw_form_count)

    def test_duplicate_and_lemma_normalization_are_removed(self):
        raw = [
            form(" Häuser ", "plural"),
            form("Ha\u0308user", "plural"),
            form("Haus", "nominative", "singular"),
            form("Hauses", "genitive", "singular"),
        ]

        selected = select_kaikki_forms("de", "noun", " Haus ", raw)

        self.assertEqual(
            [("Häuser", "plural"), ("Hauses", "genitive singular")],
            [(item.text, item.label) for item in selected.forms],
        )
        self.assertEqual(3, selected.raw_form_count)

    def test_same_surface_combines_distinct_semantic_labels(self):
        selected = select_kaikki_forms(
            "id",
            "verb",
            "ajar",
            [
                form("diajarkan", "active"),
                form("diajarkan", "passive"),
            ],
        )

        self.assertEqual(1, len(selected.forms))
        self.assertEqual("diajarkan", selected.forms[0].text)
        self.assertEqual("active · passive", selected.forms[0].label)

    def test_slavic_policy_avoids_full_case_table(self):
        raw = [
            form("dom", "nominative", "singular"),
            form("domy", "nominative", "plural"),
            form("domu", "genitive", "singular"),
            form("domów", "genitive", "plural"),
            form("domowi", "dative", "singular"),
            form("domem", "instrumental", "singular"),
        ]

        selected = select_kaikki_forms("pl", "noun", "dom", raw)

        self.assertEqual(3, len(selected.forms))
        self.assertEqual(
            ["nominative plural", "genitive singular", "genitive plural"],
            [item.label for item in selected.forms],
        )

    def test_aspect_counterpart_requires_an_explicit_standalone_marker(self):
        selected = select_kaikki_forms(
            "uk",
            "verb",
            "користуватися",
            [
                form("скористатися", "perfective"),
                form("користуючись", "adverbial", "imperfective", "present"),
                form("користуюся", "first-person", "imperfective", "present", "singular"),
            ],
        )

        self.assertEqual(
            ["perfective counterpart", "present 1sg"],
            [item.label for item in selected.forms],
        )

    def test_multiword_form_prefers_candidate_related_to_lemma(self):
        selected = select_kaikki_forms(
            "pt",
            "verb",
            "chamar ao pão, pão e ao queijo, queijo",
            [
                form("queijo", "first-person", "present", "singular"),
                form("chamo ao pão", "first-person", "present", "singular"),
            ],
        )

        self.assertEqual("chamo ao pão", selected.forms[0].text)

    def test_hindi_and_turkish_are_bounded_by_reviewed_slots(self):
        hindi = select_kaikki_forms(
            "hi",
            "verb",
            "करना",
            [
                form("karnā", "romanization"),
                form("कर", "stem"),
                form("करता", "direct", "habitual", "masculine", "singular"),
                form("करती", "habitual", "feminine", "singular"),
                form("किया", "direct", "masculine", "perfective", "singular"),
                form("की", "feminine", "perfective", "singular"),
            ],
        )
        turkish = select_kaikki_forms(
            "tr",
            "verb",
            "gitmek",
            [
                form("gider", "present", "singular", "third-person"),
                form("giderim", "aorist", "singular", "third-person"),
                form("gittim", "past", "singular", "third-person"),
            ],
        )

        self.assertEqual(5, len(hindi.forms))
        self.assertEqual([("gider", "present 3sg")], [(x.text, x.label) for x in turkish.forms])

    def test_low_inflection_languages_return_no_selected_forms(self):
        vietnamese = select_kaikki_forms(
            "vi", "noun", "nước", [form("渃", "CJK"), form("nác", "alternative")]
        )
        thai = select_kaikki_forms(
            "th", "verb", "กิน", [form("gin", "romanization"), form("การกิน", "abstract-noun")]
        )

        self.assertEqual((), vietnamese.forms)
        self.assertEqual((), thai.forms)
        self.assertEqual(2, vietnamese.raw_form_count)

    def test_indonesian_keeps_only_clear_morphology(self):
        selected = select_kaikki_forms(
            "id",
            "verb",
            "ajar",
            [
                form("mengajar", "active"),
                form("diajar", "passive"),
                form("mengajari", "active", "error-unrecognized-form"),
                form("adjar", "alternative"),
            ],
        )

        self.assertEqual(["active", "passive"], [item.label for item in selected.forms])

    def test_unknown_tags_malformed_values_and_empty_forms_are_safe(self):
        raw = [None, "bad", {}, {"form": 4}, form("x", "unknown-feature")]

        self.assertEqual((), select_kaikki_forms("de", "noun", "lemma", raw).forms)
        self.assertEqual((), select_kaikki_forms("de", "noun", "lemma", None).forms)

    def test_global_cap_is_only_a_final_guard(self):
        raw = [form(f"form-{index}", "comparative") for index in range(20)]

        selected = select_kaikki_forms(
            "xx", "adj", "lemma", raw, safety_cap=GLOBAL_FORM_SAFETY_CAP
        )

        # One semantic comparative slot wins before the cap is relevant.
        self.assertEqual(1, len(selected.forms))

    def test_global_cap_stops_pathological_policy_output(self):
        selected = select_kaikki_forms(
            "de",
            "verb",
            "gehen",
            [
                form("geht", "present", "singular", "third-person"),
                form("ging", "past"),
                form("gegangen", "participle", "past"),
            ],
            safety_cap=2,
        )

        self.assertEqual(["present 3sg", "past"], [item.label for item in selected.forms])

    def test_non_positive_cap_is_rejected(self):
        with self.assertRaises(ValueError):
            select_kaikki_forms("de", "noun", "Haus", [], safety_cap=0)


if __name__ == "__main__":
    unittest.main()
