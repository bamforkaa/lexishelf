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

## 선택 사항: 손글씨 인식 모델

앱은 공식 `com.google.mlkit:digital-ink-recognition:19.0.0`을 사용하며 최소 API 23이
필요합니다. APK에는 언어 모델을 묶지 않습니다. Word Editor의 손글씨 버튼을 누르고 모델
다운로드를 선택하면 현재 BCP 47 언어에 맞는 모델 하나만 내려받습니다. 공식 안내 기준 모델당
저장 공간은 약 20MB입니다. 다운로드 때문에 `INTERNET` permission이 필요하지만 별도 API key,
서버나 사전 network provider는 없습니다. 모델이 설치된 뒤 인식 입력과 결과 처리는 기기에서
이뤄집니다. 자세한 출처, privacy와 Manual QA는 [handwriting.md](handwriting.md)에 있습니다.

## 선택 사항: CC-CEDICT local dataset

CC-CEDICT provider에는 SDK 도구, network permission 또는 API key가 필요하지 않습니다. 다만 full GZip binary는 저장소에 포함되지 않으므로 실제 lookup을 하려면 브라우저에서 공식 release를 수동으로 받아 canonical dataset root에 두어야 합니다.

```text
<dataset-root>/cc-cedict/source/cedict_1_0_ts_utf-8_mdbg.txt.gz
```

MDBG는 automated/scripted access를 금지하므로 project script가 다운로드하지 않습니다. 기대 release, attribution, 파일 복사와 update 절차는 [cc-cedict-dataset.md](cc-cedict-dataset.md)를 따르세요. 파일이 없어도 build/test는 가능하며 앱은 검색 시 dataset unavailable을 표시합니다.

## 선택 사항: 한국어기초사전 local reverse index

