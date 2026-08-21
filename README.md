# Local Vocabulary

개인용 Android 로컬 단어장 프로젝트입니다. 사용자가 직접 입력한 단어와 표현을 Room에 저장하고, 인터넷 연결 없이 검색·수정·태그 분류할 수 있도록 시작한 첫 번째 마일스톤입니다.

## 현재 구현 상태

구현됨:

- 단어/표현, BCP 47 언어 태그, 여러 뜻, 뜻별 품사와 여러 예문, 메모 입력 및 수정
- Room 기반 로컬 저장과 트랜잭션 단위 aggregate 갱신
- 태그 생성·이름 변경·삭제 및 단어와의 다대다 연결
- 단어/뜻/예문/메모 텍스트 검색과 태그 필터
- 단어 목록, 상세, 추가·수정, 태그 관리, 기본 언어 설정 화면
- Hilt 의존성 주입, Navigation Compose, DataStore 설정
- 향후 사전 소스를 위한 `DictionaryProvider` 계약과 외부 결과 모델 경계
- 도메인/매핑/repository/ViewModel 단위 테스트, Room DAO 테스트, Compose UI 테스트

구현하지 않음:

- Cambridge, JMdict, CC-CEDICT 또는 그 밖의 실제 사전 연동
- AI/LLM 정의, 기계번역, 로그인, 클라우드 동기화, 분석, 광고, 백엔드, 프록시
- JSON 백업/복원 실행 기능과 복습 기능

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

프로젝트는 단일 `app` 모듈이지만 presentation은 package-by-feature로, domain/data/database/provider 경계는 명시적으로 분리했습니다. 자세한 내용은 [docs/architecture.md](docs/architecture.md)와 [ADR-0001](docs/decisions/0001-local-first-single-module.md)을 참고하세요.

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

사용자 데이터는 앱 전용 Room 데이터베이스에만 저장됩니다. OS 클라우드 백업은 비활성화했습니다. 앱 삭제 시 데이터가 사라질 수 있으며 JSON 백업/복원은 아직 구현되지 않았습니다. 백업은 UTF-8, 명시적 schema version, 전체 검증, import preview, 충돌 정책을 갖는 형식으로 구현할 예정입니다.

## API 키와 비밀정보

현재는 API를 사용하지 않으므로 입력할 키가 없습니다. [`config/api-credentials.properties.example`](config/api-credentials.properties.example)은 빈 미래 설정 구조만 보여 줍니다. 실제 키를 위한 `secrets.properties`, Android SDK 경로가 든 `local.properties`, keystore 파일은 `.gitignore` 대상입니다. 향후 사용자 입력 키를 클라이언트에 저장하더라도 서버 측 비밀과 같은 보호 수준을 제공할 수 없음을 UI와 문서에 표시해야 합니다.

## 알려진 제한

- 백업/복원, 복습, 즐겨찾기, 발음/reading/transliteration 필드는 후속 마일스톤입니다.
- 프로세스가 강제 종료되면 저장 전 편집 초안이 복원되지 않을 수 있습니다. 저장된 데이터는 영향을 받지 않습니다.
- 외부 사전 데이터는 라이선스·저장·편집·재배포 조건이 확인되기 전까지 다운로드하거나 저장하지 않습니다.
- Room schema version은 1입니다. 영속 배포 후 변경에는 명시적 migration이 필요하며 destructive migration을 사용하지 않습니다.
