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
│   ├── backup/             SAF launchers, backup UiState and ViewModel
│   ├── wordlist/           list/search/filter UI and ViewModel
│   ├── worddetail/         detail/delete UI and ViewModel
│   ├── wordeditor/         add/edit UI and ViewModel
│   ├── tags/               tag CRUD UI and ViewModel
│   └── settings/           basic settings UI and ViewModel
├── vocabulary/
│   ├── domain/             user-owned models and repository contracts
│   └── data/               Room-backed implementations and mapping
├── backup/
│   ├── domain/             versioned document, validation result, repository/file contracts
│   └── data/               JSON codec, ContentResolver I/O, transactional Room import/export
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

Backup Screen -> BackupViewModel -> backup contracts <- JSON/SAF/Room implementations

Future search UI -> DictionaryProvider interface <- provider-specific implementation
```

- Composable은 I/O를 수행하지 않으며 immutable `UiState`와 명시적 `Action`만 사용합니다.
- ViewModel은 `StateFlow`를 노출하고 `viewModelScope`에서 repository를 호출합니다.
- domain은 Compose, Room entity, DAO, Retrofit DTO를 알지 못합니다.
- data 구현은 Room relation을 domain model로 명시적으로 변환합니다.
- UI에는 Room entity를 전달하지 않습니다.
- 단순 repository 호출의 이름만 바꾸는 use-case 클래스는 두지 않았습니다. 입력 정규화와 규칙을 집행하는 validator만 domain에 둡니다.

## Room schema version 2

| 테이블 | 책임 | 핵심 제약 |
| --- | --- | --- |
| `vocabulary_entries` | headword, BCP 47 tag, notes, timestamps | local auto ID, unique backup ID |
| `senses` | entry별 독립적인 뜻과 품사 | entry FK, cascade delete, index, sort order |
| `examples` | sense별 여러 예문 | sense FK, cascade delete, index, sort order |
| `tags` | 사용자 태그 | normalized name와 backup ID unique index |
| `entry_tag_cross_refs` | entry-tag 다대다 연결 | composite PK, 양쪽 FK/cascade, tag index |

뜻과 예문은 delimiter 문자열로 합치지 않습니다. `VocabularyDao.saveEntry`는 entry, senses, examples, tag links 전체를 한 Room transaction으로 저장합니다. 수정 시 `createdAt`은 보존하고 `modifiedAt`만 갱신합니다. 태그 삭제는 교차 참조만 cascade하고 단어는 유지합니다.

검색은 headword, notes, sense meaning, example text에 대해 로컬 SQLite `LIKE`를 사용합니다. `%`, `_`, `\`는 repository boundary에서 escape합니다. 태그 필터는 교차 테이블 `EXISTS` 조건으로 적용합니다.

Room의 auto-generated `Long` PK는 관계 연결과 로컬 query에만 사용합니다. 외부 백업 식별자는 단어와 태그에 별도의 opaque stable ID를 사용합니다. 신규 row에는 UUID를 부여하며 `MIGRATION_1_2`는 기존 row마다 고유한 32자리 hex ID를 생성합니다. schema JSON 1과 2를 모두 보존하고 migration test로 데이터 보존과 ID uniqueness를 검증합니다. destructive migration은 정상 전략으로 사용하지 않습니다.

## 사용자 편집 데이터 보호

`VocabularyEntry`와 `ExternalDictionaryEntry`는 타입과 패키지가 분리되어 있습니다. provider DTO를 domain/UI로 직접 노출하지 않으며 현재 provider 구현은 없습니다. 향후 외부 결과를 저장하려면 사용자가 선택한 값을 새로운 `VocabularyEntryDraft`로 복사하고, 원본 attribution/source ID를 별도 필드로 모델링하는 명시적 흐름이 필요합니다. background refresh가 현재 Room aggregate를 갱신하는 API는 만들지 않습니다.

## BCP 47 언어 식별

저장 전 `Locale.Builder.setLanguageTag`로 태그를 검증하고 canonical form(예: `EN-gb` → `en-GB`)으로 정규화합니다. `_` 기반 locale 이름은 거부합니다. 표시 문자열을 내부 언어 ID로 사용하지 않습니다. 언어/공급자 선택은 향후 registry가 담당하며 화면별 `when` 분기를 만들지 않습니다.

## 설정과 비밀정보

DataStore에는 현재 새 단어의 기본 BCP 47 태그만 저장합니다. API 키는 저장하지 않습니다. 미래 키용 파일은 empty example만 제공하며 실제 `secrets.properties`, `local.properties`, keystore는 ignore합니다. 공급자가 추가될 때는 Android 로컬 credential 저장 방식을 별도 ADR로 결정하고 클라이언트 저장의 한계를 명시해야 합니다.

## Provider 확장 경계

`DictionaryProvider`는 안정 ID/display name, source/definition languages, online/offline, capability set, attribution/license metadata, search, optional exact lookup, structured error, availability check를 제공합니다. 등록과 선택은 `DictionaryProviderRegistry` 경계로 중앙화합니다.

이번 마일스톤에는 registry 구현, provider DTO, HTTP client, 로컬 dataset parser가 없습니다. 라이선스 승인이 선행되어야 합니다.

## 백업/복원 경계

canonical backup은 Room schema와 독립된 `schemaVersion: 1` UTF-8 JSON입니다. version별 serializable DTO는 `backup.domain`, strict codec과 검증은 `backup.data`, 화면 상태와 SAF contract launcher는 `feature.backup`에 둡니다. Composable은 URI 선택 결과를 action으로 전달할 뿐 파일이나 DB I/O를 하지 않습니다.

가져오기 흐름은 `파일 읽기 → JSON parsing → schema version 분기 → 전체/필드/관계 validation → preview → 사용자 확인 → Room transaction`입니다. `ValidatedBackup`만 repository import 경계에 전달할 수 있어 parse되지 않은 문서가 DB 단계로 들어가지 않습니다. 실제 반영은 outer `VocabularyDatabase.withTransaction` 안에서 실행되며 aggregate 저장의 nested Room transaction도 같은 transaction에 참여합니다. 예외가 발생하면 삭제·태그·단어·관계 변경을 모두 rollback합니다.

기본 병합은 stable ID가 같은 entry의 뜻/예문/메모/태그 관계/시간을 포함한 aggregate 전체를 예측 가능하게 교체하고, 새 stable ID는 추가하며, 백업에 없는 기존 entry는 보존합니다. field-level merge는 하지 않습니다. 전체 교체는 UI에서 별도 선택과 미리보기를 거칩니다. normalized tag identity가 같으면 기존 tag row를 재사용하므로 공유 태그가 중복되지 않습니다. 세부 schema와 version migration 규칙은 [backup.md](backup.md), 결정 근거는 [ADR-0002](decisions/0002-versioned-json-backup.md)에 있습니다.

## 테스트 전략

- JVM: BCP 47/입력 규칙, Room relation mapping, JSON parse/validation, repository timestamp/aggregate behavior, ViewModel state transition
- Android instrumented: in-memory Room의 aggregate/search/tag cascade와 export/import round trip/rollback/conflict policy, v1→v2 migration
- Compose instrumented: 편집 validation 표시 같은 핵심 UI 계약
- provider가 추가되면 local fixtures만 사용하고 live/paid API를 테스트에서 호출하지 않음
