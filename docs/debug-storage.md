# Debug 앱 저장공간 감사

Task 15.1에서 manual QA AVD를 read-only overlay로 실행해 기존 사용자 데이터를
변경하지 않고 `com.example.localvocabulary`의 실제 저장공간을 측정했다. 아래 값은
2026-08-28 측정값이며 Android의 화면 표시와 비교하기 쉽도록 MiB로 환산했다.

| 항목 | 실제 크기 | 설명 |
| --- | ---: | --- |
| 설치된 debug base APK | 327,235,849 bytes (312.09 MiB) | 압축된 debug bundle pack 포함 |
| `dictionary-packs` 전체 | 1,178,988 KiB (1,151.36 MiB) | active와 허용된 rollback version 포함 |
| CC-CEDICT | 3,932 KiB | active 1개 |
| JMdict | 111,120 KiB | active 1개 |
| 한국어기초사전 | 206,796 KiB | active 1개 |
| PanLex | 237,308 KiB | active 185,292 KiB + previous 51,984 KiB |
| Kaikki `de`/`vi` | 619,824 KiB | 언어별 active + previous |
| 이전 `no_backup/dictionary` | 369,836 KiB (361.17 MiB) | pack 전환 전 잔여 데이터, 현재 미사용 |
| ML Kit Digital Ink 모델 | 49,948 KiB (48.78 MiB) | `en`, `ja`와 공유 Latin model |
| 사용자 Room DB | 272 KiB | vocabulary와 relation 데이터 |
| cache + code cache | 64 KiB | 누적 임시 파일 없음 |

같은 AVD에서 설치 APK와 app data를 합친 값은 약 1,874.58 MiB(1.83 GiB)였으므로 Android
설정에서 보인 약 2 GB는 측정과 일치한다. 대부분은 사용자 단어가 아니라 debug APK에 압축된
pack, 설치 시 풀린 active pack, 의도적으로 유지하는 rollback 1개와 pack 전환 전 legacy
directory였다.

## 정상 보존과 실제 정리 결함

`DictionaryPackManager`는 provider/pack별 active version과 직전 version 하나만 유지한다. 두
version은 one-step rollback을 위한 정상 상태이며 세 번째 version, 미완료 `.incoming`, 실패한
`.candidate`는 초기 정리 또는 실패 처리에서 제거한다. 측정 시 세 번째 version과 지속적으로
남은 임시 설치 파일은 없었다.

반면 `noBackupFilesDir/dictionary`는 pack architecture 이전의 정확한 legacy 경로이며 현재
provider, converter, pack builder가 읽지 않는다. 앱 시작 시 이 경로만 제거하도록 정리했고
`dictionary-packs`, Room DB, backup, ML Kit 파일은 건드리지 않는다.

debug pack builder도 입력과 manifest가 같을 때 기존 `.dictpack`을 재사용한다. 이전에는
`createdAt`과 ZIP timestamp가 매 Gradle 실행마다 바뀌어 동일 dataset도 새 asset처럼 보였고,
증분 APK packaging을 반복하면 APK가 불필요하게 커질 수 있었다. clean build는 이전 증분 산출물을
제거하며, 이후 같은 입력의 assemble은 pack archive를 다시 쓰지 않는다.

## Debug와 release의 차이

`bundleDictionaryPacksInDebug=true`는 개발자가 명시적으로 켠 debug-only 편의 기능이다. 이 경우
base APK에 선택한 `.dictpack`이 압축되어 들어가고 첫 실행에서 production과 같은 manifest,
payload size, SHA-256, schema 검증과 atomic activation을 거쳐 `noBackupFilesDir/dictionary-packs`에
풀린다. 따라서 APK와 runtime pack의 중복 저장은 Manual QA 편의를 위해 예상된 비용이다.

Gradle 설정은 generated pack asset을 debug variant에만 연결한다. release APK에는 이 경로로
dictionary pack이나 ML Kit 언어 모델을 묶지 않는다. production pack은 SAF로 별도 설치하며,
ML Kit model은 사용자가 해당 언어를 선택했을 때 SDK가 내려받는다.

## 로컬 Kaikki schema 정합성

2026-08-28 개발 dataset root의 Kaikki `de`/`vi` generated DB는 SQLite
`PRAGMA user_version=3`이었지만 reviewed converter, pack manifest와 runtime 계약은 schema 2였다.
이는 잠시 도입됐다가 되돌린 semantic form-selection schema 3의 외부 generated artifact가 Git
rollback과 무관하게 남은 것이었다. 앱이 이를 `Dictionary schema 3 does not match manifest 2`로
거절한 동작은 정확했다.

2026-08-29 current converter로 production-selected 12개 DB와 pack을 모두 schema 2로 다시
생성했다. `de`/`vi`는 `PRAGMA quick_check=ok`, manifest size/SHA-256, ZIP payload SHA-256과 실제
DB checksum 일치를 확인했고 Test AVD의 production bootstrap/install 경로에서 `Wasser`와 `ăn`
exact lookup을 검증한다. 검증 허용 범위는 완화하지 않았다. candidate-only schema 1 local
artifact는 production/debug pack 대상이 아니며 필요할 때 `--analyze-candidates`로 별도 재생성한다.
이 dataset schema는 Room schema v6 또는 backup schema v5와 관련이 없다.

Task 17에서는 과거의 stale artifact를 허용한 것이 아니라 reviewed converter/runtime/manifest/
tests/docs를 함께 변경해 **새 authoritative Kaikki schema 3**을 정식 도입했다. 새 v3는 bounded
display forms와 `morphology_forms` reverse index를 포함하며 schema 2와 3을 동시에 허용하지
않는다. Production 12개 DB/pack과 compact `en-morphology.db`/pack을 current converter로
재생성했고 strict manifest↔payload version, size, SHA-256, ZIP CRC, SQLite `quick_check` 검사를
유지한다. Room v6/backup v5는 그대로다.

## 개발자 확인 절차

APK 크기를 비교할 때는 오래된 증분 산출물이 섞이지 않도록 한 번은 clean build를 사용한다.

```powershell
$env:JAVA_HOME = '<ANDROID_STUDIO_INSTALL>\jbr'
.\gradlew.bat clean assembleDebug
Get-Item .\app\build\outputs\apk\debug\app-debug.apk |
  Select-Object FullName, Length, LastWriteTime
```

Manual AVD와 Test AVD를 동시에 연결하지 않는다. 저장공간 감사나 수동 데이터 확인은 Manual
AVD에서, `connectedDebugAndroidTest`는 Test AVD에서만 수행한다.
