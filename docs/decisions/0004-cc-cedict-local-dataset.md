# ADR-0004: CC-CEDICT raw GZip asset과 reference-only import

- 상태: Superseded by ADR-0005 and ADR-0009
- 날짜: 2026-08-23
- 후속 변경: reference-only import 결정은 [ADR-0005](0005-dictionary-import-provenance.md)에서 provenance 기반 autofill로, Android asset 전달 방식은 [ADR-0009](0009-installable-dictionary-packs.md)에서 canonical development root와 installable pack으로 대체됨

## Context

첫 실제 provider는 CC-CEDICT의 중국어→영어 local lookup이다. dataset은 user vocabulary와 다른 update/lifecycle을 가져야 하며, source는 Traditional/Simplified headword, numbered pinyin과 English definitions를 제공한다. 공식 download page는 권장 release를 MDBG에서 받도록 안내하고 두 페이지는 선택한 release를 CC BY-SA 4.0으로 표시한다. MDBG는 automated/scripted access를 금지한다.

두 integration 전략을 검토했다.

| 기준 | A: raw GZip asset + runtime index | B: build-time SQLite/compact index |
| --- | --- | --- |
| APK 크기 | GZip artifact 크기만큼 증가 | index 구조와 packaging에 따라 원본보다 커질 수 있음 |
| 첫 사용 비용 | 첫 검색에서 full parse/index 필요 | asset open 후 즉시 query 가능 |
| 구현/테스트 | parser와 in-memory index만 필요 | 변환기, dictionary schema, reproducible output 검증 필요 |
| 업데이트 | GZip 교체와 metadata 갱신 | 매 release마다 변환 artifact 재생성 필요 |
| language pack 전환 | dataset reader 교체 가능 | SQLite pack reader로도 교체 가능 |
| user Room 격리 | 완전 분리 | 별도 read-only DB를 엄격히 유지해야 함 |
| 재현성 | exact source artifact를 별도로 확보해야 함 | artifact와 변환 toolchain을 모두 고정해야 함 |

현재 개인용 MVP에서는 B의 first-query 이점보다 별도 schema/변환기 유지 비용이 크다. 전체 release를 이 환경에서 합법적인 방식으로 자동 취득할 수도 없다.

CC BY-SA 4.0은 조건을 지키는 복사·재배포를 허용하지만 attribution과 ShareAlike 의무가 있다. 현재 Room/backup schema는 provider provenance와 파생 콘텐츠 license를 보존하지 않는다. 원문 definition을 editable vocabulary에 복사하면 JSON backup에서 출처와 조건이 분리된다.

## Decision

- 당시 Strategy A를 사용하여 공식 `cedict_1_0_ts_utf-8_mdbg.txt.gz`를 Android asset으로 전달했다. 현재는 `<dataset-root>/cc-cedict/source/`의 같은 GZip을 `.dictpack`으로 만들고 active pack에서 읽으며 parser/index 전략만 유지한다.
- dataset binary는 자동 다운로드하지 않고 저장소에도 포함하지 않는다. descriptor는 검증한 release timestamp, entry count, artifact, format, source와 license를 보존한다. 파일이 없으면 provider는 `LocalDatasetUnavailable`을 반환한다.
- parser와 provider-specific record/index는 `dictionary.provider.cccedict` 밖으로 노출하지 않는다. 공통 경계에는 `ExternalDictionaryEntry`만 전달한다.
- CC-CEDICT legal permission은 local persistence와 redistribution `PERMITTED`로 표현하되 `DictionaryVocabularyImportMode.REFERENCE_ONLY`를 사용한다. 이는 라이선스가 저장을 금지한다는 뜻이 아니라 현재 app schema가 의무를 안전하게 전달할 준비가 안 됐다는 뜻이다.
- 선택한 검색 결과는 transient editor reference로만 전달한다. manual draft에는 source language만 들어가며 사용자가 직접 작성한 headword, meaning, examples, notes, tags만 Room/backup에 저장한다.
- Room schema 2와 backup schema 1은 변경하지 않는다.
- attribution, source, license, artifact/release는 Settings에서 볼 수 있다.

## Consequences

장점:

- user database와 dictionary dataset update가 완전히 독립적이다.
- parser/index를 JVM fixture로 테스트할 수 있고 build-time toolchain이 추가되지 않는다.
- `CcCedictDatasetSource`를 language-pack reader로 바꾸어도 parser/provider/UI는 유지된다.
- provider refresh가 저장된 user entry를 갱신하는 API가 없다.

비용과 제한:

- full GZip을 넣은 APK는 그 파일 크기만큼 커지고 매 process의 첫 검색에 parse 시간과 메모리가 든다.
- binary가 없는 clean clone에서는 provider UI와 contract는 동작하지만 실제 lookup은 unavailable이다. 사용자는 공식 page에서 수동으로 artifact를 준비해야 한다.
- provider reference는 process memory에만 있으므로 저장 전 process death 시 사라진다.
- CC definition import를 나중에 허용하려면 Room provenance, backup schema migration, UI attribution, derived-content/ShareAlike 전달 규칙을 함께 설계해야 한다.

## Rejected alternatives

- build-time SQLite 생성: 현재 exact lookup만을 위해 별도 generator/schema/release artifact를 유지하는 비용이 과도하다.
- CC 원문을 기존 vocabulary text에 바로 복사: attribution과 ShareAlike provenance가 Room/backup에서 소실된다.
- CC policy를 `PROHIBITED`로 표시: 실제 CC BY-SA 권한을 잘못 표현한다.
- MDBG artifact 자동 다운로드: release page의 automated/scripted access 금지와 충돌한다.
