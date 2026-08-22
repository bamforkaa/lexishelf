# ADR-0005: Sense 단위 dictionary import provenance

- 상태: Accepted
- 날짜: 2026-08-23
- 관련 결정: [ADR-0003](0003-dictionary-provider-boundary.md), [ADR-0004](0004-cc-cedict-local-dataset.md)

## Context

외부 검색 결과와 사용자가 소유한 `VocabularyEntry`는 서로 다른 모델과 생명주기를 가진다. 그러나 CC-CEDICT의 English gloss를 사용자가 명시적으로 선택해 editable vocabulary에 복사하려면 CC BY-SA 4.0 attribution과 수정 사실이 Room 저장 및 JSON export 후에도 사라지지 않아야 한다.

한 entry 안에는 provider-derived sense와 user-authored sense가 함께 있을 수 있다. entry 하나에 provenance 하나만 두면 어떤 meaning/example이 외부 자료에서 왔는지 표현할 수 없다. 반대로 모든 문자열을 일반적인 field-value provenance table로 분해하면 현재 MVP의 aggregate 저장과 UI가 지나치게 복잡해진다.

## Decision

- provenance granularity는 `VocabularySense`로 정한다. user-authored sense는 provenance가 null이고 imported sense만 `DictionaryProvenance`를 가진다.
- provenance에는 stable provider ID, optional source entry/sense ID, source name/URL, license name/URL, optional dataset version, imported field set, import timestamp, modified flag를 저장한다. provider-specific column은 만들지 않는다.
- `importedFields`는 현재 sense aggregate가 실제 보유하는 `MEANING`, `PART_OF_SPEECH`, `EXAMPLES`만 표현한다. source가 제공하지 않거나 policy가 허용하지 않은 field는 복사하지 않는다.
- Room schema 3은 `sense_dictionary_provenance`와 `sense_dictionary_provenance_fields`를 별도 FK/cascade table로 추가한다. `MIGRATION_2_3`은 기존 row를 수정하지 않으므로 기존 sense는 모두 user-authored semantics를 유지한다.
- backup schema 2는 동일 provenance를 sense에 포함한다. v1 DTO와 의미는 변경하지 않고 v1 decoder는 provenance null인 v2 import model로 올린다.
- 검색 결과 도착은 form을 바꾸지 않는다. explicit `Use`만 허용된 sense를 추가하고, 유일한 빈 placeholder만 대체할 수 있다. 이미 작성되거나 저장된 sense는 유지한다. 같은 provider/source entry/source sense는 반복 추가하지 않는다.
- imported sense를 수정하면 content는 자유롭게 바뀌지만 provenance를 삭제하지 않고 `modifiedAfterImport`를 true로 설정한다. provider refresh가 저장된 content를 갱신하는 경로는 만들지 않는다.
- attribution UI는 provider name과 short license를 suggestion에, provider 기반/수정 여부를 저장된 sense에 generic하게 표시한다. 상세 source/license 링크와 notice는 Settings의 source metadata가 담당한다.
- CC-CEDICT pinyin은 common external model의 transient reading으로만 둔다. vocabulary에 Japanese kana, Chinese pinyin, IPA를 함께 수용할 persistent reading model이 아직 없으므로 notes/POS에 우회 저장하거나 이번 변경에서 별도 schema를 추가하지 않는다.
- CC-CEDICT에는 structured POS가 없으므로 POS를 추론하지 않는다. future provider가 structured POS/examples를 제공하고 policy가 허용하면 같은 imported field/provenance pipeline을 사용한다.

## Consequences

장점:

- 한 entry 안에서 user-authored와 provider-derived sense를 명확히 섞을 수 있다.
- CC-CEDICT 전용 DB 컬럼 없이 Korean Basic Dictionary, JMdict, Kaikki 같은 향후 provider가 같은 mapping/Room/backup 경계를 재사용할 수 있다.
- attribution, license, dataset version과 수정 사실이 restore 후에도 유지된다.
- 기존 Room v2 및 backup v1 데이터의 의미와 내용이 바뀌지 않는다.

제한:

- 개별 example 문자열마다 서로 다른 provider provenance를 표현하지 않는다. 현재 import는 한 external sense와 그 허용된 fields를 하나의 vocabulary sense로 추가하므로 이보다 세밀한 구조가 필요하지 않다.
- imported field를 사용자가 완전히 다시 작성해도 provenance는 자동 제거되지 않는다. 자동 ownership 판정 대신 보수적으로 source와 수정 사실을 유지한다.
- pinyin/reading 영구 저장은 generic multilingual reading model을 별도 결정하기 전까지 지원하지 않는다.

## Rejected alternatives

- entry 단위 provenance: 한 entry 안의 user/provider 혼합 sense를 구분할 수 없다.
- presentation-only flag: process 종료, Room round trip, backup/restore에서 출처가 소실된다.
- 임의의 field-value provenance graph: 현재 요구보다 복잡하고 aggregate 편집 책임을 흐린다.
- pinyin을 notes나 POS에 복사: 사용자 작성 데이터와 linguistic field의 의미를 훼손한다.
- provider definition을 provenance 없이 저장: attribution/ShareAlike 정보를 export에서 잃는다.
