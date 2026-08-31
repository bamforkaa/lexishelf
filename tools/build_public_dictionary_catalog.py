"""Build the public v1 catalog from already-reviewed .dictpack archives.

This tool never downloads or builds dictionary data. It only audits the exact
local archives selected for a GitHub release and emits public metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

from tools.build_dictionary_packs import (
    CORE_PACKS,
    KAIKKI_MORPHOLOGY_PACKS,
    KAIKKI_PACKS,
    MANIFEST_SCHEMA_VERSION,
    PackDefinition,
    pack_file_name,
)
from tools.dataset_paths import dataset_paths

CATALOG_SCHEMA_VERSION = 1
PUBLIC_RELEASE_TAG = "v0.1.0"
PUBLIC_REPOSITORY = "Bamfor/lexishelf"
SHA256_PATTERN_LENGTH = 64


@dataclass(frozen=True)
class PublicMetadata:
    display_name: str
    section: str
    description: str
    source_name: str
    source_artifact_id: str
    source_artifact_sha256: str
    source_url: str
    license_name: str
    license_url: str
    attribution: str
    share_alike: bool
    transformation_notice: str
    recommended_for: tuple[str, ...]


KOREAN_BASIC_SOURCE_SHA256 = "7cf41e62a2a36158a8be2b6d2f84c086221e9b29d4345c44e5497eebf21c8c40"
PANLEX_SOURCE_SHA256 = "e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be"
KAIKKI_SOURCE_SHA256 = "e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65"

LANGUAGE_NAMES = {
    "de": "German",
    "hi": "Hindi",
    "pl": "Polish",
    "nl": "Dutch",
    "pt": "Portuguese",
    "tr": "Turkish",
    "cs": "Czech",
    "sv": "Swedish",
    "uk": "Ukrainian",
    "vi": "Vietnamese",
    "th": "Thai",
    "id": "Indonesian",
}


def public_definitions() -> tuple[PackDefinition, ...]:
    core = tuple(definition for definition in CORE_PACKS if definition.dataset != "jmdict")
    return core + KAIKKI_PACKS + KAIKKI_MORPHOLOGY_PACKS


def metadata_for(definition: PackDefinition) -> PublicMetadata:
    if definition.pack_id == "cc-cedict.zh-en":
        return PublicMetadata(
            "CC-CEDICT",
            "SPECIALIZED",
            "중국어 → 영어 · 병음과 상세 영어 gloss",
            "CC-CEDICT / MDBG",
            definition.artifact,
            "f552a8f4e3beddd2fcf2b5ad670cff24668ee8c60da839489492722e361f8dc5",
            "https://cc-cedict.org/editor/editor.php?handler=Download",
            "CC BY-SA 4.0",
            "https://creativecommons.org/licenses/by-sa/4.0/",
            "CC-CEDICT data from MDBG, CC BY-SA 4.0.",
            True,
            "원본 UTF-8 GZip을 내용 변경 없이 검증 가능한 .dictpack으로 재포장했습니다.",
            ("zh", "zh-Hans", "zh-Hant"),
        )
    if definition.pack_id == "korean-basic.multilingual":
        return PublicMetadata(
            "한국어기초사전",
            "KOREAN_MEANINGS",
            "영어·일본어·중국어·프랑스어 등 ↔ 한국어 · 수록 범위는 제한적",
            "국립국어원 한국어기초사전",
            "korean-basic-dictionary-json.zip",
            KOREAN_BASIC_SOURCE_SHA256,
            "https://krdict.korean.go.kr/download/downloadPopup",
            "CC BY-SA 2.0 KR",
            "https://creativecommons.org/licenses/by-sa/2.0/kr/",
            "한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.",
            True,
            "공식 전체 JSON의 text만 SQLite exact index로 변환했으며 이미지·음원·멀티미디어는 제외했습니다.",
            ("en", "ja", "fr", "es", "ar", "mn", "vi", "th", "id", "ru", "zh"),
        )
    if definition.pack_id == "panlex.ko-fallback":
        return PublicMetadata(
            "PanLex (pinned 2019 snapshot)",
            "KOREAN_MEANINGS",
            "선택 언어 ↔ 한국어 · 폭넓은 어휘 fallback이며 완전한 사전이 아님",
            "PanLex Database",
            "panlex-20190901-csv.zip",
            PANLEX_SOURCE_SHA256,
            "https://web.archive.org/web/20240614064246id_/https://db.panlex.org/panlex-20190901-csv.zip",
            "CC0 1.0 (pinned artifact only)",
            "https://creativecommons.org/publicdomain/zero/1.0/",
            "PanLex Database; citation to panlex.org is recommended.",
            False,
            "체크섬으로 고정한 2019-09-01 CSV에서 검토한 언어 variety의 직접 co-denotation 관계만 SQLite로 인덱싱했습니다.",
            tuple(pair[0] for pair in definition.language_pairs if pair[0] != "ko"),
        )
    if definition.pack_id == "kaikki.en-morphology":
        return kaikki_metadata(
            definition,
            display_name="English Morphology",
            section="SPECIALIZED",
            description="is → be · went → go · source-attested 영어 form → lemma",
            recommended_for=("en",),
        )
    language = definition.language_pairs[0][0]
    return kaikki_metadata(
        definition,
        display_name=f"Kaikki — {LANGUAGE_NAMES[language]}",
        section="ENGLISH_DETAILS",
        description=f"{LANGUAGE_NAMES[language]} → 영어 · 품사·발음·활용형·예문은 원자료에 있을 때만 제공",
        recommended_for=(language,),
    )


def kaikki_metadata(
    definition: PackDefinition,
    *,
    display_name: str,
    section: str,
    description: str,
    recommended_for: tuple[str, ...],
) -> PublicMetadata:
    return PublicMetadata(
        display_name,
        section,
        description,
        "English Wiktionary via Kaikki.org / Wiktextract",
        "raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz",
        KAIKKI_SOURCE_SHA256,
        "https://kaikki.org/dictionary/rawdata.html",
        "CC BY-SA 4.0",
        "https://creativecommons.org/licenses/by-sa/4.0/",
        definition.attribution,
        True,
        "English Wiktionary text를 언어별 SQLite index로 변환했으며 외부 인용문, media/audio와 별도 라이선스 자료는 제외했습니다.",
        recommended_for,
    )


def load_pack(path: Path, definition: PackDefinition) -> tuple[dict, str, int]:
    if not path.is_file():
        raise FileNotFoundError(f"Missing reviewed public pack: {path.name}")
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if names != ["manifest.json", definition.artifact]:
            raise ValueError(f"Unexpected archive entries in {path.name}: {names}")
        manifest = json.loads(archive.read("manifest.json"))
        if archive.testzip() is not None:
            raise ValueError(f"ZIP CRC failed for {path.name}")
    expected = {
        "format": "local-vocabulary-dictionary-pack",
        "packId": definition.pack_id,
        "providerId": definition.provider_id,
        "datasetVersion": definition.dataset_version,
        "datasetSchemaVersion": definition.dataset_schema_version,
        "manifestSchemaVersion": MANIFEST_SCHEMA_VERSION,
    }
    for key, value in expected.items():
        if manifest.get(key) != value:
            raise ValueError(f"{path.name} has unexpected {key}")
    expected_pairs = [
        {"sourceLanguage": source, "resultLanguage": result, "resultKind": kind}
        for source, result, kind in definition.language_pairs
    ]
    if manifest.get("supportedLanguagePairs") != expected_pairs:
        raise ValueError(f"{path.name} has unexpected supportedLanguagePairs")
    if manifest.get("license") != {
        "licenseId": definition.license_id,
        "attribution": definition.attribution,
    }:
        raise ValueError(f"{path.name} has unexpected license metadata")
    if manifest.get("source") != {
        "officialUrl": definition.official_url,
        "sourceVersion": definition.dataset_version,
        "buildTool": "tools/build_dictionary_packs.py",
    }:
        raise ValueError(f"{path.name} has unexpected source metadata")
    payload = manifest.get("payload", {})
    if payload.get("fileName") != definition.artifact:
        raise ValueError(f"{path.name} has unexpected payload")
    if not isinstance(payload.get("sizeBytes"), int) or payload["sizeBytes"] <= 0:
        raise ValueError(f"{path.name} has invalid payload size")
    if not _is_sha256(payload.get("sha256")):
        raise ValueError(f"{path.name} has invalid payload checksum")
    return manifest, _sha256(path), path.stat().st_size


def build_catalog(
    project_root: Path,
    repository: str,
    release_tag: str,
    catalog_version: str,
    generated_at: str,
) -> tuple[dict, list[tuple[str, str]]]:
    if repository != PUBLIC_REPOSITORY:
        raise ValueError(f"Public v1 repository must be {PUBLIC_REPOSITORY}")
    if not release_tag or "/" in release_tag:
        raise ValueError("Invalid release tag")
    packs = []
    checksums: list[tuple[str, str]] = []
    for definition in public_definitions():
        if definition.dataset == "jmdict" or definition.provider_id == "jmdict":
            raise AssertionError("JMdict must not enter the public catalog")
        path = dataset_paths(definition.dataset, project_root).packs / pack_file_name(definition)
        manifest, archive_sha256, archive_bytes = load_pack(path, definition)
        metadata = metadata_for(definition)
        pairs = manifest["supportedLanguagePairs"]
        packs.append(
            {
                "packId": definition.pack_id,
                "providerId": definition.provider_id,
                "displayName": metadata.display_name,
                "section": metadata.section,
                "description": metadata.description,
                "datasetVersion": definition.dataset_version,
                "manifestSchemaVersion": MANIFEST_SCHEMA_VERSION,
                "datasetSchemaVersion": definition.dataset_schema_version,
                "supportedLanguagePairs": pairs,
                "downloadUrl": (
                    f"https://github.com/{repository}/releases/download/{quote(release_tag)}/"
                    f"{quote(path.name)}"
                ),
                "downloadSizeBytes": archive_bytes,
                "installedSizeBytes": manifest["payload"]["sizeBytes"],
                "sha256": archive_sha256,
                "payloadSha256": manifest["payload"]["sha256"],
                "sourceName": metadata.source_name,
                "sourceArtifactId": metadata.source_artifact_id,
                "sourceArtifactSha256": metadata.source_artifact_sha256,
                "sourceUrl": metadata.source_url,
                "licenseName": metadata.license_name,
                "licenseUrl": metadata.license_url,
                "attribution": metadata.attribution,
                "shareAlike": metadata.share_alike,
                "transformationNotice": metadata.transformation_notice,
                "redistributionAllowed": True,
                "publicDistributionDecision": "PUBLIC_DISTRIBUTION_ALLOWED",
                "recommendedFor": list(dict.fromkeys(metadata.recommended_for)),
            }
        )
        checksums.append((archive_sha256, path.name))
    if len(packs) != 16:
        raise AssertionError(f"Expected 16 public packs, found {len(packs)}")
    return {
        "schemaVersion": CATALOG_SCHEMA_VERSION,
        "catalogVersion": catalog_version,
        "generatedAt": generated_at,
        "packs": packs,
    }, checksums


def write_outputs(
    catalog: dict,
    checksums: list[tuple[str, str]],
    catalog_output: Path,
    checksums_output: Path,
    report_output: Path,
) -> None:
    catalog_output.parent.mkdir(parents=True, exist_ok=True)
    catalog_text = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    catalog_output.write_text(catalog_text, encoding="utf-8", newline="\n")
    all_checksums = checksums + [(_sha256(catalog_output), catalog_output.name)]
    checksums_output.parent.mkdir(parents=True, exist_ok=True)
    checksums_output.write_text(
        "".join(f"{digest}  {name}\n" for digest, name in all_checksums),
        encoding="utf-8",
        newline="\n",
    )
    report_output.parent.mkdir(parents=True, exist_ok=True)
    report_output.write_text(
        render_artifact_report(catalog),
        encoding="utf-8",
        newline="\n",
    )


def render_artifact_report(catalog: dict) -> str:
    packs = catalog["packs"]
    rows = []
    for pack in packs:
        archive_name = pack["downloadUrl"].rsplit("/", 1)[-1]
        rows.append(
            "| `{packId}` | `{providerId}` | `{datasetVersion}` / `{datasetSchemaVersion}` | "
            "`{sourceArtifactId}`<br>`{sourceArtifactSha256}` | `{archive}`<br>`{sha256}` | "
            "{download:,} / {installed:,} | {license}<br>ShareAlike: {share_alike} | "
            "PUBLIC_DISTRIBUTION_ALLOWED |".format(
                packId=pack["packId"],
                providerId=pack["providerId"],
                datasetVersion=pack["datasetVersion"],
                datasetSchemaVersion=pack["datasetSchemaVersion"],
                sourceArtifactId=pack["sourceArtifactId"],
                sourceArtifactSha256=pack["sourceArtifactSha256"],
                archive=archive_name,
                sha256=pack["sha256"],
                download=pack["downloadSizeBytes"],
                installed=pack["installedSizeBytes"],
                license=pack["licenseName"],
                share_alike="Yes" if pack["shareAlike"] else "No",
            )
        )

    def totals(pack_ids: set[str]) -> tuple[int, int]:
        selected = [pack for pack in packs if pack["packId"] in pack_ids]
        return (
            sum(pack["downloadSizeBytes"] for pack in selected),
            sum(pack["installedSizeBytes"] for pack in selected),
        )

    combinations = [
        ("Korean-first basic", {"korean-basic.multilingual", "panlex.ko-fallback"}),
        ("Chinese detail", {"korean-basic.multilingual", "cc-cedict.zh-en"}),
        ("German with Korean + English fallback", {"panlex.ko-fallback", "kaikki.de-en"}),
    ]
    combination_rows = "\n".join(
        f"| {name} | {download:,} | {installed:,} |"
        for name, ids in combinations
        for download, installed in [totals(ids)]
    )
    total_download, total_installed = totals({pack["packId"] for pack in packs})
    return f"""# Public dictionary artifact audit

