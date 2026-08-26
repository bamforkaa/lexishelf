# Dictionary dataset storage and packs

## 두 종류의 저장소

개발 PC의 대용량 원본/생성 파일과 Android runtime pack은 서로 다른 저장소입니다.

- Windows 개발 저장소: `LANG_DATABASE_DIR` → `local.properties`의 `dictionaryDataDir` → 저장소의 gitignored `.local/dictionary-data` 순서로 결정합니다.
- Android runtime 저장소: 앱 전용 `noBackupFilesDir/dictionary-packs/<providerId>/<packId>`입니다. user vocabulary Room DB와 JSON backup에는 pack payload가 들어가지 않습니다.

Windows에서 지속적으로 `D:\lang-Database`를 사용할 때는 project root의 gitignored `local.properties` 설정을 권장합니다.

```properties
dictionaryDataDir=D\:\\lang-Database
bundleDictionaryPacksInDebug=true
debugDictionaryPackLanguages=de,vi
```

`bundleDictionaryPacksInDebug`는 개발자 Manual QA 전용 opt-in입니다. `true`이면 `assembleDebug`와 `installDebug`가 core pack 네 개와 `debugDictionaryPackLanguages`에 명시한 Kaikki per-language pack만 재생성하여 APK에 포함합니다. Kaikki 목록이 없으면 core pack만 포함합니다. 첫 앱 실행은 `AndroidDictionaryPackRepository`의 production manifest/크기/SHA-256/payload 검증과 atomic activation을 그대로 사용합니다. release APK에는 적용되지 않습니다.

현재 PowerShell session에서만 덮어쓸 때는 다음 환경 변수를 사용합니다.

```powershell
$env:LANG_DATABASE_DIR = 'D:\lang-Database'
```

어느 설정도 없으면 clean clone은 `.local/dictionary-data`를 사용합니다. 특정 드라이브나 이미 존재하는 dataset을 요구하지 않으며 일반 build/test는 pack 없이 성공해야 합니다.

root 설정 변경은 기존 파일을 자동으로 이동하지 않습니다. 다음 중 하나를 선택해야 합니다.

1. 기존 `cc-cedict`, `korean-basic`, `panlex`, `jmdict`, `kaikki` directory를 새 root 아래로 동일한 layout을 유지해 직접 이동합니다.
2. 새 root의 각 `source/`에 원본을 준비하고 converter와 pack builder를 다시 실행합니다.

converter와 pack builder는 시작할 때 실제 선택된 경로를 `Dictionary dataset root:`로 출력하므로 의도한 drive를 사용하는지 확인할 수 있습니다. 자동 dataset migration은 구현하지 않았습니다.

공통 layout:

```text
<root>/
├─ cc-cedict/{source,generated,packs}/
├─ korean-basic/{source,generated,packs}/
├─ panlex/{source,generated,packs}/
├─ jmdict/{source,generated,packs}/
└─ kaikki/{source,generated,packs}/
```

현재 다섯 provider의 정확한 개발 파일은 다음과 같습니다. CC-CEDICT는 SQLite 변환 단계가 없고 GZip source가 그대로 payload가 됩니다.

| Provider | Source input | Generated payload | Pack output |
| --- | --- | --- | --- |
| CC-CEDICT | `cc-cedict/source/cedict_1_0_ts_utf-8_mdbg.txt.gz` | 없음 | `cc-cedict/packs/cc-cedict.zh-en-2026-08-22T08_27_42Z.dictpack` |
| 한국어기초사전 | `korean-basic/source/korean-basic-dictionary-json.zip` | `korean-basic/generated/korean_basic_dictionary.db` | `korean-basic/packs/korean-basic.multilingual-2026-08-19.dictpack` |
| PanLex | `panlex/source/panlex-20190901-csv.zip` | `panlex/generated/panlex_korean_fallback.db` | `panlex/packs/panlex.ko-fallback-2019-09-01.dictpack` |
| JMdict | `jmdict/source/JMdict_e.gz` | `jmdict/generated/jmdict.db` | `jmdict/packs/jmdict.ja-en-2026-08-23.dictpack` |
| Kaikki | `kaikki/source/raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz` | `kaikki/generated/<language>.db` | `kaikki/packs/kaikki.<language>-en-enwiktionary-2026-08-05.dictpack` |

`build_jmdict_index.py`, `build_krdict_index.py`, `build_panlex_index.py`, `build_kaikki_indexes.py`는 생략 가능한 input/output 대신 위 canonical convention을 사용합니다. CC-CEDICT는 별도 변환 없이 source GZip을 pack payload로 사용합니다. pack builder는 repository의 legacy Android assets를 fallback 입력으로 사용하지 않습니다.

## Pack 만들기와 설치

검토한 artifact를 위 source/generated 위치에 둔 뒤 실행합니다.

```powershell
python -m tools.build_dictionary_packs
python -m tools.build_dictionary_packs --pack jmdict
python -X utf8 -m tools.build_dictionary_packs --pack kaikki --kaikki-language de
```

생성된 `.dictpack`은 각 dataset의 `packs/`에 생깁니다. Kaikki는 provider ID 하나 아래에 `kaikki.de-en` 같은 pack ID를 여러 개 설치할 수 있고 resolver가 provider ID와 exact language pair를 함께 사용해 선택합니다.

앱의 설정 → 사전 pack → 로컬 pack 설치에서 Android Storage Access Framework로 파일을 선택합니다. 공용 저장소 permission은 필요하지 않습니다. 개발 AVD에는 다음처럼 파일을 전달한 후 Files picker에서 선택할 수도 있습니다.

```powershell
adb push .\path\to\jmdict.ja-en-2026-08-23.dictpack /sdcard/Download/
```

