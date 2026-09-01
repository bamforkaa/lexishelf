# Dictionary pack 저장과 설치

LexiShelf는 base APK와 dictionary dataset을 분리합니다. 앱은 사전 데이터가 없어도 manual
vocabulary workflow를 제공하고, 사용자는 필요한 language pack만 선택해 설치합니다.

## 저장소 경계

Dictionary data에는 서로 다른 두 저장소가 있습니다.

- 개발 PC workspace: 원본 artifact, converter output과 생성된 `.dictpack`
- Android app-private storage: 검증하고 활성화한 runtime pack

Runtime pack은 user vocabulary Room DB와 분리되어 있으며 JSON backup에도 포함되지 않습니다.
Pack update, rollback 또는 삭제는 사용자 단어·뜻·예문·태그·wordbook을 수정하지 않습니다.

개발 PC의 dataset root는 다음 우선순위로 선택합니다.

1. 현재 process의 `LANG_DATABASE_DIR`
2. gitignored `local.properties`의 `dictionaryDataDir`
3. 저장소의 gitignored `.local/dictionary-data`

Windows에서 별도 drive를 사용하는 예시:

```properties
dictionaryDataDir=D\:\\dictionary-data
```

또는 현재 PowerShell session에서만 지정할 수 있습니다.

```powershell
$env:LANG_DATABASE_DIR = 'D:\dictionary-data'
```

공통 layout은 다음과 같습니다.

```text
<dataset-root>/
├── cc-cedict/{source,generated,packs}/
├── korean-basic/{source,generated,packs}/
├── panlex/{source,generated,packs}/
├── jmdict/{source,generated,packs}/
└── kaikki/{source,generated,packs}/
```

원본과 생성 artifact는 크기와 별도 license 때문에 Git 또는 base APK에 포함하지 않습니다.

## 공개 catalog 설치

Release app은 다음 stable endpoint에서 schema v1 catalog를 가져옵니다.

```text
https://github.com/Bamfor/lexishelf/releases/latest/download/dictionary-catalog-v1.json
```

Catalog parser는 허용된 GitHub HTTPS URL, provider/pack identity, BCP 47 language pair, dataset
schema, archive와 payload 크기·SHA-256, source/license metadata를 확인합니다. 마지막으로 검증된
catalog는 app-private storage에 atomic하게 cache합니다.

Download는 같은 app-private filesystem의 staging file로 받습니다. Archive와 manifest 검증,
payload extraction과 integrity check가 모두 성공한 뒤에만 active version pointer를 교체합니다.
취소, checksum mismatch, malformed pack과 install failure는 기존 active version을 유지합니다.

정확한 공개 pack 목록, 크기, checksum과 artifact별 배포 결정은
[public artifact audit](public-dictionary-artifacts.md)에 있습니다.

## Local pack 생성

공식 source와 license를 [dictionary source review](dictionary-sources.md)에서 먼저 확인하고,
검토한 artifact만 canonical `source/` 또는 `generated/` 위치에 둡니다. 전체 pack 또는 하나의
provider를 생성할 수 있습니다.

```powershell
python -m tools.build_dictionary_packs
python -m tools.build_dictionary_packs --pack jmdict
python -X utf8 -m tools.build_dictionary_packs --pack kaikki --kaikki-language de
```

생성된 archive는 provider의 `packs/` directory에 저장됩니다. 일반 Gradle build는 이 명령을
실행하거나 원본 dataset을 자동으로 다운로드하지 않습니다.

개발용 debug APK에 일부 pack을 넣는 opt-in 설정도 있습니다.

```properties
bundleDictionaryPacksInDebug=true
debugDictionaryPackLanguages=de,vi
```

이 설정은 manual QA용이며 production manifest와 동일한 검증·activation 경로를 사용합니다.
Release APK에는 적용되지 않습니다. 대용량 pack이 필요하지 않은 평상시에는 설정하지 마세요.

## Local pack 설치

앱의 **설정 → 사전 데이터 → 로컬 pack 설치**에서 `.dictpack`을 선택합니다. Android Storage
Access Framework를 사용하므로 broad storage permission은 필요하지 않습니다.

Emulator의 Downloads로 명시적인 pack을 보낸 뒤 system file picker에서 선택할 수도 있습니다.

```powershell
adb push .\path\to\dictionary.dictpack /sdcard/Download/
```

App-private directory에 `adb push`로 직접 넣으면 manifest validation과 activation pointer를
우회하므로 정상 설치로 인정되지 않습니다.

## Manifest schema v1

`.dictpack`은 첫 entry가 `manifest.json`이고 그 뒤에 payload 하나가 오는 ZIP archive입니다.
Provider별 envelope 형식을 만들지 않고 공통 schema를 사용합니다.

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

`manifestSchemaVersion`은 archive envelope version입니다. Provider payload의
`datasetSchemaVersion`, user Room schema와 JSON backup schema는 서로 독립적으로 versioning합니다.

## Update, rollback과 failure

설치기는 plain payload filename, 정확히 한 payload, declared size와 SHA-256을 확인합니다. SQLite
payload는 schema와 integrity를, GZip payload는 전체 stream integrity를 검사합니다. 성공한 update는
현재 version과 직전 version을 남겨 한 단계 rollback을 지원합니다.

압축 해제된 payload는 최대 512 MiB이며, 설치 후 최소 32 MiB가 남을 수 있는 저장 공간이 있어야
합니다. Android 8 이상에서는 OS가 앱에 할당할 수 있는 공간을 확인하고, Android 6~7에서는 해당
app-private filesystem의 사용 가능한 공간을 fallback으로 확인합니다. 이 검사나 실제 파일 쓰기가
실패하면 incoming 파일을 정리하고 기존 active version을 유지합니다.

Pack이 없거나 유효하지 않으면 provider는 structured unavailable failure를 반환합니다. 이 오류는
dictionary suggestion 영역에만 영향을 주며 manual entry, 저장된 vocabulary 검색과 backup은 계속
동작합니다.
