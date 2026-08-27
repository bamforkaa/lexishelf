import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools.build_dictionary_packs import PACKS, build_pack
from tools.panlex_language_config import PANLEX_FOREIGN_LANGUAGE_TAGS
from tools.kaikki_language_config import (
    KAIKKI_INDEX_SCHEMA_VERSION,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)


class BuildDictionaryPacksTest(unittest.TestCase):
    def test_kaikki_uses_one_pack_per_language_under_one_provider_id(self) -> None:
        definitions = [item for item in PACKS if item.provider_id == "kaikki"]

        self.assertEqual(list(KAIKKI_SUPPORTED_LANGUAGE_TAGS), [item.artifact[:-3] for item in definitions])
        self.assertEqual({"kaikki"}, {item.provider_id for item in definitions})
        for definition in definitions:
            language = definition.artifact.removesuffix(".db")
            self.assertEqual(((language, "en", "TRANSLATION"),), definition.language_pairs)
            self.assertEqual(f"kaikki.{language}-en", definition.pack_id)
            self.assertEqual(KAIKKI_INDEX_SCHEMA_VERSION, definition.dataset_schema_version)

    def test_panlex_manifest_pairs_come_from_the_shared_language_config(self) -> None:
        definition = next(item for item in PACKS if item.provider_id == "panlex")

        self.assertEqual(len(PANLEX_FOREIGN_LANGUAGE_TAGS) * 2, len(definition.language_pairs))
        for language in PANLEX_FOREIGN_LANGUAGE_TAGS:
            self.assertIn((language, "ko", "TRANSLATION"), definition.language_pairs)
            self.assertIn(("ko", language, "TRANSLATION"), definition.language_pairs)

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

    def test_unchanged_pack_is_reused_without_timestamp_or_byte_churn(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "dictionary-data"
            project = Path(temporary) / "project"
            definition = PACKS[0]
            source = root / definition.dataset / "source" / definition.artifact
            source.parent.mkdir(parents=True)
            source.write_bytes(b"stable dictionary payload")

            with patch.dict(os.environ, {"LANG_DATABASE_DIR": str(root)}):
                output, first_manifest = build_pack(definition, project)
                first_bytes = output.read_bytes()
                first_modified = output.stat().st_mtime_ns
                _, second_manifest = build_pack(definition, project)

            self.assertEqual(first_manifest["createdAt"], second_manifest["createdAt"])
            self.assertEqual(first_bytes, output.read_bytes())
            self.assertEqual(first_modified, output.stat().st_mtime_ns)

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
