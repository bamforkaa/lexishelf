# ADR-0009: Wordbook 분리와 안전한 editor identity

- 상태: Accepted
- 날짜: 2026-08-25

## Context

Task 10B에서는 기존 Tag를 사용자 컬렉션 UI에도 재사용했다. 그러나 Language는 entry의
BCP 47 metadata이고, Wordbook은 사용자가 만든 컬렉션이며, Tag는 자유 annotation이다.
세 개념을 한 Tag row로 표현하면 삭제·백업·필터 의미가 모호해진다. 또한 suggestion의
선택 표시는 있었지만 해제할 수 없었고, 같은 언어와 표기의 단어를 실수로 다시 저장할 수
있었다.

## Decision

- Room v5에 최소 `wordbooks`와 `entry_wordbook_cross_refs`를 추가한다. Tag schema와
  기존 사용자 Tag는 변경하거나 자동 분류하지 않는다.
- Language filter는 `VocabularyEntry.languageTag`를 직접 사용하며 중앙 resolver는 표시만
  담당한다.
- backup v4는 Wordbook stable ID와 관계를 추가하고 v1–v3은 empty Wordbook으로 올린다.
- suggestion 선택은 provider/source entry/source sense를 포함하는 stable suggestion key로
  transient contribution을 추적한다. 다시 누르면 해당 key가 추가한 미수정 content만
  제거한다. 수정된 content는 보존하고 provenance 연결만 제거한다.
- 새 entry의 중복 후보는 canonical language와 headword의 trim, 연속 공백 축약, Unicode
  NFC, `Locale.ROOT` lowercase identity로 찾는다. fuzzy matching이나 UNIQUE constraint는
  사용하지 않는다. 사용자가 명시적으로 별도 저장할 수 있다.

## Consequences

- 하나의 단어는 여러 Wordbook과 여러 Tag에 독립적으로 속할 수 있다.
- Wordbook/Tag 삭제는 자신의 cross-reference만 cascade하며 vocabulary aggregate를
  삭제하지 않는다.
- 기존 language-like Tag와 과거 중복 entry는 그대로 유효하다.
- imported content를 수정한 뒤 deselect하면 사용자 text를 잃지 않는 대신 provenance가
  제거되어 이후에는 user-owned content로 취급된다.
- 자동 merge는 의미 손실 위험 때문에 이번 결정에 포함하지 않는다.
