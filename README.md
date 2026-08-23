# Local Vocabulary

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
- Storage Access Framework 기반 UTF-8 JSON 백업/복원, import 미리보기와 명시적 충돌 정책
- Hilt 의존성 주입, Navigation Compose, DataStore 설정
- online API/local dataset을 함께 수용하는 `DictionaryProvider` 계약, capability/usage policy, provider registry와 안전한 editor seed 경계
- 도메인/매핑/repository/ViewModel 단위 테스트, Room DAO 테스트, Compose UI 테스트

구현하지 않음:

- Cambridge, JMdict, Kaikki/Wiktionary 또는 그 밖의 사전 연동
- CC-CEDICT pinyin/prefix/fuzzy 검색, 한국어기초사전 prefix/fuzzy 검색과 자동 dataset download/update
- AI/LLM 정의, 기계번역, 로그인, 클라우드 동기화, 분석, 광고, 백엔드, 프록시
- 복습 기능

앱 Manifest에는 인터넷 권한이 없으며 현재 기능은 완전히 오프라인으로 동작합니다.

## 언어와 사전 공급자

수동 입력은 유효한 BCP 47 언어 태그(예: `en`, `ko`, `ja`, `zh-Hant`)를 사용하므로 특정 언어 목록으로 제한하지 않습니다. 이는 각 언어의 사전 지원을 의미하지 않습니다. 실제 provider는 다음 세 개이며 모두 `LOCAL_DATASET`이므로 앱에는 `INTERNET` permission이 없습니다.

- `cc-cedict`: `zh-Hans → en`, `zh-Hant → en` exact headword lookup
- `korean-basic-dictionary`: `ko`와 `en`, `ja`, `fr`, `es`, `ar`, `mn`, `vi`, `th`, `id`, `ru`, `zh` 사이의 양방향 translation exact lookup
- `panlex`: `ko`와 `de`, `hi`, `pl`, `la` 사이의 양방향 direct translation exact lookup

CC-CEDICT GZip binary는 저장소에 포함되지 않습니다. 공식 release page가 자동 접근을 금지하므로 사용자가 브라우저에서 artifact를 받아 정해진 asset 경로에 두어야 합니다. 파일이 없으면 build는 성공하고 Word Editor도 정상 동작하며, suggestion 영역에만 dataset unavailable 상태를 표시합니다. 정확한 artifact와 수동 설치/update 절차는 [CC-CEDICT 데이터셋 문서](docs/cc-cedict-dataset.md)에 있습니다.

한국어기초사전은 공식 전체 JSON을 개발 시 읽기 전용 SQLite exact/reverse index로 변환합니다. 생성 DB는 저장소에 포함되지 않으며 없을 때도 다른 provider와 수동 입력은 정상 동작합니다. 2026-08-19 자료의 설치·변환·업데이트 절차와 attribution은 [한국어기초사전 데이터셋 문서](docs/korean-basic-dictionary-dataset.md)에 있습니다. 공식 중국어 번역은 script를 구분하지 않으므로 `zh-Hans`/`zh-Hant`를 추측하지 않고 `zh`로 선언합니다.

PanLex는 검증한 2019-09-01 공식 CSV snapshot에서 `deu-000`/`hin-000`/`pol-000`/`lat-000`과 `kor-000`의 동일 meaning 직접 관계만 53,211,136-byte SQLite로 필터합니다. pivot translation이나 provider에 없는 linguistic field는 만들지 않습니다. snapshot은 오래되었고 현재 공식 distribution host가 unavailable한 제한을 명시적으로 유지합니다. source, checksum, CC0, 40개 QA sample과 생성 절차는 [PanLex 데이터셋 문서](docs/panlex-dataset.md)에 있습니다.

`Add word`는 선택 화면 없이 Word Editor를 바로 엽니다. 유효한 source language와 registry가 제공하는 result language pair가 있으면 headword를 기준으로 400ms 후 자동 검색하며, 빈 문자열과 동일 query/pair는 다시 검색하지 않습니다. 여러 provider의 결과와 오류는 provider별 suggestion card에만 표시되어 dataset/network 오류가 수동 편집이나 저장을 막지 않습니다.

