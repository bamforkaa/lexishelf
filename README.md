# Local Vocabulary

> Task 9: CC-CEDICT, 한국어기초사전, PanLex, JMdict payload를 base APK에서 generic
> installable dictionary pack으로 분리했습니다. Word Editor는 한국어 결과를 영어보다
> 먼저 제시하고, compact scrollable row-tap suggestion과 user-initiated NAVER 공식
> 사전 링크를 제공합니다. [pack 문서](docs/dictionary-packs.md)를 참고하세요.

개인용 Android local-first 단어장 프로젝트입니다. 사용자가 직접 입력한 단어와 표현은 Room에 저장되고, 선택적으로 설치한 CC-CEDICT, 한국어기초사전과 PanLex local dataset을 오프라인 검색해 참고할 수 있습니다.

## 현재 구현 상태

구현됨:

- 단어/표현, BCP 47 언어 태그, 여러 뜻, 뜻별 품사와 여러 예문, 메모 입력 및 수정
- Room 기반 로컬 저장과 트랜잭션 단위 aggregate 갱신
- 태그 생성·이름 변경·삭제 및 단어와의 다대다 연결
- 단어/뜻/예문/메모 텍스트 검색과 태그 필터
- 단어 목록, 상세, 추가·수정, 태그 관리, 기본 언어 설정 화면
- Word Editor headword 기반의 registry 공통 inline suggestion, CC-CEDICT 중국어 exact lookup
- 한국어기초사전의 한국어↔11개 외국어 양방향 exact/reverse lookup
- PanLex filtered index의 German/Hindi/Polish/Latin↔한국어 양방향 exact fallback
- 400ms debounce, provider별 결과/오류 표시, 명시적 autofill과 sense 단위 provenance
- provider dataset과 독립된 base APK, SAF local pack install/update/delete/one-step rollback
- 한국어 결과 우선·영어 fallback 언어 선택, 최대 20개 query와 최대 25개 lazy suggestion row
- reading 근처의 NAVER 공식 사전 reference link(user tap `ACTION_VIEW` only, fetch/scrape 없음)
- Storage Access Framework 기반 UTF-8 JSON 백업/복원, import 미리보기와 명시적 충돌 정책
- Hilt 의존성 주입, Navigation Compose, DataStore 설정
- online API/local dataset을 함께 수용하는 `DictionaryProvider` 계약, capability/usage policy, provider registry와 안전한 editor seed 경계
- 도메인/매핑/repository/ViewModel 단위 테스트, Room DAO 테스트, Compose UI 테스트

구현하지 않음:

- Cambridge, Kaikki/Wiktionary 또는 그 밖의 사전 연동
- CC-CEDICT pinyin/prefix/fuzzy 검색, 한국어기초사전 prefix/fuzzy 검색과 자동 dataset download/update
- AI/LLM 정의, 기계번역, 로그인, 클라우드 동기화, 분석, 광고, 백엔드, 프록시
- 복습 기능

앱 Manifest에는 인터넷 권한이 없으며 현재 기능은 완전히 오프라인으로 동작합니다.

## 언어와 사전 공급자

수동 입력은 유효한 BCP 47 언어 태그(예: `en`, `ko`, `ja`, `zh-Hant`)를 사용하므로 특정 언어 목록으로 제한하지 않습니다. 이는 각 언어의 사전 지원을 의미하지 않습니다. 실제 provider는 다음 네 개이며 모두 `LOCAL_DATASET`이므로 앱에는 `INTERNET` permission이 없습니다.

- `cc-cedict`: `zh-Hans → en`, `zh-Hant → en` exact headword lookup
- `korean-basic-dictionary`: `ko`와 `en`, `ja`, `fr`, `es`, `ar`, `mn`, `vi`, `th`, `id`, `ru`, `zh` 사이의 양방향 translation exact lookup
- `panlex`: `ko`와 `de`, `hi`, `pl`, `la` 사이의 양방향 direct translation exact lookup
- `jmdict`: `ja → en` exact kanji/kana lookup

대용량 dataset은 release base APK asset으로 읽지 않습니다. 일반 설치에서는 configurable dataset root에서 `.dictpack`을 만들고 설정 화면에서 SAF로 설치합니다. pack이 없어도 build와 수동 Word Editor는 정상 동작하고 suggestion 영역에만 dataset unavailable이 표시됩니다. 자세한 root/manifest/install 절차는 [dictionary pack 문서](docs/dictionary-packs.md)에 있습니다.

