from pathlib import Path
import tempfile
import unittest

from tools.dataset_paths import (
    FALLBACK_DIRECTORY,
    dataset_root_summary,
    resolve_dataset_root,
)


class DatasetPathsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def test_environment_override_has_highest_priority(self) -> None:
        properties = self.root / "local.properties"
        properties.write_text("dictionaryDataDir=configured\n", encoding="utf-8")
        actual = resolve_dataset_root(
            self.root,
            {"LANG_DATABASE_DIR": str(self.root / "environment path")},
            properties,
        )
        self.assertEqual((self.root / "environment path").resolve(), actual)

    def test_local_property_supports_escaped_windows_path(self) -> None:
        properties = self.root / "local.properties"
        properties.write_text(r"dictionaryDataDir=D\:\\lang Database" + "\n", encoding="utf-8")
        actual = resolve_dataset_root(self.root, {}, properties)
        self.assertTrue(str(actual).endswith(r"D:\lang Database"))

    def test_relative_local_property_and_path_with_spaces(self) -> None:
        properties = self.root / "local.properties"
        properties.write_text("dictionaryDataDir=data sets/dictionaries\n", encoding="utf-8")
        self.assertEqual(
            (self.root / "data sets" / "dictionaries").resolve(),
            resolve_dataset_root(self.root, {}, properties),
        )

    def test_clean_clone_uses_project_local_fallback(self) -> None:
        self.assertEqual(
            (self.root / FALLBACK_DIRECTORY).resolve(),
            resolve_dataset_root(self.root, {}, self.root / "missing.properties"),
        )

    def test_resolved_root_summary_is_visible_and_unambiguous(self) -> None:
        root = Path(r"D:\lang-Database")
        self.assertEqual(
            "Dictionary dataset root:\nD:\\lang-Database",
            dataset_root_summary(root),
        )


if __name__ == "__main__":
    unittest.main()
