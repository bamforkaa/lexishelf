# 아키텍처

## 목표와 범위

첫 마일스톤은 사용자 직접 입력 기반의 로컬 단어장입니다. 외부 사전 결과는 임시 source material이고 사용자 단어는 영속적이며 자유롭게 편집 가능한 별도 모델입니다. 이 경계 때문에 향후 공급자 갱신이 사용자 편집 내용을 자동으로 덮어쓸 수 없습니다.

## 모듈과 패키지

현재는 하나의 Android `app` 모듈입니다.

```text
com.example.localvocabulary/
├── app/                    Application, Activity, Navigation, Hilt modules
├── core/
│   ├── common/             time boundary
│   ├── database/           Room database, entities, DAO, relations
│   └── ui/                 shared Compose theme
├── feature/
│   ├── wordlist/           list/search/filter UI and ViewModel
│   ├── worddetail/         detail/delete UI and ViewModel
│   ├── wordeditor/         add/edit UI and ViewModel
│   ├── tags/               tag CRUD UI and ViewModel
│   └── settings/           basic settings UI and ViewModel
├── vocabulary/
│   ├── domain/             user-owned models and repository contracts
│   └── data/               Room-backed implementations and mapping
├── settings/               DataStore contract and implementation
└── dictionary/
    ├── domain/             provider contract and external result models
    └── registry/           future provider discovery boundary
```

presentation 패키지는 feature별로 분리되고 domain/data/database는 Android UI와 독립된 책임을 갖습니다. 나중에 모듈을 분리할 때 이 패키지 경계를 추출점으로 사용합니다.

## 의존성 방향과 데이터 흐름

```text
Compose Screen -> ViewModel -> Repository interface <- Room repository -> DAO -> Room
                           \-> Settings interface <- DataStore repository

Future search UI -> DictionaryProvider interface <- provider-specific implementation
```

- Composable은 I/O를 수행하지 않으며 immutable `UiState`와 명시적 `Action`만 사용합니다.
- ViewModel은 `StateFlow`를 노출하고 `viewModelScope`에서 repository를 호출합니다.
- domain은 Compose, Room entity, DAO, Retrofit DTO를 알지 못합니다.
- data 구현은 Room relation을 domain model로 명시적으로 변환합니다.
- UI에는 Room entity를 전달하지 않습니다.
- 단순 repository 호출의 이름만 바꾸는 use-case 클래스는 두지 않았습니다. 입력 정규화와 규칙을 집행하는 validator만 domain에 둡니다.

## Room schema version 1

| 테이블 | 책임 | 핵심 제약 |
| --- | --- | --- |
| `vocabulary_entries` | headword, BCP 47 tag, notes, timestamps | local auto ID |
| `senses` | entry별 독립적인 뜻과 품사 | entry FK, cascade delete, index, sort order |
| `examples` | sense별 여러 예문 | sense FK, cascade delete, index, sort order |
| `tags` | 사용자 태그 | normalized name unique index |
| `entry_tag_cross_refs` | entry-tag 다대다 연결 | composite PK, 양쪽 FK/cascade, tag index |

뜻과 예문은 delimiter 문자열로 합치지 않습니다. `VocabularyDao.saveEntry`는 entry, senses, examples, tag links 전체를 한 Room transaction으로 저장합니다. 수정 시 `createdAt`은 보존하고 `modifiedAt`만 갱신합니다. 태그 삭제는 교차 참조만 cascade하고 단어는 유지합니다.

검색은 headword, notes, sense meaning, example text에 대해 로컬 SQLite `LIKE`를 사용합니다. `%`, `_`, `\`는 repository boundary에서 escape합니다. 태그 필터는 교차 테이블 `EXISTS` 조건으로 적용합니다.

schema JSON은 `app/schemas/com.example.localvocabulary.core.database.VocabularyDatabase/1.json`에 export합니다. 첫 영속 릴리스 이후 schema 변경에는 검토된 migration과 migration test가 필요합니다. destructive migration은 정상 전략으로 사용하지 않습니다.

## 사용자 편집 데이터 보호

`VocabularyEntry`와 `ExternalDictionaryEntry`는 타입과 패키지가 분리되어 있습니다. provider DTO를 domain/UI로 직접 노출하지 않으며 현재 provider 구현은 없습니다. 향후 외부 결과를 저장하려면 사용자가 선택한 값을 새로운 `VocabularyEntryDraft`로 복사하고, 원본 attribution/source ID를 별도 필드로 모델링하는 명시적 흐름이 필요합니다. background refresh가 현재 Room aggregate를 갱신하는 API는 만들지 않습니다.

## BCP 47 언어 식별

저장 전 `Locale.Builder.setLanguageTag`로 태그를 검증하고 canonical form(예: `EN-gb` → `en-GB`)으로 정규화합니다. `_` 기반 locale 이름은 거부합니다. 표시 문자열을 내부 언어 ID로 사용하지 않습니다. 언어/공급자 선택은 향후 registry가 담당하며 화면별 `when` 분기를 만들지 않습니다.

## 설정과 비밀정보

DataStore에는 현재 새 단어의 기본 BCP 47 태그만 저장합니다. API 키는 저장하지 않습니다. 미래 키용 파일은 empty example만 제공하며 실제 `secrets.properties`, `local.properties`, keystore는 ignore합니다. 공급자가 추가될 때는 Android 로컬 credential 저장 방식을 별도 ADR로 결정하고 클라이언트 저장의 한계를 명시해야 합니다.

## Provider 확장 경계

`DictionaryProvider`는 안정 ID/display name, source/definition languages, online/offline, capability set, attribution/license metadata, search, optional exact lookup, structured error, availability check를 제공합니다. 등록과 선택은 `DictionaryProviderRegistry` 경계로 중앙화합니다.

이번 마일스톤에는 registry 구현, provider DTO, HTTP client, 로컬 dataset parser가 없습니다. 라이선스 승인이 선행되어야 합니다.

## 백업/복원 설계(미구현)

canonical backup은 다음 원칙의 UTF-8 JSON입니다.

- 최상위 `schemaVersion`
- entry/sense/example/tag/cross-reference를 lossless하게 표현
- 전체 문서를 임시 구조로 deserialize하고 참조/언어 태그/필수 필드를 먼저 검증
- 적용 전 신규/중복/충돌 수 preview
- replace, merge, cancel 중 사용자가 명시적으로 선택하는 conflict policy
- transaction 적용과 실패 시 전체 rollback
- provider raw response나 API credential은 포함하지 않음

현재 OS cloud backup도 비활성화했으므로 uninstall 전에 데이터 보존 수단이 없습니다. JSON 구현은 완료 조건으로 남아 있습니다.

## 테스트 전략

- JVM: BCP 47/입력 규칙, Room relation mapping, repository timestamp/aggregate behavior, ViewModel state transition
- Android instrumented: in-memory Room의 aggregate/search/tag cascade
- Compose instrumented: 편집 validation 표시 같은 핵심 UI 계약
- provider가 추가되면 local fixtures만 사용하고 live/paid API를 테스트에서 호출하지 않음
