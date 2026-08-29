"""Build generic local .dictpack archives from reviewed dictionary artifacts."""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

from tools.dataset_paths import dataset_paths, dataset_root_summary
from tools.kaikki_language_config import (
    KAIKKI_DATASET_RELEASE_ID,
    KAIKKI_INDEX_SCHEMA_VERSION,
    KAIKKI_SUPPORTED_LANGUAGE_TAGS,
)
from tools.panlex_language_config import panlex_language_pairs

PACK_FORMAT = "local-vocabulary-dictionary-pack"
MANIFEST_SCHEMA_VERSION = 1


@dataclass(frozen=True)
class PackDefinition:
    dataset: str
    artifact: str
    pack_id: str
    provider_id: str
    dataset_version: str
    dataset_schema_version: int
    language_pairs: tuple[tuple[str, str, str], ...]
    license_id: str
    attribution: str
    official_url: str


CORE_PACKS = (
    PackDefinition(
        "cc-cedict", "cedict_1_0_ts_utf-8_mdbg.txt.gz", "cc-cedict.zh-en",
        "cc-cedict", "2026-08-22T08:27:42Z", 1,
        (("zh-Hans", "en", "TRANSLATION"), ("zh-Hant", "en", "TRANSLATION")),
        "CC-BY-SA-4.0", "CC-CEDICT data from MDBG, CC BY-SA 4.0.",
        "https://cc-cedict.org/editor/editor.php?handler=Download",
    ),
    PackDefinition(
        "korean-basic", "korean_basic_dictionary.db", "korean-basic.multilingual",
        "korean-basic-dictionary", "2026-08-19", 1,
        tuple(
            pair
            for language in ("en", "ja", "fr", "es", "ar", "mn", "vi", "th", "id", "ru", "zh")
            for pair in (("ko", language, "TRANSLATION"), (language, "ko", "TRANSLATION"))
        ),
        "CC-BY-SA-2.0-KR", "한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.",
        "https://krdict.korean.go.kr/",
    ),
    PackDefinition(
        "panlex", "panlex_korean_fallback.db", "panlex.ko-fallback",
        "panlex", "2019-09-01", 1,
        panlex_language_pairs(),
        "CC0-1.0", "PanLex Database; citation to panlex.org is recommended.",
        "https://panlex.org/",
    ),
    PackDefinition(
        "jmdict", "jmdict.db", "jmdict.ja-en", "jmdict", "2026-08-23", 1,
        (("ja", "en", "TRANSLATION"),), "CC-BY-SA-4.0",
        "JMdict by EDRDG and contributors, CC BY-SA 4.0.",
        "https://www.edrdg.org/jmdict/j_jmdict.html",
    ),
)


def kaikki_pack_definition(language: str) -> PackDefinition:
    if language not in KAIKKI_SUPPORTED_LANGUAGE_TAGS:
        raise ValueError(f"Unsupported Kaikki pack language: {language}")
    return PackDefinition(
        dataset="kaikki",
        artifact=f"{language}.db",
        pack_id=f"kaikki.{language}-en",
        provider_id="kaikki",
        dataset_version=KAIKKI_DATASET_RELEASE_ID,
        dataset_schema_version=KAIKKI_INDEX_SCHEMA_VERSION,
        language_pairs=((language, "en", "TRANSLATION"),),
        license_id="CC-BY-SA-4.0",
        attribution=(
            "English Wiktionary data extracted by Wiktextract and distributed through "
            "Kaikki.org. Wiktionary text is dual-licensed under CC BY-SA 4.0 and GFDL "
            "1.1+; this pack uses the CC BY-SA 4.0 option."
        ),
        official_url="https://kaikki.org/dictionary/rawdata.html",
    )


KAIKKI_PACKS = tuple(map(kaikki_pack_definition, KAIKKI_SUPPORTED_LANGUAGE_TAGS))
PACKS = CORE_PACKS + KAIKKI_PACKS


def pack_file_name(definition: PackDefinition) -> str:
    return f"{definition.pack_id}-{_safe_version(definition.dataset_version)}.dictpack"


