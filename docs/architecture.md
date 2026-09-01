# 아키텍처

## 목표와 경계

LexiShelf는 계정이나 프로젝트 운영 backend 없이 동작하는 local-first Android 앱입니다.
사용자가 작성하고 편집한 vocabulary가 핵심 데이터이며, 외부 dictionary result는 명시적으로
선택하기 전까지 임시 입력 자료로만 취급합니다. Provider update나 검색 결과 refresh는 저장된
사용자 내용을 자동으로 덮어쓰지 않습니다.

현재는 하나의 `app` Gradle module을 사용합니다. 작은 개인용 앱에서 불필요한 module 경계를
만들지 않되 presentation, domain, data와 provider-specific code의 의존성은 package로 분리합니다.

## 코드 구성

```text
app/src/main/java/com/example/localvocabulary/
├── app/                    Application, Activity, Navigation, Hilt modules
├── core/
│   ├── common/             time and shared boundaries
│   ├── database/           Room database, entities, DAO and relations
│   └── ui/                 shared Compose theme and UI primitives
├── feature/
│   ├── backup/             export/restore UI and ViewModel
│   ├── handwriting/        session-only handwriting input
│   ├── settings/           settings and dictionary pack UI
│   ├── tags/               tag CRUD
│   ├── wordbooks/          wordbook CRUD and membership
│   ├── worddetail/         vocabulary detail/delete
│   ├── wordeditor/         manual editing and dictionary suggestions
│   ├── wordlist/           local list/search/filter
│   └── writingpractice/    session-only review workflow
├── vocabulary/
│   ├── domain/             user-owned models and repository contracts
│   └── data/               Room-backed implementations and mapping
├── backup/                 versioned backup domain and JSON/SAF data layer
├── dictionary/
│   ├── domain/             common provider contracts and search models
│   ├── provider/           source-specific parsers, indexes and mappings
│   └── registry/           centralized provider discovery and selection
├── handwriting/            ML Kit boundary and recognition models
└── settings/               DataStore contract and implementation
```

Provider-specific DTO, parser and database row types remain inside their provider package. Room
entities are mapped to domain models before reaching ViewModels, and Compose does not perform
database, network or file I/O directly.

## 화면 상태와 데이터 흐름

Screens use unidirectional data flow:

```text
user action → ViewModel → repository/domain boundary → immutable UiState → Compose
```

각 screen-level ViewModel은 `StateFlow`로 immutable UI state를 노출합니다. Navigation controller는
UI tree 깊숙이 전달하지 않고 screen callback으로 변환합니다. 복구할 가치가 있는 editor 입력과
설정은 `SavedStateHandle`, Room 또는 DataStore의 책임에 맞게 보존합니다.

## 사용자 vocabulary 저장

Room schema version 6은 vocabulary entry를 중심으로 다음 aggregate를 저장합니다.

- headword, BCP 47 source language, reading과 pronunciation
- ordered senses와 meanings
- examples, notes, favorite와 timestamps
- tags와 wordbooks의 many-to-many 관계
- grammatical gender와 review metadata
- dictionary import provenance와 사용자 수정 여부

뜻과 sense는 delimiter-separated string으로 합치지 않습니다. Foreign key, index, uniqueness
constraint와 transaction을 사용해 aggregate와 관계를 일관되게 저장합니다. Room migration은
기존 사용자 데이터를 보존하며 destructive migration을 정상 upgrade 경로로 사용하지 않습니다.

앱 설정은 DataStore에 저장합니다. Dictionary dataset은 별도의 app-private pack 저장소에 두며
사용자 Room DB 또는 JSON backup에 포함하지 않습니다.

## 사용자 편집 데이터 보호

Dictionary search result를 표시하는 것만으로 editor draft나 Room data가 변경되지는 않습니다.
사용자가 suggestion row를 선택했을 때만 허용된 field를 draft에 복사하고 source, license,
dataset version과 source entry identity를 provenance로 남깁니다.