로컬 QA에서는 `local.properties`에 `bundleDictionaryPacksInDebug=true`를 지정할 수 있습니다. 그러면 `assembleDebug`/`installDebug`가 configured dataset root의 pack 네 개를 자동 재생성해 debug APK에만 포함합니다. 앱을 처음 열면 동일한 production manifest/size/SHA-256/payload 검증과 atomic activation을 거치며, 같은 payload가 이미 활성화돼 있으면 재설치하지 않습니다. 이 옵션을 끄면 기존 SAF 설치 및 `tools.stage_dictionary_packs` 흐름을 사용합니다.

한국어기초사전은 공식 전체 JSON을 개발 시 읽기 전용 SQLite exact/reverse index로 변환합니다. 생성 DB는 저장소에 포함되지 않으며 없을 때도 다른 provider와 수동 입력은 정상 동작합니다. 2026-08-19 자료의 설치·변환·업데이트 절차와 attribution은 [한국어기초사전 데이터셋 문서](docs/korean-basic-dictionary-dataset.md)에 있습니다. 공식 중국어 번역은 script를 구분하지 않으므로 `zh-Hans`/`zh-Hant`를 추측하지 않고 `zh`로 선언합니다.

PanLex는 검증한 2019-09-01 공식 CSV snapshot에서 `deu-000`/`hin-000`/`pol-000`/`lat-000`과 `kor-000`의 동일 meaning 직접 관계만 53,211,136-byte SQLite로 필터합니다. pivot translation이나 provider에 없는 linguistic field는 만들지 않습니다. snapshot은 오래되었고 현재 공식 distribution host가 unavailable한 제한을 명시적으로 유지합니다. source, checksum, CC0, 40개 QA sample과 생성 절차는 [PanLex 데이터셋 문서](docs/panlex-dataset.md)에 있습니다.

`Add word`는 선택 화면 없이 Word Editor를 바로 엽니다. 유효한 source language와 registry가 제공하는 result language pair가 있으면 headword를 기준으로 400ms 후 자동 검색하며, 빈 문자열과 동일 query/pair는 다시 검색하지 않습니다. result language option은 `ko`, `en`, 기타 순서이고 영어는 제거하지 않습니다. selectable sense/result가 4개 이하면 내용 높이만 사용하는 일반 목록이고, provider header를 제외한 selectable row가 5개 이상이면 높이 352dp의 단일 flatten `LazyColumn` 안에서 끝까지 스크롤합니다. row를 누르면 generic mapper로 가져오며 `Use`/`Use this sense` button은 없습니다. provider 오류는 suggestion 영역에만 있어 수동 편집이나 저장을 막지 않습니다.

외부 사전 결과는 persistent `VocabularyEntry`와 별도인 임시 domain model입니다. 결과 도착만으로 편집 필드를 바꾸지 않고 사용자가 suggestion row를 명시적으로 눌렀을 때만 `DictionaryEntryDraftMapper`를 통과합니다. 허용된 gloss/번역은 새 sense로 추가되며 provider/source/license/dataset/import 시점과 수정 여부가 sense provenance로 Room과 JSON backup에 보존됩니다. reading은 first-class field와 entry-level provenance로 별도 보존됩니다. 기존 사용자 sense를 지우지 않고 같은 source entry/sense의 반복 선택은 중복 추가하지 않습니다.

NAVER 링크는 dictionary content provider가 아닙니다. 현재 `en`, `ja`, `zh`(script variant 포함), `fr`, `de`, `es`, `ru`, `ar`, `hi`, `pl`, `mn`, `la`의 확인된 공식 destination만 중앙 mapping하며, headword만 URI encode합니다. 링크를 누르기 전 network request가 없고 NAVER content를 import/provenance/Room/backup에 넣지 않습니다. unsupported language나 빈 headword에서는 숨깁니다.

공급자별 라이선스 조사 상태와 구현 차단 조건은 [docs/dictionary-sources.md](docs/dictionary-sources.md)에 기록했습니다.

## 기술 구성

- Kotlin(AGP 9 내장 Kotlin), Jetpack Compose, Material 3
- Room, Coroutines/Flow, DataStore
- Navigation Compose, Hilt, KSP
- Gradle Version Catalog와 Gradle Wrapper
- JUnit 4, AndroidX Test, Room testing, Compose UI test

프로젝트는 단일 `app` 모듈이지만 presentation은 package-by-feature로, domain/data/database/provider 경계는 명시적으로 분리했습니다. 자세한 내용은 [architecture](docs/architecture.md), [pack ADR](docs/decisions/0009-installable-dictionary-packs.md), [external link ADR](docs/decisions/0010-user-initiated-external-dictionary-links.md)을 참고하세요.

## 개발 환경 설정

현재 컴퓨터의 점검 결과와 새 Windows 컴퓨터에서 재현하는 절차는 [docs/setup.md](docs/setup.md)에 있습니다. 전역 Gradle은 설치하지 않습니다.

