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
│   ├── handwriting/        temporary Ink session UI state and recognition orchestration
│   ├── writingpractice/     session-only question rules, UiState, setup/practice/summary UI
│   ├── tags/               tag CRUD UI and ViewModel
│   ├── wordbooks/          wordbook CRUD, detail and batch membership UI
│   └── settings/           basic settings UI and ViewModel
├── vocabulary/
│   ├── domain/             user-owned models and repository contracts
│   └── data/               Room-backed implementations and mapping
├── backup/
│   ├── domain/             versioned document, validation result, repository/file contracts
│   └── data/               JSON codec, ContentResolver I/O, transactional Room import/export
├── settings/               DataStore contract and implementation
├── handwriting/
│   ├── domain/             stroke/model/result contracts, insertion policy
│   └── data/               ML Kit identifier/model/recognizer adapter
└── dictionary/
    ├── domain/             provider contract, capability/failure/source models
    ├── importer/           licensed result → transient editor seed guard/mapping
    ├── provider/cccedict/  active-pack GZip reader, v1/v2 parser, exact index, common mapping
    ├── provider/koreanbasic/ read-only SQLite exact/reverse lookup, common mapping
    ├── provider/panlex/    filtered read-only SQLite direct relation lookup, common mapping
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
               immutable raw groups         CC raw GZip -> parser -> exact index
                             |
               suggestion synthesizer
                             |
               language-grouped UiState
                             |               Korean JSON-derived read-only SQLite
                             |               PanLex CSV-derived filtered read-only SQLite
                             |
               explicit suggestion-row selection -> DictionaryEntryDraftMapper
                             |
               editable user/import draft -> VocabularyRepository -> Room

WordEditorScreen -> HandwritingInputViewModel -> HandwritingRecognitionService
       Canvas (x/y/time)             |                    ^
       explicit candidate tap        v                    |
       -> HeadwordChanged       ML Kit adapter -> on-device model

WritingPracticeScreen -> WritingPracticeViewModel -> VocabularyRepository
        setup/scope action              |                  |
        explicit submit                 v                  v
        session-only summary      compact practice row <- Room DAO
                 |
                 +-> shared HandwritingInputViewModel -> explicit candidate -> answer text
