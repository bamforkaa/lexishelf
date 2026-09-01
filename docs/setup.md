# 개발 환경 설정

LexiShelf는 Kotlin과 Jetpack Compose로 작성된 Android application입니다. 전역 Gradle 설치는
사용하지 않으며 저장소의 Gradle Wrapper로 build와 test를 실행합니다. 일반 source build에는
dictionary 원본 데이터, production signing key 또는 API key가 필요하지 않습니다.

## 필요한 도구

- 최신 stable Android Studio
- Android SDK Platform 37와 compatible Android SDK Build Tools
- Android Studio bundled JDK 또는 build와 호환되는 JDK
- 저장소에 포함된 Gradle Wrapper
- Android emulator 또는 USB debugging을 허용한 실제 Android device

Kotlin compiler와 Android/Kotlin plugin version은
`gradle/libs.versions.toml`과 Gradle build가 관리하므로 별도 Kotlin 설치가 필요하지 않습니다.

## 새 컴퓨터에서 준비하기

1. [Android Studio](https://developer.android.com/studio)를 설치합니다.
2. 첫 실행 wizard 또는 **SDK Manager**에서 Android SDK Platform 37, SDK Build Tools,
   Platform-Tools와 Android Emulator를 설치합니다.
3. Device Manager에서 emulator를 하나 만들거나 USB-connected device의 developer options와
   USB debugging을 켭니다.
4. 저장소를 clone하고 Android Studio에서 root directory를 엽니다.
5. Android Studio가 선택한 SDK path로 gitignored `local.properties`를 생성했는지 확인합니다.

Windows `local.properties` 예시는 다음과 같습니다. 실제 사용자 경로만 로컬 파일에 넣고
commit하지 않습니다.

```properties
sdk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
```

Android Studio가 제공하는 JDK를 현재 PowerShell session에서 사용하려면:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --version
```

시스템 전체 환경 변수나 shell profile 수정은 필수가 아닙니다. macOS/Linux에서는 Android Studio
설치 위치의 bundled JDK를 `JAVA_HOME`으로 지정하고 `./gradlew`를 사용합니다.

## Build와 test

Repository root에서 다음 명령을 실행합니다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

성공한 debug APK는 일반적으로 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

연결된 test device가 있을 때 instrumentation test를 실행할 수 있습니다.

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

`adb devices`에서 `device` 상태인 대상이 있어야 합니다. 실제 사용자 데이터가 있는 device 대신
test 전용 emulator/device를 사용하세요. Instrumentation test는 app install, storage 초기화 또는
test fixture 생성을 수행할 수 있습니다.

## Android Studio에서 실행하기

1. Gradle sync가 끝날 때까지 기다립니다.
2. toolbar에서 `app` run configuration을 선택합니다.
3. emulator 또는 USB device를 선택합니다.
4. **Run**을 실행합니다.

앱은 dictionary pack 없이 시작할 수 있어야 하며 manual vocabulary CRUD, local search, tags,
wordbooks, settings와 backup 화면은 별도의 dataset 준비 없이 사용할 수 있습니다.

## Dictionary pack

일반 사용자는 앱의 **설정 → 사전 데이터**에서 공개 pack을 선택해 내려받습니다. Source build와
자동화 test는 대용량 dictionary 원본을 요구하지 않습니다.

Converter나 local provider를 개발하는 경우에만 repository 밖의 dataset workspace를 준비하고
검토된 원본으로 `.dictpack`을 생성합니다. 원본·생성 DB·pack은 Git에 commit하지 않습니다.
경로, pack command와 설치 lifecycle은 [dictionary pack 문서](dictionary-packs.md), 사용할 수 있는
source와 license는 [dictionary source review](dictionary-sources.md)를 확인하세요.

## 손글씨 모델

ML Kit Digital Ink language model은 base APK에 포함되지 않습니다. 지원되는 model은 앱 안에서
사용자가 선택해 내려받고 삭제할 수 있습니다. Model download에는 network가 필요하지만 인식할
stroke와 candidate는 session-only이며 LexiShelf의 Room/JSON backup에 저장되지 않습니다.

## API credential과 signing secret

공개 v0.1.0의 local dictionary pack workflow는 API key가 필요하지 않습니다. 향후 credential이
필요한 provider를 추가하더라도 real key를 source, resource, Gradle file, test fixture나 문서에
넣지 않습니다.

Production signing key, password와 signing properties도 저장소 밖에서 관리합니다. 공식 APK의
공개 certificate fingerprint와 사용자 검증 방법은 [APK 검증 문서](apk-verification.md)에 있습니다.

## 문제 해결

### `java` 또는 `JAVA_HOME` 오류

Android Studio의 `jbr` directory를 현재 shell의 `JAVA_HOME`으로 지정한 뒤
`.\gradlew.bat --version`을 다시 실행합니다. 전역 Java 설치는 필수가 아닙니다.

### Android SDK를 찾지 못함

Android Studio의 **SDK Manager**에서 실제 SDK path를 확인하고 `local.properties`의 `sdk.dir`을
고칩니다. 이 파일은 사용자별 경로이므로 commit하지 않습니다.

### 연결된 device가 없음

Android Studio Device Manager에서 emulator를 시작하거나 USB device의 debugging authorization을
확인한 뒤 `<ANDROID_SDK_PATH>/platform-tools/adb devices`를 실행합니다.

### Dictionary dataset이 없음

정상 상태입니다. Dictionary provider만 unavailable로 표시되고 manual entry와 저장된 vocabulary는
계속 동작해야 합니다. 필요한 pack은 앱에서 설치하거나 개발자가 명시적으로 local import합니다.
