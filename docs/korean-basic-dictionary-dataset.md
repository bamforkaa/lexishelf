# 한국어기초사전 로컬 데이터셋

> Task 9부터 생성 DB는 APK asset이 아니라 `korean-basic/generated/`에서 generic pack으로
> 만듭니다. 현재 root/install 절차는 [dictionary-packs.md](dictionary-packs.md)가 우선합니다.

이 provider는 국립국어원의 공식 한국어기초사전 전체 내려받기 JSON을 개발자가 명시적으로 전처리한 읽기 전용 SQLite 인덱스로 사용합니다. 앱은 데이터셋을 자동으로 내려받지 않으며 API key, network permission, 실시간 네트워크가 필요하지 않습니다.

## 선택한 공식 자료

- 공식 사전: [한국어기초사전](https://krdict.korean.go.kr/)
- 전체 데이터: [사전 전체 내려받기](https://krdict.korean.go.kr/download/downloadPopup)의 JSON ZIP
- 현재 확인 release: `2026-08-19`
- pack payload: `<dataset-root>/korean-basic/generated/korean_basic_dictionary.db`
- 텍스트 license: [Creative Commons Attribution-ShareAlike 2.0 Korea](https://creativecommons.org/licenses/by-sa/2.0/kr/)
- 표시 문구: `한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.`

2026-08-23에 내려받은 공식 JSON ZIP은 84,455,509 bytes였고 로컬 계산 SHA-256은 `7cf41e62a2a36158a8be2b6d2f84c086221e9b29d4345c44e5497eebf21c8c40`입니다. 이는 국립국어원이 게시한 checksum이 아니라 이 개발 환경에서 재현성 확인용으로 계산한 값입니다.

## 생성 절차

1. 브라우저에서 공식 전체 내려받기 화면을 열고 JSON을 선택합니다.
2. ZIP을 `<dataset-root>/korean-basic/source/korean-basic-dictionary-json.zip`에 둡니다. 원본 ZIP과 생성 DB는 Git에 commit하지 않습니다.
3. 저장소 루트에서 Python 표준 라이브러리만 사용하는 변환기와 pack builder를 실행합니다.

```powershell
python -m tools.build_krdict_index
python -m tools.build_dictionary_packs --pack korean-basic
```

4. 변환기 테스트와 Android 검사를 실행합니다.

```powershell
python -m unittest discover -s tools/tests -v
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

현재 release의 기대 metadata는 다음과 같습니다.

| 항목 | 값 |
| --- | ---: |
| indexed entries | 56,555 |
| senses | 76,833 |
| translations | 826,492 |
| reverse exact keys | 1,938,697 |
| SQLite index size | 211,701,760 bytes |

원본 export의 `LexicalEntry.val`은 관련 관용구에서 재사용되므로 전역 PK로 사용하지 않습니다. `Lemma`는 object 또는 활용형 variant를 함께 가진 array일 수 있으며 변환기는 명시적 `writtenForm`만 선택합니다. 인덱스는 내부 정수 PK를 쓰고, provenance에는 공식 ID와 명시적 표제어를 합친 안정 식별자 및 공식 sense ID를 보존합니다.

## 런타임 동작

인덱스는 사용자 vocabulary Room DB와 완전히 별개입니다. generic `.dictpack`을 설정 화면에서 설치하면 production pack validator가 검증한 SQLite를 `noBackupFilesDir/dictionary-packs/korean-basic-dictionary/korean-basic.multilingual` 아래에서 read-only로 엽니다. 이후 exact Korean headword index 또는 언어별 reverse index를 디스크에서 조회하며 전체 데이터를 메모리에 올리지 않습니다. active pack이 없거나 schema/release가 맞지 않으면 해당 provider만 `LocalDatasetUnavailable` 또는 malformed-data 오류를 표시하고 수동 단어 입력은 계속 동작합니다.

지원 pair는 `ko`와 `en`, `ja`, `fr`, `es`, `ar`, `mn`, `vi`, `th`, `id`, `ru`, `zh` 사이의 양방향 translation exact lookup입니다. 공식 중국어 데이터가 Hans/Hant를 구분하지 않으므로 임의로 `zh-Hans` 또는 `zh-Hant`를 부여하지 않습니다. prefix/fuzzy 검색은 지원하지 않습니다.

변환기는 headword, 명시적 part of speech, sense 순서, 공식 번역과 순서, 안정 source ID만 인덱싱합니다. 역색인은 공식 번역 문자열과 그 문자열에 명시된 구분형 대안을 exact key로 만들 뿐 번역을 생성하거나 추론하지 않습니다. 오디오, 이미지, 비디오, 발음 media는 개별 권리 조건이 있으므로 읽거나 저장하거나 패키징하지 않습니다.

## 업데이트와 rollback

1. 공식 download page의 새 release 날짜, 텍스트 license, 언어 목록과 JSON 구조를 다시 확인합니다.
2. 새 ZIP으로 변환기 테스트와 전체 변환을 실행합니다.
3. indexed counts와 malformed/skipped count 변화를 검토합니다.
4. `KOREAN_BASIC_DICTIONARY_RELEASE_ID`, descriptor metadata와 이 문서를 함께 갱신합니다. 인덱스 테이블 구조가 바뀌면 Room version이 아니라 별도 index schema version을 올립니다.
5. 새 APK에서 forward/reverse exact lookup과 attribution을 검증합니다.
6. 검증 실패 시 이전 APK/asset으로 돌아갑니다. 사전 인덱스 교체는 사용자 Room을 migrate하거나 삭제하지 않습니다.

생성 DB는 `.gitignore` 대상입니다. clean clone에서는 데이터셋이 없어도 빌드와 테스트가 가능하지만 실제 한국어기초사전 검색은 unavailable로 표시됩니다.