Generated from the exact reviewed `.dictpack` archives selected for catalog
`{catalog['catalogVersion']}`. This is release metadata, not legal advice. Updating any source or
archive requires a new artifact-level audit; a provider-wide license assumption is not enough.

## Public v1 provider decision

| Provider | Public v1? | Pack(s) | License basis | Maintenance concern |
| --- | --- | --- | --- | --- |
| Korean Basic Dictionary | Yes | `korean-basic.multilingual` | Official text under CC BY-SA 2.0 KR; multimedia excluded | Recheck each export date, text policy and conversion |
| CC-CEDICT | Yes | `cc-cedict.zh-en` | Exact MDBG release under CC BY-SA 4.0 | Recheck release timestamp, terms and attribution |
| PanLex | Yes, pinned artifact only | `panlex.ko-fallback` | Embedded CC0 grant in checksum-pinned 2019-09-01 snapshot | Never generalize this decision to another snapshot |
| Kaikki / English Wiktionary | Yes | 12 language packs plus English morphology | Selected CC BY-SA 4.0 reuse path | Recheck dump, filter policy, schema and external-material exclusions |
| JMdict | **No** | None | EDRDG CC BY-SA 4.0 plus a regular-update procedure | Public v1 does not assume the continuing update obligation; local/developer integration remains |

