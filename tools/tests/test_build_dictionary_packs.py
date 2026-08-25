import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools.build_dictionary_packs import PACKS, build_pack


class BuildDictionaryPacksTest(unittest.TestCase):
    def test_builds_from_canonical_source(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "dictionary-data"
            project = Path(temporary) / "project"
            definition = PACKS[0]
            source = root / definition.dataset / "source" / definition.artifact
            source.parent.mkdir(parents=True)
            source.write_bytes(b"canonical dictionary payload")

            with patch.dict(os.environ, {"LANG_DATABASE_DIR": str(root)}):
                output, manifest = build_pack(definition, project)

            self.assertEqual(source.stat().st_size, manifest["payload"]["sizeBytes"])
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(source.read_bytes(), archive.read(definition.artifact))

    def test_legacy_android_asset_is_not_a_pack_input(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "dictionary-data"
            project = Path(temporary) / "project"
            definition = PACKS[0]
            legacy = (
                project
                / "app"
                / "src"
                / "main"
                / "assets"
                / "dictionary"
                / "cccedict"
                / definition.artifact
            )
            legacy.parent.mkdir(parents=True)
            legacy.write_bytes(b"legacy payload")

            with patch.dict(os.environ, {"LANG_DATABASE_DIR": str(root)}):
                with self.assertRaisesRegex(FileNotFoundError, "Missing canonical artifact"):
                    build_pack(definition, project)


if __name__ == "__main__":
    unittest.main()
