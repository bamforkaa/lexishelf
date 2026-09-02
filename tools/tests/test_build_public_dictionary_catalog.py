from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from tools.build_dictionary_packs import KAIKKI_PACKS
from tools.build_public_dictionary_catalog import (
    PUBLIC_REPOSITORY,
    load_pack,
    metadata_for,
    public_definitions,
)


class PublicDictionaryCatalogTest(unittest.TestCase):
    def test_public_set_has_only_reviewed_v1_packs_and_excludes_jmdict(self) -> None:
        definitions = public_definitions()

        self.assertEqual(16, len(definitions))
        self.assertNotIn("jmdict", {definition.provider_id for definition in definitions})
        self.assertEqual(12, len(KAIKKI_PACKS))
        self.assertEqual(
            {"de", "hi", "pl", "nl", "pt", "tr", "cs", "sv", "uk", "vi", "th", "id"},
            {definition.language_pairs[0][0] for definition in KAIKKI_PACKS},
        )

    def test_every_public_pack_has_artifact_level_provenance(self) -> None:
        for definition in public_definitions():
            metadata = metadata_for(definition)
            self.assertTrue(metadata.source_artifact_id)
            self.assertEqual(64, len(metadata.source_artifact_sha256))
            self.assertTrue(metadata.source_url.startswith("https://"))
            self.assertTrue(metadata.license_url.startswith("https://"))
            self.assertTrue(metadata.transformation_notice)

    def test_load_pack_rejects_manifest_identity_mismatch(self) -> None:
        definition = public_definitions()[0]
        payload = b"fixture"
        manifest = {
            "format": "local-vocabulary-dictionary-pack",
            "packId": "wrong.pack",
            "providerId": definition.provider_id,
            "datasetVersion": definition.dataset_version,
            "datasetSchemaVersion": definition.dataset_schema_version,
            "manifestSchemaVersion": 1,
            "payload": {
                "fileName": definition.artifact,
                "sizeBytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            },
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fixture.dictpack"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.json", json.dumps(manifest))
                archive.writestr(definition.artifact, payload)

            with self.assertRaisesRegex(ValueError, "unexpected packId"):
                load_pack(path, definition)

    def test_load_pack_rejects_language_or_license_metadata_mismatch(self) -> None:
        definition = public_definitions()[0]
        payload = b"fixture"
        manifest = {
            "format": "local-vocabulary-dictionary-pack",
            "packId": definition.pack_id,
            "providerId": definition.provider_id,
            "datasetVersion": definition.dataset_version,
            "datasetSchemaVersion": definition.dataset_schema_version,
            "manifestSchemaVersion": 1,
            "supportedLanguagePairs": [],
            "payload": {
                "fileName": definition.artifact,
                "sizeBytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            },
            "license": {
                "licenseId": definition.license_id,
                "attribution": definition.attribution,
            },
            "source": {
                "officialUrl": definition.official_url,
                "sourceVersion": definition.dataset_version,
                "buildTool": "tools/build_dictionary_packs.py",
            },
        }
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "fixture.dictpack"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("manifest.json", json.dumps(manifest))
                archive.writestr(definition.artifact, payload)

            with self.assertRaisesRegex(ValueError, "supportedLanguagePairs"):
                load_pack(path, definition)

    def test_public_repository_is_pinned(self) -> None:
        self.assertEqual("bamforkaa/lexishelf", PUBLIC_REPOSITORY)

    def test_committed_catalog_matches_the_reviewed_public_set(self) -> None:
        catalog_path = Path(__file__).resolve().parents[2] / "distribution" / "dictionary-catalog-v1.json"
        catalog = json.loads(catalog_path.read_text(encoding="utf-8"))

        self.assertEqual(1, catalog["schemaVersion"])
        self.assertEqual(16, len(catalog["packs"]))
        self.assertEqual(
            {definition.pack_id for definition in public_definitions()},
            {pack["packId"] for pack in catalog["packs"]},
        )
        self.assertNotIn("jmdict", {pack["providerId"] for pack in catalog["packs"]})


if __name__ == "__main__":
    unittest.main()