외부 사전 결과는 persistent `VocabularyEntry`와 별도인 임시 domain model입니다. 결과 도착만으로 편집 필드를 바꾸지 않고 사용자가 `Use`를 명시적으로 선택했을 때만 `DictionaryEntryDraftMapper`를 통과합니다. 허용된 gloss/번역은 새 sense로 추가되며 provider/source/license/dataset/import 시점과 수정 여부가 sense provenance로 Room과 JSON backup에 보존됩니다. 한국어기초사전 reverse lookup의 `Use`는 공식 한국어 표제어와 명시적 품사를 같은 generic pipeline으로 채웁니다. 기존 사용자 sense를 지우지 않고 같은 source entry/sense의 반복 선택은 중복 추가하지 않습니다. pinyin은 현재 저장 모델에 generic reading field가 없어 transient reference로만 표시하며 notes에 넣지 않습니다. source에 없는 필드는 추론하지 않습니다.

공급자별 라이선스 조사 상태와 구현 차단 조건은 [docs/dictionary-sources.md](docs/dictionary-sources.md)에 기록했습니다.

## 기술 구성

- Kotlin(AGP 9 내장 Kotlin), Jetpack Compose, Material 3
- Room, Coroutines/Flow, DataStore
- Navigation Compose, Hilt, KSP
- Gradle Version Catalog와 Gradle Wrapper
- JUnit 4, AndroidX Test, Room testing, Compose UI test

프로젝트는 단일 `app` 모듈이지만 presentation은 package-by-feature로, domain/data/database/provider 경계는 명시적으로 분리했습니다. 자세한 내용은 [docs/architecture.md](docs/architecture.md), [백업 형식 문서](docs/backup.md), [provider 경계 ADR](docs/decisions/0003-dictionary-provider-boundary.md), [CC-CEDICT integration ADR](docs/decisions/0004-cc-cedict-local-dataset.md), [provenance/autofill ADR](docs/decisions/0005-dictionary-import-provenance.md), [한국어기초사전 역색인 ADR](docs/decisions/0006-korean-basic-dictionary-local-reverse-index.md), [PanLex filtered fallback ADR](docs/decisions/0007-panlex-filtered-local-fallback.md)을 참고하세요.

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

백업 schema v2에는 단어, 뜻/품사/예문, 메모, 태그/관계, 생성·수정 시간과 provider-derived sense의 provenance가 포함됩니다. 직접 작성한 sense에는 provenance가 없습니다. 앱 설정, API key, credential, secret은 포함되지 않으며 기존 schema v1 파일도 계속 가져올 수 있습니다. 현재 모델에는 favorite와 review metadata가 없어 해당 필드도 없습니다.

## API 키와 비밀정보

현재는 API를 사용하지 않으므로 입력할 키가 없습니다. [`config/api-credentials.properties.example`](config/api-credentials.properties.example)은 빈 미래 설정 구조만 보여 줍니다. 실제 키를 위한 `secrets.properties`, Android SDK 경로가 든 `local.properties`, keystore 파일은 `.gitignore` 대상입니다. 향후 사용자 입력 키를 클라이언트에 저장하더라도 서버 측 비밀과 같은 보호 수준을 제공할 수 없음을 UI와 문서에 표시해야 합니다.

## 알려진 제한

- 복습, 즐겨찾기, 발음/reading/transliteration 필드는 후속 마일스톤입니다.
- 프로세스가 강제 종료되면 저장 전 편집 초안이 복원되지 않을 수 있습니다. 저장된 데이터는 영향을 받지 않습니다.
- 외부 사전 데이터는 라이선스·저장·편집·재배포 조건이 확인되기 전까지 다운로드하거나 저장하지 않습니다.
- clean clone에는 CC-CEDICT binary, 한국어기초사전 SQLite index와 PanLex SQLite index가 없어 수동 설치 전 해당 dictionary lookup을 할 수 없습니다.
- CC-CEDICT raw GZip은 첫 검색 때 메모리 exact index로 변환되며 full artifact의 APK/latency/memory 비용은 artifact를 제공한 환경에서 별도 측정해야 합니다.
- 한국어기초사전 index는 약 200MB이므로 APK 및 첫 asset 복사 비용이 큽니다. 현재는 개인 sideload용이며 후속 배포에서는 별도 language pack 전달을 검토해야 합니다.
- PanLex source snapshot은 2019년 자료이고 현재 official distribution/API가 unavailable하므로 최신성에 한계가 있습니다. 생성 index도 raw 53MB여서 공개 배포 전 별도 language pack을 검토해야 합니다.
- Room schema version은 3입니다. v1→v2는 기존 단어/태그에 backup stable ID를 부여하고, v2→v3는 기존 aggregate를 보존한 채 선택적인 sense provenance 테이블만 추가합니다. destructive migration은 사용하지 않습니다.