현재 셸에서는 Android Studio 내장 JBR을 다음처럼 임시로 지정할 수 있습니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --version
```

빌드와 검사:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

### 대용량 dictionary dataset 경로

Windows에서 `D:\lang-Database`를 계속 사용할 때는 project root의 gitignored `local.properties`에 다음 값을 두는 방법을 권장합니다.

```properties
dictionaryDataDir=D\:\\lang-Database
bundleDictionaryPacksInDebug=true
```

현재 PowerShell session에서만 잠시 덮어쓰려면 다음 환경 변수를 사용합니다.

```powershell
$env:LANG_DATABASE_DIR = 'D:\lang-Database'
```

실제 우선순위는 `LANG_DATABASE_DIR` → `local.properties`의 `dictionaryDataDir` → `.local/dictionary-data`입니다. 이 설정을 바꿔도 기존 source/generated/packs 파일은 자동 이동되지 않습니다. 기존 dataset directory를 새 root의 동일한 layout으로 직접 옮기거나 새 root에서 converter와 pack builder를 다시 실행해야 합니다. 자세한 layout과 설치 절차는 [docs/dictionary-packs.md](docs/dictionary-packs.md)에 있습니다.

생성되는 개발용 APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. 연결된 기기에 설치하려면 SDK의 adb를 직접 사용합니다.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\app\build\outputs\apk\debug\app-debug.apk
```

에뮬레이터 또는 기기가 준비된 뒤 계측 테스트를 실행합니다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## 데이터와 백업

사용자 데이터는 앱 전용 Room 데이터베이스에 저장되며 OS 클라우드 백업은 비활성화했습니다. 목록 화면의 `백업`에서 Android 시스템 파일 선택기를 열어 UTF-8 JSON 파일을 내보내거나 가져올 수 있습니다. 앱은 공용 저장소 권한을 요청하지 않습니다.

가져오기는 파일 전체를 읽고 JSON/schema/데이터/참조를 검증한 뒤 미리보기를 표시합니다. 사용자가 확인하기 전에는 DB를 변경하지 않으며, 확인 후 반영도 하나의 Room transaction에서 수행합니다. 기본 정책은 stable ID가 같은 단어 aggregate만 갱신하고 새 단어를 추가하며 그 밖의 기존 데이터는 유지하는 병합입니다. 전체 교체는 사용자가 명시적으로 선택해야 합니다. 정확한 schema와 정책은 [docs/backup.md](docs/backup.md)에 있습니다.

백업 schema v3에는 단어, reading, 뜻/품사/예문, 메모, 태그/관계, 생성·수정 시간과 provider-derived entry/sense provenance가 포함됩니다. 직접 작성한 field에는 provenance가 없습니다. 앱 설정, API key, credential, secret은 포함되지 않으며 기존 schema v1/v2 파일도 계속 가져올 수 있습니다. 현재 모델에는 favorite와 review metadata가 없어 해당 필드도 없습니다.

## API 키와 비밀정보

현재는 API를 사용하지 않으므로 입력할 키가 없습니다. [`config/api-credentials.properties.example`](config/api-credentials.properties.example)은 빈 미래 설정 구조만 보여 줍니다. 실제 키를 위한 `secrets.properties`, Android SDK 경로가 든 `local.properties`, keystore 파일은 `.gitignore` 대상입니다. 향후 사용자 입력 키를 클라이언트에 저장하더라도 서버 측 비밀과 같은 보호 수준을 제공할 수 없음을 UI와 문서에 표시해야 합니다.

## 알려진 제한

- 복습, 즐겨찾기와 별도 audio/transliteration 필드는 후속 마일스톤입니다.
- 프로세스가 강제 종료되면 저장 전 편집 초안이 복원되지 않을 수 있습니다. 저장된 데이터는 영향을 받지 않습니다.
- 외부 사전 데이터는 라이선스·저장·편집·재배포 조건이 확인되기 전까지 다운로드하거나 저장하지 않습니다.
- clean clone과 zero-pack 앱은 dictionary lookup 대신 `LocalDatasetUnavailable`을 표시합니다. pack 전달은 현재 local SAF/ADB 개발 흐름뿐이며 remote catalog/signature는 구현하지 않았습니다.
- CC-CEDICT raw GZip은 설치 pack에서 첫 검색 때 메모리 exact index로 변환되므로 첫 query memory/latency를 별도로 관찰해야 합니다.
- PanLex source snapshot은 2019년 자료이고 현재 official distribution/API가 unavailable하므로 최신성에 한계가 있습니다.
- Room schema version은 4입니다. v1→v2는 backup stable ID, v2→v3는 sense provenance, v3→v4는 reading과 entry-field provenance를 기존 aggregate를 보존하며 추가합니다. destructive migration은 사용하지 않습니다.
