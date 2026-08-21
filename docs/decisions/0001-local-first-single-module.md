# ADR-0001: 단일 모듈 local-first aggregate 구조

- 상태: Accepted
- 날짜: 2026-08-21

## Context

첫 마일스톤은 계정과 서버 없이 수동 입력 단어 CRUD, 여러 뜻/예문, 태그, 검색을 오프라인으로 제공해야 한다. 미래에는 서로 다른 언어 능력과 라이선스를 가진 사전 공급자를 추가할 수 있지만, 외부 결과가 사용자 편집 데이터를 지배해서는 안 된다. 초기 저장소에는 Android 코드가 없었으므로 작게 시작하면서도 이후 기능 모듈 추출점을 유지해야 한다.

## Decision

하나의 `app` 모듈에서 시작하고 package-by-feature presentation과 명시적인 domain/data/database/dictionary 경계를 사용한다.

사용자 단어는 `VocabularyEntry` aggregate로 취급한다. Room에서는 entry, sense, example, tag, cross-reference를 정규화하고 한 transaction으로 저장한다. 외부 사전 결과는 별도 `ExternalDictionaryEntry` 모델과 `DictionaryProvider` 계약으로만 설계하며 이번 마일스톤에는 구현체를 등록하지 않는다.

ViewModel은 repository interface에만 의존하고 immutable `UiState`, `StateFlow`, explicit action을 사용한다. 단순 전달용 use-case는 만들지 않고 실제 규칙인 BCP 47/필수 입력 검증을 domain validator에 둔다.

## Consequences

장점:

- Room entity와 provider DTO가 UI/domain으로 새지 않는다.
- 뜻/예문/태그를 독립적으로 수정하고 무결성을 DB에서 보장할 수 있다.
- 외부 데이터 갱신과 사용자 소유 데이터 저장 경로가 분리된다.
- 초기 Gradle/빌드 복잡성을 낮추면서 package 경계를 나중에 모듈로 추출할 수 있다.

비용:

- aggregate mapping과 transaction 코드가 필요하다.
- 한 모듈 안에서는 Gradle이 계층 의존성을 강제로 막지 못하므로 review와 테스트가 경계를 지켜야 한다.
- Room relation 기반 목록은 데이터 규모가 커지면 paging/FTS와 query 최적화가 필요할 수 있다.

## Rejected alternatives

- 모든 기능을 `MainActivity`와 단일 ViewModel에 배치: 변경 이유와 테스트 경계가 섞인다.
- Room entity를 UI 모델로 사용: persistence 변경이 UI에 전파되고 사용자/외부 모델 경계가 무너진다.
- 처음부터 다중 Gradle 모듈: 현재 규모에 비해 빌드와 DI 설정 비용이 크다.
- 뜻/예문/태그를 delimiter 문자열이나 JSON column 하나에 저장: 검색, 순서, 관계 무결성과 부분 편집이 어려워진다.
- provider 이름별 `when`을 화면에 배치: 새 공급자 추가가 기존 화면 수정으로 번진다.
