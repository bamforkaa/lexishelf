# PanLex Korean lexical fallback dataset

PanLex는 한국어기초사전이 지원하지 않는 언어를 보완하는 오프라인 lexical candidate 공급자다. 저장소에는 원본·생성 DB·팩을 커밋하지 않으며, configured dataset root의 `panlex/{source,generated,packs}`를 사용한다.

## 고정 source와 라이선스 경계

현재 구현은 최신 PanLex 배포판이 아니라 체크섬으로 고정한 2019-09-01 공식 CSV snapshot만 사용한다.

| 항목 | 값 |
| --- | --- |
| 원래 공식 URL | `https://db.panlex.org/panlex-20190901-csv.zip` |
| 보존 응답 | [Web Archive 2024-06-14 capture](https://web.archive.org/web/20240614064246id_/https://db.panlex.org/panlex-20190901-csv.zip) |
| canonical 파일 | `panlex/source/panlex-20190901-csv.zip` |
| release | `2019-09-01` |
| ZIP bytes | `1,272,778,403` |
| uncompressed bytes | `5,431,386,329` |
| SHA-256 | `e7a53a3851ce4cecd8e60497a2e0d0c3285cdfb67d4ce65da466beee315bb3be` |
| SHA-1 Base32 | `6QDQ5UR4NHMTAYXTQT4R3YGDVDENVPDJ` |
| ZIP integrity | `testzip(): None` |
| artifact 내장 라이선스 | CC0 1.0 Universal |

2026-08-26에 복원한 ZIP은 위 크기와 SHA-256이 일치했고 모든 member의 CRC 검사도 통과했다. ZIP 안의 `LICENSE.txt`는 해당 snapshot을 CC0 1.0 Universal로 제공하며 copy, modification, redistribution과 commercial use를 허용한다고 명시한다.

중요하게도 현재 [PanLex 공식 license page](https://panlex.org/license/)의 조건만으로 과거 artifact의 권리를 추론하지 않는다. 이 앱의 `CC0-1.0` metadata는 체크섬이 일치하고 내장 `LICENSE.txt`를 직접 확인한 2019-09-01 artifact에만 적용한다. 새 snapshot이나 다른 artifact가 같은 조건이라고 추측하지 않으며, 교체 전 source·artifact 내장 license·checksum을 다시 검토해야 한다.

## 지원 pair와 variety mapping

최종 범위는 기존 4개와 신규 8개를 합한 12개 외국어다. UI/domain에는 canonical BCP 47 tag만 노출하고 PanLex variety UID는 converter 내부 metadata로 유지한다.

| 상태 | BCP 47 | PanLex variety | exact pair |
| --- | --- | --- | --- |
| 기존 | `de` | `deu-000` | `de ↔ ko` |
| 기존 | `hi` | `hin-000` | `hi ↔ ko` |
| 기존 | `pl` | `pol-000` | `pl ↔ ko` |
| 기존 | `la` | `lat-000` | `la ↔ ko` |
| 신규 | `nl` | `nld-000` | `nl ↔ ko` |
| 신규 | `pt` | `por-000` | `pt ↔ ko` |
| 신규 | `it` | `ita-000` | `it ↔ ko` |
| 신규 | `tr` | `tur-000` | `tr ↔ ko` |
| 신규 | `cs` | `ces-000` | `cs ↔ ko` |
| 신규 | `sv` | `swe-000` | `sv ↔ ko` |
| 신규 | `fi` | `fin-000` | `fi ↔ ko` |
| 신규 | `uk` | `ukr-000` | `uk ↔ ko` |
| 공통 target/source | `ko` | `kor-000` | 위 12개와 양방향 |

`nb`는 일반 `no`를 뭉뚱그리지 않고 Bokmål의 canonical BCP 47 `nb`와 `nob-000`을 분석했다. Persian은 `fa`를 `pes-000`, Malay는 `ms`를 Standard Malay `zsm-000`, Filipino는 `fil-000`, Swahili는 `swh-000`으로 명시적으로 검토했다. 이들은 이번 선택에는 포함하지 않았다.

build-time source of truth는 `tools/panlex_language_config.py`다. converter와 pack builder가 같은 list/pair를 import하며 `build_dictionary_packs.py`에 별도 언어 목록을 두지 않는다. Android runtime mirror는 같은 순서를 사용하고, DB의 `supported_language_tags`가 다르면 dataset을 malformed로 거부하는 회귀 테스트로 경계를 확인한다.

## 후보 coverage 측정

측정값은 `kor-000`과 각 대표 variety가 같은 source-owned PanLex meaning에 함께 denotation된 distance-1 관계를 중복 expression pair 단위로 합친 결과다. 영어 pivot, graph traversal, 형태 추론, MT는 포함하지 않는다.

`DB 기여 추정`은 43개 언어 분석 DB(`385,433,600` bytes)에서 언어별 UTF-8 relation payload 비율로 전체 SQLite 크기를 배분한 값이다. index/page overhead 때문에 정확한 독립 DB 크기는 아니지만, 기존 4개 추정 합계가 실제 기존 DB와 근접해 선택 비교에는 충분하다.

| BCP 47 | variety | direct pairs | foreign expressions | Korean expressions | DB 기여 추정 |
| --- | --- | ---: | ---: | ---: | ---: |
| `nl` | `nld-000` | 101,117 | 47,981 | 52,205 | 16.2 MiB |
| `pt` | `por-000` | 93,838 | 46,977 | 53,437 | 15.4 MiB |
| `it` | `ita-000` | 115,217 | 54,693 | 62,814 | 18.8 MiB |
| `tr` | `tur-000` | 109,852 | 46,777 | 52,398 | 18.3 MiB |
| `cs` | `ces-000` | 108,866 | 50,558 | 59,972 | 17.8 MiB |
| `sv` | `swe-000` | 79,516 | 41,404 | 50,626 | 12.7 MiB |
| `nb` | `nob-000` | 60,010 | 32,509 | 40,621 | 9.6 MiB |
| `da` | `dan-000` | 45,703 | 26,350 | 33,730 | 7.3 MiB |
| `fi` | `fin-000` | 96,496 | 47,551 | 54,216 | 15.7 MiB |
| `el` | `ell-000` | 57,524 | 29,782 | 36,953 | 11.0 MiB |
| `he` | `heb-000` | 34,996 | 23,076 | 27,706 | 6.3 MiB |
| `fa` | `pes-000` | 31,006 | 17,862 | 21,828 | 5.3 MiB |
| `uk` | `ukr-000` | 86,326 | 41,433 | 45,614 | 16.2 MiB |
| `ro` | `ron-000` | 49,927 | 27,231 | 33,924 | 8.0 MiB |
| `hu` | `hun-000` | 84,291 | 41,607 | 45,999 | 14.1 MiB |
| `bg` | `bul-000` | 49,445 | 27,943 | 31,280 | 9.3 MiB |
| `sr` | `srp-000` | 18,422 | 13,511 | 14,683 | 3.5 MiB |
| `hr` | `hrv-000` | 64,633 | 32,370 | 36,171 | 10.5 MiB |
| `sk` | `slk-000` | 81,072 | 36,776 | 41,720 | 13.1 MiB |
| `sl` | `slv-000` | 31,109 | 19,037 | 22,567 | 5.1 MiB |
| `ms` | `zsm-000` | 18,490 | 13,161 | 12,941 | 2.9 MiB |
| `fil` | `fil-000` | 4,205 | 3,598 | 3,676 | 0.7 MiB |
| `sw` | `swh-000` | 14,720 | 8,349 | 10,992 | 2.3 MiB |
| `ca` | `cat-000` | 59,843 | 30,663 | 42,077 | 9.5 MiB |
| `et` | `ekk-000` | 31,992 | 20,434 | 24,338 | 5.2 MiB |
| `lv` | `lvs-000` | 25,507 | 16,578 | 19,847 | 4.2 MiB |
| `lt` | `lit-000` | 36,926 | 22,418 | 26,624 | 6.2 MiB |
| `is` | `isl-000` | 41,944 | 20,520 | 25,880 | 6.6 MiB |
| `ka` | `kat-000` | 27,933 | 14,160 | 19,693 | 5.8 MiB |
| `hy` | `hye-000` | 59,807 | 24,666 | 31,726 | 11.2 MiB |
| `ur` | `urd-000` | 14,063 | 9,067 | 10,578 | 2.4 MiB |
| `bn` | `ben-000` | 15,563 | 9,764 | 12,668 | 3.2 MiB |
| `ta` | `tam-000` | 11,574 | 8,902 | 9,266 | 2.6 MiB |
| `te` | `tel-000` | 14,182 | 8,185 | 10,092 | 2.8 MiB |
| `eu` | `eus-000` | 31,083 | 15,810 | 22,941 | 4.9 MiB |
| `gl` | `glg-000` | 32,657 | 17,109 | 23,982 | 5.2 MiB |
| `af` | `afr-000` | 21,865 | 11,053 | 16,577 | 3.4 MiB |
| `kk` | `kaz-000` | 10,718 | 7,663 | 7,789 | 1.9 MiB |
| `uz` | `uzn-000` | 8,614 | 6,402 | 6,625 | 1.3 MiB |

선정한 신규 8개는 모두 한국어기초사전의 기존 외국어 목록 밖이고, 각 언어가 최소 79,516개 direct pair를 가진다. 총 12개 언어로 범위를 제한해 하나의 multilingual pack과 현재 provider 구조를 유지했다.

제외한 notable 후보 중 `hu`, `sk`, `nb`, `hr`, `ca`, `hy`, `el`은 coverage가 유의미하지만 초기 12개 상한과 pack 크기 때문에 다음 확장 후보로 남겼다. `he`, `fa`, `ms`, `fil`, `sw`, `ur`, `bn`, `ta`, `te`, `kk`, `uz` 등은 이번 선택 언어보다 coverage가 낮다. Korean Basic Dictionary가 이미 지원하는 `en/ja/fr/es/ar/mn/vi/th/id/ru/zh`는 PanLex 중복 provider로 우선 추가하지 않았다.

## 생성과 결과

canonical source를 준비한 뒤 다음 명령을 실행한다. 기본 언어 목록은 shared config에서 읽으므로 production build에 `--languages`를 반복해 쓰지 않는다.

```powershell
python -X utf8 -m tools.build_panlex_index
python -X utf8 -m tools.build_dictionary_packs --pack panlex
```

`--languages`는 후보 분석이나 명시적 실험용으로 남아 있지만, 그 결과를 production pack으로 사용하려면 shared config, runtime descriptor와 tests를 함께 변경해야 한다.

| 지표 | 기존 4개 | 현재 12개 | 변화 |
| --- | ---: | ---: | ---: |
| unique direct pairs | 307,530 | 1,098,758 | +791,228 |
| generated SQLite | 53,211,136 B | 189,714,432 B | +136,503,296 B |
| compressed `.dictpack` | 21,044,212 B | 72,462,031 B | +51,417,819 B |
| debug APK | 149,942,392 B | 201,374,823 B | +51,432,431 B |
| converter wall time | 과거 미기록 | 502.454 s | 동일 5.43GB source 처리 |

SQLite `PRAGMA integrity_check`는 `ok`, pack ZIP CRC 검사는 성공했다. payload SHA-256은 `10b780bc4f05d0d6d82d8772fc57364b469dd59991699d8fe01ea54de13d8280`이다. 12개 언어에서 외국어→한국어와 한국어→외국어를 각각 50개씩, 총 1,200개 warm read-only exact query로 측정한 PC latency는 median `0.0248ms`, p95 `0.0411ms`, max `0.5894ms`였다. API 37 test AVD에서 72.5MB pack을 production validation/install/activation 경로로 설치하는 데 `4,906ms`, 새 `PanLexDataSource`의 첫 `nl → ko` query에 `15ms`가 걸렸다.

pack manifest의 `createdAt`은 build 시각이므로 동일 payload를 다시 압축할 때 archive가 1 byte 정도 달라질 수 있다. 위 pack/APK 값은 최종 계측 직전 파일의 실제 크기이며, 안정성 판단은 archive byte가 아니라 payload size와 SHA-256을 사용한다.

기존 언어 회귀 수치는 바뀌지 않았다.

| 언어 | 기존 pairs | 현재 pairs | 결과 |
| --- | ---: | ---: | --- |
| `de` | 183,477 | 183,477 | 동일 |
| `hi` | 23,266 | 23,266 | 동일 |
| `pl` | 68,870 | 68,870 | 동일 |
| `la` | 31,917 | 31,917 | 동일 |

## 신규 언어 QA exact samples

아래 값은 최종 생성 DB에서 실제로 조회한 top candidate다. 괄호는 `translation quality / source group count / source attestation count`다.

| 언어 | 실제 exact query → Korean top candidate |
| --- | --- |
| `nl` | `water → 물 (69/12/12)`; `huis → 집 (88/15/15)`; `school → 학교 (44/7/7)`; `liefde → 사랑 (30/5/5)`; `eten → 먹다 (30/6/6)` |
| `pt` | `água → 물 (76/13/13)`; `casa → 집 (97/16/16)`; `escola → 학교 (58/9/9)`; `amor → 사랑 (30/5/5)`; `comer → 먹다 (23/5/5)` |
| `it` | `acqua → 물 (83/14/14)`; `casa → 집 (102/17/17)`; `scuola → 학교 (58/9/9)`; `amore → 사랑 (33/5/5)`; `mangiare → 먹다 (42/8/8)` |
| `tr` | `su → 물 (69/12/12)`; `ev → 집 (75/13/13)`; `okul → 학교 (58/9/9)`; `aşk → 사랑 (28/4/4)`; `yemek → 먹다 (28/5/5)` |
| `cs` | `voda → 물 (76/13/13)`; `dům → 집 (82/14/14)`; `škola → 학교 (47/7/7)`; `láska → 사랑 (35/6/6)`; `jíst → 먹다 (38/6/6)` |
| `sv` | `vatten → 물 (69/12/12)`; `hus → 집 (78/13/13)`; `skola → 학교 (51/8/8)`; `kärlek → 사랑 (23/4/4)`; `äta → 먹다 (37/7/7)` |
| `fi` | `vesi → 물 (69/12/12)`; `talo → 집 (92/15/15)`; `koulu → 학교 (58/9/9)`; `rakkaus → 사랑 (35/6/6)`; `syödä → 먹다 (42/8/8)` |
| `uk` | `вода → 물 (62/11/11)`; `дім → 집 (61/10/10)`; `школа → 학교 (34/5/5)`; `любов → 사랑 (21/3/3)`; `їсти → 먹다 (19/3/3)` |

PanLex 결과는 lexical candidate일 뿐 사전 definition, POS, example, 발음이나 문법 정보라고 주장하지 않는다. source quality/group/attestation은 deterministic ranking에만 사용하며 같은 text를 다른 provider와 합치거나 deduplicate하지 않는다.

## runtime과 데이터 수명

pack은 production `DictionaryPackManager`의 manifest/schema/size/SHA-256 검증, atomic install과 activation을 그대로 사용한다. 하나의 PanLex provider가 24개 양방향 language pair를 선언하며, source/result pair가 DB에 있는 경우에만 exact lookup한다. pack이 없으면 suggestion 영역에 `LocalDatasetUnavailable`만 표시되고 수동 입력은 유지된다.

검색 결과는 transient external model이고, 사용자가 suggestion row를 명시적으로 toggle할 때만 generic mapper를 거쳐 editable sense와 provenance가 생성된다. provider refresh는 저장된 사용자 데이터를 덮어쓰지 않는다. 전체 PanLex DB는 Room이나 JSON backup에 들어가지 않는다.

AppLanguageCatalog는 이번 작업에서 변경하지 않았다. Task 10E의 persistent custom BCP 47 catalog가 provider 지원과 독립적이므로, 사용자는 picker에서 신규 언어를 추가해 계속 사용할 수 있다.

현재 72.5MB pack은 개발용 단일 multilingual pack으로 관리 가능한 범위라고 판단했다. 다음 확장에서 크기가 크게 증가하면 provider를 복제하지 않고 language-group sub-pack과 manifest discovery를 별도 설계해야 한다. Kaikki, fuzzy/morphological search, 자동 download/update, provider result synthesis는 이 작업에 포함하지 않는다.
