# 사전 소스 라이선스 조사

최종 확인일: 2026-08-31

현재 실제 provider는 CC-CEDICT, 한국어기초사전, PanLex, JMdict, Kaikki 다섯 개입니다. full dataset binary는 Git이나 base APK에 없고, 공개 허용 목록의 release asset만 앱의 검증된 catalog 경로로 내려받습니다. 개발 converter는 원본 dataset을 자동으로 받지 않으며 테스트는 작고 결정적인 fixture만 사용합니다. 아래 내용은 구현 결정을 위한 조사 기록이며 법률 자문이 아닙니다. 서로 모순되거나 구체적 계약이 보이지 않는 항목은 허용으로 추측하지 않습니다.

코드의 `DictionaryUsagePolicy`도 법률 판단을 대신하지 않습니다. 확인하지 않은 local persistence, redistribution, cache 값은 기본 `UNKNOWN`입니다. 검색 결과 표시는 permission과 별개지만, provider text를 Room/backup으로 이어지는 draft에 복사하려면 local persistence와 redistribution이 모두 `PERMITTED`이고 app import mode도 `COPY_EXPORTABLE_FIELDS`여야 합니다. 복사된 sense는 provider/source/license provenance를 Room과 backup에 함께 보존해야 합니다. 이 조건을 충족하지 못하는 provider는 `REFERENCE_ONLY`입니다.

## Cambridge Dictionary API / licensed data

공식 출처:

