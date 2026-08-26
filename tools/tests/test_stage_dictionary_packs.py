from pathlib import Path
import tempfile
import unittest

from tools.stage_dictionary_packs import expected_pack_paths, select_avd_serial


class StageDictionaryPacksTest(unittest.TestCase):
    def test_expected_paths_use_each_dataset_pack_directory_and_exact_file_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            project = Path(temporary)
            actual = tuple(
                path.relative_to(project).as_posix()
                for path in expected_pack_paths(project, kaikki_languages=())
            )

        self.assertEqual(
            (
                ".local/dictionary-data/cc-cedict/packs/"
                "cc-cedict.zh-en-2026-08-22T08_27_42Z.dictpack",
                ".local/dictionary-data/korean-basic/packs/"
                "korean-basic.multilingual-2026-08-19.dictpack",
                ".local/dictionary-data/panlex/packs/"
                "panlex.ko-fallback-2019-09-01.dictpack",
                ".local/dictionary-data/jmdict/packs/jmdict.ja-en-2026-08-23.dictpack",
            ),
            actual,
        )

    def test_selected_kaikki_language_adds_only_its_per_language_pack(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            project = Path(temporary)
            names = tuple(
                path.name
                for path in expected_pack_paths(project, kaikki_languages=("de", "nl"))
            )

        self.assertEqual(6, len(names))
        self.assertIn("kaikki.de-en-enwiktionary-2026-08-05.dictpack", names)
        self.assertIn("kaikki.nl-en-enwiktionary-2026-08-05.dictpack", names)

    def test_selects_only_the_explicit_manual_avd_name(self) -> None:
        avds = {
            "emulator-5554": "Medium_Phone_Test",
            "emulator-5556": "Medium_Phone_Manual",
        }

        self.assertEqual("emulator-5556", select_avd_serial(avds, "Medium_Phone_Manual"))

    def test_refuses_to_fall_back_to_a_different_or_missing_avd(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "is not connected"):
            select_avd_serial({"emulator-5554": "Medium_Phone_Test"}, "Medium_Phone_Manual")


if __name__ == "__main__":
    unittest.main()