## Exact public artifacts

`Source SHA-256` identifies the reviewed publisher artifact. `Archive SHA-256` identifies the
project-generated release asset. Download and installed sizes are bytes. Every row has
`redistributionAllowed=true`; otherwise the generator refuses to emit the public catalog.

| Pack ID | Provider ID | Dataset / schema | Source artifact / SHA-256 | Release archive / SHA-256 | Download / installed | License | Decision |
| --- | --- | --- | --- | --- | ---: | --- | --- |
{chr(10).join(rows)}

The catalog and each pack manifest preserve source, version, license, attribution, source URL and
the transformation identity. Transformation details are in
[`dictionary-catalog-v1.json`](../distribution/dictionary-catalog-v1.json); archive checksums are
also in [`SHA256SUMS.txt`](../distribution/SHA256SUMS.txt).

## Size implications

All 16 public packs total **{total_download:,} download bytes** and **{total_installed:,} installed
bytes**. The app does not offer “install all” as a default.

| Example selection | Download bytes | Installed bytes |
| --- | ---: | ---: |
{combination_rows}

Coverage is inherently incomplete. Korean Basic installation does not guarantee a Korean meaning
for every expression, and PanLex is a broad lexical fallback rather than a complete dictionary.

## License and transformation details

- **Korean Basic Dictionary:** official full JSON export dated 2026-08-19, source SHA-256
  `{KOREAN_BASIC_SOURCE_SHA256}`. The converter indexes text only and excludes image, audio and
  other multimedia.