- [Cambridge Dictionary Develop](https://dictionary.cambridge.org/develop.html)
- [Cambridge License our data](https://dictionary.cambridge.org/us/license.html)

확인된 사실:

- 공식 Develop 페이지는 Dictionary API가 여러 dictionary/method에 접근을 제공한다고 설명한다.
- 데이터 라이선스 페이지는 사용 목적에 따른 문의와 가격 모델 협의를 안내한다.
- Cambridge 페이지 콘텐츠는 Cambridge University Press & Assessment copyright 대상이다.

확인되지 않아 구현을 막는 항목:

- 개인용 앱이 사용할 구체적인 API access/credential 발급 조건
- API 응답의 영구 저장, 사용자 편집본 저장, 원문 definition 파생 저장 허용 여부
- cache 기간/크기, offline 사용, backup 포함 가능 여부
- 화면별 attribution 문구/링크, rate limit, redistribution 제한
- 오디오/발음 등 각 media의 별도 권리

결정: 공식 계약/terms 원문을 확보하고 위 항목을 확인하기 전에는 호출, cache, raw definition 영구 저장을 구현하지 않는다. 웹 페이지 scraping은 하지 않는다.

## JMdict

Task 8 구현 결정(2026-08-23): 공식 영문 전용 `JMdict_e.gz`를 `jmdict`
(`LOCAL_DATASET`, `ja → en`)로 구현했습니다. 선택 release의 SHA-256은
`11c3fb43a82ae775269e6832d117c4f52152f4d8cf49f44c16a0ed619aa98a6a`입니다. 생성
index는 Git에 포함하지 않으며, 재생성·검증·rollback 절차는
[JMdict dataset 문서](jmdict-dataset.md)와 [ADR-0008](decisions/0008-jmdict-compact-local-index-and-reading.md)이
정의합니다.

공식 출처:

- [EDRDG General Dictionary Licence Statement](https://www.edrdg.org/edrdg/licence.html)
- [JMdict project](https://www.edrdg.org/jmdict/j_jmdict.html)
- [Official distribution index](https://ftp.edrdg.org/pub/Nihongo/00INDEX.html)
- [JMdict DTD documentation](https://www.edrdg.org/jmdict/jmdict_dtd_h.html)

확인된 사실:

- license statement는 JMdict의 Japanese/English components를 Creative Commons Attribution-ShareAlike 4.0으로 제공한다고 명시한다.
- share/remix가 가능하지만 attribution과 share-alike 조건이 있다.
- smartphone app은 메뉴에서 접근 가능한 About/Sources 등의 별도 화면에 acknowledgement를 제공해야 하고 documentation/license 링크 또는 사본을 제공해야 한다.
- 사용 앱은 최신 데이터로 정기 갱신하는 절차를 가져야 한다.
- multilingual JMdict의 비영어 번역은 별도 compiler copyright가 적용될 수 있다고 명시되어 있다.
- DTD는 `ent_seq`를 entry별 unique numeric sequence number로 정의한다. 이 값을 source entry ID로
  보존하지만, 모든 향후 release에서의 불변성까지 보장하는 별도 계약은 확인하지
  못했으므로 update 검증에서 QA key와 ID churn을 확인한다.

저장/편집/재배포 판단:

- 영문 전용 artifact의 Japanese/English data는 attribution과 ShareAlike 조건을 유지하면
  local persistence, user editing, backup/export, redistribution이 가능하다. provider policy는
  `PERMITTED` / `COPY_EXPORTABLE_FIELDS`이지만 license 의무가 사라진다는 뜻이 아니다.
- Settings / Dictionary Sources에 source, release, acknowledgement, official/license link를 표시한다.
  명시적인 suggestion row 선택으로 추가된 reading/gloss/POS에는 source ID·license·release provenance를 보존하고
  JSON backup에도 함께 내보낸다.
- 비영어 gloss distribution은 권리 범위를 별도 확인하기 전에 등록하지 않는다.
- provider, converter와 local/development import는 유지하지만, license가 요구하는 정기 갱신을 보장할
  public update channel이 없으므로 JMdict는 v0.1.0 공개 catalog와 release asset에서 제외한다.

## Kaikki / English Wiktionary / Wiktextract

공식 출처:

- [Kaikki raw data downloads](https://kaikki.org/dictionary/rawdata.html)
- [Kaikki English Wiktionary dictionary](https://kaikki.org/dictionary/)
- [Wiktionary copyrights](https://en.wiktionary.org/wiki/Wiktionary:Copyrights)
- [Wiktextract software license](https://github.com/tatuylonen/wiktextract/blob/master/LICENSE)

확인된 사실과 선택:

- Kaikki official raw page는 English Wiktionary dump의 raw Wiktextract JSONL/GZip과 보통 주 1회 이상의 update를 제공한다. 현재 고정본은 `2026-08-05` dump를 `2026-08-23` 추출한 2,826,618,017-byte gzip이며 SHA-256은 `e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65`이다.
- Kaikki의 언어별 post-processed downloads는 deprecated/removal 예정으로 표시된다. 개발자가 current official raw를 명시적으로 한 번 받아 streaming filter하며 앱/Gradle이 자동 다운로드하지 않는다.
- Kaikki는 extracted data를 Wiktionary와 같은 CC BY-SA/GFDL 조건으로 제공한다고 명시한다. English Wiktionary 원 entry text는 CC BY-SA 4.0과 GFDL 1.1 or later의 dual license다.
- 이 앱과 generated pack은 CC BY-SA 4.0 재사용 경로를 선택하고 source entry URL, Kaikki/Wiktextract attribution, release/license를 provenance에 보존한다. local persistence/redistribution/cache의 `PERMITTED`는 attribution/ShareAlike 조건을 지워 주는 값이 아니다.
- Wiktextract 프로그램의 MIT license는 parser software에 대한 것이며 extracted Wiktionary data의 license를 대체하지 않는다.
- Wiktionary는 외부 source의 quotation/text/image/sound에 별도 조건이나 fair use가 있을 수 있다고 경고한다. Kaikki `examples` 중 `type=example`이고 `ref`가 없는 contributor-authored usage text만 선택한 CC BY-SA 4.0 경로로 index에 넣고, `quotation`, `ref`가 있는 attributed text와 audio/image/media URL은 제외한다. import한 example은 enclosing sense의 source entry/sense/license provenance를 공유한다.
- raw POS와 normalized generic POS를 분리한다. pronunciation/gender/forms는 검색 결과에 표시하되 explicit row tap에서 허용된 English gloss/POS/example과 textual pronunciation/gender만 generic mapper로 가져오고 forms는 transient로 유지한다. refresh는 저장된 user data를 갱신하지 않는다. 실제 field coverage와 결정은 [linguistic-metadata.md](linguistic-metadata.md)에 있다.
- provider ID는 `kaikki`, 첫 지원 pair는 `de|hi|pl|nl|pt|tr|cs|sv|uk|vi|th|id → en`이다. 후보 coverage, 선택/제외 근거, pack 크기와 QA key는 [kaikki-dataset.md](kaikki-dataset.md)에 있다.

미결정 사항:

- 새 Kaikki/Wiktionary release마다 license/source field와 checksum이 동일한지 재검토해야 한다.
- quotation/attributed example 또는 audio/media를 향후 표시·저장하려면 개별 source/attribution/fair-use 조건을 field별로 다시 확인해야 한다.
- 각 새 dump를 공개 catalog에 올리기 전 source/license/checksum과 CC BY-SA attribution 문구를 다시 검토해야 한다.

## CC-CEDICT

공식 출처:

- [CC-CEDICT download page](https://cc-cedict.org/editor/editor.php?handler=Download)
- [MDBG recommended release page](https://www.mdbg.net/chinese/dictionary?page=cc-cedict)
- [CC-CEDICT v1 syntax](https://cc-cedict.org/wiki/syntax)
- [CC-CEDICT v2 syntax](https://cc-cedict.org/wiki/syntax_v2)
- [Creative Commons BY-SA 4.0 deed](https://creativecommons.org/licenses/by-sa/4.0/)

확인된 사실:

- CC-CEDICT 공식 download page는 권장 최신 release를 MDBG에서 받도록 안내하고, editing/review용 latest non-verified build와 구분한다.
- 2026-08-23 확인 당시 권장 release page는 `2026-08-22 08:27:42 GMT`, 124,889 entries, GZip artifact `cedict_1_0_ts_utf-8_mdbg.txt.gz`를 표시했다.
- 두 선택한 download/release page는 현재 work를 Creative Commons Attribution-ShareAlike 4.0 International로 명시한다. source를 밝히는 attribution과 개선·추가한 데이터의 같은 license 공유 의무를 설명한다.
- CC BY-SA 4.0은 조건을 준수하면 share/adapt와 재배포를 허용하며 attribution과 ShareAlike를 요구한다. 코드의 `PERMITTED`는 이 조건이 사라진다는 의미가 아니다.
- MDBG release page는 automated or scripted access를 금지한다. 따라서 Gradle/app/shell downloader를 구현하지 않았고 이 작업에서도 artifact를 자동 취득하지 않았다.
- visible release listing에는 공식 checksum이 없었다. 확인되지 않은 checksum은 만들지 않는다.
- v1 기본 형식은 `Traditional Simplified [pin1 yin1] /sense/gloss/`이며 현재 규칙에서 slash는 sense, semicolon은 같은 sense의 gloss를 구분한다. v2는 pinyin에 double brackets를 쓰며 2023-12부터 도입되었다. provider parser는 둘을 명시적으로 구분한다.
- CC-CEDICT 자체는 machine-readable POS를 제공한다고 가정할 수 없으며 공식 v1 guide도 POS를 쓰지 않는다고 설명한다. provider는 POS/example/etymology/audio를 생성하지 않는다.

선택한 release metadata와 배포:

| 항목 | 값 |
| --- | --- |
| provider ID | `cc-cedict` |
| source/result | `zh-Hans → en`, `zh-Hant → en` translation |
| artifact | `cedict_1_0_ts_utf-8_mdbg.txt.gz` |
| release ID | `2026-08-22T08:27:42Z` |
| entries | 124,889 |
| format | CC-CEDICT v1 UTF-8 GZip; parser는 v2 record도 지원 |
| license | Creative Commons Attribution-ShareAlike 4.0 International |
| attribution | `CC-CEDICT data from MDBG, licensed under CC BY-SA 4.0.`과 source/license link를 Settings에 표시 |

저장/재배포 결정:

- descriptor의 local persistence와 redistribution은 CC BY-SA 4.0 조건 아래 `PERMITTED`, cache는 `PERMITTED`로 기록한다.
- 현재 Room schema 6과 backup schema 5는 imported sense별 provider/source/source entry/license/dataset/imported field/import time/modified 상태, entry-level reading provenance, ordered pronunciation provenance와 grammatical gender를 보존한다. 따라서 app import mode는 `COPY_EXPORTABLE_FIELDS`이며 명시적인 suggestion row 선택이 English gloss와 pinyin reading을 generic field로 추가할 수 있다.
- 검색 결과의 pinyin은 generic reading field로 명시적인 suggestion row 선택 때만 저장하며 entry-level provenance를 함께 보존한다. notes에 넣지 않는다. CC-CEDICT에 없는 structured POS/example도 추론하거나 생성하지 않는다.
- user-authored sense에는 provenance가 없고 imported sense에는 provenance가 있다. 사용자가 imported text를 자유롭게 수정할 수 있지만 출처는 유지되고 수정 여부가 표시된다. JSON backup도 이 구분을 보존한다.
- 검색 결과 도착이나 provider refresh는 editor/Room을 변경하지 않는다. 같은 source entry/sense를 반복 선택하면 accidental duplicate를 추가하지 않는다.
- full GZip은 저장소에 없다. 사용자는 브라우저로 공식 artifact를 내려받아 configured dataset root에 두고 generic `.dictpack`으로 만든다. 정확한 install/update/rollback 절차는 [dictionary-packs.md](dictionary-packs.md)에 있다.

공식 project wiki home의 오래된 CC BY-SA 3.0 표기와 현재 download/release page의 4.0 표기가 일치하지 않는 점은 숨기지 않는다. 이번 구현이 선택한 2026 release의 distribution pages가 명시하는 4.0을 적용했고, future artifact update 때 license를 다시 확인한다.

## 한국어기초사전

공식 출처:

- [한국어기초사전](https://krdict.korean.go.kr/)
- [Open API 사용 안내](https://krdict.korean.go.kr/kor/openApi/openApiInfo)
- [Open API 인증키 신청](https://krdict.korean.go.kr/kor/openApi/openApiRegister)
- [사전 전체 내려받기](https://krdict.korean.go.kr/download/downloadPopup)
- [저작권 정책](https://krdict.korean.go.kr/eng/kboardPolicy/copyRightTermsInfo)
- [CC BY-SA 2.0 KR](https://creativecommons.org/licenses/by-sa/2.0/kr/)

확인된 사실:

- 공식 Open API는 search/view XML API, 이메일로 발급되는 32자리 인증키와 하루 최대 50,000건 제한을 문서화한다.
- 공식 translation language option은 English, Japanese, French, Spanish, Arabic, Mongolian, Vietnamese, Thai, Indonesian, Russian, Chinese의 11개다.
- search data에는 stable target code, 한국어 표제어, 품사, sense 순서/definition과 언어별 번역 표제어/definition이 명시되어 있다.
- 공식 전체 내려받기 화면은 Excel/XML/JSON을 제공한다. 2026-08-23 확인 당시 JSON release는 2026-08-19였고 ZIP response는 84,455,509 bytes였다.
- 저작권 정책은 별도로 표시된 자료를 제외한 text를 CC BY-SA 2.0 KR로 제공하고 출처 표시와 동일 조건 공유를 요구한다.
- multimedia/audio/image/video/발음 자료는 개별 저작권 조건을 가질 수 있으므로 이번 provider는 가져오거나 표시하거나 저장하지 않는다.

선택한 구현과 metadata:

| 항목 | 값 |
| --- | --- |
| provider ID | `korean-basic-dictionary` |
| access | `LOCAL_DATASET` |
| source/result | `ko ↔ en|ja|fr|es|ar|mn|vi|th|id|ru|zh` translation exact lookup |
| source artifact | 공식 전체 JSON ZIP |
| release ID | `2026-08-19` |
| generated artifact | `korean_basic_dictionary.db` |
| indexed entries | 56,555 |
| license | Creative Commons Attribution-ShareAlike 2.0 Korea |
| attribution | `한국어기초사전 - 국립국어원 제공, CC BY-SA 2.0 KR.` |

API 대신 local dataset을 선택한 이유:

- foreign→Korean reverse exact lookup을 offline에서 제공할 수 있다.
- 사용자별 API key, network failure, 일일 요청 한도가 필요 없다.
- raw JSON은 약 1GB 비압축이므로 runtime in-memory parse 대신 개발 시 별도 SQLite reverse index를 생성한다.
- generated dictionary DB는 user Room/backup과 다른 lifecycle과 schema version을 가진다.

저장/재배포 결정:

- 확인한 text license 조건 아래 local persistence, cache와 redistribution을 `PERMITTED`, import mode를 `COPY_EXPORTABLE_FIELDS`로 기록한다. `PERMITTED`는 attribution/ShareAlike 의무가 사라진다는 의미가 아니다.
- 명시적인 suggestion row 선택으로 가져온 한국어/외국어 translation과 명시적 POS에는 provider/source entry/source sense/license/release/import/수정 provenance가 함께 저장되고 JSON backup에도 유지된다.
- 공식 전체 export의 ID가 관련 관용구에서 재사용되므로 source entry reference는 공식 ID와 표제어를 함께 사용하고 공식 sense ID도 별도로 보존한다.
- provider refresh나 새 release는 저장된 user-authored/provider-derived sense를 자동 수정하지 않는다.
- 생성 SQLite와 원본 ZIP은 Git/base APK에 포함하지 않고 generic pack으로 설치한다. 생성 절차는 [korean-basic-dictionary-dataset.md](korean-basic-dictionary-dataset.md), 설치 lifecycle은 [dictionary-packs.md](dictionary-packs.md)에 있다.
- 공식 중국어 data에 script 구분이 없으므로 `zh-Hans`/`zh-Hant`를 추측하지 않고 BCP 47 `zh`를 사용한다.

## PanLex

공식 출처:

- [PanLex](https://panlex.org/)
- [PanLex data license](https://panlex.org/license)
- [PanLex data model](https://dev.panlex.org/data-model/)
- [PanLex database design](https://dev.panlex.org/database-design/)
- [PanLex translation evaluation](https://dev.panlex.org/translation-evaluation/)

확인된 사실:

- 체크섬으로 고정한 `panlex-20190901-csv.zip`의 내장 `LICENSE.txt`는 그 artifact를 CC0 1.0 Universal로 제공한다. copy/modify/distribute와 commercial use가 허용되며 PanLex site 또는 2014 LREC paper citation을 권장한다.
- 현재 PanLex 공식 license page의 조건만으로 과거 snapshot의 조건을 추론하지 않는다. 과거 고정 artifact의 내장 CC0 grant를 현재나 미래 배포물로 일반화하지 않는다.
- expression은 하나의 language variety에 속하고 denotation은 expression을 source-owned meaning에 연결한다. 이번 구현은 동일 meaning의 Korean/foreign co-denotation만 direct relation으로 사용한다.
- distance-1 translation quality는 source group별 최대 quality를 합산하는 방식으로 설명된다. converter ranking은 이 개념을 따르고 deterministic tie-break만 추가한다.
- 2026-08-26 현재 공식 snapshot page는 artifact 목록을 제공하지 않고 과거 API/database host는 사용할 수 없었다. 공식 URL의 archived 2019-09-01 CSV response를 size, SHA-256, ZIP CRC와 embedded CC0 license까지 확인했지만 최신 자료라고 표현하지 않는다.

선택한 구현과 metadata:

| 항목 | 값 |
| --- | --- |
| provider ID | `panlex` |
| access | `LOCAL_DATASET` |
| source/result | `de|hi|pl|la|nl|pt|it|tr|cs|sv|fi|uk ↔ ko` translation exact lookup |
| reviewed PanLex UID | `deu-000`, `hin-000`, `pol-000`, `lat-000`, `nld-000`, `por-000`, `ita-000`, `tur-000`, `ces-000`, `swe-000`, `fin-000`, `ukr-000`, `kor-000` |
| source release | `2019-09-01` official CSV snapshot |
| generated artifact | `panlex_korean_fallback.db` |
| indexed relations | 1,098,758 unique expression pairs |
| license | 고정한 2019-09-01 artifact의 CC0 1.0 Universal |

저장/재배포 결정:

- 고정 artifact의 내장 CC0 grant에 따라 local persistence, redistribution과 cache를 `PERMITTED`, import mode를 `COPY_EXPORTABLE_FIELDS`로 기록한다. 새 PanLex 배포물에는 이 결정을 재사용하지 않는다.
- 명시적인 suggestion row 선택으로 가져온 translation에 PanLex expression/meaning/source ID, release, source/license를 sense provenance로 저장하고 JSON backup에도 유지한다.
- 전체 PanLex dataset은 user Room이나 backup에 넣지 않는다. 검색 result는 transient이며 refresh가 저장된 사용자 data를 바꾸지 않는다.
- 다른 언어를 거치는 pivot translation, definition/POS/example 추론은 하지 않는다.
- source snapshot, coverage, converter와 update 절차는 [panlex-dataset.md](panlex-dataset.md), 결정은 [ADR-0007](decisions/0007-panlex-filtered-local-fallback.md)에 기록했다.

미결정 사항:

- 현재 official distribution/API 복구 여부와 새 release의 안정 download/checksum manifest
- 고정한 2019 artifact 외 다른 PanLex artifact의 license와 redistribution 조건
- 여러 source attestation의 상세 attribution을 UI에 노출할 필요

## 구현 전 공통 승인 체크리스트

- 공식 source URL과 선택한 artifact/version
- license/terms 원문과 보관 사본 또는 안정 링크
- 영구 저장, 편집/파생 저장, cache, backup, redistribution 허용 범위
- 필수 attribution 문구와 노출 위치
- update 의무와 실패/rollback 정책
- source entry ID 및 provenance 보존 방식
- 테스트 fixture의 재배포 허용 여부와 sanitization
- 사용자 편집 내용이 provider refresh로 덮어써지지 않는 테스트

## NAVER Dictionary external reference (data provider 아님)

- 공식 시작점: [NAVER Dictionary](https://dict.naver.com/)
- 2026-08-23에 현재 공식 service destination을 확인한 언어만 중앙 mapping에 넣었습니다: `en`, `ja`, `zh`, `fr`, `de`, `es`, `ru`, `ar`, `hi`, `pl`, `mn`, `la`.
- `zh-Hans`/`zh-Hant`는 external navigation에만 base `zh` destination을 사용합니다. 이는 provider dataset의 script identity를 합치는 규칙이 아닙니다.
- URI는 해당 공식 base와 `#/search?query=<encoded-headword>` 조합으로 provider 구현 한 곳에서 만듭니다. destination이 바뀌면 이 mapping과 고정 테스트를 함께 재검증합니다.
- 앱은 NAVER page/API/audio를 fetch, scrape, parse, prefetch, cache, import 또는 재배포하지 않습니다. 사용자가 화면 링크를 누를 때만 Android `ACTION_VIEW`를 보냅니다. 현재 Manifest의 `INTERNET` permission은 사용자 요청에 따른 ML Kit 손글씨 모델 다운로드용이며 NAVER나 dictionary provider가 앱 내부 network 요청을 한다는 뜻이 아닙니다.
- NAVER는 자동 provenance source가 아닙니다. 외부 페이지를 보고 사용자가 직접 쓴 뜻/reading/note는 user-authored content입니다.
