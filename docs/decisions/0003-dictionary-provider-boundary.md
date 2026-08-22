# ADR-0003: capability 기반 DictionaryProvider와 사용자 데이터 경계

> 후속 결정: provider-derived sense의 영구 provenance와 CC-CEDICT autofill은 [ADR-0005](0005-dictionary-import-provenance.md)를 따른다.

- 상태: Accepted
- 날짜: 2026-08-22

## Context

향후 공급원에는 credential과 network가 필요한 commercial API, 설치·update가 필요한 local dataset, 서로 다른 언어 pair와 linguistic field를 제공하는 사전이 함께 존재한다. 지원 언어의 단순 cross-product, provider별 UI `when`, 모든 field 강제, 검색 결과의 자동 Room 저장은 잘못된 capability 표시와 사용자 편집 덮어쓰기 또는 license 위반을 만들 수 있다.

이 결정 당시 실제 provider는 없었으며 호출·download·persistence보다 확장 경계를 먼저 고정했다. 첫 구현인 CC-CEDICT의 후속 결정은 [ADR-0004](0004-cc-cedict-local-dataset.md)에 기록한다.

## Decision

- Provider ID는 lowercase stable value type이며 registry key와 향후 persistence 값으로 사용한다.
- 지원 언어는 validated BCP 47 source/result와 `MONOLINGUAL_DEFINITION|TRANSLATION`으로 구성된 exact pair다. Script는 필요할 때 별도 field로 표현한다.
- Descriptor는 online/local access, capability set, source attribution, optional dataset metadata, tri-state persistence/redistribution permission, app import mode, field override, cache policy를 제공한다.
- Common result는 provider DTO와 분리하며 linguistic field는 nullable value 또는 empty list다. Provider가 제공하지 않는 field를 만들지 않는다.
- Pagination은 opaque continuation token과 truncation 상태로 표현한다.
- Failure는 언어, credential/auth, rate/network, provider/data/local dataset/no-result/unknown 범주를 구분한다.
- Registry는 Hilt set multibinding으로 provider를 발견하고 stable ID uniqueness와 exact pair filtering을 중앙화한다.
- External result는 persistent `VocabularyEntry`와 별도다. 명시적 user selection과 `DictionaryEntryDraftMapper`를 거쳐 transient editor reference와 user-editable draft를 만든다.
- Persistence permission은 검색 결과 표시가 아니라 provider content를 export 가능한 vocabulary에 복사하는 경계에 적용한다. 현재 backup은 저장된 vocabulary text 전체를 export하므로 app import mode가 `COPY_EXPORTABLE_FIELDS`이고 local persistence와 redistribution이 모두 `PERMITTED`인 field만 복사한다.
- app import mode `REFERENCE_ONLY`, permission `UNKNOWN` 또는 `PROHIBITED`는 원문이 없는 빈 manual draft와 transient result를 가진 `ReferenceOnly`를 반환한다. 사용자는 transient 결과를 참고하되 자신의 headword, meaning, notes, tags를 별도로 작성하고 저장할 수 있다.
- Global usage policy를 기본으로 사용하고 definition, translation, example 등의 field override를 허용한다. Editor seed는 copied field 집합, immutable source reference, transient result를 분리한다. Provider refresh가 existing vocabulary를 갱신하는 repository API는 만들지 않는다.

## Consequences

장점:

- online API와 local dataset이 같은 작은 contract를 구현하면서 availability/failure는 구분할 수 있다.
- Japanese reading, Chinese romanization, gender, audio, inflection 등이 없는 provider도 빈 값으로 자연스럽게 표현된다.
- `zh-Hans`/`zh-Hant`와 definition/translation 언어가 정확한 pair로 유지된다.
- 새 provider는 provider package, DTO/parser/data source, common mapping, `@IntoSet` registration, tests만 추가하면 된다.
- 확인되지 않은 저장 권한을 허용으로 간주하거나 provider result 전체를 자동 저장하기 어렵지만, 검색 결과 표시와 사용자 수동 입력은 막지 않는다.
- 저장된 사용자 데이터와 새 검색 결과 사이에 자동 update 경로가 없다.

비용과 후속 작업:

- 각 실제 provider를 등록할 때 공식 terms에 근거한 descriptor 값과 fixture 재배포 허용 여부를 정해야 한다.
- 현재 source reference와 transient provider content는 Room/backup에 저장되지 않는다. Attribution/source ID의 영구 보존이 계약상 필요하면 provider 원문과 분리된 provenance용 Room migration과 backup schema migration이 필요하다.
- Private backup이 provider terms에서 redistribution에 해당하는지는 provider별 공식 계약으로 확인해야 한다. 확인 전에는 명시적 redistribution permission이 없는 provider content를 export 가능한 draft에 복사하지 않는다.
- Provider별 pagination token, dataset version/update, credential availability 구현은 각 provider package 책임이다.

## Rejected alternatives

- Source/definition 언어를 독립 집합으로 선언: 실제로 지원하지 않는 언어 pair의 cross-product를 암시한다.
- 언어 또는 provider enum을 UI에 하드코딩: 새 provider가 unrelated screen 변경을 요구한다.
- 모든 linguistic field를 필수 문자열로 만들기: 없는 데이터를 가짜 값으로 채우게 된다.
- Provider별 DTO를 presentation까지 전달: provider 변경이 UI와 저장 모델로 전파된다.
- External result를 곧바로 Room entity로 변환: 사용자 선택을 건너뛰고 refresh 덮어쓰기 및 license 제한을 무시할 수 있다.
- 복잡한 provider별 generic hierarchy: 현재 필요한 capability set과 immutable optional model보다 비용이 크다.
