"""Verify canonical Kaikki pack manifests, ZIP CRC, payload sizes, and SHA-256."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
import zipfile

from tools.build_dictionary_packs import (
    KAIKKI_MORPHOLOGY_PACKS,
    KAIKKI_PACKS,
    pack_file_name,
)
from tools.dataset_paths import dataset_paths
from tools.kaikki_language_config import KAIKKI_INDEX_SCHEMA_VERSION


def audit_pack(path: Path) -> dict[str, object]:
    with zipfile.ZipFile(path) as archive:
        bad_member = archive.testzip()
        if bad_member is not None:
            raise ValueError(f"ZIP CRC failed for {path}: {bad_member}")
        manifest = json.loads(archive.read("manifest.json"))
        if manifest["datasetSchemaVersion"] != KAIKKI_INDEX_SCHEMA_VERSION:
            raise ValueError(f"Unexpected schema in {path}")
        payload = manifest["payload"]
        digest = hashlib.sha256()
        size = 0
        with archive.open(payload["fileName"]) as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                size += len(chunk)
                digest.update(chunk)
        if size != payload["sizeBytes"]:
            raise ValueError(f"Payload size mismatch in {path}")
        if digest.hexdigest() != payload["sha256"]:
            raise ValueError(f"Payload SHA-256 mismatch in {path}")
    return {
        "pack": path.name,
        "schema": manifest["datasetSchemaVersion"],
        "payloadBytes": size,
        "payloadSha256": digest.hexdigest(),
        "zipBytes": path.stat().st_size,
    }


def main() -> None:
    packs = dataset_paths("kaikki").packs
    definitions = KAIKKI_PACKS + KAIKKI_MORPHOLOGY_PACKS
    print(
        json.dumps(
            [audit_pack(packs / pack_file_name(definition)) for definition in definitions],
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