core pack 네 개와 선택한 Kaikki pack을 명시적으로 이름을 확인한 Manual AVD의 Downloads에 staging하려면 다음 helper를 사용합니다.

```powershell
python -m tools.stage_dictionary_packs --avd-name Medium_Phone_Manual `
  --kaikki-language de --kaikki-language vi
```

Kaikki 언어 옵션을 생략하면 production-selected 12개 Kaikki pack을 모두 staging합니다. helper는 Test AVD나 이름이 다른 기기로 자동 fallback하지 않으며 pack을 활성화하지도 않습니다. `bundleDictionaryPacksInDebug=false`일 때는 staging 후 앱의 설정 → `로컬 pack 설치`에서 각 파일을 선택해야 합니다. Manual QA에서 옵션을 `true`로 설정하면 별도 staging 없이 `installDebug` 후 첫 실행에 core와 configured Kaikki pack이 검증·활성화됩니다. 같은 payload hash의 활성 pack은 건너뜁니다.

## Manifest schema v1

`.dictpack`은 `manifest.json`이 첫 entry이고 payload 하나가 뒤따르는 ZIP입니다. provider별 manifest 형식은 만들지 않습니다.

```json
{
  "format": "local-vocabulary-dictionary-pack",
  "manifestSchemaVersion": 1,
  "packId": "jmdict.ja-en",
  "providerId": "jmdict",
  "datasetVersion": "2026-08-23",
  "datasetSchemaVersion": 1,
  "supportedLanguagePairs": [
    {"sourceLanguage": "ja", "resultLanguage": "en", "resultKind": "TRANSLATION"}
  ],
  "payload": {"fileName": "jmdict.db", "sizeBytes": 113729536, "sha256": "..."},
  "license": {"licenseId": "CC-BY-SA-4.0", "attribution": "..."},
  "createdAt": "2026-08-23T00:00:00Z",
  "source": {"officialUrl": "...", "sourceVersion": "...", "buildTool": "..."}
}
```

`manifestSchemaVersion`은 pack envelope version이고 provider dataset SQLite의 `datasetSchemaVersion`, user Room schema v5, JSON backup schema v4와 서로 독립적입니다.

## 설치, update, rollback

설치기는 user-selected stream을 같은 app-owned filesystem의 임시 directory에 풉니다. manifest 구조, 정확히 한 payload, plain file name, declared size와 SHA-256을 확인하고, SQLite면 `user_version`/`integrity_check`, GZip이면 전체 CRC stream 검사를 수행합니다. 모든 검증이 끝난 immutable version directory만 rename한 뒤 작은 activation pointer를 교체합니다.

실패하면 activation pointer를 바꾸지 않아 기존 active pack을 그대로 사용합니다. 성공한 update는 active와 직전 version만 남기고 설정 화면에서 한 단계 rollback할 수 있습니다. crash로 남은 incoming/candidate/비활성 version은 다음 repository 초기화 또는 성공한 install 때 정리합니다. pack 삭제는 해당 pack directory만 지우며 Room/backup에는 접근하지 않습니다.

Pack이 없으면 provider는 `LocalDatasetUnavailable`을 반환하고 Word Editor suggestion group에만 오류가 보입니다. 수동 입력/저장과 기존 vocabulary 조회는 계속 동작합니다.

debug application ID 기준 실제 filesystem은 일반적으로 `/data/user/0/com.example.localvocabulary/no_backup/dictionary-packs/<providerId>/<packId>/` 아래입니다. 각 pack에는 `activation` pointer와 `versions/<datasetVersion>-<sha-prefix>/manifest.json` 및 payload가 있습니다. 이 경로에 `adb push`로 직접 파일을 넣는 것은 activation을 만들지 않고 검증을 우회하므로 정상 workflow가 아닙니다.

## 현재 pack inventory

| Pack | Version | Payload | Payload SHA-256 | 생성 archive 크기 |
| --- | --- | ---: | --- | ---: |
| `cc-cedict.zh-en` | `2026-08-22T08:27:42Z` | 3,969,462 | `f552a8f4e3beddd2fcf2b5ad670cff24668ee8c60da839489492722e361f8dc5` | 3,971,410 |
| `korean-basic.multilingual` | `2026-08-19` | 211,701,760 | `f76134c04668dd20897d50afab04dff6cf96de914e0eb96ce58cc8e7e4d7103b` | 67,967,805 |
| `panlex.ko-fallback` | `2019-09-01` | 189,714,432 | `10b780bc4f05d0d6d82d8772fc57364b469dd59991699d8fe01ea54de13d8280` | 72,462,031 |
| `jmdict.ja-en` | `2026-08-23` | 113,729,536 | `4ee28595198dbe0d8a166e16f74d976ba172cd29614d2d96b2ed933a8c1784e2` | 24,012,369 |
| `kaikki.<language>-en` (12개 합계) | `enwiktionary-2026-08-05` | 1,289,584,640 | language별 상이 | 약 215.9 MB |

이 checksum은 현재 로컬에서 검토한 payload의 pack integrity 값입니다. 공식 publisher checksum이라고 주장하지 않습니다.

Task 8의 dataset 포함 debug APK는 144,462,083 bytes였습니다. Task 9의 pack 미포함 base APK는 18,466,693 bytes입니다. Manual QA opt-in으로 core pack 네 개를 포함한 2026-08-25 debug APK는 129,043,442 bytes였습니다. Kaikki pack은 전체가 아니라 `debugDictionaryPackLanguages`의 subset만 추가하며, Task 12의 실제 APK 크기는 [Kaikki dataset 문서](kaikki-dataset.md)의 검증 결과를 따른다. release/base APK 분리 정책에는 영향을 주지 않습니다.
