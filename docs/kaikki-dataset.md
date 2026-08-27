# Kaikki/Wiktextract English fallback dataset

## 고정한 공식 원본

이 provider는 English Wiktionary를 Wiktextract로 구조화한 Kaikki.org의 공식 raw JSONL을 사용한다.

| 항목 | 검증값 |
| --- | --- |
| provider ID | `kaikki` |
| 공식 안내 | [Kaikki raw downloads](https://kaikki.org/dictionary/rawdata.html) |
| download URL | `https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz` |
| local artifact | `raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz` |
| 기반 dump | English Wiktionary `2026-08-05` |
| extraction/publish date | `2026-08-23` |
| Wiktextract revisions shown by Kaikki | `872fc7b`, `4deed51` |
| actual compressed bytes | `2,826,618,017` |
| SHA-256 | `e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65` |
| format | UTF-8 gzip JSONL, one object per entry |

Kaikki는 raw extraction을 보통 적어도 주 1회 갱신한다고 설명한다. 위 URL의 내용은 바뀔 수 있으므로 앱의 release ID는 dump 날짜를 고정하고 converter는 위 checksum이 일치하지 않으면 output을 만들기 전에 중단한다. 새 release는 날짜, checksum, coverage, QA 표를 다시 검토해야 한다.

공식 사이트에는 언어별 post-processed download도 있으나 현재 deprecated이며 제거 예정이라고 표시된다. 19개 후보를 각각 받으면 중복된 대형 artifact와 서로 다른 post-processing lifecycle을 관리해야 한다. 이 작업에서는 개발자가 명시적으로 한 번 받은 current official raw gzip을 streaming으로 한 번 읽고 검토 대상 언어만 분리했다. 자동 download는 구현하지 않았고 Android build도 원본을 받지 않는다. 관측 download 시간은 약 10분 8초였지만 회선에 따라 달라진다.

## 개발 저장소와 명령

```text
D:\lang-Database\kaikki\
├─ source\
│  ├─ raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz
│  └─ filtered\<language>.jsonl.gz
├─ generated\<language>.db
└─ packs\kaikki.<language>-en-enwiktionary-2026-08-05.dictpack
```

`source/filtered`는 공식 별도 artifact가 아니라 converter가 측정과 재현을 위해 만든 언어별 subset이다. 프로젝트 asset에는 raw/DB/pack을 넣지 않는다.

```powershell
Get-FileHash `
  D:\lang-Database\kaikki\source\raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz `
  -Algorithm SHA256

# 확정된 12개 production language만 변환
python -X utf8 -m tools.build_kaikki_indexes

# release 선정 때만 19개 후보 전체 재측정
python -X utf8 -m tools.build_kaikki_indexes --analyze-candidates

# 언어별 pack 생성
python -X utf8 -m tools.build_dictionary_packs --pack kaikki
python -X utf8 -m tools.build_dictionary_packs --pack kaikki --kaikki-language de
```

변환기는 NFC, trim, 연속 whitespace 축약, locale-independent lowercase만 적용한다. prefix, fuzzy, morphology, transliteration search는 만들지 않는다. DB와 filtered gzip은 임시 파일에 완성한 뒤 교체하므로 parsing/checksum 실패가 기존 성공 산출물을 부분 파일로 바꾸지 않는다.

## 2026-08-05 후보 coverage — Task 12 schema v1 baseline

`indexed`는 최소 한 개의 English gloss가 있어 실제 검색 결과로 만들 수 있는 entry 수다. `headwords`는 원본 spelling 기준 distinct count이며 homograph entry는 합치지 않는다. 비율의 분모는 indexed entry다. Source MiB는 locally filtered gzip, DB MiB는 compact SQLite 크기다. 이 표의 `examples %`는 초기 언어 선정을 위해 quotation을 포함한 모든 text-bearing example record를 센 schema v1 baseline이다. 현재 import 가능한 `type=example`/no-`ref` coverage와 schema v2 크기는 아래 pack 표를 기준으로 한다.

| 언어 | 선택 | raw entries | headwords | indexed | senses | POS % | pron. % | forms % | examples % | gender % | source MiB | DB MiB |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| de | yes | 369,987 | 351,342 | 369,967 | 631,694 | 100.0 | 22.6 | 27.8 | 4.5 | 55.9 | 66.4 | 282.6 |
| hi | yes | 38,877 | 35,645 | 38,856 | 57,527 | 100.0 | 93.9 | 100.0 | 11.6 | 68.5 | 13.2 | 31.7 |
| pl | yes | 197,870 | 174,543 | 197,832 | 264,955 | 100.0 | 99.9 | 48.9 | 4.5 | 58.6 | 48.6 | 176.8 |
| la | no | 891,992 | 836,623 | 886,465 | 1,002,925 | 100.0 | 9.4 | 87.2 | 0.9 | 32.5 | 69.7 | 421.2 |
| nl | yes | 145,891 | 138,123 | 145,878 | 189,727 | 100.0 | 38.3 | 44.4 | 4.7 | 41.6 | 21.4 | 95.3 |
| pt | yes | 445,245 | 424,326 | 445,173 | 525,560 | 100.0 | 17.4 | 16.5 | 1.4 | 23.5 | 36.3 | 159.7 |
| it | no | 623,477 | 589,149 | 623,349 | 719,299 | 100.0 | 16.5 | 19.2 | 1.0 | 35.5 | 50.3 | 229.1 |
| tr | yes | 45,666 | 41,866 | 45,617 | 59,779 | 100.0 | 40.2 | 46.9 | 6.7 | 0.0 | 25.9 | 74.7 |
| cs | yes | 72,049 | 69,709 | 72,027 | 85,100 | 100.0 | 98.7 | 66.1 | 7.4 | 60.7 | 15.1 | 105.7 |
| sv | yes | 312,098 | 302,358 | 312,058 | 345,896 | 100.0 | 2.1 | 15.4 | 4.0 | 17.1 | 21.8 | 136.3 |
| fi | no | 265,948 | 251,884 | 265,940 | 309,770 | 100.0 | 68.6 | 66.8 | 4.1 | 0.0 | 204.9 | 790.6 |
| uk | yes | 59,483 | 55,175 | 59,433 | 80,902 | 100.0 | 98.4 | 100.0 | 6.2 | 15.0 | 22.3 | 129.0 |
| fr | no | 402,395 | 389,660 | 402,291 | 458,804 | 100.0 | 32.3 | 22.4 | 3.0 | 32.7 | 39.8 | 143.8 |
| es | no | 809,603 | 771,158 | 809,470 | 873,873 | 100.0 | 20.7 | 14.2 | 1.9 | 24.4 | 61.9 | 263.3 |
| ru | no | 442,348 | 427,379 | 442,256 | 492,073 | 100.0 | 99.2 | 100.0 | 3.9 | 11.6 | 66.6 | 496.3 |
| ar | no | 77,339 | 26,141 | 35,941 | 58,713 | 100.0 | 56.4 | 99.8 | 14.5 | 6.0 | 37.5 | 120.6 |
| vi | yes | 51,655 | 39,881 | 46,170 | 56,466 | 100.0 | 74.0 | 24.9 | 20.8 | 0.0 | 9.2 | 15.5 |
| th | yes | 20,950 | 17,570 | 20,939 | 27,665 | 100.0 | 99.3 | 98.4 | 13.7 | 0.0 | 6.5 | 8.6 |
| id | yes | 39,774 | 34,566 | 39,662 | 55,557 | 100.0 | 75.3 | 75.7 | 4.4 | 0.1 | 7.7 | 14.0 |

첫 batch는 `de, hi, pl, nl, pt, tr, cs, sv, uk, vi, th, id → en` 12개다. 앞의 9개는 기존 PanLex Korean 결과와 함께 richer English fallback을 제공한다. `vi/th/id`는 Korean Basic과 겹치지만 합계 DB가 약 38MiB로 작고 pronunciation/forms/example metadata 가치가 높다. `la/it/fi`는 초기 설치 비용이 크고, `fr/es/ru/ar`는 Korean Basic 범위와 겹치면서 installed DB 비용이 더 커 다음 batch로 미뤘다. `ja/zh`는 각각 JMdict/CC-CEDICT가 있어 후보에서 제외했다.

## Compact index와 pack

한 provider ID `kaikki` 아래 언어별 pack을 둔다. pack ID와 provider identity를 합치지 않는다. 각 manifest는 오직 `<source> → en / TRANSLATION` 한 pair를 선언하고 resolver가 provider와 exact language pair로 active pack을 선택한다.

| pair / pack ID | indexed entries | entries with eligible examples | DB bytes | pack bytes |
| --- | ---: | ---: | ---: | ---: |
| `de → en` / `kaikki.de-en` | 369,967 | 8,110 | 147,476,480 | 36,732,025 |
| `hi → en` / `kaikki.hi-en` | 38,856 | 3,756 | 18,321,408 | 4,927,192 |
| `pl → en` / `kaikki.pl-en` | 197,832 | 6,582 | 72,261,632 | 21,935,826 |
| `nl → en` / `kaikki.nl-en` | 145,878 | 4,740 | 47,034,368 | 14,359,038 |
| `pt → en` / `kaikki.pt-en` | 445,173 | 3,144 | 142,696,448 | 39,933,975 |
| `tr → en` / `kaikki.tr-en` | 45,617 | 1,975 | 15,765,504 | 4,817,383 |
| `cs → en` / `kaikki.cs-en` | 72,027 | 4,548 | 24,186,880 | 7,465,096 |
| `sv → en` / `kaikki.sv-en` | 312,058 | 9,843 | 91,308,032 | 26,429,658 |
| `uk → en` / `kaikki.uk-en` | 59,433 | 2,917 | 25,755,648 | 7,506,649 |
| `vi → en` / `kaikki.vi-en` | 46,170 | 6,449 | 16,785,408 | 5,600,242 |
| `th → en` / `kaikki.th-en` | 20,939 | 1,862 | 9,203,712 | 2,698,555 |
| `id → en` / `kaikki.id-en` | 39,662 | 1,352 | 14,446,592 | 4,824,410 |
| total | 1,793,612 | 55,278 | 625,242,112 | 177,230,049 |

Pack bytes are the observed 2026-08-28 schema v3 build. Payload bytes/SHA-256 are stable. The builder now reuses an existing archive when payload and content-defining manifest fields match, preserving `createdAt` and preventing large incremental debug APKs from accumulating obsolete asset regions.

DB index schema v3에는 stable hashed source entry ID, normalized headword index, deterministic source order와 compact JSON payload만 둔다. payload는 headword, raw/normalized POS mapping source, 최대 8개 textual IPA/enPR/`zh-pron`, 언어/POS별 semantic-v1 policy가 고른 대표 form과 정제된 raw form count, sense/gloss 순서, source sense ID, gender, import 가능한 usage example text/count를 보존한다. homograph/etymology entry는 합치지 않는다. v2 pack은 runtime에서 뜻과 나머지 metadata를 계속 읽지만 raw-order first-24 forms는 표시하지 않는다. 자세한 policy는 [linguistic-forms.md](linguistic-forms.md)에 있다.

Kaikki `examples` 배열에서는 `type=example`이고 외부 `ref`가 없는 source text만 원본 순서대로 sense당 최대 2개 보존한다. `quotation`, attributed text, example translation, audio/media URL, category, related-word graph, raw template와 full etymology는 저장하지 않는다. 사용자가 row를 눌렀을 때만 generic mapper가 English gloss/POS/example, textual pronunciation과 grammatical gender를 각 field provenance와 함께 가져온다. forms는 provider result의 transient metadata로 유지한다. 실측 coverage와 저장 결정은 [linguistic-metadata.md](linguistic-metadata.md)에 있다.

## 실제 exact QA key

각 항목은 generated DB에 존재하는 exact headword와 첫 대표 English gloss다. `sv: skola`처럼 homograph가 있으면 UI에 여러 entry가 별도로 나타날 수 있다.

| 언어 | 실제 query → 확인 gloss 5개 |
| --- | --- |
| de | `Wasser → water`, `Haus → house`, `Schule → school`, `Liebe → love`, `essen → to eat` |
| hi | `पानी → water`, `घर → house/home`, `विद्यालय → school`, `प्यार → love`, `खाना → food/meal` |
| pl | `woda → water`, `dom → house`, `szkoła → school`, `miłość → love`, `jeść → to eat` |
| nl | `water → water`, `huis → house/home`, `school → school`, `liefde → love`, `eten → to eat` |
| pt | `água → water`, `casa → house`, `escola → school`, `amor → love`, `comer → to eat` |
| tr | `su → water`, `ev → house/home`, `okul → school`, `aşk → love`, `yemek → to eat` |
| cs | `voda → water`, `dům → house`, `škola → school`, `láska → love`, `jíst → to eat` |
| sv | `vatten → water`, `hus → house`, `skola → school` (homograph 중 하나), `kärlek → love`, `äta → to eat` |
| uk | `вода → water`, `дім → house/home`, `школа → school`, `любов → love`, `їсти → to eat` |
| vi | `nước → water`, `nhà → house/home`, `trường → school`, `tình yêu → love`, `ăn → to eat` |
| th | `น้ำ → water`, `บ้าน → house`, `โรงเรียน → school`, `ความรัก → love`, `กิน → consume` |
| id | `air → water`, `rumah → house`, `sekolah → school`, `cinta → love`, `makan → to eat` |

## 성능과 무결성

Windows 개발 PC에서 official raw 한 번을 19개 후보로 streaming/filter/index하는 초기 측정에는 `1,082.10s`가 걸렸다. Task 15 schema v3의 선택 12개 full rebuild는 `630.67s`였고 filtered source 합계는 `308,748,758` bytes다. 모든 selected DB의 schema는 3이고 `form_selection_policy=semantic-v1` metadata를 가진다. sense당 retained example 최대값은 2이며 pack manifest도 dataset schema 3을 선언한다. 기존 exact index/query 구조는 바뀌지 않았다.

Manual/Test 개발 설정은 `debugDictionaryPackLanguages=de,vi`만 core pack과 함께 bundle한다. Task 15 preflight의 incremental APK는 `266,959,605` bytes였지만 ZIP entry가 아닌 약 44MiB의 stale packaging 공간이 있었고, 조사 DB/test fixture는 active entry에 없었다. `clean assembleDebug` 후 schema v3 form 축소까지 반영한 APK는 `223,014,116` bytes(정상 ZIP overhead 99,302 bytes)다. active 용량은 PanLex, 한국어기초사전, Kaikki de, JMdict, Kaikki vi, CC-CEDICT의 의도한 6개 debug pack이 지배한다. API 37 `Medium_Phone_Test` AVD에서 production pack bootstrap/validation 후 real `Wasser` exact query의 첫 datasource lookup은 기존 full-suite 실행 로그 기준 `7ms`였다(별도 targeted run은 `13ms`). release APK와 Kaikki 미선택 debug build는 이 pack들을 포함하지 않는다.

## 라이선스와 attribution

Kaikki는 이 data가 Wiktionary와 같은 CC BY-SA 및 GFDL 조건이라고 명시한다. English Wiktionary의 원 entry text는 CC BY-SA 4.0과 GFDL 1.1 or later로 dual-license된다. 이 앱/pack은 CC BY-SA 4.0 재사용 경로를 선택하고 `English Wiktionary via Kaikki/Wiktextract`, exact source entry URL, release, license URL을 provenance에 보존한다. Wiktextract 소프트웨어 자체의 MIT license는 추출 data의 license를 대체하지 않는다.

Wiktionary가 외부 출처의 text, quotation, image, sound를 별도 조건이나 fair use로 포함할 수 있다고 공식적으로 경고하므로 attributed quotation은 계속 제외한다. 이번 index는 contributor-authored usage example으로 구조화된 `type=example` 중 `ref`가 없는 text만 선택한 CC BY-SA 4.0 재사용 경로로 포함한다. 원문 entry URL, source sense ID와 license는 sense provenance 및 backup에 유지한다. 이 문서는 법률 자문이 아니다.

설계 결정은 [ADR-0011](decisions/0011-kaikki-per-language-english-fallback.md), generic 설치/rollback은 [dictionary-packs.md](dictionary-packs.md)를 참고한다.
