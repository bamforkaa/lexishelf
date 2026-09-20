from pathlib import Path
import hashlib
import json
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from tools.prepare_github_release import (
    EXPECTED_SIGNER_CERT_SHA256,
    EXPECTED_APPLICATION_ID,
    _verify_apk_metadata,
    _verify_apk_signature,
    stage_release,
)


class PrepareGithubReleaseTest(unittest.TestCase):
    def test_requires_an_explicit_release_notes_file(self) -> None:
        with TemporaryDirectory() as directory:
            project_root = Path(directory)
            apk = project_root / "release.apk"
            apk.touch()
            apksigner = project_root / "apksigner"
            apksigner.touch()

            with self.assertRaisesRegex(FileNotFoundError, "release notes Markdown"):
                stage_release(
                    project_root,
                    apk,
                    project_root / "missing-release-notes.md",
                    project_root / "build" / "release-assets",
                    apksigner,
                    app_version="0.2.0",
                )

    def test_accepts_only_the_permanent_release_certificate(self) -> None:
        with TemporaryDirectory() as directory:
            apksigner = Path(directory) / "apksigner"
            apksigner.touch()
            apk = Path(directory) / "release.apk"
            with patch(
                "tools.prepare_github_release.subprocess.run",
                return_value=CompletedProcess(
                    args=[],
                    returncode=0,
                    stdout=(
                        "Verifies\nNumber of signers: 1\n"
                        f"Signer #1 certificate SHA-256 digest: {EXPECTED_SIGNER_CERT_SHA256}\n"
                    ),
                    stderr="",
                ),
            ):
                _verify_apk_signature(apk, apksigner)

    def test_rejects_a_valid_apk_from_another_signer(self) -> None:
        with TemporaryDirectory() as directory:
            apksigner = Path(directory) / "apksigner"
            apksigner.touch()
            apk = Path(directory) / "release.apk"
            with patch(
                "tools.prepare_github_release.subprocess.run",
                return_value=CompletedProcess(
                    args=[],
                    returncode=0,
                    stdout=f"Signer #1 certificate SHA-256 digest: {'0' * 64}\n",
                    stderr="",
                ),
            ):
                with self.assertRaisesRegex(ValueError, "permanent LexiShelf"):
                    _verify_apk_signature(apk, apksigner)

    def test_rejects_an_unsigned_apk(self) -> None:
        with TemporaryDirectory() as directory:
            apksigner = Path(directory) / "apksigner"
            apksigner.touch()
            apk = Path(directory) / "release.apk"
            with patch(
                "tools.prepare_github_release.subprocess.run",
                return_value=CompletedProcess(
                    args=[],
                    returncode=1,
                    stdout="DOES NOT VERIFY\n",
                    stderr="",
                ),
            ):
                with self.assertRaisesRegex(ValueError, "verification failed"):
                    _verify_apk_signature(apk, apksigner)


class ReleaseStagingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / "app-release.apk"
        self.apk.write_bytes(b"synthetic signed APK fixture")
        self.notes = self.root / "RELEASE_NOTES.md"
        self.notes.write_text("# LexiShelf v0.2.0\n", encoding="utf-8")
        (self.root / "NOTICE.md").write_text(
            "[Artifacts](docs/public-dictionary-artifacts.md)\n"
            "[Catalog](distribution/dictionary-catalog-v1.json)\n"
            "[Software](docs/third-party-software.md)\n",
            encoding="utf-8",
        )
        self.pack = self.root / "fixture.dictpack"
        self.pack.write_bytes(b"synthetic reviewed pack fixture")
        self.catalog = {
            "schemaVersion": 1,
            "catalogVersion": "dictionary-inventory-1",
            "generatedAt": "2026-08-31T00:00:00Z",
            "packs": [{
                "providerId": "cc-cedict",
                "downloadUrl": "https://github.com/bamforkaa/lexishelf/releases/download/v0.1.0/fixture.dictpack",
                "downloadSizeBytes": self.pack.stat().st_size,
                "sha256": hashlib.sha256(self.pack.read_bytes()).hexdigest(),
            }],
        }
        self.catalog_file = self.root / "reviewed-catalog.json"
        self.catalog_file.write_text(json.dumps(self.catalog, indent=4) + "\n", encoding="utf-8")
        self.output = self.root / "build" / "release-assets" / "v0.2.0"
        self.old_asset = self.root / "build" / "release-assets" / "v0.1.0" / "old.apk"
        self.old_asset.parent.mkdir(parents=True)
        self.old_asset.write_bytes(b"preserve the historical release")
        for name in ("_verify_apk_signature", "_verify_apk_metadata"):
            self.enterContext(patch(f"tools.prepare_github_release.{name}"))
        self.build_catalog = self.enterContext(patch(
            "tools.prepare_github_release.build_catalog", return_value=(self.catalog, []),
        ))
        self.enterContext(patch(
            "tools.prepare_github_release.dataset_paths", return_value=SimpleNamespace(packs=self.root),
        ))

    def stage(self, **options):
        return stage_release(
            self.root, self.apk, self.notes, self.output, self.root / "apksigner.bat",
            app_version="0.2.0", catalog_source=self.catalog_file, **options,
        )

    def assert_checksums_match_assets(self, assets: list[Path]) -> None:
        checksums = dict(
            (name, digest)
            for digest, name in (
                line.split("  ", 1)
                for line in (self.output / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines()
            )
        )
        expected = {
            path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in assets if path.name != "SHA256SUMS.txt"
        }
        self.assertEqual(expected, checksums)

    def test_app_only_release_has_five_assets_and_four_exact_checksums(self) -> None:
        assets = self.stage()
        self.assertEqual({
            "LexiShelf-v0.2.0.apk", "dictionary-catalog-v1.json", "RELEASE_NOTES.md",
            "THIRD_PARTY_NOTICES.md", "SHA256SUMS.txt",
        }, {path.name for path in assets})
        self.assert_checksums_match_assets(assets)
        self.assertEqual(self.catalog_file.read_bytes(), (self.output / "dictionary-catalog-v1.json").read_bytes())
        self.assertEqual(b"preserve the historical release", self.old_asset.read_bytes())
        self.build_catalog.assert_called_once_with(
            self.root, repository="bamforkaa/lexishelf", release_tag="v0.1.0",
            catalog_version="dictionary-inventory-1", generated_at="2026-08-31T00:00:00Z",
        )

    def test_standalone_notices_link_to_app_and_dictionary_tags_independently(self) -> None:
        original = (self.root / "NOTICE.md").read_bytes()
        self.stage()
        notices = (self.output / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")
        self.assertIn("/blob/v0.2.0/docs/third-party-software.md", notices)
        self.assertIn("/blob/v0.1.0/docs/public-dictionary-artifacts.md", notices)
        self.assertIn("/blob/v0.1.0/distribution/dictionary-catalog-v1.json", notices)
        self.assertEqual(original, (self.root / "NOTICE.md").read_bytes())

    def test_explicit_pack_release_includes_packs_and_their_checksums(self) -> None:
        self.catalog["packs"][0]["downloadUrl"] = self.catalog["packs"][0]["downloadUrl"].replace("v0.1.0", "v0.2.0")
        self.catalog_file.write_text(json.dumps(self.catalog), encoding="utf-8")
        assets = self.stage(dictionary_release_tag="v0.2.0", include_dictionary_packs=True)
        self.assertEqual(self.pack.read_bytes(), (self.output / self.pack.name).read_bytes())
        self.assertEqual(6, len(assets))
        self.assert_checksums_match_assets(assets)
        self.assertEqual("v0.2.0", self.build_catalog.call_args.kwargs["release_tag"])

    def test_catalog_mismatch_is_rejected_before_output_creation(self) -> None:
        tampered = {**self.catalog, "generatedAt": "2026-09-01T00:00:00Z"}
        self.catalog_file.write_text(json.dumps(tampered), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "does not match the reviewed"):
            self.stage()
        self.assertFalse(self.output.exists())

    def test_pack_mismatch_rolls_back_staging_without_touching_historical_assets(self) -> None:
        self.pack.write_bytes(b"tampered pack")
        with self.assertRaisesRegex(ValueError, "size mismatch"):
            self.stage(include_dictionary_packs=True)
        self.assertFalse(self.output.exists())
        self.assertEqual([], list(self.output.parent.glob(".*.staging-*")))
        self.assertEqual(b"preserve the historical release", self.old_asset.read_bytes())

    def test_existing_output_cannot_be_replaced(self) -> None:
        self.output.mkdir(parents=True)
        sentinel = self.output / "keep.txt"
        sentinel.write_bytes(b"keep")
        with self.assertRaises(FileExistsError):
            self.stage()
        self.assertEqual(b"keep", sentinel.read_bytes())

    def test_output_outside_build_is_rejected(self) -> None:
        self.output = self.root / "outside-build"
        with self.assertRaisesRegex(ValueError, "child of"):
            self.stage()
        self.assertFalse(self.output.exists())

    def test_unsafe_release_tag_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "single path components"):
            self.stage(release_tag="../v0.2.0")
        self.assertFalse(self.output.exists())


class ApkMetadataTest(unittest.TestCase):
    def test_metadata_must_match_the_requested_non_debuggable_app(self) -> None:
        with TemporaryDirectory() as directory:
            aapt = Path(directory) / "aapt"
            aapt.touch()
            valid = f"package: name='{EXPECTED_APPLICATION_ID}' versionCode='2' versionName='0.2.0'\n"
            for text, error in (
                (valid, None),
                (valid.replace(EXPECTED_APPLICATION_ID, "example.other"), "application ID"),
                (valid.replace("0.2.0", "0.1.0"), "APK version"),
                (valid.replace("versionCode='2'", "versionCode='0'"), "versionCode"),
                (valid + "application-debuggable\n", "debuggable"),
            ):
                with self.subTest(error=error), patch(
                    "tools.prepare_github_release.subprocess.run",
                    return_value=CompletedProcess([], 0, stdout=text, stderr=""),
                ):
                    if error:
                        with self.assertRaisesRegex(ValueError, error):
                            _verify_apk_metadata(Path("release.apk"), aapt, "0.2.0")
                    else:
                        _verify_apk_metadata(Path("release.apk"), aapt, "0.2.0")


if __name__ == "__main__":
    unittest.main()
