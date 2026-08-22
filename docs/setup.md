# 개발 환경 설정

## 2026-08-22 현재 컴퓨터 점검 결과

점검은 읽기 전용 명령으로 수행했습니다. 시스템 도구 설치, PATH/레지스트리/셸 프로필 변경은 하지 않았습니다.

| 항목 | 확인 결과 | 상태 |
| --- | --- | --- |
| 운영체제 | Windows build `10.0.26200`, Gradle 식별값 `Windows 11 10.0 amd64` | 사용 가능 |
| CPU | Intel64, x64/AMD64 프로세스와 OS | 사용 가능 |
| Git | `2.54.0.windows.1` | 사용 가능 |
| Android Studio | `C:\Program Files\Android\Android Studio`, build `AI-261.26222.65.2613.15948027` | 설치됨 |
| JDK | Studio 내장 JBR/JDK `25.0.2`와 `javac 25.0.2` | 설치됨, PATH 미등록 |
| `JAVA_HOME` | `C:\Program Files\Java\jdk-21`을 가리키지만 해당 경로가 없음 | 수정 필요 |
| Android SDK | `C:\Users\a6230\AppData\Local\Android\Sdk` | 설치됨 |
| SDK Platform | Android 17 / API 37, revision 2 | 설치됨 |
| Build Tools | `36.0.0` | 설치됨, AGP 9.3 기본 버전과 일치 |
| adb | `1.0.41`, platform-tools `37.0.1-15733141` | 직접 경로로 사용 가능 |
| Emulator | `37.1.11.0` | 실행 파일 설치됨 |
| Command-line Tools | `cmdline-tools/latest/bin/sdkmanager.bat` 없음 | 선택 설치 필요 |
| 시스템 이미지/AVD | `Medium_Phone` AVD, Android 17/API 37, `emulator-5554` | 실행 및 계측 테스트 확인 |
| 전역 Gradle | 없음 | 정상; 설치하지 않음 |
| Gradle Wrapper | 프로젝트의 Gradle `9.5.0` Wrapper | 사용 가능 |
| 전역 Kotlin CLI | `kotlinc` 없음 | 별도 설치 불필요 |
| Android 환경 변수 | `ANDROID_HOME`, `ANDROID_SDK_ROOT` 없음 | `local.properties`로 빌드 가능 |

WMI/CIM을 통한 Windows 제품명 세부 조회는 현재 권한에서 `Access denied`였지만 .NET runtime과 Gradle이 x64 Windows 환경을 확인했습니다. Android Studio의 정확한 배포 채널은 build metadata만으로 단정하지 않고 `Help > About`과 `Help > Check for Updates`에서 확인해야 합니다.