한국어기초사전 provider도 API key, network permission 또는 Android SDK 추가 도구가 필요하지 않습니다. 공식 [사전 전체 내려받기](https://krdict.korean.go.kr/download/downloadPopup)에서 JSON ZIP을 브라우저로 받은 뒤 Python 3 표준 라이브러리 변환기를 실행합니다.

```powershell
Copy-Item C:\path\to\korean-basic-dictionary-json.zip `
  D:\lang-Database\korean-basic\source\korean-basic-dictionary-json.zip
python -m tools.build_krdict_index
```

생성 DB는 `<dataset-root>/korean-basic/generated/korean_basic_dictionary.db`에 생기며 약 200MB입니다. 누락되어도 pack 미포함 build/test와 수동 저장, 다른 provider는 정상이고 한국어기초사전 suggestion group만 dataset unavailable을 표시합니다. 공식 source, license, 현재 release의 기대 count와 update 절차는 [korean-basic-dictionary-dataset.md](korean-basic-dictionary-dataset.md)를 따르세요.

## 선택 사항: PanLex Korean fallback index

PanLex provider도 network permission이나 Android SDK 추가 도구 없이 별도 read-only SQLite pack payload를 사용합니다. 현재 공식 distribution/API가 unavailable하므로 확인하지 않은 mirror나 third-party 변환본을 사용하지 마세요. 검증한 2019-09-01 공식 CSV snapshot의 archived official response, 정확한 SHA-256/SHA-1 digest, artifact 내장 CC0와 현재 공식 license의 차이는 [panlex-dataset.md](panlex-dataset.md)에 기록했습니다.

원본 ZIP을 저장소 밖에 준비한 뒤 Python 3 표준 라이브러리 변환기를 실행합니다.

```powershell
Copy-Item C:\path\to\panlex-20190901-csv.zip `
  D:\lang-Database\panlex\source\panlex-20190901-csv.zip
python -X utf8 -m tools.build_panlex_index
```

생성 DB는 `<dataset-root>/panlex/generated/panlex_korean_fallback.db`에 생기며 현재 12개 언어 검증본은 189,714,432 bytes입니다. 누락되어도 pack 미포함 build/test, 수동 저장과 다른 provider는 정상이고 PanLex suggestion group만 dataset unavailable을 표시합니다. 변환기는 shared reviewed language-variety config와 고정 artifact의 embedded CC0 license를 검사하며 pivot translation을 생성하지 않습니다.

## 선택 사항: JMdict local index

Android build는 JMdict를 다운로드하지 않습니다. 공식 `JMdict_e.gz`를 받은 후 checksum과
release를 확인하고 다음을 실행합니다.

```powershell
Copy-Item C:\path\to\JMdict_e.gz D:\lang-Database\jmdict\source\JMdict_e.gz
python -m tools.build_jmdict_index
```

정확한 artifact와 checksum은 [jmdict-dataset.md](jmdict-dataset.md)에 있습니다. 생성 DB는
`<dataset-root>/jmdict/generated/jmdict.db`이며, 없어도 pack 미포함 build와 manual vocabulary는 정상이고 JMdict suggestion group만 dataset unavailable입니다.

## 선택 사항: Kaikki multilingual English fallback

Android/Gradle build는 Kaikki 원본을 다운로드하거나 runtime JSON parsing을 하지 않습니다. 공식 raw page에서 current English Wiktionary extraction gzip을 명시적으로 받은 뒤, [Kaikki dataset 문서](kaikki-dataset.md)의 dump date·bytes·SHA-256을 확인해 다음 exact 이름으로 둡니다.

```text
D:\lang-Database\kaikki\source\raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz
```

`python -X utf8 -m tools.build_kaikki_indexes`는 production 12개 `<language>.db`와 compact
`en-morphology.db`를 함께 만듭니다. 후자는 full English dictionary가 아니라 실제
surface→lemma 관계만 보관합니다. `python -X utf8 -m tools.build_dictionary_packs --pack
kaikki`는 12개 language pack과 `kaikki.en-morphology-...dictpack`을 생성합니다. Manual QA
debug bundle에서 `debugDictionaryPackLanguages=de,vi`를 사용하면 de/vi와 English morphology
pack이 함께 들어갑니다.

```powershell
python -X utf8 -m tools.build_kaikki_indexes
python -X utf8 -m tools.build_dictionary_packs --pack kaikki
```

원본은 약 2.63GiB이고 production-selected 12개 DB 합계는 약 1.20GiB이므로 충분한 disk/time을 확보하세요. converter는 Python 표준 라이브러리만 사용하며 source checksum mismatch나 malformed JSON이면 기존 성공 output을 유지하고 실패합니다. pack이 없는 언어만 suggestion 영역에서 dataset unavailable이며 manual entry와 다른 provider는 정상 동작합니다.

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

## 대용량 dictionary dataset root와 pack

Android SDK 경로와 dictionary dataset 경로는 별개입니다. converter는 다음 우선순위로 개발 PC root를 결정합니다.

1. 현재 process의 `LANG_DATABASE_DIR`
2. gitignored `local.properties`의 `dictionaryDataDir`
3. 저장소 내부 gitignored `.local/dictionary-data`

Windows에서 `D:\lang-Database`를 지속적으로 사용할 때는 project root의 gitignored `local.properties`에 다음 값을 추가하는 방법을 권장합니다.

```properties
dictionaryDataDir=D\:\\lang-Database
bundleDictionaryPacksInDebug=true
debugDictionaryPackLanguages=de,vi
```

두 번째 값은 Manual QA용 debug APK에만 pack을 포함하는 opt-in입니다. 세 번째 값은 큰 Kaikki 전체 12개 중 debug APK에 넣을 언어만 제한합니다. `installDebug` 전에 Python pack builder가 core 네 개와 선택한 Kaikki pack을 생성하며, 첫 앱 실행에서 production pack 검증과 activation이 완료될 때까지 짧은 준비 화면을 표시합니다. release APK에는 dataset을 포함하지 않습니다.

현재 PowerShell session에서만 우선 적용하려면 환경 변수를 사용합니다.

```powershell
$env:LANG_DATABASE_DIR = 'D:\lang-Database'
```

그다음 converter/pack builder를 실행합니다. 각 도구는 실제로 선택한 root를 `Dictionary dataset root:` 다음 줄에 출력합니다.

```powershell
python -m tools.build_jmdict_index
python -m tools.build_krdict_index
python -m tools.build_panlex_index
python -X utf8 -m tools.build_kaikki_indexes
python -m tools.build_dictionary_packs
```

경로에 공백이 있어도 지원하며 clean clone에서 D:가 없어도 일반 Gradle build/test는 실패하지 않습니다. 설정 변경은 파일 이동 명령이 아니므로 기존 source/generated/packs를 자동 이동하지 않습니다. 기존 dataset directory를 새 root 아래의 동일한 dataset layout으로 직접 이동하거나, 새 root의 `source/`에 원본을 준비하고 converter와 pack builder를 다시 실행해야 합니다. 이번 버전에는 자동 migration이 없습니다.

생성한 `.dictpack`은 설정 화면의 `로컬 pack 설치`로 고릅니다. 앱은 Storage Access Framework를 사용하므로 storage permission이 필요하지 않습니다. Android runtime 저장 경로는 `noBackupFilesDir/dictionary-packs`이며 Windows root와 무관합니다. 자세한 layout/manifest/update/rollback은 [dictionary-packs.md](dictionary-packs.md)에 있습니다.

Manual QA에서는 Test AVD를 끄고 `Medium_Phone_Manual`만 연결한 뒤 다음처럼 core pack과 필요한 Kaikki pack을 Downloads에 staging할 수 있습니다.

```powershell
python -m tools.build_dictionary_packs
python -m tools.stage_dictionary_packs --avd-name Medium_Phone_Manual `
  --kaikki-language de --kaikki-language vi
```

`bundleDictionaryPacksInDebug=false`인 경우 staging은 설치가 아닙니다. 앱의 설정 → `로컬 pack 설치`에서 `Download/LocalVocabularyPacks`의 파일을 선택해야 합니다. `true`인 Manual QA 구성에서는 `installDebug`가 pack 포함 APK를 설치하고 첫 실행이 `AndroidDictionaryPackRepository`의 manifest/size/SHA-256/payload 검증과 atomic activation을 수행합니다.

debug bundle은 압축 pack과 활성화된 unpacked payload를 함께 차지하며 rollback용 직전 version도
하나 유지합니다. APK 크기 회귀를 측정할 때는 `clean assembleDebug`를 사용하세요. 실제 Manual AVD
저장공간 구성과 정리 정책은 [debug-storage.md](debug-storage.md)에 있습니다. release variant는
이 generated debug asset 경로를 사용하지 않습니다.

## 이번 작업에서 실제 실행한 검증

최종 버전 조합에서 다음 결과를 얻었습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `.\gradlew.bat --version` | 성공, Gradle 9.5.0 / JBR 25.0.2 / Windows amd64 |
| `.\gradlew.bat testDebugUnitTest` | 성공, JVM 단위 테스트 185개 통과 |
| `.\gradlew.bat lintDebug` | 성공, errors 0 / dependency·version warning 8개 |
| `.\gradlew.bat clean` | 성공, stale 증분 산출물 제거 |
| `.\gradlew.bat assembleDebug` 최종 실행 | 성공, schema 2 `de`/`vi` pack을 포함한 `app-debug.apk` 264,611,485 bytes 생성 |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,410,667 bytes 생성 |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, `Medium_Phone_Test(AVD) - 17`에서 115개 발견 / 114개 실행·통과 / staged-pack opt-in 테스트 1개 건너뜀 / 실패 0. 번들 `de`/`vi` 실제 조회 통합 테스트는 실행·통과 |

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

## 2026-08-23 한국어기초사전 provider 검증

공식 2026-08-19 JSON ZIP을 저장소 밖의 임시 디렉터리에서 읽어 local SQLite index를 생성했습니다. 변환은 38.427초, 결과는 56,555 entries / 76,833 senses / 826,492 translations / 1,938,697 reverse keys / 211,701,760 bytes였습니다. PC SQLite cold exact query는 한국어 forward 0.271ms, English reverse 0.345ms였지만 Android 첫 asset 복사 latency와 peak memory는 별도로 측정하지 않았습니다. 런타임은 1MiB copy buffer와 disk-backed SQLite cursor를 사용하며 전체 index를 heap에 올리지 않습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `python -m unittest discover -s tools/tests -v` | 성공, converter fixture 2개 통과 |
| `.\gradlew.bat testDebugUnitTest` | 성공, 13 suites / 79 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공, 0 errors / 기존 dependency version warning 4개 |
| `.\gradlew.bat assembleDebug` | 성공, `app-debug.apk` 93,170,569 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,265,280 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, 연결된 유일한 `Medium_Phone_Test(AVD) - 17`에서 28개 통과 |

Task 5.2의 CC-CEDICT asset 포함 APK 18,588,003 bytes와 비교하면 provider 코드와 한국어기초사전 index 포함 후 74,582,566 bytes 증가했습니다. APK 안에서 211,701,760-byte SQLite asset은 deflate되어 74,551,231 bytes를 차지합니다. 수동 QA 데이터가 있는 `Medium_Phone`은 연결하지 않았고 명시적으로 분리된 `Medium_Phone_Test`만 계측 대상으로 사용했습니다.

중간 결함도 기록합니다.

- 공식 export에서 `LexicalEntry.val`이 관련 관용구에 재사용되어 최초 단독 PK 변환이 unique constraint로 실패했습니다. user Room이 아니라 provider index의 internal integer PK로 분리하고 provenance는 공식 ID와 표제어를 조합하도록 수정했습니다.
- 공식 `Lemma`가 object뿐 아니라 활용형 variant를 포함한 array로도 나타나 최초 full conversion이 2,139개를 누락했습니다. 명시적 `writtenForm`을 두 형태에서 읽는 fixture regression을 추가한 뒤 전체 56,555개를 재생성했습니다.
- 첫 compile은 sandbox 내부 Wrapper download가 `Permission denied: getsockopt`로 실패했습니다. 승인된 Gradle cache/network 접근으로 재실행해 성공했으며 시스템 설정은 변경하지 않았습니다.

## 2026-08-23 PanLex fallback provider 검증

검증한 official 2019-09-01 CSV snapshot을 저장소 밖 임시 디렉터리에서 읽어 `de/hi/pl/la ↔ ko` direct relation SQLite를 생성했습니다. full conversion은 1,753.8초였고 결과는 307,530 unique expression relations / 53,211,136 bytes였습니다. PC read-only SQLite exact query 500회는 median 0.060ms / p95 0.112ms였으며 `integrity_check`는 `ok`였습니다. Android 첫 asset copy latency와 기기 query latency는 계측 테스트를 실행하지 않아 측정하지 않았습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `python -X utf8 -m unittest discover -s tools\tests -v` | 성공, converter fixture 7개(기존 2 + PanLex 5) 통과 |
| `.\gradlew.bat testDebugUnitTest` | 성공, 14 suites / 85 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공, 0 errors / 기존 dependency version warning 4개 |
| `.\gradlew.bat assembleDebug` | 성공, `app-debug.apk` 116,588,051 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,265,338 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 실행하지 않음. 연결된 유일한 `emulator-5554`가 `Medium_Phone_Manual`이어서 수동 QA data를 보호함 |

Task 6 기준 APK 93,170,569 bytes에서 23,417,482 bytes 증가했습니다. 53,211,136-byte PanLex SQLite asset은 APK 안에서 23,301,486 bytes로 deflate되었습니다. Android datasource의 네 언어 양방향 exact/normalization/ranking/truncation/malformed fixture는 AndroidTest APK에 컴파일됐지만 test-only AVD에서 실행된 것으로 표현하지 않습니다.

중간 결함도 기록합니다.

- converter fixture의 첫 성공 경로는 Windows에서 `VACUUM` connection이 열린 채 atomic replace를 시도해 file lock으로 실패했습니다. connection을 명시적으로 닫도록 수정하고 partial cleanup까지 회귀 테스트했습니다.
- Unicode fixture는 normalized lookup key뿐 아니라 display source text도 NFC로 바뀐다고 잘못 기대했습니다. 공식 expression 표기는 보존하고 normalized key만 NFC라는 경계를 검증하도록 테스트를 수정했습니다.
- 첫 AndroidTest APK compile은 fixture의 mixed SQL bind array가 intersection type으로 추론되어 실패했습니다. test-only array type을 `Any`로 명시한 뒤 재실행해 성공했습니다.

## 2026-08-23 JMdict provider 검증

공식 2026-08-23 `JMdict_e.gz`를 저장소 밖 임시 경로에서 읽어 provider 전용 SQLite를 생성했습니다. 생성 DB는 113,729,536 bytes, 최종 debug APK는 144,462,083 bytes입니다. 자세한 checksum, mapping, QA sample, performance는 [jmdict-dataset.md](jmdict-dataset.md)에 기록했습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `python -X utf8 -m unittest discover -s tools\tests -v` | 성공, converter test 10개 통과 |
| `.\gradlew.bat testDebugUnitTest` | 성공, 15 suites / 94 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공 |
| `.\gradlew.bat assembleDebug` | 성공, `app-debug.apk` 144,462,083 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,283,741 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, 연결된 유일한 `Medium_Phone_Test(AVD) - 17`에서 37개 통과 |

full asset 계측 test의 강제 첫 복사는 531ms, 첫 open + `食べる` exact query는 19ms였습니다. 중간에 추가한 Compose test가 동일 POS node 두 개를 단일 node로 기대해 37개 중 1개가 실패했습니다. production UI는 두 sense의 POS를 정상 표시하고 있었으며, test expectation을 2개로 바로잡은 뒤 해당 화면 test 5개와 전체 37개를 순서대로 재실행해 통과했습니다.

## 2026-08-23 Task 9 dictionary pack 검증

Task 9에서 dataset 포함 debug APK 144,462,083 bytes로부터 dictionary payload를 분리했을 당시 base APK는 18,466,693 bytes, AndroidTest APK는 1,289,772 bytes였습니다. 이 수치는 당시 빌드 기록입니다. 현재 데이터 모델은 Room schema v6, JSON backup schema v5입니다.

| 명령 | 실제 결과 |
| --- | --- |
| `python -m unittest discover -s tools/tests -v` | 성공, dataset root/converter test 14개 통과 |
| `.\gradlew.bat testDebugUnitTest` | 성공, 18 suites / 102 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공, errors 0 / dependency·version warning 6개 |
| `.\gradlew.bat assembleDebug` | 성공, `app-debug.apk` 18,466,693 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,289,772 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, 연결된 유일한 `Medium_Phone_Test(AVD) - 17`에서 47개 발견, 45개 실행·통과, optional staged-pack test 2개 건너뜀 |

`/data/local/tmp/local-vocabulary-packs`에 실제 생성 pack을 staging하고 opt-in 계측 test를 별도로 실행했습니다. 한 pack 설치와 네 pack 동시 설치가 모두 성공했으며 측정값은 CC-CEDICT 229ms, JMdict 1,130ms, 한국어기초사전 5,023ms, PanLex 1,756ms였습니다. 설치 직후 JMdict 첫 exact query는 20ms였습니다. 이 값은 해당 Test AVD의 단일 계측값이며 일반 기기의 benchmark로 간주하지 않습니다.

중간 실패 두 건은 production 결함이 아니었습니다. pack repository test가 test-only 내부 설치 후 명시적 refresh를 누락했고, Compose test가 sense별로 두 번 표시되는 headword를 한 node로 기대했습니다. 두 test expectation/setup만 바로잡은 뒤 관련 test 11개와 전체 계측 suite를 재실행해 통과했습니다. pack이 0개인 기본 상태에서도 전체 editor 및 수동 저장 흐름은 정상이며, 실제 pack 1개/4개 상태는 opt-in integration test에서 검증했습니다.

## 2026-08-26 Task 11 PanLex coverage 확장 검증

체크섬을 검증한 동일 2019-09-01 source에서 후보 39개 언어를 측정한 뒤 PanLex 범위를 기존 `de/hi/pl/la`와 신규 `nl/pt/it/tr/cs/sv/fi/uk`의 총 12개로 제한했습니다. 전체 변환은 502.454초, 결과는 1,098,758 unique direct pairs / 189,714,432-byte SQLite / 72,462,031-byte pack입니다. 기존 네 언어 relation count는 모두 동일했습니다. 상세 candidate table, artifact license 경계, QA sample과 성능은 [panlex-dataset.md](panlex-dataset.md)에 있습니다.

| 명령 | 실제 결과 |
| --- | --- |
| `python -X utf8 -m unittest discover -s tools\tests -v` | 성공, 23개 통과 |
| `python -X utf8 -m tools.build_panlex_index` | 성공, 502.454초, `integrity_check=ok` |
| `python -X utf8 -m tools.build_dictionary_packs --pack panlex` | 성공, production manifest/size/SHA-256 pack 생성 |
| `.\gradlew.bat testDebugUnitTest` | 성공, 23 suites / 133 tests / 실패·오류·건너뜀 0 |
| `.\gradlew.bat lintDebug` | 성공 |
| `.\gradlew.bat assembleDebug` | 성공, 최종 검증 파일 `app-debug.apk` 201,374,823 bytes |
| `.\gradlew.bat assembleDebugAndroidTest` | 성공, `app-debug-androidTest.apk` 1,414,953 bytes |
| `.\gradlew.bat connectedDebugAndroidTest` | 성공, `Medium_Phone_Test`에서 92개 발견 / 실패 0 / optional staged-pack 2개 건너뜀 |
| opt-in real PanLex pack 계측 | 성공, production install/activation 4,906ms / 첫 `nl → ko` exact query 15ms |

수동 QA AVD는 연결하지 않았습니다. Test AVD만 headless로 시작했고, 전체 suite 후 opt-in pack test를 별도로 실행했습니다. 당시 Task 11에서는 Room schema v5와 backup schema v4를 변경하지 않았습니다.