- **CC-CEDICT:** exact MDBG artifact `cedict_1_0_ts_utf-8_mdbg.txt.gz`, release
  2026-08-22T08:27:42Z. It is repackaged without changing the source text; attribution and
  ShareAlike remain.
- **PanLex:** only `panlex-20190901-csv.zip` with source SHA-256 `{PANLEX_SOURCE_SHA256}`. The
  generated subset indexes selected language varieties and direct co-denotation relations. The
  artifact's embedded CC0 grant is not asserted for another PanLex release.
- **Kaikki / Wiktionary:** English Wiktionary dump `enwiktionary-2026-08-05`, source SHA-256
  `{KAIKKI_SOURCE_SHA256}`. Each language is independent; no aggregate pack is published. The
  converter excludes externally attributed quotations, media/audio and separately licensed
  material and selects the CC BY-SA 4.0 reuse path.
"""


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _is_sha256(value: object) -> bool:
    return (
        isinstance(value, str)
        and len(value) == SHA256_PATTERN_LENGTH
        and all(character in "0123456789abcdef" for character in value)
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=PUBLIC_REPOSITORY)
    parser.add_argument("--release-tag", default=PUBLIC_RELEASE_TAG)
    parser.add_argument("--catalog-version", default=PUBLIC_RELEASE_TAG)
    parser.add_argument(
        "--generated-at",
        default=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    )
    parser.add_argument("--output", type=Path, default=Path("distribution/dictionary-catalog-v1.json"))
    parser.add_argument("--checksums-output", type=Path, default=Path("distribution/SHA256SUMS.txt"))
    parser.add_argument(
        "--report-output",
        type=Path,
        default=Path("docs/public-dictionary-artifacts.md"),
    )
    args = parser.parse_args()
    project_root = Path(__file__).resolve().parents[1]
    catalog, checksums = build_catalog(
        project_root,
        repository=args.repository,
        release_tag=args.release_tag,
        catalog_version=args.catalog_version,
        generated_at=args.generated_at,
    )
    write_outputs(catalog, checksums, args.output, args.checksums_output, args.report_output)
    print(
        json.dumps(
            {
                "catalog": str(args.output),
                "packs": len(catalog["packs"]),
                "downloadBytes": sum(pack["downloadSizeBytes"] for pack in catalog["packs"]),
                "installedBytes": sum(pack["installedSizeBytes"] for pack in catalog["packs"]),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
