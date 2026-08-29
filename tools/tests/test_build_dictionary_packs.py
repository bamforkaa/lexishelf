import os
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools.build_dictionary_packs import (
    KAIKKI_MORPHOLOGY_PACKS,
    KAIKKI_PACKS,
    PACKS,
    build_pack,
)
from tools.panlex_language_config import PANLEX_FOREIGN_LANGUAGE_TAGS
from tools.kaikki_language_config import (
    KAIKKI_INDEX_SCHEMA_VERSION,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)


class BuildDictionaryPacksTest(unittest.TestCase):
    def test_kaikki_uses_one_pack_per_language_under_one_provider_id(self) -> None:
        definitions = list(KAIKKI_PACKS)

        self.assertEqual(list(KAIKKI_SUPPORTED_LANGUAGE_TAGS), [item.artifact[:-3] for item in definitions])
        self.assertEqual({"kaikki"}, {item.provider_id for item in definitions})
        for definition in definitions:
            language = definition.artifact.removesuffix(".db")
            self.assertEqual(((language, "en", "TRANSLATION"),), definition.language_pairs)
            self.assertEqual(f"kaikki.{language}-en", definition.pack_id)
            self.assertEqual(KAIKKI_INDEX_SCHEMA_VERSION, definition.dataset_schema_version)

        morphology = KAIKKI_MORPHOLOGY_PACKS[0]
        self.assertEqual("en-morphology.db", morphology.artifact)
        self.assertEqual("kaikki.en-morphology", morphology.pack_id)
        self.assertEqual((("en", "en", "MONOLINGUAL_DEFINITION"),), morphology.language_pairs)
        self.assertEqual(KAIKKI_INDEX_SCHEMA_VERSION, morphology.dataset_schema_version)

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

    def test_unchanged_payload_reuses_pack_without_rewriting_archive(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "dictionary-data"
            project = Path(temporary) / "project"
            definition = PACKS[0]
            source = root / definition.dataset / "source" / definition.artifact
            source.parent.mkdir(parents=True)
            source.write_bytes(b"stable dictionary payload")

            with patch.dict(os.environ, {"LANG_DATABASE_DIR": str(root)}):
                output, first_manifest = build_pack(definition, project)
                fixed_timestamp = 1_000_000_000
                os.utime(output, (fixed_timestamp, fixed_timestamp))
                first_bytes = output.read_bytes()
                _, second_manifest = build_pack(definition, project)

            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(first_bytes, output.read_bytes())
            self.assertEqual(fixed_timestamp, output.stat().st_mtime)


if __name__ == "__main__":
    unittest.main()
