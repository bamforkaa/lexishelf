# PanLex Korean fallback dataset

> Task 9부터 생성 DB는 APK asset이 아니라 `panlex/generated/`에서 generic pack으로
> 만듭니다. 현재 root/install 절차는 [dictionary-packs.md](dictionary-packs.md)가 우선합니다.

## 역할과 현재 범위

PanLex는 기존 한국어기초사전이 지원하지 않는 언어에 대한 보조 lexical translation source입니다. 현재 앱은 다음 exact translation pair만 선언합니다.

- `de ↔ ko` — German `deu-000`과 Korean `kor-000`
- `hi ↔ ko` — Hindi `hin-000`과 Korean `kor-000`
- `pl ↔ ko` — Polish `pol-000`과 Korean `kor-000`
- `la ↔ ko` — Latin `lat-000`과 Korean `kor-000`

BCP 47 tag를 PanLex macrolanguage 전체에 느슨하게 연결하지 않습니다. 선택한 PanLex language-variety UID 하나를 명시적으로 고정하며, 다른 dialect/script/variety를 자동 병합하지 않습니다.

## 공식 source와 license

- 프로젝트: [PanLex](https://panlex.org/)
- license: [PanLex data license](https://panlex.org/license)
- data model: [PanLex data model](https://dev.panlex.org/data-model/)
- database 구조: [PanLex database design](https://dev.panlex.org/database-design/)
- distance-1 ranking: [PanLex translation evaluation](https://dev.panlex.org/translation-evaluation/)

PanLex Database CSV/JSON snapshots는 CC0 1.0 Universal로 공개되어 copy, modification, redistribution이 허용됩니다. 법적 attribution 의무와 별개로 PanLex는 `panlex.org` 또는 2014 LREC paper 인용을 권장합니다. 앱 descriptor와 imported sense provenance에 source, license, snapshot release를 보존합니다.

## 선택한 artifact와 가용성 제한

2026-08-23 조사 시 현재 snapshot 페이지는 download manifest를 제공하지 않았고 과거 공식 distribution host `db.panlex.org`와 공식 API host `api.panlex.org`는 DNS에서 해석되지 않았습니다. 따라서 실시간 API 또는 확인되지 않은 third-party 변환본은 사용하지 않았습니다.

이번 재현에 사용한 파일은 Web Archive가 보존한 공식 PanLex distribution URL의 원본 응답입니다.

| 항목 | 값 |
| --- | --- |
| 원래 공식 URL | `https://db.panlex.org/panlex-20190901-csv.zip` |
| 보존 응답 | [Web Archive 2024-06-14 capture](https://web.archive.org/web/20240614064246id_/https://db.panlex.org/panlex-20190901-csv.zip) |
| release | `2019-09-01` |
| ZIP bytes | `1,272,778,403` |
| uncompressed bytes | `5,431,386,329` |
| SHA-256 | `e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be` |
| SHA-1 Base32 | `6QDQ5UR4NHMTAYXTQT4R3YGDVDENVPDJ` |
| embedded license | CC0 1.0 Universal |

SHA-1 Base32 값은 Web Archive CDX가 해당 capture에 기록한 digest와 일치하고 ZIP integrity test도 통과했습니다. 하지만 이 release는 오래되었습니다. 새 공식 distribution이 복구되면 같은 것이라고 가정하지 말고 source, license, release, checksum, schema와 coverage를 다시 검토해야 합니다.

## API와 snapshot 비교

| 기준 | 현재 PanLex API | 검증한 CSV snapshot + filtered index |
| --- | --- | --- |
| endpoint 상태 | 과거 official client는 `api.panlex.org/v2`를 가리키지만 2026-08-23 host DNS unavailable | archived official response를 checksum으로 재현 가능 |
| key/auth/rate limit | 현재 endpoint/documentation을 사용할 수 없어 확인 불가; 추측하지 않음 | 없음 |
| offline | 불가 | 가능 |
| Korean reverse lookup | endpoint가 unavailable해 검증 불가 | Korean exact index를 명시적으로 생성 |
| APK/data size | local asset 없음, network 필요 | full source 제외; 53,211,136-byte filtered SQLite |
| preprocessing | server 의존 | 개발 시 약 5.43GB CSV를 한 번 처리 |
| exact lookup | 현재 측정 불가 | indexed bidirectional exact query |
| reproducibility | live service 상태와 response에 의존 | release/checksum/converter/mapping 고정 |
| update | service 측 정책을 현재 확인할 수 없음 | 새 official release마다 명시적 검토·변환·rollback |
| source/license preservation | current response를 확인할 수 없음 | source expression/meaning/source IDs, release와 CC0 metadata 보존 |

따라서 local-first와 검증 가능성을 우선해 snapshot 전략을 선택했습니다. 이는 API가 영구적으로 부적합하다는 결론이 아니라 현재 endpoint가 실제 구현/검증에 사용할 수 없다는 시점 결정입니다.

## 생성 방법

원본 ZIP을 `<dataset-root>/panlex/source/panlex-20190901-csv.zip`에 준비한 뒤 Python 3 표준 라이브러리 변환기와 pack builder를 실행합니다.

```powershell
python -X utf8 -m tools.build_panlex_index --languages de,hi,pl,la
python -m tools.build_dictionary_packs --pack panlex
```

변환기는 다음을 실패 조건으로 검사합니다.

- `panlex-YYYYMMDD-csv` root와 필수 CSV table
- embedded `LICENSE.txt`의 검토된 CC0 grant
- 코드에서 명시적으로 review한 BCP 47 → PanLex UID mapping
- 정수 ID와 source quality/group field의 형식
- 최종 DB 생성 후 임시 파일을 닫고 atomic replace

원본 ZIP, stage DB와 생성 DB는 Git에 포함하지 않습니다. Gradle이나 앱은 데이터를 자동 download하지 않습니다. pack이 없으면 build와 수동 단어 저장은 정상이고 PanLex suggestion group만 `LocalDatasetUnavailable`입니다.

## 직접 관계 정책과 ranking

인덱스 row는 같은 PanLex `meaning`에 Korean expression과 선택 language-variety expression이 함께 denotation된 distance-1 관계만 사용합니다. 한 meaning은 한 PanLex source에 속하므로 representative `meaning_id`와 `source_id`를 provenance에 남깁니다.

다음을 만들지 않습니다.

- English 또는 다른 언어를 거치는 pivot translation
- graph distance 2 이상의 inferred relation
- morphology, fuzzy match, generated synonym
- part of speech, definition, example처럼 이 인덱스가 제공하지 않는 field

동일 expression pair에 여러 source attestation이 있으면 source group마다 최대 quality를 구하고 그 값을 합산하는 PanLex `tr1q` distance-1 방식으로 점수를 계산합니다. quality, group count와 source attestation count가 같을 때만 text/ID로 deterministic 정렬합니다.

검색 key는 NFC, trim, 연속 whitespace collapse, locale-independent lowercase만 적용합니다. 번역 표기는 원본 expression text를 보존합니다. 형태 변화나 발음 구별 기호를 제거하지 않습니다.

## 실제 생성 결과

선택한 네 언어의 표현 쌍 중복을 집계한 최종 index 결과입니다.

| 언어 | raw direct attestation rows | unique expression relations | foreign expressions | Korean expressions |
| --- | ---: | ---: | ---: | ---: |
| de | 221,839 | 183,477 | 84,210 | 76,454 |
| hi | 33,896 | 23,266 | 14,897 | 16,275 |
| pl | 91,978 | 68,870 | 38,672 | 46,825 |
| la | 43,003 | 31,917 | 14,800 | 17,815 |
| 합계 | 390,716 | 307,530 | — | — |

- 생성 SQLite: `53,211,136` bytes
- 네 언어 raw direct relation의 논리적 UTF-8 TSV 크기 합계: `19,339,946` bytes (header/file-system overhead 제외 측정치)
- schema version: `1` (`PRAGMA user_version`)
- PC read-only indexed exact lookup 500회: median `0.060ms`, p95 `0.112ms`, max `0.376ms`
- `PRAGMA integrity_check`: `ok`

수치는 app user Room/backup schema version과 독립적입니다. PC latency는 Android 첫 asset copy 시간이나 기기 latency를 의미하지 않습니다.

## 수동 QA exact samples

아래 40개 pair는 생성된 최종 DB에서 exact relation 존재를 검증했습니다. 첫 후보 순서를 보장하는 표가 아니라 source→Korean direct relation 존재 확인용입니다.

| 언어 | 실제 exact pairs 10개 |
| --- | --- |
| de | `lernen → 배우다`; `Wasser → 물`; `Buch → 책`; `Schule → 학교`; `Liebe → 사랑`; `essen → 먹다`; `trinken → 마시다`; `gehen → 가다`; `Haus → 집`; `Hund → 개` |
| hi | `पानी → 물`; `घर → 집`; `किताब → 책`; `प्यार → 사랑`; `कुत्ता → 개`; `स्कूल → 학교`; `खाना → 먹다`; `पीना → 마시다`; `आदमी → 사람`; `बच्चा → 아이` |
| pl | `dom → 집`; `woda → 물`; `książka → 책`; `szkoła → 학교`; `miłość → 사랑`; `pies → 개`; `kot → 고양이`; `jeść → 먹다`; `pić → 마시다`; `człowiek → 사람` |
| la | `aqua → 물`; `domus → 집`; `liber → 책`; `schola → 학교`; `amor → 사랑`; `canis → 개`; `felis → 고양이`; `edere → 먹다`; `bibere → 마시다`; `homo → 사람` |

## 기존 한국어기초사전 언어의 측정 결과

PanLex를 기존 11개 언어의 중복 provider로 등록할지 판단하기 위해 같은 reviewed representative variety로 실제 snapshot coverage를 측정했습니다. 아래 값은 unique pair 집계 전의 direct attestation row 수이며 이번 APK asset에는 포함되지 않습니다.

| BCP 47 | PanLex UID | direct rows | distinct foreign expressions | distinct Korean expressions |
| --- | --- | ---: | ---: | ---: |
| en | `eng-000` | 364,224 | 112,139 | 150,198 |
| ja | `jpn-000` | 129,683 | 57,100 | 51,082 |
| zh | `cmn-000` | 134,552 | 55,156 | 50,478 |
| fr | `fra-000` | 172,981 | 64,157 | 70,798 |
| es | `spa-000` | 140,468 | 54,840 | 61,836 |
| ru | `rus-000` | 212,260 | 90,422 | 75,728 |
| ar | `arb-000` | 62,999 | 30,731 | 29,501 |
| mn | `khk-000` | 12,831 | 6,035 | 6,804 |
| vi | `vie-000` | 44,045 | 19,958 | 18,833 |
| th | `tha-000` | 115,721 | 46,817 | 43,449 |
| id | `ind-000` | 64,199 | 25,002 | 37,231 |

커버리지가 존재한다는 사실만으로 더 정확하거나 현재 release가 더 최신이라는 뜻은 아닙니다. 기존 공식 한국어기초사전 provider가 이미 이 pair들을 지원하고 PanLex snapshot은 2019년 자료이므로 이번 초기 asset에서는 제외했습니다.

## runtime과 update/rollback

설정 화면에서 generic `.dictpack`을 설치하면 production pack validator가 검증한 SQLite를 `noBackupFilesDir/dictionary-packs/panlex/panlex.ko-fallback` 아래에서 read-only로 엽니다. schema, release와 선택 언어 metadata가 다르면 fallback data를 사용하지 않고 provider-local malformed error를 반환합니다. dictionary DB는 사용자 Room에 import되지 않으며 JSON backup에도 dataset 전체가 들어가지 않습니다.

새 release update 절차:

1. 공식 artifact와 license/distribution 상태를 다시 확인한다.
2. 새 release의 checksum과 보관 가능한 official URL을 기록한다.
3. 필요한 language-variety UID를 각 언어별로 다시 review한다.
4. converter fixture와 full conversion을 실행한다.
5. coverage, QA sample, ranking, DB/APK 크기와 Android latency를 비교한다.
6. `PANLEX_RELEASE_ID`, count, docs와 pack manifest metadata를 함께 갱신한다.
7. old/new pack을 별도로 검증한 뒤 새 파일만 배포한다. 실패하면 기존 active version을 유지한다.

provider refresh는 저장된 vocabulary를 자동 갱신하지 않습니다. 사용자가 명시적으로 누른 suggestion row만 기존 generic provenance pipeline을 통해 user-owned editable sense가 되며 이후 provider dataset update와 lifecycle이 분리됩니다.
