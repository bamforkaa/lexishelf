from pathlib import Path
from subprocess import CompletedProcess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from tools.prepare_github_release import (
    EXPECTED_SIGNER_CERT_SHA256,
    _verify_apk_signature,
)


class PrepareGithubReleaseTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
