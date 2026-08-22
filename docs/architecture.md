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
    ├── domain/             provider contract, capability/failure/source models
    ├── importer/           licensed result → transient editor seed guard/mapping
    ├── provider/cccedict/  GZip asset reader, v1/v2 parser, exact index, common mapping
    └── registry/           provider discovery and exact language-pair filtering
```

presentation 패키지는 feature별로 분리되고 domain/data/database는 Android UI와 독립된 책임을 갖습니다. 나중에 모듈을 분리할 때 이 패키지 경계를 추출점으로 사용합니다.

## 의존성 방향과 데이터 흐름

```text
Compose Screen -> ViewModel -> Repository interface <- Room repository -> DAO -> Room
                           \-> Settings interface <- DataStore repository

Backup Screen -> BackupViewModel -> backup contracts <- JSON/SAF/Room implementations

WordEditorScreen -> WordEditorViewModel -> DictionaryProviderRegistry -> providers
                             |                          |
               400 ms debounced query       CC-CEDICT provider
                             |                          |
               provider-grouped UiState     raw GZip -> parser -> exact index
                             |
               explicit Use action -> DictionaryEntryDraftMapper
                             |
               editable user/import draft -> VocabularyRepository -> Room
```

- Composable은 I/O를 수행하지 않으며 immutable `UiState`와 명시적 `Action`만 사용합니다.
- ViewModel은 `StateFlow`를 노출하고 `viewModelScope`에서 repository를 호출합니다.
- domain은 Compose, Room entity, DAO, Retrofit DTO를 알지 못합니다.
- data 구현은 Room relation을 domain model로 명시적으로 변환합니다.
- UI에는 Room entity를 전달하지 않습니다.
- 단순 repository 호출의 이름만 바꾸는 use-case 클래스는 두지 않았습니다. 입력 정규화와 규칙을 집행하는 validator만 domain에 둡니다.

## Room schema version 3

| 테이블 | 책임 | 핵심 제약 |
| --- | --- | --- |
| `vocabulary_entries` | headword, BCP 47 tag, notes, timestamps | local auto ID, unique backup ID |
| `senses` | entry별 독립적인 뜻과 품사 | entry FK, cascade delete, index, sort order |
| `examples` | sense별 여러 예문 | sense FK, cascade delete, index, sort order |
| `tags` | 사용자 태그 | normalized name와 backup ID unique index |
| `entry_tag_cross_refs` | entry-tag 다대다 연결 | composite PK, 양쪽 FK/cascade, tag index |
| `sense_dictionary_provenance` | provider-derived sense의 source/license/import 상태 | sense와 1:0 PK/FK, cascade delete |
| `sense_dictionary_provenance_fields` | 해당 sense에서 provider로부터 가져온 field 종류 | provenance FK, composite PK, cascade delete |

뜻과 예문은 delimiter 문자열로 합치지 않습니다. `VocabularyDao.saveEntry`는 entry, senses, examples, tag links 전체를 한 Room transaction으로 저장합니다. 수정 시 `createdAt`은 보존하고 `modifiedAt`만 갱신합니다. 태그 삭제는 교차 참조만 cascade하고 단어는 유지합니다.

검색은 headword, notes, sense meaning, example text에 대해 로컬 SQLite `LIKE`를 사용합니다. `%`, `_`, `\`는 repository boundary에서 escape합니다. 태그 필터는 교차 테이블 `EXISTS` 조건으로 적용합니다.

Room의 auto-generated `Long` PK는 관계 연결과 로컬 query에만 사용합니다. 외부 백업 식별자는 단어와 태그에 별도의 opaque stable ID를 사용합니다. 신규 row에는 UUID를 부여하며 `MIGRATION_1_2`는 기존 row마다 고유한 32자리 hex ID를 생성합니다. `MIGRATION_2_3`은 기존 entry/sense/example/tag/relation/stable ID를 건드리지 않고 nullable 의미의 별도 provenance 테이블 두 개만 생성합니다. schema JSON 1~3과 aggregate 보존 migration test를 유지하며 destructive migration은 사용하지 않습니다.

## 사용자 편집 데이터 보호

`VocabularyEntry`와 `ExternalDictionaryEntry`는 타입과 패키지가 분리되어 있습니다. provider DTO는 provider package 안에서 common external model로 mapping해야 하며 domain/UI로 직접 노출하지 않습니다. 검색 결과 표시에는 persistence permission을 적용하지 않습니다. 사용자가 결과의 `Use`를 선택하면 `DictionaryEntryDraftMapper`가 user-editable `VocabularyEntryDraft`와 별도의 source reference/transient entry를 만듭니다.

Provider 원문을 export 가능한 draft로 복사하려면 app-level `DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS`와 field별 local persistence/redistribution `PERMITTED`가 모두 필요합니다. 법적 permission과 현재 앱의 compliance 준비 상태를 분리하기 위한 이중 gate입니다. `REFERENCE_ONLY`, `UNKNOWN` 또는 `PROHIBITED` 결과는 원문을 transient entry에만 유지하고 manual draft에는 provider content를 넣지 않습니다. 사용자는 inline suggestion/reference를 보면서 자신의 headword, meaning, example, notes, tags를 작성해 정상 저장할 수 있습니다.

CC-CEDICT는 CC BY-SA 4.0의 attribution/ShareAlike 조건을 잃지 않도록 app import mode를 `COPY_EXPORTABLE_FIELDS`로 설정했습니다. explicit `Use`는 허용된 gloss를 새 sense로 추가하고 provider/source entry/source sense/source·license URL/dataset version/import time/imported fields를 provenance로 함께 저장합니다. user-authored sense는 provenance가 없으므로 한 entry 안의 혼합 상태를 표현할 수 있습니다. 기존 sense는 지우지 않고 같은 stable source를 반복 선택하면 추가하지 않습니다. imported sense를 편집하면 provenance를 유지하고 `modifiedAfterImport`만 true로 바꿉니다. 검색 결과 도착·refresh 자체는 editor나 저장된 entry를 갱신하지 않습니다.

## BCP 47 언어 식별

저장 전 `Locale.Builder.setLanguageTag`로 태그를 검증하고 canonical form(예: `EN-gb` → `en-GB`)으로 정규화합니다. `_` 기반 locale 이름은 거부합니다. 표시 문자열을 내부 언어 ID로 사용하지 않습니다. 언어/공급자 선택은 향후 registry가 담당하며 화면별 `when` 분기를 만들지 않습니다.

## 설정과 비밀정보

DataStore에는 현재 새 단어의 기본 BCP 47 태그만 저장합니다. API 키는 저장하지 않습니다. 미래 키용 파일은 empty example만 제공하며 실제 `secrets.properties`, `local.properties`, keystore는 ignore합니다. 공급자가 추가될 때는 Android 로컬 credential 저장 방식을 별도 ADR로 결정하고 클라이언트 저장의 한계를 명시해야 합니다.

## Provider 확장 경계

`DictionaryProviderId`는 persistence 가능한 lowercase stable value입니다. 언어는 validated BCP 47 값으로 표현하고, 독립적인 source/result 언어 집합 대신 `(source language, result language, monolingual definition 또는 translation)` pair를 선언합니다. 따라서 `zh-Hans`와 `zh-Hant`, script, region variant를 임의로 합치거나 지원하지 않는 cross-product를 만들지 않습니다. script code는 언어 태그와 별도 optional field입니다.

Descriptor는 `ONLINE`/`LOCAL_DATASET`, capability set, attribution, license 식별자, dataset artifact/release/format metadata, local persistence/redistribution의 `UNKNOWN|PERMITTED|PROHIBITED`, app import mode, field override, cache policy/note를 제공합니다. Result는 alternate written form, reading, pronunciation text/audio, transliteration, examples, part of speech, gender, inflection, etymology를 optional 값으로 표현합니다. Search page에는 opaque continuation token과 truncation 상태가 있습니다.

Failure는 unsupported source, unsupported result language/kind, missing credential, authentication, rate limit, network, provider unavailable, malformed data, no result, local dataset unavailable, unknown을 구분합니다. CC-CEDICT provider는 asset 부재와 malformed dataset을 구분하며 offline lookup에서는 network failure를 만들지 않습니다.

`DefaultDictionaryProviderRegistry`는 stable ID 중복을 거부하고 exact language pair로 descriptor를 찾습니다. Hilt set multibinding에는 stable ID `cc-cedict` provider가 등록됩니다. Editor의 source language는 저장되는 BCP 47 `languageTag`이고 result language/kind option은 해당 source를 지원하는 registry descriptor에서 만듭니다. 선택된 pair를 지원하는 모든 provider를 동시에 검색하며 결과와 오류는 provider별 immutable group으로 표시하므로 CC 전용 `when`이 없습니다. 새 provider package는 구현과 `@IntoSet` binding, fixture/tests만 추가하면 같은 inline UI를 재사용합니다.

Headword 검색 request는 trim된 query와 exact language pair의 immutable 값입니다. 빈 query/pair는 실행하지 않고 `StateFlow.debounce(400ms)`, `distinctUntilChanged`, `collectLatest`를 적용합니다. provider들은 한 request 안에서 병렬 검색하지만 각 failure/exception은 해당 provider group에만 격리합니다. 새 결과는 suggestion state만 바꾸며 editor form은 건드리지 않습니다. 명시적인 `Use`도 사용자가 이미 sense/example을 입력했거나 기존 entry를 편집 중이면 그 값을 보존합니다.

## CC-CEDICT dataset과 index

선택한 전략은 raw UTF-8 GZip asset + lazy process-local exact index입니다. 수동 설치한 `cedict_1_0_ts_utf-8_mdbg.txt.gz`를 `CcCedictAssetSource`가 열고 `CcCedictParser`가 v1 single bracket와 v2 double bracket pinyin 형식을 구분합니다. comment/blank line은 count하고 malformed line은 line number/raw text/reason을 가진 issue로 보고하며, 유효 record를 손상시키거나 전체 앱을 crash시키지 않습니다. 유효 record가 하나도 없으면 malformed dataset failure입니다.

index는 원본 file order를 보존한 Simplified/Traditional map 두 개입니다. exact match 결과가 여러 개면 같은 순서로 반환하고 result limit 적용 여부를 `isTruncated`로 표시합니다. pinyin/prefix/fuzzy index는 만들지 않았습니다. parser record와 index type은 provider package의 `internal` type이며 presentation에는 common `ExternalDictionaryEntry`만 전달합니다. pinyin은 transient `DictionaryReading`, 다른 script form은 `DictionaryWrittenForm`, English slash sense/semicolon gloss 구조는 common sense/meaning으로 명시적으로 mapping합니다. vocabulary domain에는 아직 generic reading 저장 field가 없으므로 pinyin은 Room/backup/notes로 복사하지 않습니다. CC-CEDICT가 제공하지 않는 POS, example, etymology, audio는 비어 있으며 추론하지 않습니다.

asset은 user Room과 별도이며 앱 시작 시 import하지 않습니다. 첫 검색만 background dispatcher에서 전체 parse/index 비용을 내고 같은 process에서 재사용합니다. 향후 language pack은 `CcCedictDatasetSource` 구현만 교체할 수 있습니다. 전략 비교와 persistence 결정은 [ADR-0004](decisions/0004-cc-cedict-local-dataset.md), 설치/update 절차는 [cc-cedict-dataset.md](cc-cedict-dataset.md)에 있습니다.

## 백업/복원 경계

canonical backup은 Room schema와 독립된 `schemaVersion: 2` UTF-8 JSON입니다. v2는 sense provenance를 포함하고 user-authored sense는 해당 값이 null입니다. v1 DTO와 의미는 그대로 유지하며 decoder가 v1을 provenance 없는 current import model로 올립니다. version별 serializable DTO는 `backup.domain`, strict codec과 검증은 `backup.data`, 화면 상태와 SAF contract launcher는 `feature.backup`에 둡니다. Composable은 URI 선택 결과를 action으로 전달할 뿐 파일이나 DB I/O를 하지 않습니다.

가져오기 흐름은 `파일 읽기 → JSON parsing → schema version 분기 → 전체/필드/관계 validation → preview → 사용자 확인 → Room transaction`입니다. `ValidatedBackup`만 repository import 경계에 전달할 수 있어 parse되지 않은 문서가 DB 단계로 들어가지 않습니다. 실제 반영은 outer `VocabularyDatabase.withTransaction` 안에서 실행되며 aggregate 저장의 nested Room transaction도 같은 transaction에 참여합니다. 예외가 발생하면 삭제·태그·단어·관계 변경을 모두 rollback합니다.

기본 병합은 stable ID가 같은 entry의 뜻/예문/메모/태그 관계/시간을 포함한 aggregate 전체를 예측 가능하게 교체하고, 새 stable ID는 추가하며, 백업에 없는 기존 entry는 보존합니다. field-level merge는 하지 않습니다. 전체 교체는 UI에서 별도 선택과 미리보기를 거칩니다. normalized tag identity가 같으면 기존 tag row를 재사용하므로 공유 태그가 중복되지 않습니다. 세부 schema와 version migration 규칙은 [backup.md](backup.md), 결정 근거는 [ADR-0002](decisions/0002-versioned-json-backup.md)에 있습니다.

## 테스트 전략

- JVM: BCP 47/입력 규칙, Room relation/provenance mapping, JSON v1→v2 parse/validation, repository timestamp/aggregate behavior, ViewModel state transition, provider contract/registry/editor seed policy, CC-CEDICT parser/index/autofill, editor debounce/latest-query/provider grouping/error isolation/user-edit protection
- Android instrumented: in-memory Room의 aggregate/search/tag cascade와 provenance export/import round trip/rollback/conflict policy, v1→v2 및 v2→v3 migration
- Compose instrumented: 편집 validation, provider별 inline suggestion, compact attribution, imported source indicator 같은 핵심 UI 계약
- CC-CEDICT tests는 출처와 CC BY-SA 4.0 notice가 있는 작은 UTF-8 fixture만 사용하며 full dataset이나 network에 의존하지 않음