가져온 뜻이나 예문을 사용자가 편집해도 attribution은 유지되며 수정 상태를 구분합니다. 동일한
source sense를 반복 선택해 accidental duplicate를 만들지 않고, provider refresh가 사용자 편집
내용을 갱신하지 않습니다. User-authored sense에는 provider provenance를 만들지 않습니다.

## 언어 식별

내부 언어 identity는 translated display name이 아니라 normalized BCP 47 tag를 사용합니다.
표시 이름은 UI locale에 따라 별도로 계산합니다. Provider는 지원하는 source/result language pair와
result capability를 descriptor로 선언하며, selection logic은 중앙 registry가 담당합니다.

## Dictionary provider와 pack

모든 online/local source는 common `DictionaryProvider` contract를 구현합니다. Contract는 stable
provider ID, 표시 이름, 지원 언어 pair, online/offline capability, attribution/license metadata,
availability와 structured failure를 제공합니다. Raw provider response는 common domain model로
mapping된 뒤에만 presentation으로 전달됩니다.

공개 APK는 dictionary DB를 포함하지 않습니다. 사용자는 versioned catalog에서 필요한 pack만
내려받거나 Storage Access Framework로 local `.dictpack`을 선택합니다. 설치기는 manifest, provider
identity, dataset schema, 크기와 SHA-256을 검증한 뒤 app-private staging에서 atomic activation을
수행합니다. 실패하면 기존 active pack을 유지하고 임시 파일만 제거합니다.

Pack 설치·update·rollback·삭제는 사용자 vocabulary Room DB에 접근하지 않습니다. Pack이 없거나
손상돼도 manual vocabulary workflow와 저장된 단어 조회는 계속 동작합니다. 상세 형식은
[dictionary pack 문서](dictionary-packs.md), 공개 artifact의 정확한 checksum과 라이선스 결정은
[public artifact audit](public-dictionary-artifacts.md)에 있습니다.

## Backup과 restore

Canonical backup은 UTF-8 versioned JSON입니다. 현재 backup schema version은 5이며 document 전체를
decode하고 검증한 뒤에만 Room transaction을 시작합니다. Import는 stable ID와 normalized tag
identity를 사용하고, replace가 필요한 경우 preview와 명시적인 conflict policy를 거칩니다.

알 수 없는 future schema, malformed relation, 중복 ID와 유효하지 않은 언어 tag는 적용 전에
거부합니다. 기존 데이터의 자동 전체 삭제나 silent destructive replacement는 허용하지 않습니다.
자세한 형식은 [backup 문서](backup.md)에 있습니다.

## 개인정보와 비밀정보

Vocabulary, handwriting stroke와 backup data를 프로젝트 운영 server로 보내지 않습니다.
Handwriting stroke와 recognition candidate는 session-only이고 Room/backup에 저장하지 않습니다.
Network는 catalog/pack download, ML Kit SDK/model 동작과 사용자가 연 external dictionary link에만
사용될 수 있습니다.

API key, signing key와 password는 Git에 포함하지 않습니다. Local configuration은 gitignored
properties 또는 process environment를 사용하며, client-side credential storage가 server-side
secret과 같은 보호를 제공한다고 주장하지 않습니다.

## 테스트 전략

- Domain rule과 mapping: JVM unit test
- Repository와 ViewModel state transition: fake 기반 unit test
- Room DAO, migration과 aggregate round trip: Android instrumentation test
- Provider parser/index: 고정된 local fixture와 generated test database
- Backup: version compatibility, validation, conflict와 restoration test
- Critical screen flow: Compose UI test

자동화 test는 live paid API를 호출하지 않습니다. 기능 변경 시 가장 가까운 layer의 test를 함께
수정하고, release 전에는 Wrapper를 사용해 unit test, lint, debug assemble과 필요한 device test를
실제로 실행한 결과만 기록합니다.