```

- Composable은 I/O를 수행하지 않으며 immutable `UiState`와 명시적 `Action`만 사용합니다.
- ViewModel은 `StateFlow`를 노출하고 `viewModelScope`에서 repository를 호출합니다.
- domain은 Compose, Room entity, DAO, Retrofit DTO를 알지 못합니다.
- data 구현은 Room relation을 domain model로 명시적으로 변환합니다.
- UI에는 Room entity를 전달하지 않습니다.
- 단순 repository 호출의 이름만 바꾸는 use-case 클래스는 두지 않았습니다. 입력 정규화와 규칙을 집행하는 validator만 domain에 둡니다.

손글씨 adapter는 Compose, Word Editor나 WordList 상태를 알지 못합니다. 공용
`HandwritingInputViewModel`은 model 선택 전 Ink, 350ms stroke-end debounce와
generation/cancellation을 소유합니다. 후보 callback은 editor에서는 기존
`WordEditorAction.HeadwordChanged`, 목록에서는 `WordListAction.QueryChanged`로 전달됩니다.
따라서 dictionary debounce, stale import 정리, duplicate 검사와 기존 Room search/filter를 그대로
거칩니다. 획·후보·모델 상태는 Room/backup에 기록하지 않고 최근 model 언어 5개만 별도
DataStore preference에 둡니다.

쓰기 연습도 같은 `HandwritingInputViewModel`, stable Canvas, language resolver와 ML Kit adapter를
사용합니다. 새 문제·retry·입력 mode 변경 때 증가하는 `handwritingSessionKey`를 destination이
관찰해 현재 entry 언어로 새 handwriting session을 열기 때문에 이전 Ink, 후보, answer와 stale
recognition job이 다음 문제로 넘어가지 않습니다. 후보 tap은 `AnswerChanged`만 보내며 submit을
자동 실행하지 않습니다. unsupported/missing model 상태는 inline 영역에만 머물고 키보드 mode는
항상 선택할 수 있습니다.

`WritingPracticeViewModel`은 전체/언어/Wordbook/Tag filter를 `VocabularyRepository`에 전달하고,
DAO는 aggregate 대신 entry ID와 대표 meaning/reading/pronunciation/POS/gender/example만 담은
compact projection을 반환합니다. setup에서 안전한 힌트가 있는 ID를 한 번 shuffle하고 10/20/전체
길이로 snapshot하여 recomposition이나 vocabulary Flow 갱신이 진행 중 순서를 바꾸지 않습니다.
세션 state와 first-attempt 결과는 메모리에만 있으며 Room/DataStore/backup write 경로가 없습니다.

## Room schema version 6

Task 8의 v4는 `vocabulary_entries.reading`과 provider-neutral
`entry_dictionary_provenance`를 추가합니다. Entry-level reading provenance와 sense-level
meaning/POS/example provenance를 분리하여 sense 삭제가 reading 출처를 없애지 않게 합니다.
`MIGRATION_3_4`는 기존 aggregate를 건드리지 않고 빈 reading과 빈 entry provenance만
추가합니다. JMdict 전용 Room 컬럼은 없습니다. Task 10C의 `MIGRATION_4_5`는
기존 aggregate와 Tag를 변경하지 않고 독립적인 Wordbook 및 entry-wordbook relation만
추가합니다. 기존 `en`, `ja` 같은 사용자 Tag는 삭제하거나 의미를 추정하지 않습니다.
`MIGRATION_5_6`는 ordered textual pronunciation과 그 entry-level provenance table을 추가하고
`senses`에 nullable grammatical gender columns를 더합니다. 기존 entry/sense/example/relation은
갱신하거나 재작성하지 않습니다.

| 테이블 | 책임 | 핵심 제약 |
| --- | --- | --- |
| `vocabulary_entries` | headword, BCP 47 tag, notes, timestamps | local auto ID, unique backup ID |
| `senses` | entry별 독립적인 뜻, 품사와 optional 문법 성 | entry FK, cascade delete, index, sort order |
| `examples` | sense별 여러 예문 | sense FK, cascade delete, index, sort order |
| `vocabulary_pronunciations` | entry별 ordered textual pronunciation | stable ID unique, entry FK/cascade, notation/value/language/order |
| `pronunciation_dictionary_provenance` | provider-derived pronunciation 출처 | pronunciation과 1:0 PK/FK, cascade delete |
| `tags` | 사용자 태그 | normalized name와 backup ID unique index |
| `entry_tag_cross_refs` | entry-tag 다대다 연결 | composite PK, 양쪽 FK/cascade, tag index |
| `wordbooks` | 사용자 단어 컬렉션 | normalized name와 backup ID unique index |
| `entry_wordbook_cross_refs` | entry-wordbook 다대다 연결 | composite PK, 양쪽 FK/cascade, wordbook index |
| `sense_dictionary_provenance` | provider-derived sense의 source/license/import 상태 | sense와 1:0 PK/FK, cascade delete |
| `sense_dictionary_provenance_fields` | 해당 sense에서 provider로부터 가져온 field 종류 | provenance FK, composite PK, cascade delete |

뜻, 예문과 발음은 delimiter 문자열로 합치지 않습니다. `VocabularyDao.saveEntry`는 entry, ordered pronunciation, senses, examples, tag/wordbook links 전체를 한 Room transaction으로 저장합니다. 수정 시 `createdAt`은 보존하고 `modifiedAt`만 갱신합니다. Tag나 Wordbook 삭제는 해당 교차 참조만 cascade하고 단어/sense/example/발음/다른 분류는 유지합니다. Wordbook batch add는 `INSERT IGNORE`, batch remove는 단일 `DELETE ... IN (...)`을 DAO transaction에서 수행하므로 중복 관계나 부분 반영을 남기지 않습니다.

검색은 headword, notes, sense meaning, example text에 대해 로컬 SQLite `LIKE`를 사용합니다. `%`, `_`, `\`는 repository boundary에서 escape합니다. Language는 `vocabulary_entries.language_tag`, Tag와 Wordbook은 각각의 교차 테이블 `EXISTS` 조건으로 독립 필터링합니다. 목록 query는 pronunciation relation을 로드하지 않는 별도 Room projection을 사용합니다. 쓰기 연습도 scope를 SQL에서 제한한 compact hint projection만 읽으며 모든 entry aggregate를 메모리에 올리지 않습니다. editor/detail/backup의 단일 aggregate load만 ordered pronunciation 전체를 읽습니다.

Room의 auto-generated `Long` PK는 관계 연결과 로컬 query에만 사용합니다. 외부 백업 식별자는 단어, pronunciation, Tag, Wordbook에 별도의 opaque stable ID를 사용합니다. 신규 row에는 UUID를 부여하며 `MIGRATION_1_2`는 기존 row마다 고유한 32자리 hex ID를 생성합니다. schema JSON 1~6과 각 단계 aggregate 보존 migration test를 유지하며 destructive migration은 사용하지 않습니다.

## 사용자 편집 데이터 보호

`VocabularyEntry`와 `ExternalDictionaryEntry`는 타입과 패키지가 분리되어 있습니다. provider DTO는 provider package 안에서 common external model로 mapping해야 하며 domain/UI로 직접 노출하지 않습니다. 검색 결과 표시에는 persistence permission을 적용하지 않습니다. 사용자가 selectable result/sense row를 누르면 `DictionaryEntryDraftMapper`가 user-editable `VocabularyEntryDraft`와 별도의 source reference/transient entry를 만듭니다.

Provider 원문을 export 가능한 draft로 복사하려면 app-level `DictionaryVocabularyImportMode.COPY_EXPORTABLE_FIELDS`와 field별 local persistence/redistribution `PERMITTED`가 모두 필요합니다. 법적 permission과 현재 앱의 compliance 준비 상태를 분리하기 위한 이중 gate입니다. `REFERENCE_ONLY`, `UNKNOWN` 또는 `PROHIBITED` 결과는 원문을 transient entry에만 유지하고 manual draft에는 provider content를 넣지 않습니다. 사용자는 inline suggestion/reference를 보면서 자신의 headword, meaning, example, notes, tags를 작성해 정상 저장할 수 있습니다.

CC-CEDICT·한국어기초사전·JMdict·Kaikki는 각각의 CC BY-SA attribution/ShareAlike 조건을 잃지 않도록, PanLex는 확인한 고정 artifact의 CC0 grant 아래 app import mode를 `COPY_EXPORTABLE_FIELDS`로 설정했습니다. 명시적 row tap은 허용된 gloss/번역, POS, example, textual pronunciation과 grammatical gender를 field capability에 따라 추가하고 provider/source entry/source sense/source·license URL/dataset version/import time/imported fields를 provenance로 함께 저장합니다. `ExternalDictionarySense.sourceSenseId`는 optional common field이며 이를 제공하지 않는 기존 provider는 기존 순번 fallback을 유지합니다. user-authored field는 provenance가 없으므로 한 entry 안의 혼합 상태를 표현할 수 있습니다. 선택한 row를 다시 누르면 editor session의 field/example snapshot과 비교해 그 suggestion이 추가한 unchanged contribution만 제거합니다. 사용자가 고친 meaning/POS/example/pronunciation/gender와 새로 쓴 field는 보존하면서 provenance 연결을 해제합니다. 다른 선택이 같은 reading이나 pronunciation을 사용하는 경우 해당 field는 유지합니다. 검색 결과 도착·refresh 자체는 editor나 저장된 entry를 갱신하지 않습니다.

## BCP 47 언어 식별

저장 전 `Locale.Builder.setLanguageTag`로 태그를 검증하고 canonical form(예: `EN-gb` → `en-GB`)으로 정규화합니다. `_` 기반 locale 이름은 거부합니다. 중앙 `AppLanguageCatalog`의 17개 built-in 항목은 source metadata이며, Settings와 Word Editor의 같은 `LanguagePickerField`가 이름·autonym·code 검색을 제공합니다. catalog 밖의 valid tag를 picker에서 확정하면 canonical tag만 DataStore의 `user_language_tags` set에 저장합니다. built-in과 canonical identity가 같으면 별도 사용자 항목을 만들지 않습니다. 사용자 항목의 autonym/localized/English name과 script/region 표기는 플랫폼 `Locale` metadata에서 만들고, metadata가 부족하면 canonical raw tag로 fallback합니다. 현재 vocabulary/default 값이 어느 catalog에도 없어도 transient current item으로 표시합니다. 표시 이름은 내부 identity나 Tag로 저장하지 않습니다.

LanguagePicker, Settings, Tag/Wordbook 관리와 batch 목록은 Compose 기본 `LazyColumn`/`LazyRow`만 사용하며 custom pointer/fling 배율이 없습니다. Word Editor는 화면 전체 `LazyColumn`과 selectable suggestion 5개 이상일 때만 생기는 352dp 내부 `LazyColumn`의 Compose 기본 nested scrolling을 사용합니다. 실제 touch fling을 임의로 느리게 만들지 않으며, 에뮬레이터 mouse wheel/host trackpad 속도는 호스트 입력 변환 차이로 취급합니다. 마지막 항목 도달, swipe 중 row 오선택 방지, suggestion 내부 목록 동작을 UI test로 검증합니다.

## 중복 저장 보호

새 entry 저장 시 repository는 canonical source language가 같은 행만 읽고 headword를 trim, 연속 공백 축약, Unicode NFC, `Locale.ROOT` lowercase한 identity로 비교합니다. fuzzy matching과 DB UNIQUE constraint는 사용하지 않습니다. 후보가 있으면 저장을 멈추고 기존 entry 열기 또는 명시적인 별도 저장을 제공하므로 homograph와 과거 중복은 유효합니다. 기존 entry 편집은 자신의 ID를 제외합니다.

## 설정과 비밀정보

DataStore에는 새 단어의 기본 BCP 47 태그와 picker에서 추가한 canonical 사용자 언어 tag set을 저장합니다. 이는 vocabulary aggregate가 아닌 앱 preference이므로 canonical JSON backup v5에는 포함하지 않습니다. vocabulary 자체의 언어 태그는 계속 Room/backup에 저장되고 catalog 밖의 현재 값도 picker에 표시됩니다. API 키는 저장하지 않습니다. 미래 키용 파일은 empty example만 제공하며 실제 `secrets.properties`, `local.properties`, keystore는 ignore합니다. 공급자가 추가될 때는 Android 로컬 credential 저장 방식을 별도 ADR로 결정하고 클라이언트 저장의 한계를 명시해야 합니다.

## Provider 확장 경계

`DictionaryProviderId`는 persistence 가능한 lowercase stable value입니다. 언어는 validated BCP 47 값으로 표현하고, 독립적인 source/result 언어 집합 대신 `(source language, result language, monolingual definition 또는 translation)` pair를 선언합니다. 따라서 `zh-Hans`와 `zh-Hant`, script, region variant를 임의로 합치거나 지원하지 않는 cross-product를 만들지 않습니다. script code는 언어 태그와 별도 optional field입니다.

Descriptor는 `ONLINE`/`LOCAL_DATASET`, capability set, attribution, license 식별자, dataset artifact/release/format metadata, local persistence/redistribution의 `UNKNOWN|PERMITTED|PROHIBITED`, app import mode, field override, cache policy/note를 제공합니다. Result는 alternate written form, reading, pronunciation text/audio, transliteration, examples, part of speech, gender, 대표 inflection과 전체 form count, 원문 없이 제공 가능한 example count, etymology를 optional 값으로 표현합니다. Search page에는 opaque continuation token과 truncation 상태가 있습니다.

Failure는 unsupported source, unsupported result language/kind, missing credential, authentication, rate limit, network, provider unavailable, malformed data, no result, local dataset unavailable, unknown을 구분합니다. CC-CEDICT provider는 asset 부재와 malformed dataset을 구분하며 offline lookup에서는 network failure를 만들지 않습니다.

`DefaultDictionaryProviderRegistry`는 stable ID 중복을 거부하고 exact language pair로 descriptor를 찾습니다. Hilt set multibinding에는 `cc-cedict`, `korean-basic-dictionary`, `panlex`, `jmdict`, `kaikki`가 등록됩니다. Editor의 source language는 저장되는 BCP 47 `languageTag`이고 result language/kind option은 해당 source를 지원하는 registry descriptor에서 만듭니다. 지원되는 모든 pair/provider를 동시에 검색하고 결과와 오류는 provider별 immutable raw group으로 먼저 보존합니다. 그 위의 presentation synthesis만 result language 중심으로 정리하므로 provider-specific `when`이 UI에 퍼지지 않습니다. 새 provider package는 구현과 `@IntoSet` binding, fixture/tests만 추가하면 같은 inline UI를 재사용합니다.

Headword 검색 request는 trim된 query와 exact language pair 집합의 immutable 값입니다. 빈 query/pair 집합은 실행하지 않고 `StateFlow.debounce(400ms)`, `distinctUntilChanged`, `collectLatest`를 적용합니다. provider들은 한 request 안에서 병렬 검색하지만 각 failure/exception은 해당 provider/pair group에만 격리합니다. 새 결과는 raw/synthesized suggestion state만 바꾸며 editor form은 건드리지 않습니다. 명시적인 row tap도 사용자가 이미 sense/example을 입력했거나 기존 entry를 편집 중이면 그 값을 보존합니다.

Headword 또는 source-language/search-context identity가 바뀌면 이번 editor session에서 import한 contribution만 즉시 draft에서 정리하고 synthesis selection/check state를 초기화합니다. unchanged provider meaning/POS/reading/pronunciation/gender/example은 제거하지만 user-authored 또는 수정된 text, notes, Tag, Wordbook은 유지합니다. 기존 저장 entry에서 불러온 aggregate는 session snapshot이 아니므로 draft headword 변경만으로 삭제되지 않으며 Room은 Save transaction 전까지 변하지 않습니다.

## CC-CEDICT dataset과 index

선택한 전략은 installable pack의 raw UTF-8 GZip + lazy process-local exact index입니다. `CcCedictPackSource`가 `DictionaryPackResolver`의 active payload를 열고 `CcCedictParser`가 v1 single bracket와 v2 double bracket pinyin 형식을 구분합니다. comment/blank line은 count하고 malformed line은 line number/raw text/reason을 가진 issue로 보고하며, 유효 record를 손상시키거나 전체 앱을 crash시키지 않습니다. 유효 record가 하나도 없으면 malformed dataset failure입니다.

index는 원본 file order를 보존한 Simplified/Traditional map 두 개입니다. exact match 결과가 여러 개면 같은 순서로 반환하고 result limit 적용 여부를 `isTruncated`로 표시합니다. pinyin/prefix/fuzzy index는 만들지 않았습니다. parser record와 index type은 provider package의 `internal` type이며 presentation에는 common `ExternalDictionaryEntry`만 전달합니다. pinyin은 common `DictionaryReading`, 다른 script form은 `DictionaryWrittenForm`, English slash sense/semicolon gloss 구조는 common sense/meaning으로 명시적으로 mapping합니다. Task 8에서 추가된 provider-neutral reading field를 통해 사용자가 suggestion row를 명시적으로 누르면 pinyin도 Room/backup에 저장할 수 있고 provenance를 따로 유지합니다. refresh는 저장된 reading을 덮어쓰지 않습니다. CC-CEDICT가 제공하지 않는 POS, example, etymology, audio는 비어 있으며 추론하지 않습니다.

pack은 user Room과 별도입니다. 일반/release 앱은 사용자가 SAF로 설치한 pack만 사용합니다. Manual QA용 debug opt-in은 configured root의 pack을 debug APK에 포함하고 첫 실행에 동일한 production 검증·atomic activation 경로로 설치하지만 user Room에는 접근하지 않습니다. 같은 payload hash는 다시 설치하지 않습니다. 첫 검색만 background dispatcher에서 전체 parse/index 비용을 내고 active pack identity가 같은 동안 재사용합니다. 설치/update/delete로 identity가 바뀌면 cache를 다시 엽니다. 전략 비교와 persistence 결정은 [ADR-0004](decisions/0004-cc-cedict-local-dataset.md), 공통 lifecycle은 [ADR-0009](decisions/0009-installable-dictionary-packs.md)에 있습니다.

## 한국어기초사전 dataset과 reverse index

공식 Open API와 전체 download를 비교해 local-first reverse exact lookup에 적합한 전체 JSON을 선택했습니다. 개발 시 `tools/build_krdict_index.py`가 JSON ZIP을 한 chunk씩 읽어 provider 전용 SQLite로 변환합니다. user vocabulary Room에는 dictionary row를 넣지 않으며 Room schema와 backup schema도 바꾸지 않습니다.

인덱스는 Korean normalized headword와 `en`, `ja`, `fr`, `es`, `ar`, `mn`, `vi`, `th`, `id`, `ru`, `zh`별 normalized reverse key를 가집니다. NFC, trim, 연속 whitespace와 root-case만 정규화하고, 공식 번역 문자열에 명시적으로 나열된 대안 외의 단어를 생성하지 않습니다. `ko → foreign`은 official sense/translation 순서를, `foreign → ko`는 source entry/sense 순서를 유지합니다. prefix/fuzzy search는 없습니다.

공식 전체 export의 lexical ID가 관련 관용구에 재사용되므로 dictionary DB 관계에는 internal integer PK를 쓰고 common provenance source entry ID에는 `공식 ID:표제어`, source sense ID에는 공식 sense ID를 사용합니다. provider-specific row와 SQLite type은 `provider.koreanbasic` 밖으로 나오지 않고 `ExternalDictionaryEntry`/`ExternalDictionarySense`로 매핑됩니다.

211,701,760-byte SQLite는 base APK에 복사하지 않고 active pack 경로에서 read-only로 엽니다. 누락/손상은 해당 provider group의 오류로만 변환됩니다. CC BY-SA 2.0 KR text는 generic provenance와 backup에 attribution을 동반하며 audio/image/video/pronunciation media는 index와 앱에서 제외합니다.

## PanLex filtered direct-relation index

PanLex provider도 user Room과 별도인 read-only SQLite lifecycle을 사용합니다. `tools/build_panlex_index.py`는 검토한 BCP 47→PanLex variety mapping의 expression과 `kor-000` expression을 고르고, 같은 source-owned meaning에 함께 denotation된 distance-1 relation만 생성합니다. 다른 언어를 거치는 pivot이나 graph inference는 만들지 않습니다.

같은 expression pair의 여러 attestation은 source group별 최대 quality를 합산하고 대표 `meaning_id`/`source_id`, source/group count를 보존합니다. runtime query는 NFC/trim/whitespace/root-case exact key와 indexed `LIMIT + 1`만 사용해 결과 제한과 truncation을 계산합니다. PanLex-specific relation/SQLite 타입은 `provider.panlex` 내부에서 common `ExternalDictionaryEntry`로 매핑됩니다.

현재 descriptor는 기존 한국어기초사전 범위와 겹치지 않는 `de|hi|pl|la|nl|pt|it|tr|cs|sv|fi|uk ↔ ko`를 선언합니다. shared build config가 converter와 pack manifest의 24개 양방향 pair를 만들고, runtime mirror와 DB `supported_language_tags` metadata가 다르면 index를 거부합니다. 생성 index는 1,098,758 unique expression relations/189,714,432 bytes입니다. missing/malformed dataset은 PanLex suggestion group에만 표시되고 editor manual save를 막지 않습니다. 고정한 2019 artifact의 CC0 source/license/release와 PanLex expression/meaning/source ID는 row toggle 후 generic sense provenance와 backup에 보존되며 dataset refresh는 user sense를 갱신하지 않습니다. 현재 PanLex 배포물의 라이선스가 다르므로 새 artifact에는 이 결정을 자동 적용하지 않습니다. 결정은 [ADR-0007](decisions/0007-panlex-filtered-local-fallback.md), 재현/coverage/update 절차는 [panlex-dataset.md](panlex-dataset.md)에 있습니다.

## Kaikki per-language English fallback index

`tools/build_kaikki_indexes.py`는 checksum으로 고정한 official English Wiktionary raw Wiktextract JSONL을 streaming으로 한 번 읽고 reviewed language별 compact SQLite를 만든다. NFC/trim/whitespace 뒤 기본적으로 `Locale.ROOT`, Turkish에는 `tr` dotted/dotless-I case mapping을 converter/runtime 양쪽에서 동일하게 적용한 exact key만 색인하며 homograph entry와 source sense 순서를 합치지 않는다. runtime은 raw JSONL을 읽지 않고 provider package 내부의 abbreviated record를 common `ExternalDictionaryEntry`로 명시적으로 mapping한다.

provider ID는 하나의 `kaikki`이고 pack은 `kaikki.<source>-en` per-language다. generic `DictionaryPackResolver`의 기존 provider-only API를 유지하면서 `providerId + DictionaryLanguagePair` overload를 추가해 여러 active pack이 공존한다. 한 language pack의 누락/손상은 해당 suggestion group만 `LocalDatasetUnavailable`/malformed가 되고 다른 Kaikki pack과 provider를 막지 않는다.

Kaikki index schema v3는 headword, raw POS, English gloss/sense, textual pronunciation, semantic policy로 선택한 최대 8개 display form, 신뢰 가능한 전체 morphology form count, form→lemma reverse index, gender와 import 가능한 usage example text/count를 보존한다. Task 18은 DDL을 바꾸지 않고 optional sense payload `u`에 reviewed whitelist label만 추가한다. generic `DictionarySenseLabel`은 register, temporal, grammar, region을 분리하고 source sense 및 synthesis contribution 경계를 유지한다. suggestion은 최대 세 label과 `+N`을 표시하지만 전체 label을 accessibility semantics에 제공한다. raw/unknown tags, categories, topics, form mechanics는 UI로 전달하지 않으며 label은 Room/backup/import에 저장하지 않는다. 각 sense는 원본 순서의 최대 두 example만 보유하고 `quotation`, `ref`가 있는 attributed text, category/graph/full etymology와 audio/media는 제외한다. row tap은 기존 mapper로 English gloss/POS/example과 허용된 textual pronunciation/gender를 source/license/release provenance와 함께 가져온다. forms, labels와 morphology context는 transient UI metadata로 유지한다. Exact provider 결과를 먼저 보존한 뒤 `DictionaryMorphologyResolver`가 source metadata로 semantic eligibility를 통과한 실제 relation을 exact-case/source order로 정렬하고 distinct lemma 최대 5개를 반환한다. 첫 lemma는 primary, 나머지는 alternate analysis로 표시하며 registry provider를 `exactLookup`으로 한 번만 재조회한다. 자세한 정책은 [usage-labels.md](usage-labels.md), [linguistic-forms.md](linguistic-forms.md), [morphology-search.md](morphology-search.md), [ADR-0016](decisions/0016-bounded-forms-and-source-attested-morphology.md), [ADR-0017](decisions/0017-morphology-lemma-eligibility-and-ambiguity-ranking.md), [ADR-0018](decisions/0018-learner-facing-sense-label-whitelist.md)에 있다. Room v6와 backup v5는 변경하지 않는다.

## 백업/복원 경계

canonical backup은 Room schema와 독립된 `schemaVersion: 5` UTF-8 JSON입니다. v5는 stable ordered pronunciation/provenance와 sense grammatical gender를 추가하며 v1/v2/v3/v4 import는 새 field가 빈 기존 의미로 현재 모델에 올라옵니다. v4 Wordbook과 v3 reading/provenance의 user-authored null semantics도 그대로 유지합니다. version별 serializable DTO는 `backup.domain`, strict codec과 검증은 `backup.data`, 화면 상태와 SAF contract launcher는 `feature.backup`에 둡니다. Composable은 URI 선택 결과를 action으로 전달할 뿐 파일이나 DB I/O를 하지 않습니다.

## JMdict compact local index

`JmDictDataSource`만 별도 read-only SQLite를 읽고 compressed XML-derived record는
`provider.jmdict` 밖으로 노출되지 않습니다. DB는 `DictionaryPackResolver`의 active pack에서 열며 common transient model은 alternative readings와
writing/reading restriction set을 표현합니다. mapper는 `re_restr`/`re_nokanji`와
`stagk`/`stagr`를 적용한 sense만 내보냅니다. inline UI는 sense row 자체를 선택하며 refresh는
Room repository를 호출하지 않습니다. 자세한 형식과 수치는 [jmdict-dataset.md](jmdict-dataset.md)에 있습니다.

가져오기 흐름은 `파일 읽기 → JSON parsing → schema version 분기 → 전체/필드/관계 validation → preview → 사용자 확인 → Room transaction`입니다. `ValidatedBackup`만 repository import 경계에 전달할 수 있어 parse되지 않은 문서가 DB 단계로 들어가지 않습니다. 실제 반영은 outer `VocabularyDatabase.withTransaction` 안에서 실행되며 aggregate 저장의 nested Room transaction도 같은 transaction에 참여합니다. 예외가 발생하면 삭제·태그·단어·관계 변경을 모두 rollback합니다.

기본 병합은 stable ID가 같은 entry의 뜻/예문/메모/태그 관계/시간을 포함한 aggregate 전체를 예측 가능하게 교체하고, 새 stable ID는 추가하며, 백업에 없는 기존 entry는 보존합니다. field-level merge는 하지 않습니다. 전체 교체는 UI에서 별도 선택과 미리보기를 거칩니다. normalized tag identity가 같으면 기존 tag row를 재사용하므로 공유 태그가 중복되지 않습니다. 세부 schema와 version migration 규칙은 [backup.md](backup.md), 결정 근거는 [ADR-0002](decisions/0002-versioned-json-backup.md)에 있습니다.

## 테스트 전략

- JVM: BCP 47/입력 규칙, Room relation/provenance/practice projection mapping, JSON v1→v2 parse/validation, repository timestamp/aggregate behavior, ViewModel state transition, 쓰기 연습 hint leak 방지·NFC exact 판정·scope/session/retry 통계, provider contract/registry/editor seed policy, CC-CEDICT parser/index/autofill, 한국어기초사전 양방향 mapping/언어/license/autofill, PanLex mapping/ranking/provenance/registry coexistence, editor debounce/latest-query/provider grouping/error isolation/user-edit protection
- Python fixture: 한국어기초사전 공식 JSON shape/lexical ID/forward-reverse SQLite, PanLex direct relation/variety allowlist/source-group ranking, Kaikki homograph/sense/rich metadata/Unicode/checksum/atomic failure cleanup
- Android instrumented: in-memory Room의 aggregate/search/Tag·Wordbook cascade와 scope별 compact practice query, pronunciation/gender/provenance를 포함한 export-import round trip·rollback·conflict policy, v1→v2→v3→v4→v5→v6 데이터 보존 migration, 작은 별도 SQLite fixture의 한국어기초사전·PanLex·Kaikki exact query와 malformed schema 처리
- Compose instrumented: 편집 validation, provider별 inline suggestion, compact attribution, imported source indicator, 쓰기 연습 entry point·headword 숨김·candidate 명시 선택·keyboard fallback·stable inline Canvas 같은 핵심 UI 계약
- CC-CEDICT tests는 출처와 CC BY-SA 4.0 notice가 있는 작은 UTF-8 fixture만 사용하며 full dataset이나 network에 의존하지 않음

## Dictionary pack과 editor orchestration

`dictionary.pack`은 generic manifest codec, installer/repository/resolver를 소유합니다. 다섯 provider의 source는 stable provider ID로 active pack file을 얻고 provider-specific parser/SQLite mapping은 기존 package 안에 남습니다. Kaikki처럼 provider 하나가 여러 pack을 가지면 exact language pair까지 resolver key에 포함하고 기존 one-pack provider 동작은 유지합니다. SAF URI/ZIP/checksum/파일 교체는 data boundary에서만 처리하고 Settings Composable은 action을 보낼 뿐 I/O를 하지 않습니다. pack 삭제/update는 `VocabularyDatabase`를 주입받지 않습니다.

Word Editor는 provider ID가 아니라 result language preference(`ko`, `en`, 나머지)에 따라 모든 지원 결과를 정렬합니다. provider/pair query limit은 20이고 raw provider group은 유지합니다. `DictionarySuggestionSynthesizer`는 NFC·trim·공백 축약을 사용하되 대소문자는 보존하며, exact lexical identity와 restriction/POS/sense 경계가 안전한 경우에만 여러 source contribution을 한 visible candidate로 묶습니다. UI index를 쓰지 않는 stable key는 result language, normalized lexical/semantic content, 정렬된 source identity set으로 만듭니다. primary source는 import 가능 field, 예문/reading/POS/보조 metadata, 중앙화된 provider role, source identity 순서로 결정하고 기존 mapper/provenance 경로만 사용합니다. 합성된 selectable row가 4개 이하면 자연 높이 `Column`, 5개 이상이면 352dp로 제한한 단일 `LazyColumn`을 사용합니다. 자세한 실제 overlap과 정책은 [dictionary-synthesis.md](dictionary-synthesis.md)에 있습니다.

`ExternalDictionaryReferenceProvider`는 content provider와 별도입니다. NAVER 구현은 검증된 BCP 47 base language mapping과 headword로 URI만 만들며 Word Editor의 Reading 바로 아래 secondary action으로 표시합니다. ViewModel effect 뒤 user tap에서만 `ACTION_VIEW`를 보내고 network client, HTML parser, downloader, Room/backup mapping dependency가 없습니다.
