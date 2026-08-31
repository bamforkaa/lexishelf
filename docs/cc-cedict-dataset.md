# CC-CEDICT 데이터셋 설치와 업데이트

> Task 9부터 이 파일은 APK asset 경로에 두지 않습니다. configured dataset root의
> `cc-cedict/source/`에 둔 뒤 generic pack을 만드세요. 현재 절차는
> [dictionary-packs.md](dictionary-packs.md)가 우선합니다.

이 프로젝트의 CC-CEDICT provider 코드는 실제 CC-CEDICT UTF-8 GZip release를 읽지만 데이터셋 binary 자체는 저장소에 포함하지 않습니다. 2026-08-23 확인 당시 MDBG release page가 automated or scripted access를 금지하므로 Gradle task, shell script 또는 앱 downloader로 파일을 받지 않습니다.

## 고정한 release metadata

| 항목 | 값 |
| --- | --- |
| 공식 프로젝트 download page | `https://cc-cedict.org/editor/editor.php?handler=Download` |
| 권장 release page | `https://www.mdbg.net/chinese/dictionary?page=cc-cedict` |
| 확인 시각 | 2026-08-23 |
| release | `2026-08-22 08:27:42 GMT` (`2026-08-22T08:27:42Z`) |
| entry 수 | 124,889 |
| artifact | `cedict_1_0_ts_utf-8_mdbg.txt.gz` |
| 형식 | Traditional/Simplified를 포함한 CC-CEDICT v1 UTF-8 text의 GZip archive |
| 라이선스 | Creative Commons Attribution-ShareAlike 4.0 International |

위 값은 앱의 `CcCedictProvider` descriptor와 일치합니다. MDBG listing에는 공식 checksum이 표시되지 않았으므로 확인되지 않은 hash를 문서나 코드에 만들지 않았습니다.

## 새 clone에서 수동 설치

1. 브라우저에서 CC-CEDICT 공식 download page를 연 뒤 권장 MDBG release page로 이동합니다.
2. release timestamp, entry count, artifact name과 CC BY-SA 4.0 표시가 위 표와 일치하는지 확인합니다. 더 새 release만 보인다면 아래 업데이트 절차를 먼저 수행하며 새 파일을 기존 release라고 표시하지 않습니다.
3. 브라우저에서 `cedict_1_0_ts_utf-8_mdbg.txt.gz`를 직접 다운로드합니다. 자동 다운로드 command를 만들거나 실행하지 않습니다.
4. 파일을 configured dataset root의 다음 경로에 그대로 복사합니다. 압축을 풀거나 user vocabulary Room DB로 import하지 않습니다.

```text
<dataset-root>/cc-cedict/source/cedict_1_0_ts_utf-8_mdbg.txt.gz
```

별도 dataset root를 설정한 PowerShell 예시는 사용자가 내려받은 정확한 파일을 지정해야 합니다.

```powershell
$sourceDirectory = 'D:\dictionary-data\cc-cedict\source'
New-Item -ItemType Directory -Force $sourceDirectory
Copy-Item -LiteralPath 'C:\path\chosen-by-user\cedict_1_0_ts_utf-8_mdbg.txt.gz' `
  -Destination $sourceDirectory
Get-FileHash -Algorithm SHA256 `
  (Join-Path $sourceDirectory 'cedict_1_0_ts_utf-8_mdbg.txt.gz')
python -m tools.build_dictionary_packs --pack cc-cedict
```

마지막 hash는 자신의 build input 기록용입니다. 공식 checksum과 비교했다는 의미가 아닙니다. `.gz` 파일은 실수로 commit하지 않도록 `.gitignore`에 포함됩니다. artifact가 없으면 build는 성공하지만 검색 화면은 `LocalDatasetUnavailable`을 표시합니다.

## 실행 구조와 비용

GZip 원본은 base APK asset에 들어가지 않습니다. 설정 화면에서 generic pack으로 설치된 active payload를 첫 CC-CEDICT 검색에서 background I/O dispatcher가 읽어 UTF-8 text를 parse하고 Simplified/Traditional exact-match map을 메모리에 만듭니다. 이후 같은 app process의 검색은 해당 index를 재사용합니다. user vocabulary Room schema와 별도이며 앱 시작이나 첫 화면에서는 import 작업을 하지 않습니다.

전체 artifact가 이 작업 환경에 없으므로 전체 데이터셋을 포함한 APK 증가량, 첫 parse 시간과 peak memory는 측정하지 않았습니다. binary를 공급한 뒤 release별로 APK 크기와 첫 검색 시간을 실제 측정해 기록해야 합니다.

## release 업데이트

1. 공식 download page와 MDBG release page에서 새 release, entry count, artifact, 라이선스를 다시 확인합니다.
2. 브라우저로 새 GZip을 수동 다운로드하고 별도 위치에서 SHA-256을 기록합니다.
3. parser fixture와 전체 JVM test를 먼저 실행합니다. malformed count가 갑자기 증가하면 skip을 숨기지 말고 format 변경 여부를 조사합니다.
4. `CcCedictProvider.RELEASE_ID`, `RELEASE_ENTRY_COUNT`, 필요하면 `CC_CEDICT_ARTIFACT_NAME`과 format metadata를 갱신합니다.
5. 이 문서, `docs/dictionary-sources.md`, Settings의 표시값과 provider contract test를 같은 change에서 갱신합니다.
6. 새 GZip으로 generic pack을 만들고 `testDebugUnitTest`, `lintDebug`, `assembleDebug`와 Test AVD pack 설치를 검증합니다.
7. update 실패 시 activation이 기존 pack을 유지하는지 확인합니다. user Room DB에는 어떤 변경도 하지 않습니다.
