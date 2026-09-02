# 공식 APK 확인

LexiShelf의 공식 sideload build는
[GitHub Releases](https://github.com/bamforkaa/lexishelf/releases)에 게시된
`LexiShelf-v0.1.0.apk`입니다. 소스에서 직접 만든 APK나 제3자가 다시 서명한 APK는 공식 배포
APK가 아닙니다.

## 파일 무결성

Release에 함께 게시된 `SHA256SUMS.txt`의 값과 내려받은 APK의 SHA-256을 비교할 수 있습니다.
Windows PowerShell에서는 다음 명령을 사용합니다.

```powershell
Get-FileHash .\LexiShelf-v0.1.0.apk -Algorithm SHA256
```

Android는 APK 옆에 checksum 파일이 있다는 이유만으로 이를 자동 검증하지 않습니다. checksum
비교는 선택적인 추가 확인 절차입니다.

## 서명자 확인

Android SDK Build Tools의 `apksigner`로 APK의 인증서를 확인할 수 있습니다.

```text
<ANDROID_SDK_PATH>/build-tools/<VERSION>/apksigner verify --verbose --print-certs LexiShelf-v0.1.0.apk
```

LexiShelf production signing certificate의 SHA-256 fingerprint는 다음과 같습니다.

```text
10:51:D4:C5:8B:DC:08:AE:CC:1A:FF:17:A9:57:3E:0B:8B:F7:DB:06:10:51:AF:3B:2E:10:5E:F3:DE:A7:B4:BA
```

APK 파일의 SHA-256은 release마다 바뀔 수 있지만, 공식 update가 같은 Android 앱으로 인정되려면
서명 identity가 유지되어야 합니다. 비밀번호와 private signing key는 저장소나 공개 문서에
포함하지 않습니다.