def build_pack(definition: PackDefinition, project_root: Path) -> tuple[Path, dict]:
    paths = dataset_paths(definition.dataset, project_root)
    source = _find_artifact(definition, paths.generated, paths.source)
    paths.packs.mkdir(parents=True, exist_ok=True)
    digest = _sha256(source)
    manifest_without_build_time = {
        "format": PACK_FORMAT,
        "manifestSchemaVersion": MANIFEST_SCHEMA_VERSION,
        "packId": definition.pack_id,
        "providerId": definition.provider_id,
        "datasetVersion": definition.dataset_version,
        "datasetSchemaVersion": definition.dataset_schema_version,
        "supportedLanguagePairs": [
            {"sourceLanguage": source_language, "resultLanguage": result_language, "resultKind": kind}
            for source_language, result_language, kind in definition.language_pairs
        ],
        "payload": {
            "fileName": definition.artifact,
            "sizeBytes": source.stat().st_size,
            "sha256": digest,
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
    output = paths.packs / pack_file_name(definition)
    reusable = _reusable_manifest(
        output=output,
        expected_without_build_time=manifest_without_build_time,
        payload_name=definition.artifact,
    )
    if reusable is not None:
        return output, reusable
    manifest = {
        **manifest_without_build_time,
        "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    temporary = output.with_suffix(output.suffix + ".tmp")
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            archive.writestr(
                "manifest.json",
                json.dumps(manifest, ensure_ascii=False, separators=(",", ":")),
            )
            archive.write(source, definition.artifact)
        temporary.replace(output)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
    return output, manifest


def _reusable_manifest(
    output: Path,
    expected_without_build_time: dict,
    payload_name: str,
) -> dict | None:
    if not output.is_file():
        return None
    try:
        with zipfile.ZipFile(output) as archive:
            if archive.namelist() != ["manifest.json", payload_name]:
                return None
            existing = json.loads(archive.read("manifest.json"))
            if not isinstance(existing, dict) or not existing.get("createdAt"):
                return None
            comparable = {key: value for key, value in existing.items() if key != "createdAt"}
            if comparable != expected_without_build_time:
                return None
            if archive.getinfo(payload_name).file_size != expected_without_build_time["payload"]["sizeBytes"]:
                return None
            return existing
    except (OSError, KeyError, ValueError, zipfile.BadZipFile, json.JSONDecodeError):
        return None


def _find_artifact(
    definition: PackDefinition,
    generated: Path,
    source: Path,
) -> Path:
    candidates = (
        generated / definition.artifact,
        source / definition.artifact,
    )
    artifact = next((candidate for candidate in candidates if candidate.is_file()), None)
    if artifact is None:
        expected = " or ".join(str(candidate) for candidate in candidates)
        raise FileNotFoundError(
            f"Missing canonical artifact for {definition.dataset}: {expected}"
        )
    return artifact


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_version(value: str) -> str:
    return "".join(character if character.isalnum() or character in ".-_" else "_" for character in value)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--pack",
        action="append",
        choices=[definition.dataset for definition in PACKS],
        help="Build only the selected dataset; repeat to build multiple packs",
    )
    parser.add_argument(
        "--kaikki-language",
        action="append",
        choices=KAIKKI_SUPPORTED_LANGUAGE_TAGS,
        help="Restrict Kaikki pack builds; repeat for multiple languages",
    )
    args = parser.parse_args()
    project_root = Path(__file__).resolve().parents[1]
    requested_kaikki_languages = set(args.kaikki_language or KAIKKI_SUPPORTED_LANGUAGE_TAGS)
    selected = [
        item
        for item in PACKS
        if (not args.pack or item.dataset in args.pack) and
        (item.dataset != "kaikki" or item.artifact.removesuffix(".db") in requested_kaikki_languages)
    ]
    resolved_root = dataset_paths(selected[0].dataset, project_root).root
    print(dataset_root_summary(resolved_root))
    report = []
    for definition in selected:
        output, manifest = build_pack(definition, project_root)
        report.append(
            {
                "pack": str(output),
                "archiveBytes": output.stat().st_size,
                "payloadBytes": manifest["payload"]["sizeBytes"],
                "sha256": manifest["payload"]["sha256"],
            }
        )
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
