# 공식 APK 확인

LexiShelf의 공식 sideload build는
[GitHub Releases](https://github.com/bamforkaa/lexishelf/releases)에 게시된
`LexiShelf-v<version>.apk`입니다. 소스에서 직접 만든 APK나 제3자가 다시 서명한 APK는 공식 배포
APK가 아닙니다.

## 파일 무결성

Release에 함께 게시된 `SHA256SUMS.txt`의 값과 내려받은 APK의 SHA-256을 비교할 수 있습니다.
Windows PowerShell에서는 다음 명령을 사용합니다.

```powershell
Get-FileHash '.\LexiShelf-v<version>.apk' -Algorithm SHA256
```

Android는 APK 옆에 checksum 파일이 있다는 이유만으로 이를 자동 검증하지 않습니다. checksum
비교는 선택적인 추가 확인 절차입니다.

## 서명자 확인

Android SDK Build Tools의 `apksigner`로 APK의 인증서를 확인할 수 있습니다.

```text
<ANDROID_SDK_PATH>/build-tools/<BUILD_TOOLS_VERSION>/apksigner verify --verbose --print-certs LexiShelf-v<version>.apk
```

LexiShelf production signing certificate의 SHA-256 fingerprint는 다음과 같습니다.

```text
10:51:D4:C5:8B:DC:08:AE:CC:1A:FF:17:A9:57:3E:0B:8B:F7:DB:06:10:51:AF:3B:2E:10:5E:F3:DE:A7:B4:BA
```

APK 파일의 SHA-256은 release마다 바뀔 수 있지만, 공식 update가 같은 Android 앱으로 인정되려면
서명 identity가 유지되어야 합니다. 비밀번호와 private signing key는 저장소나 공개 문서에
포함하지 않습니다.

`<version>`은 내려받은 APK의 실제 버전으로 바꿉니다. 같은 application ID와 production
인증서를 사용하는 새 공식 APK는 기존 앱을 삭제하지 않고 업데이트합니다. Debug 서명 앱은
production 앱과 서명이 달라 직접 업데이트할 수 없으므로 설치를 전환하기 전에 JSON을 백업합니다.

## 개발자용 release staging

프로젝트 루트에서 production 서명을 설정한 뒤 다음 검증을 실행합니다.

```powershell
.\gradlew.bat :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease -PrequireProductionSigning=true
```

검증된 APK로 staging을 생성하는 예시는 다음과 같습니다. `<version>`, `<pack-tag>`, SDK 경로는
실제 값으로 바꿉니다. 앱 버전과 사전 pack release tag는 독립적입니다.

```powershell
python -B -m tools.prepare_github_release `
  --app-version '<version>' `
  --release-tag 'v<version>' `
  --dictionary-release-tag '<pack-tag>' `
  --catalog-source distribution/dictionary-catalog-v1.json `
  --signed-apk app/build/outputs/apk/release/app-release.apk `
  --release-notes RELEASE_NOTES.md `
  --apksigner '<ANDROID_SDK_PATH>/build-tools/<BUILD_TOOLS_VERSION>/apksigner.bat' `
  --output 'build/release-assets/v<version>'
```

기본 staging은 APK, catalog, release notes, standalone notices와 `SHA256SUMS.txt` 다섯 파일입니다.
Checksum은 자신을 제외한 실제 첨부 파일만 대상으로 합니다. Catalog는 URL과 hash를 바꾸지 않고
복사하므로 기존 pack release를 계속 참조할 수 있습니다. 검증용으로 기존에 검토한 로컬 pack
archive가 필요하지만 기본 staging에는 복사하지 않습니다.

사전 데이터도 배포하는 경우에만 검토된 catalog와 `--include-dictionary-packs`를 사용합니다.
도구는 APK 서명·버전·application ID와 catalog/pack 무결성을 검사하며, 기존 출력 디렉터리를
덮어쓰지 않습니다. 실패 시 임시 staging만 제거하고 과거 release asset은 유지합니다.
Staging 생성은 Git tag나 GitHub Release를 만들거나 업로드하지 않습니다.