프로젝트는 AGP `9.3.1`, Gradle `9.5.0`, compile/target SDK 37, Java/Kotlin bytecode target 17을 사용합니다. [AGP 9.3 호환성 표](https://developer.android.com/build/releases/agp-9-3-0-release-notes)는 최소/기본 Gradle 9.5.0, 최소 JDK 17, 기본 Build Tools 36.0.0, 최대 API 37을 명시합니다. Gradle 9.5는 JBR 25에서 실행 가능하며 이 컴퓨터에서도 실제 검증했습니다.

## 현재 컴퓨터에서 바로 빌드하기

잘못된 전역 `JAVA_HOME`을 자동으로 변경하지 않았습니다. 현재 PowerShell 세션에만 Studio 내장 JBR을 지정합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
```

`local.properties`에는 다음 SDK 경로가 있으며 `.gitignore`에 포함됩니다.

```properties
sdk.dir=C\:\\Users\\a6230\\AppData\\Local\\Android\\Sdk
```

검사 명령:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat lintDebug
```

백업/복원은 Android 시스템 파일 선택기(Storage Access Framework)를 사용하므로 별도 storage permission이나 SDK 도구 설치가 필요하지 않습니다. 실제 기기 또는 AVD에서 파일 제공자를 열 수 있어야 하며 수동 절차는 [backup.md](backup.md)에 있습니다.

## 선택 사항: CC-CEDICT local dataset

CC-CEDICT provider에는 SDK 도구, network permission 또는 API key가 필요하지 않습니다. 다만 full GZip binary는 저장소에 포함되지 않으므로 실제 lookup을 하려면 브라우저에서 공식 release를 수동으로 받아 다음 asset 경로에 두어야 합니다.

```text
app/src/main/assets/dictionary/cccedict/cedict_1_0_ts_utf-8_mdbg.txt.gz
```

MDBG는 automated/scripted access를 금지하므로 project script가 다운로드하지 않습니다. 기대 release, attribution, 파일 복사와 update 절차는 [cc-cedict-dataset.md](cc-cedict-dataset.md)를 따르세요. 파일이 없어도 build/test는 가능하며 앱은 검색 시 dataset unavailable을 표시합니다.

## 누락 항목 설치 및 설정

### 1. Android Studio 안정 채널 확인

최신 안정판은 공식 [Android Studio 다운로드/릴리스 페이지](https://developer.android.com/studio)에서 확인합니다. 현재 설치는 프로젝트를 빌드했으므로 재설치할 필요가 없습니다. Android Studio에서 `Help > Check for Updates`를 열고 Stable channel의 업데이트만 적용하세요. Canary/Beta/RC는 이 프로젝트의 기본 요구사항이 아닙니다.

### 2. JDK 설정 바로잡기

별도 JDK를 설치하지 않아도 Studio 내장 JBR을 사용할 수 있습니다.

- Android Studio: `File > Settings > Build, Execution, Deployment > Build Tools > Gradle`
- `Gradle JDK`를 `GRADLE_LOCAL_JAVA_HOME` 또는 설치된 Android Studio의 `jbr`로 선택
- 터미널에서는 위 PowerShell 명령으로 세션 단위 `JAVA_HOME`을 지정

영구 환경 변수 변경이 필요하다면 사용자가 직접 Windows 사용자 환경 변수의 `JAVA_HOME`을 유효한 JDK 17 이상 경로로 수정해야 합니다. 이 저장소 작업에서는 변경하지 않았습니다. Android 빌드의 JDK 선택 원리는 [공식 JDK 문서](https://developer.android.com/build/jdks)를 참고하세요.

### 3. SDK Command-line Tools 설치

현재 `sdkmanager`와 `avdmanager`가 없습니다. 빌드에는 필수적이지 않지만 터미널에서 SDK/AVD를 관리하려면 설치합니다.

1. Android Studio에서 `Tools > SDK Manager`를 엽니다.
2. `SDK Tools` 탭에서 `Android SDK Command-line Tools (latest)`를 선택합니다.
3. `Android SDK Platform-Tools`, `Android Emulator`, `Android SDK Build-Tools 36.0.0`이 선택되어 있는지 확인합니다.
4. `Apply`를 누르고 라이선스를 검토·수락합니다.

설치 후 확인:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --version
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" version
```

### 4. 에뮬레이터와 AVD 준비

현재 `Medium_Phone` API 37 AVD가 구성되어 있으며 `adb devices -l`에서 `emulator-5554 device`로 확인했습니다. 새 컴퓨터에서 같은 환경을 준비하려면 다음 절차를 사용합니다.

1. `Tools > SDK Manager > SDK Platforms`에서 Android 17/API 37의 Google APIs x86_64 시스템 이미지를 설치합니다.
2. `Tools > Device Manager > Add a new device > Create Virtual Device`를 선택합니다.
3. 일반 휴대폰 프로필과 설치한 API 37 x86_64 이미지를 선택합니다.
4. AVD를 시작하고 다음을 확인합니다.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
.\gradlew.bat connectedDebugAndroidTest
```

### 5. 실제 Android 기기 사용

1. 기기에서 개발자 옵션과 USB 디버깅을 활성화합니다.
2. Windows가 제조사 USB 드라이버를 요구하면 제조사의 공식 드라이버만 설치합니다.
3. USB 연결 후 기기에서 RSA 디버깅 승인을 확인합니다.
4. `adb devices` 결과가 `device` 상태인지 확인합니다.

## 새 컴퓨터에서 재현하기

1. 최신 안정 Android Studio를 설치합니다.
2. Setup Wizard에서 Android SDK, Platform Tools, Emulator를 설치합니다.
3. SDK Manager에서 API 37과 Build Tools 36.0.0을 확인합니다.
4. 저장소를 열고 Gradle JDK를 JDK 17 이상 또는 Studio 내장 JBR로 선택합니다.
5. 프로젝트 루트에 자신의 SDK 경로를 담은 `local.properties`를 만듭니다.
6. 전역 Gradle이나 전역 Kotlin CLI를 설치하지 말고 `gradlew.bat`/`gradlew`를 사용합니다.
7. 위 검사 명령을 실행합니다.

## 이번 작업에서 실제 실행한 검증

최종 버전 조합에서 다음 결과를 얻었습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `.\gradlew.bat --version` | 성공, Gradle 9.5.0 / JBR 25.0.2 / Windows amd64 |
| `.\gradlew.bat testDebugUnitTest` | 성공, JVM 단위 테스트 38개 통과 |
| `.\gradlew.bat assembleDebug` | 성공, `app-debug.apk` 생성 |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, `Medium_Phone(AVD) - 17`에서 21개 통과 / 실패·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공, 0 errors / 4 version-availability warnings |

중간 실패도 숨기지 않습니다.

- 첫 Wrapper 배포본 다운로드: 샌드박스 네트워크 차단으로 `java.net.SocketException: Permission denied: getsockopt`; 네트워크 허용 후 성공.
- 최초 Hilt 2.57.1: AGP 9 새 DSL 비호환으로 `Android BaseExtension not found`; AGP 9 지원 안정판 Hilt로 교체.
- 첫 Kotlin 컴파일: 잘못 추가한 `weight` import가 internal symbol을 가리킴; import 제거 후 성공.
- 첫 단위 테스트 실행: 테스트 자체가 `Int`와 `Long`을 비교; assertion을 `Long`으로 수정 후 6개 모두 통과.
- 첫 백업 기능 전체 계측 실행: 새 migration test가 schema JSON을 test assets에서 찾지 못해 20개 중 1개 실패. `app/schemas`를 `androidTest` assets에 연결한 뒤 migration 단독 테스트와 전체 20개 테스트가 통과함.
- migration 단독 검증의 첫 명령: PowerShell이 따옴표 없는 `-Pandroid.testInstrumentationRunnerArguments.class=...`를 분리해 테스트 시작 전에 실패. 인자 전체를 따옴표로 묶어 재실행함.

JBR 25에서 Gradle native-platform이 제한 API 사용 경고를 출력하지만 현재 빌드 실패는 아닙니다. 원한다면 Gradle 실행 JDK를 17 또는 21로 통일해 경고를 줄일 수 있습니다.

lint의 네 warning은 Gradle 9.7.1, Compose/serialization compiler plugin 2.4.10, kotlinx.serialization 1.11.0이 더 새롭다는 알림입니다. 이 프로젝트는 AGP 9.3의 공식 기본 Gradle 9.5.0과 AGP 내장 Kotlin 2.3.10에 맞추고, 같은 Kotlin 계열에서 빌드 검증한 kotlinx.serialization 1.10.0을 사용하므로 자동 상향하지 않았습니다.

## 2026-08-23 CC-CEDICT 작업 검증

모든 Gradle 명령은 잘못된 global `JAVA_HOME`을 바꾸지 않고 현재 PowerShell process에서만 Android Studio JBR을 지정해 실행했습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `\.\gradlew.bat testDebugUnitTest` | 성공, 12 suites / 56 tests / 실패·오류·건너뜀 0 |
| `\.\gradlew.bat lintDebug` | 성공, 0 errors / 기존 dependency version warning 4개 |
| `\.\gradlew.bat assembleDebug` | 성공, code-only `app-debug.apk` 14,046,842 bytes |
| `\.\gradlew.bat connectedDebugAndroidTest` | 실행하지 않음. 연결된 유일한 `Medium_Phone`은 기존 사용자 데이터가 있는 수동 QA AVD이며 test-only AVD가 아님 |

변경 전 기존 APK는 14,032,994 bytes였고 full CC-CEDICT GZip 없이 provider/UI/NOTICE만 포함한 APK 증가는 13,848 bytes입니다. full artifact가 없으므로 데이터셋 포함 APK 크기와 첫 parse 시간/peak memory는 측정하지 않았습니다.

중간 실패:

- 첫 기준선 실행은 global `JAVA_HOME=C:\Program Files\Java\jdk-21`이 존재하지 않아 Gradle 시작 전 실패했습니다. 시스템 설정을 바꾸지 않고 Studio JBR을 process-local로 사용했습니다.
- sandbox 안의 첫 Wrapper 실행은 Gradle 9.5.0 다운로드가 `Permission denied: getsockopt`로 실패했고 승인된 외부 실행에서 wrapper distribution을 받은 뒤 성공했습니다.
- 첫 production compile은 존재하지 않는 `Char.isNotWhitespace` reference와 잘못 명시한 Compose `weight` import로 실패했습니다. lambda와 scope extension 사용으로 수정한 뒤 성공했습니다.
- 첫 새 test compile은 `VocabularySenseDraft`의 필수 `partOfSpeech`, `examples` 인자가 빠져 실패했습니다. fixture draft를 완전하게 만든 뒤 전체 56 tests가 통과했습니다.

## 2026-08-23 dictionary provenance/autofill 검증

Task 5.2 검증도 Android Studio JBR을 현재 PowerShell process에만 지정해 실행했습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `.\gradlew.bat testDebugUnitTest` | 성공, 12 suites / 70 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공, errors 0 / dependency version warning 4개 |
| `.\gradlew.bat assembleDebug` | 성공, CC-CEDICT asset을 포함한 `app-debug.apk` 18,588,003 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,265,222 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 실행하지 않음. 기존 사용자 데이터가 있는 수동 QA AVD를 uninstall/초기화하지 않기 위해 별도 test-only AVD가 필요함 |

Room v2→v3 aggregate 보존, provenance Room round trip, backup v1 호환성과 Compose attribution 테스트는 AndroidTest APK에 포함되어 컴파일되었습니다. 기기에서의 실제 실행 결과로 표현하지 않으며 test-only AVD에서 별도로 실행해야 합니다.
