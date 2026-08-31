# Release checklist

이 checklist는 실제 배포마다 새 commit/tag에서 다시 실행한다. 과거 성공 기록을 현재 성공으로
간주하지 않는다.

## Before build

- [ ] `git status --short`가 의도한 변경만 표시한다.
- [ ] `applicationId`, `versionCode`, `versionName`, min/target/compile SDK를 검토한다.
- [ ] public build이면 Git 밖의 production signing config와 key 접근 권한을 확인한다.
- [ ] root `LICENSE`가 실제 선택한 source-code license인지 확인한다.
- [ ] `local.properties`, keystore, backup, APK/AAB, DB, `.dictpack`, source dataset이 tracked되지 않는다.
- [ ] pack별 source/version/checksum/license/redistribution 결정을 다시 확인한다.
- [ ] PanLex 결정이 정확히 고정한 artifact에만 적용되는지 확인한다.
- [ ] Settings의 사전 출처/attribution과 배포 NOTICE가 pack manifest와 일치한다.
- [ ] privacy policy/Data safety가 현재 ML Kit SDK 동작과 merged permissions를 반영한다.
- [ ] `python -X utf8 -m tools.build_public_dictionary_catalog`가 exact 16-pack catalog를 재현한다.
- [ ] public catalog와 release staging에 JMdict/aggregate Kaikki/old Kaikki schema가 없다.

## Verification

```powershell
$env:JAVA_HOME = '<ANDROID_STUDIO_INSTALL>\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest   # disposable Test AVD only
.\gradlew.bat assembleRelease -PrequireProductionSigning=true
.\gradlew.bat bundleRelease
.\gradlew.bat lintRelease
```

- [ ] fresh Test AVD: base app launch, empty state, manual CRUD, settings, missing-pack UI.
- [ ] production installer path: valid pack install/search/restart.
- [ ] malformed/checksum/schema mismatch가 active pack을 손상하지 않는다.
- [ ] 여러 provider pack이 공존하고 remove가 user vocabulary를 보존한다.
- [ ] backup v5 export → reset → restore 후 relation/provenance가 동일하다.
- [ ] v1/v2/v3/v4 legacy backup과 Room v1→v6 migration regression이 통과한다.
- [ ] network off에서 CRUD/local lookup/morphology/practice/installed-model recognition이 동작한다.
- [ ] handwriting model missing/download/recognition/delete를 Manual AVD에서 명시적으로 확인한다.
- [ ] multi-word manual save/search/practice와 실제 provider coverage를 확인한다.
- [ ] populated DB의 `PRAGMA quick_check`와 `foreign_key_check`가 정상이다.

## Artifact audit

- [ ] release APK/AAB에 `.dictpack`, provider DB, source dataset, test fixture가 없다.
- [ ] merged manifest permission/exported component를 기록한다.
- [ ] APK/AAB bytes와 SHA-256을 기록한다.
- [ ] universal APK native ABI와 AAB split 전제를 구분한다.
- [ ] `apksigner verify --print-certs`로 intended signer를 확인한다.
- [ ] production certificate SHA-256 fingerprint를 이전 공개 release와 비교한다.
- [ ] release artifact를 실제 target device/channel에서 fresh install하고 smoke test한다.
- [ ] signed update install이 기존 Room/DataStore/pack을 보존하는지 확인한다.

## Publish

- [ ] release notes와 known limitations가 현재 동작을 설명한다.
- [ ] app/package NOTICE, privacy policy와 support/backup 안내를 함께 배포한다.
- [ ] 최종 artifact checksum과 보관 위치를 기록한다.
- [ ] reviewed commit을 tag하고 source/artifact correspondence를 보존한다.
- [ ] `tools.prepare_github_release` output의 모든 파일을 `SHA256SUMS.txt`와 대조한다.
- [ ] `dictionary-catalog-v1.json`을 release asset으로 올리고 stable latest URL을 실제 fetch한다.
- [ ] release asset 목록에 JMdict가 없는지 다시 확인한다.
- [ ] 배포 뒤 install/update/restore/pack download 경로를 한 번 더 확인한다.
