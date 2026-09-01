"""Stage a verified LexiShelf GitHub release without writing binaries to Git."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import uuid
from pathlib import Path
from urllib.parse import unquote, urlparse

from tools.dataset_paths import dataset_paths
from tools.build_public_dictionary_catalog import (
    PUBLIC_RELEASE_TAG,
    PUBLIC_REPOSITORY,
    build_catalog,
)

PUBLIC_PROVIDER_DATASETS = {
    "cc-cedict": "cc-cedict",
    "korean-basic-dictionary": "korean-basic",
    "panlex": "panlex",
    "kaikki": "kaikki",
}
EXPECTED_SIGNER_CERT_SHA256 = "1051d4c58bdc08aecc1aff17a9573e0b8bf7db061051af3b2e105ef3dea7b4ba"


def stage_release(
    project_root: Path,
    signed_apk: Path,
    release_notes: Path,
    output: Path,
    apksigner: Path,
) -> list[Path]:
    project_root = project_root.resolve()
    output = output.resolve()
    build_root = (project_root / "build").resolve()
    if not output.is_relative_to(build_root) or output == build_root:
        raise ValueError("Release staging output must be a child of <PROJECT_ROOT>/build")
    if output.exists():
        raise FileExistsError(f"Release staging output already exists: {output}")
    if not signed_apk.is_file() or signed_apk.suffix.lower() != ".apk":
        raise FileNotFoundError("A signed release APK is required")
    if not release_notes.is_file():
        raise FileNotFoundError("A release notes Markdown file is required")
    _verify_apk_signature(signed_apk, apksigner)

    catalog_path = project_root / "distribution" / "dictionary-catalog-v1.json"
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if catalog.get("schemaVersion") != 1 or not catalog.get("packs"):
        raise ValueError("Invalid release catalog")
    expected_catalog, _ = build_catalog(
        project_root,
        repository=PUBLIC_REPOSITORY,
        release_tag=PUBLIC_RELEASE_TAG,
        catalog_version=PUBLIC_RELEASE_TAG,
        generated_at=catalog.get("generatedAt", ""),
    )
    if catalog != expected_catalog:
        raise ValueError("Release catalog does not match the reviewed public artifacts")
    if any(pack.get("providerId") == "jmdict" for pack in catalog["packs"]):
        raise ValueError("JMdict must not be staged for public v1")

    temporary = output.with_name(f".{output.name}.staging-{uuid.uuid4()}")
    temporary.mkdir(parents=True)
    try:
        staged: list[Path] = []
        apk_target = temporary / "LexiShelf-v0.1.0.apk"
        shutil.copy2(signed_apk, apk_target)
        staged.append(apk_target)

        catalog_target = temporary / catalog_path.name
        shutil.copy2(catalog_path, catalog_target)
        staged.append(catalog_target)

        notice_target = temporary / "THIRD_PARTY_NOTICES.md"
        shutil.copy2(project_root / "NOTICE.md", notice_target)
        staged.append(notice_target)

        release_notes_target = temporary / "RELEASE_NOTES.md"
        shutil.copy2(release_notes, release_notes_target)
        staged.append(release_notes_target)

        for pack in catalog["packs"]:
            provider_id = pack["providerId"]
            dataset = PUBLIC_PROVIDER_DATASETS.get(provider_id)
            if dataset is None:
                raise ValueError(f"Unknown public provider: {provider_id}")
            file_name = unquote(urlparse(pack["downloadUrl"]).path.rsplit("/", 1)[-1])
            source = dataset_paths(dataset, project_root).packs / file_name
            if not source.is_file():
                raise FileNotFoundError(f"Missing release pack: {file_name}")
            if source.stat().st_size != pack["downloadSizeBytes"]:
                raise ValueError(f"Release pack size mismatch: {file_name}")
            if _sha256(source) != pack["sha256"]:
                raise ValueError(f"Release pack checksum mismatch: {file_name}")
            target = temporary / file_name
            shutil.copy2(source, target)
            staged.append(target)

        checksums = temporary / "SHA256SUMS.txt"
        checksums.write_text(
            "".join(
                f"{_sha256(path)}  {path.name}\n"
                for path in sorted(staged, key=lambda candidate: candidate.name)
            ),
            encoding="utf-8",
            newline="\n",
        )
        staged.append(checksums)
        temporary.rename(output)
        return [output / path.name for path in staged]
    except BaseException:
        shutil.rmtree(temporary, ignore_errors=True)
        raise


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verify_apk_signature(apk: Path, apksigner: Path) -> None:
    if not apksigner.is_file():
        raise FileNotFoundError("Android SDK apksigner is required")
    result = subprocess.run(
        [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise ValueError("APK signature verification failed; release staging was refused")
    verification_output = f"{result.stdout}\n{result.stderr}"
    fingerprints = {
        match.lower()
        for match in re.findall(
            r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})",
            verification_output,
        )
    }
    if fingerprints != {EXPECTED_SIGNER_CERT_SHA256}:
        raise ValueError("APK signer does not match the permanent LexiShelf release identity")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signed-apk", type=Path, required=True)
    parser.add_argument("--release-notes", type=Path, required=True)
    parser.add_argument("--apksigner", type=Path, required=True)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("build/release-assets/v0.1.0"),
    )
    args = parser.parse_args()
    project_root = Path(__file__).resolve().parents[1]
    staged = stage_release(
        project_root,
        args.signed_apk.resolve(),
        args.release_notes.resolve(),
        args.output,
        args.apksigner.resolve(),
    )
    print(json.dumps({"assets": [path.name for path in staged]}, indent=2))


if __name__ == "__main__":
    main()
