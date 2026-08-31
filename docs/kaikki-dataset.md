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
D:\dictionary-data\kaikki\
├─ source\
│  ├─ raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz
│  └─ filtered\<language>.jsonl.gz
├─ generated\<language>.db
└─ packs\kaikki.<language>-en-enwiktionary-2026-08-05.dictpack
```

`source/filtered`는 공식 별도 artifact가 아니라 converter가 측정과 재현을 위해 만든 언어별 subset이다. 프로젝트 asset에는 raw/DB/pack을 넣지 않는다.

```powershell
Get-FileHash `
  D:\dictionary-data\kaikki\source\raw-wiktextract-data-enwiktionary-2026-08-05.jsonl.gz `
  -Algorithm SHA256

# 확정된 12개 production language만 변환
python -X utf8 -m tools.build_kaikki_indexes

# 영어 morphology auxiliary만 source 전체에서 재생성
python -X utf8 -m tools.build_kaikki_indexes --only-english-morphology

# release 선정 때만 19개 후보 전체 재측정
python -X utf8 -m tools.build_kaikki_indexes --analyze-candidates

# 언어별 pack 생성
python -X utf8 -m tools.build_dictionary_packs --pack kaikki
python -X utf8 -m tools.build_dictionary_packs --pack kaikki --kaikki-language de
```

변환기의 exact key와 morphology key는 NFC, trim, 연속 whitespace 축약 후 기본적으로 locale-independent lowercase를 적용한다. Turkish는 정서법상 서로 다른 `I ↔ ı`, `İ ↔ i` pair를 보존하기 위해 `tr` locale case mapping을 converter와 Android runtime 양쪽에서 동일하게 사용한다. prefix, fuzzy, stemming, guessed lemma, transliteration search는 만들지 않는다. Morphology는 실제 tagged form→entry headword 관계만 별도 exact reverse index로 만든다. DB와 filtered gzip은 임시 파일에 완성한 뒤 교체하므로 parsing/checksum 실패가 기존 성공 산출물을 부분 파일로 바꾸지 않는다.

## 2026-08-05 후보 coverage — Task 12 schema v1 baseline

`indexed`는 최소 한 개의 English gloss가 있어 실제 검색 결과로 만들 수 있는 entry 수다. `headwords`는 원본 spelling 기준 distinct count이며 homograph entry는 합치지 않는다. 비율의 분모는 indexed entry다. Source MiB는 locally filtered gzip, DB MiB는 compact SQLite 크기다. 이 표의 `examples %`는 초기 언어 선정을 위해 quotation을 포함한 모든 text-bearing example record를 센 schema v1 baseline이다. 당시 import 가능한 `type=example`/no-`ref` coverage와 schema v2 크기는 아래 역사 pack 표를 기준으로 한다. 현재 계약은 문서 하단의 Task 17 schema v3 표다.

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

## Compact index와 pack — Task 15.2 schema v2 역사 baseline

한 provider ID `kaikki` 아래 언어별 pack을 둔다. pack ID와 provider identity를 합치지 않는다. 각 manifest는 오직 `<source> → en / TRANSLATION` 한 pair를 선언하고 resolver가 provider와 exact language pair로 active pack을 선택한다.

| pair / pack ID | indexed entries | entries with eligible examples | DB bytes | pack bytes |
| --- | ---: | ---: | ---: | ---: |
| `de → en` / `kaikki.de-en` | 369,967 | 8,110 | 304,447,488 | 46,517,945 |
| `hi → en` / `kaikki.hi-en` | 38,856 | 3,756 | 34,914,304 | 6,417,826 |
| `pl → en` / `kaikki.pl-en` | 197,832 | 6,582 | 190,668,800 | 30,112,533 |
| `nl → en` / `kaikki.nl-en` | 145,878 | 4,740 | 101,761,024 | 17,411,446 |
| `pt → en` / `kaikki.pt-en` | 445,173 | 3,144 | 171,732,992 | 41,882,607 |
| `tr → en` / `kaikki.tr-en` | 45,617 | 1,975 | 78,929,920 | 7,552,835 |
| `cs → en` / `kaikki.cs-en` | 72,027 | 4,548 | 112,893,952 | 12,759,507 |
| `sv → en` / `kaikki.sv-en` | 312,058 | 9,843 | 146,935,808 | 29,671,592 |
| `uk → en` / `kaikki.uk-en` | 59,433 | 2,917 | 136,470,528 | 13,216,728 |
| `vi → en` / `kaikki.vi-en` | 46,170 | 6,449 | 17,539,072 | 5,788,359 |
| `th → en` / `kaikki.th-en` | 20,939 | 1,862 | 10,752,000 | 2,981,359 |
| `id → en` / `kaikki.id-en` | 39,662 | 1,352 | 15,286,272 | 4,971,010 |
| total | 1,793,612 | 55,278 | 1,322,332,160 | 219,283,747 |

Pack bytes are the observed 2026-08-26 build. Payload bytes/SHA-256 are stable; the ZIP size can vary by a few bytes because `manifest.createdAt` records the build time.

당시 DB index schema v2에는 stable hashed source entry ID, normalized headword index, deterministic source order와 compact JSON payload를 뒀다. payload는 headword, raw/normalized POS mapping source, 최대 8개 textual IPA/enPR/`zh-pron`, 최대 24개 source-order form과 전체 form count, sense/gloss 순서, source sense ID, gender, import 가능한 usage example text/count를 보존했다. Task 17 schema v3는 source-order form 보존을 bounded semantic selection으로 교체하고 별도 reverse index를 추가했다. homograph/etymology entry를 합치지 않는 원칙은 유지한다.

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

Windows 개발 PC에서 official raw 한 번을 19개 후보로 streaming/filter/index하는 초기 측정에는 `1,082.10s`가 걸렸다. Task 15.2 당시 schema v2 converter로 선택 12개를 다시 만든 실행은 `918.96s`였고 source SHA-256 `e4dbb4a3f96338ae240c1f3fcc65b6ec73746f71ffb3907dde33c3af0e61bb65`를 먼저 검증했다. 당시 selected DB와 pack manifest의 schema는 2였다. 현재 schema v3의 무결성과 성능은 아래 Task 17 보고를 기준으로 한다.

Task 15.2의 Manual/Test 개발 설정은 `debugDictionaryPackLanguages=de,vi`만 core pack과 함께 bundle했다. 당시 debug APK는 `264,611,485` bytes였다. API 37 test AVD에서 production pack bootstrap/validation 후 real `Wasser`와 `ăn` exact query가 통과했고, `Wasser`의 첫 datasource lookup은 full-suite 실행 로그 기준 `24ms`였다. Task 17 debug bundle에는 이 둘과 English morphology auxiliary pack이 포함되며 현재 APK/latency는 아래 보고를 기준으로 한다. release APK와 Kaikki 미선택 debug build는 이 pack들을 포함하지 않는다.

## 라이선스와 attribution

Kaikki는 이 data가 Wiktionary와 같은 CC BY-SA 및 GFDL 조건이라고 명시한다. English Wiktionary의 원 entry text는 CC BY-SA 4.0과 GFDL 1.1 or later로 dual-license된다. 이 앱/pack은 CC BY-SA 4.0 재사용 경로를 선택하고 `English Wiktionary via Kaikki/Wiktextract`, exact source entry URL, release, license URL을 provenance에 보존한다. Wiktextract 소프트웨어 자체의 MIT license는 추출 data의 license를 대체하지 않는다.

Wiktionary가 외부 출처의 text, quotation, image, sound를 별도 조건이나 fair use로 포함할 수 있다고 공식적으로 경고하므로 attributed quotation은 계속 제외한다. 이번 index는 contributor-authored usage example으로 구조화된 `type=example` 중 `ref`가 없는 text만 선택한 CC BY-SA 4.0 재사용 경로로 포함한다. 원문 entry URL, source sense ID와 license는 sense provenance 및 backup에 유지한다. 이 문서는 법률 자문이 아니다.

설계 결정은 [ADR-0011](decisions/0011-kaikki-per-language-english-fallback.md), generic 설치/rollback은 [dictionary-packs.md](dictionary-packs.md)를 참고한다.

## Task 17 schema v3 forms and morphology report

Schema v3 is the authoritative current generated-index contract. It keeps exact entry lookup and
adds a separate `morphology_forms` reverse index. Display forms are selected by the conservative
policy in [linguistic-forms.md](linguistic-forms.md); reverse lookup and exact-only provider re-query
are described in [morphology-search.md](morphology-search.md). All values below were measured from
the regenerated `2026-08-05` artifacts; every DB returned `PRAGMA quick_check=ok`.

| language | selected forms avg / p95 / max | morphology rows | unique surfaces | DB bytes | pack bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| de | 0.338 / 2 / 3 | 747,647 | 729,610 | 277,938,176 | 75,256,468 |
| hi | 0.528 / 2 / 3 | 253,505 | 248,033 | 80,154,624 | 12,076,269 |
| pl | 0.407 / 2 / 3 | 443,187 | 417,554 | 137,605,120 | 36,081,585 |
| nl | 0.438 / 2 / 2 | 142,568 | 137,529 | 68,259,840 | 21,067,131 |
| pt | 0.195 / 1 / 4 | 475,603 | 433,428 | 214,892,544 | 54,761,521 |
| tr | 0.319 / 1 / 1 | 386,244 | 383,274 | 79,294,464 | 14,463,495 |
| cs | 0.713 / 2 / 2 | 367,817 | 360,603 | 78,368,768 | 18,537,880 |
| sv | 0.324 / 3 / 3 | 255,078 | 249,254 | 130,940,928 | 35,179,213 |
| uk | 0.676 / 3 / 3 | 353,906 | 334,965 | 99,495,936 | 18,754,907 |
| vi | 0 / 0 / 0 | 0 | 0 | 16,793,600 | 5,577,597 |
| th | 0 / 0 / 0 | 0 | 0 | 9,211,904 | 2,686,642 |
| id | 0.746 / 2 / 2 | 30,475 | 28,504 | 19,271,680 | 6,831,478 |
| en morphology only | n/a | 543,791 | 521,889 | 86,433,792 | 29,322,956 |

Across the 1,793,612 production dictionary entries, 613,274 forms were selected for display
(average 0.342; observed max 4) under a final safety cap of 8. The 12 language indexes contain
3,456,030 reverse rows; the English auxiliary index raises the total to 3,999,821. Lemmas per
surface are normally one (p95 one except Portuguese p95 two); the measured maximum is 20 in
Swedish. Multiple lemmas are kept and deterministically limited by runtime rather than silently
choosing one.

The first policy pass exposed two pathologies and was not accepted as final: Portuguese literal
`metaphonic` was a technical pseudo-form connected to 586 lemmas, and full German adjective /
Turkish combinatorial tables produced 1,345,318 / 1,156,031 rows. Regression filters reduced the
final maxima to 6 Portuguese lemmas before the pseudo-form removal (final max 5), 747,647 German
rows, and 386,244 Turkish rows while retaining principal queries such as `Häuser → Haus`,
`ging → gehen`, `evler → ev`, `gitti/gidiyor → gitmek`. A final raw-source audit also
found that Turkish conjugation person/number tags conflict with surface text (`gittim` is tagged
third-person). Turkish verb display labels are therefore suppressed while the source-attested
reverse relations remain searchable.

Representative generated relations are:

| language | exact generated surface → lemma samples |
| --- | --- |
| de | `Häuser → Haus`, `ging → gehen`, `gegangen → gehen` |
| hi | `विश्वों → विश्व`, `विश्वो → विश्व`, `कुत्ती → कुत्ता` |
| pl | `domy → dom`, `większy → duży/wielki`, `gratisów → gratis` |
| nl | `huizen → huis`, `ging → gaan`, `gegaan → gaan` |
| pt | `casas → casa/casar`, `fui → ir/ser`, `comendo → comer/comendar` |
| tr | `evler → ev/evlemek`, `gitti → gitmek`, `gidiyor → gitmek` |
| cs | `domy → dům`, `šel → jít`, `větší → velký/veliký` |
| sv | `husen → hus`, `gick → gå`, `gått → gå` |
| uk | `йшов → йти`, `соба́ки → собака`, `соба́к → собака` |
| id | `rumah-rumah → rumah`, `berjalan → jalan`, `dimakan → makan` |
| vi / th | no relation; current forms are not reliable inflections |
| en auxiliary | `is/are/was/were/been/being → be`, `went → go/gan`, `children → child/childer` |

Representative bounded display payloads from the final DBs include `Haus → plural Häuser,
genitive singular Hauses`, `gehen → present 3sg geht, past ging, past participle gegangen`,
`huis → plural huizen`, `comer → present 1sg como, preterite 3sg comeu, past participle
comido, gerund comendo`, and `ev → plural evler`. `gitmek` deliberately displays no forms
because its reviewed raw person tags are not trustworthy.

The twelve schema-v3 dictionary DBs total 1,212,227,584 bytes, 110,104,576 bytes less than the
old schema-v2 DB total because unbounded retained payload forms were removed. The English
morphology-only DB brings the current total to 1,298,661,376 bytes. Packs total 330,597,142 bytes
including English. A full English dictionary index was deliberately not generated: the raw source
contains 1,487,639 English entries, while the measured auxiliary already covers 491,229
forms-containing entries before precision filtering and 378,019 afterward in 86,433,792 DB bytes.
Shipping another full definition index would
duplicate provider scope and violate the bounded-storage objective.

The full 13-output conversion took 918.26s on the development PC. Warm desktop reverse lookups
measured 0.021–0.0336ms median and at most 0.0594ms p95 across non-empty indexes. Pack audit streamed
every archive and confirmed schema 3, ZIP CRC, payload size, and payload SHA-256. Android Test AVD
production bootstrap/query tests passed with `Wasser` direct lookup at 13ms, `Häuser → Haus`
morphology plus lemma exact lookup at 8ms, and English `is`/`are`/`went` morphology at 4/2/3ms.
The debug bundle containing the four core packs, `de`, `vi`, and English morphology is
339,786,641 bytes. A separate read-only audit isolated `select_display_forms` calls from gzip/JSON
decoding: 620,651 form-bearing raw entries took 50,713.55ms total (weighted 81.71µs/entry).
The per-language high was Turkish 213.29µs/entry because its raw arrays reach 1,897 cells;
German was 196.42µs/entry. The complete reproducible raw audit is produced by
`python -X utf8 -m tools.report_kaikki_forms --top 0 --output <report.json>`.

### Task 18 sense-label payload

The reviewed usage-label whitelist adds only an optional compact `u` array to each affected sense
payload; SQLite DDL, indices, manifest contract, and schema 3 validation stay unchanged. The
12 dictionary DBs now total `1,217,822,720` bytes (`+5,595,136`, about 0.46%) and their packs total
`302,282,267` bytes (`+1,008,081`, about 0.33%). Including the unchanged English morphology-only
artifact, the current totals are `1,304,256,512` DB bytes and `331,605,223` pack bytes. Exact label
coverage, whitelist rationale, and source-attested QA rows are in
[usage-labels.md](usage-labels.md). The API 37 Test AVD validated real `sehen` transitivity and
`Wissenschaft` countability from the bundled German pack; first exact lookup was `17ms`, the
existing morphology-plus-lemma check was `13ms`, and the resulting debug APK was `339,955,336`
bytes.

### Task 17.1 English precision audit

The reviewed raw source showed that `be` is a lexical verb entry whose lowercase `is` form is tagged
present/singular/third-person. The competing headword `I` is a noun entry whose title-cased `Is`
form is tagged plural; every sense is an abbreviation/alternate form. The original policy inspected
POS and form tags but not sense metadata, so lowercasing the exact key merged both relations.

The corrected converter rejects an English lemma only when all senses are explicitly non-lexical or
non-canonical. It removed 153,441 non-canonical relations, 9,126 non-lexical relations, and seven
technical `form only` rows. Ambiguous surfaces fell from 3,058 to 630, while valid ambiguity such as
`axes → axe/axis`, `alumni → alum/alumna/alumnus`, and `better → good/well` remains. The regenerated
DB has `PRAGMA user_version=3`, `quick_check=ok`, SHA-256
`46647d48363dd2bdf92ba685cd072b1b26544ed29ea663c04387313d9449aa1c`. Its pack manifest remains
schema 3 and declares the same payload checksum; the `.dictpack` SHA-256 is
`4d55f17056d419c582b00ec35c76ae6b79a0d99a981edd8b66f9335d0b1b7fdf`.

Candidate order is exact original case, then source entry/form order. After distinct-lemma filtering,
the first candidate is shown as the primary analysis and the rest as alternates; the safety cap is
five and explicit when truncated. Full rationale and before/after ambiguity metrics are in
[morphology-search.md](morphology-search.md) and [ADR-0017](decisions/0017-morphology-lemma-eligibility-and-ambiguity-ranking.md).

### Cross-language case normalization audit

A 2026-08-30 read-only audit grouped all 12 production reverse indexes by `normalized_form` and
counted distinct source spellings. `vi` and `th` have no morphology rows by policy. Counts and the
interpretation of case-only lexical pairs are documented in [morphology-search.md](morphology-search.md).

The audit found one normalization defect: ROOT lowercase mapped Turkish `İ` to `i + U+0307` and
`I` to `i`. This affected 171 indexed relations, including `İngilizceleştirir →
İngilizceleştirmek`, which a normal lowercase `ingilizceleştirir` query could not reach. Turkish
case mapping now produces `I → ı`, `İ → i`, `i → i`, `ı → ı`. Only `tr.db` and its pack were
regenerated; schema v3 and all 386,244 relation rows remain unchanged. The corrected payload
SHA-256 is `e7eca10d9986278642b7fb11ad3dae3aee78c33acf020b542742031e37d04055`.
