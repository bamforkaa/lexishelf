# Local Vocabulary

개인용 Android 로컬 단어장 프로젝트입니다. 사용자가 직접 입력한 단어와 표현을 Room에 저장하고, 인터넷 연결 없이 검색·수정·태그 분류할 수 있도록 시작한 첫 번째 마일스톤입니다.

## 현재 구현 상태

구현됨:

- 단어/표현, BCP 47 언어 태그, 여러 뜻, 뜻별 품사와 여러 예문, 메모 입력 및 수정
- Room 기반 로컬 저장과 트랜잭션 단위 aggregate 갱신
- 태그 생성·이름 변경·삭제 및 단어와의 다대다 연결
- 단어/뜻/예문/메모 텍스트 검색과 태그 필터
- 단어 목록, 상세, 추가·수정, 태그 관리, 기본 언어 설정 화면
- Storage Access Framework 기반 UTF-8 JSON 백업/복원, import 미리보기와 명시적 충돌 정책
- Hilt 의존성 주입, Navigation Compose, DataStore 설정
- 향후 사전 소스를 위한 `DictionaryProvider` 계약과 외부 결과 모델 경계
- 도메인/매핑/repository/ViewModel 단위 테스트, Room DAO 테스트, Compose UI 테스트

구현하지 않음:

- Cambridge, JMdict, CC-CEDICT 또는 그 밖의 실제 사전 연동
- AI/LLM 정의, 기계번역, 로그인, 클라우드 동기화, 분석, 광고, 백엔드, 프록시
- 복습 기능

앱 Manifest에는 인터넷 권한이 없으며 현재 기능은 완전히 오프라인으로 동작합니다.

## 언어와 사전 공급자

수동 입력은 유효한 BCP 47 언어 태그(예: `en`, `ko`, `ja`, `zh-Hant`)를 사용하므로 특정 언어 목록으로 제한하지 않습니다. 이는 각 언어의 사전 지원을 의미하지 않습니다. 현재 등록되거나 활성화된 사전 공급자는 없습니다.

공급자별 라이선스 조사 상태와 구현 차단 조건은 [docs/dictionary-sources.md](docs/dictionary-sources.md)에 기록했습니다.

## 기술 구성

- Kotlin(AGP 9 내장 Kotlin), Jetpack Compose, Material 3
- Room, Coroutines/Flow, DataStore
- Navigation Compose, Hilt, KSP
- Gradle Version Catalog와 Gradle Wrapper
- JUnit 4, AndroidX Test, Room testing, Compose UI test

프로젝트는 단일 `app` 모듈이지만 presentation은 package-by-feature로, domain/data/database/provider 경계는 명시적으로 분리했습니다. 자세한 내용은 [docs/architecture.md](docs/architecture.md), [백업 형식 문서](docs/backup.md), [ADR-0001](docs/decisions/0001-local-first-single-module.md), [ADR-0002](docs/decisions/0002-versioned-json-backup.md)를 참고하세요.

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

백업에는 단어, 뜻/품사/예문, 메모, 태그/관계, 생성·수정 시간만 포함됩니다. 앱 설정, API key, credential, secret은 포함되지 않습니다. 현재 모델에는 favorite와 review metadata가 없으므로 schema v1에도 해당 필드가 없습니다.

## API 키와 비밀정보

현재는 API를 사용하지 않으므로 입력할 키가 없습니다. [`config/api-credentials.properties.example`](config/api-credentials.properties.example)은 빈 미래 설정 구조만 보여 줍니다. 실제 키를 위한 `secrets.properties`, Android SDK 경로가 든 `local.properties`, keystore 파일은 `.gitignore` 대상입니다. 향후 사용자 입력 키를 클라이언트에 저장하더라도 서버 측 비밀과 같은 보호 수준을 제공할 수 없음을 UI와 문서에 표시해야 합니다.

## 알려진 제한

- 복습, 즐겨찾기, 발음/reading/transliteration 필드는 후속 마일스톤입니다.
- 프로세스가 강제 종료되면 저장 전 편집 초안이 복원되지 않을 수 있습니다. 저장된 데이터는 영향을 받지 않습니다.
- 외부 사전 데이터는 라이선스·저장·편집·재배포 조건이 확인되기 전까지 다운로드하거나 저장하지 않습니다.
- Room schema version은 2입니다. v1→v2 migration은 기존 단어/태그에 백업용 stable ID를 부여합니다. 이후 변경에도 명시적 migration을 사용하며 destructive migration을 사용하지 않습니다.
