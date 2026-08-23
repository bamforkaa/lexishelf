# ADR-0006: 한국어기초사전 로컬 SQLite 역색인

- 상태: Accepted
- 날짜: 2026-08-23
- 관련 결정: [ADR-0003](0003-dictionary-provider-boundary.md), [ADR-0005](0005-dictionary-import-provenance.md)

## Context

한국어기초사전은 공식 Open API와 전체 데이터 내려받기를 모두 제공한다. Open API는 이메일로 발급받은 key와 네트워크가 필요하고 일일 50,000건 제한이 있다. 전체 내려받기는 Excel/XML/JSON 형태로 제공되며 현재 JSON은 한국어 표제어와 11개 언어 번역을 포함한다.

핵심 use case는 외국어 exact term에서 한국어 후보를 찾는 reverse lookup이다. API 방식은 사용자 credential 관리와 네트워크 failure 처리가 필요하고 전체 역색인을 제공하지 않는다. 공식 전체 JSON은 약 1GB 비압축이고 수십만 번역을 포함하므로 raw asset을 실행 중 전부 parse하거나 메모리에 올리는 것도 적절하지 않다.

## Decision

- 공식 전체 JSON download를 선택하고 stable provider ID를 `korean-basic-dictionary`, access를 `LOCAL_DATASET`으로 둔다.
- build 전 명시적으로 실행하는 Python 변환기가 별도 read-only SQLite exact/reverse index를 만든다. 앱이나 Gradle은 데이터를 자동 다운로드하지 않는다.
- user vocabulary Room, Room schema version, backup schema version과 dictionary index schema/version을 서로 독립시킨다.
- 런타임은 asset을 release별 `noBackupFilesDir`에 한 번 복사한 뒤 indexed SQL exact query만 수행한다. 전체 dataset을 heap에 올리지 않는다.
- `ko ↔ en|ja|fr|es|ar|mn|vi|th|id|ru|zh`의 공식 translation pair만 선언한다. 공식 중국어 source가 script를 구분하지 않으므로 `zh`를 사용한다.
- official file order, sense order, translation order를 deterministic result order로 유지한다. 역색인은 공식 표기와 명시적으로 나열된 대안만 정규화하며 generated/machine translation을 만들지 않는다.
- 전체 export에서 lexical ID가 관용구에 재사용되는 실제 구조 때문에 app source entry ID는 `공식 ID:표제어`, source sense ID는 공식 sense ID로 기록한다. 이 값은 generic provenance pipeline을 그대로 사용한다.
- CC BY-SA 2.0 KR 조건 아래 text local persistence/backup/redistribution을 `PERMITTED`로 표현하고 source/license/release/수정 상태를 provenance로 동반한다. multimedia는 제외한다.

## Consequences

장점:

- API key와 network 없이 한국어↔외국어 양방향 exact lookup이 가능하다.
- 약 194만 reverse key를 디스크 index로 조회해 startup heap과 반복 parse 비용을 피한다.
- provider-specific schema/type은 provider package와 converter에 머물고 editor/Room/backup은 기존 common model과 provenance를 재사용한다.
- 사전 pack 교체 실패가 사용자 Room transaction이나 backup에 영향을 주지 않는다.

비용과 제한:

- 현재 생성 index는 211,701,760 bytes여서 APK 크기와 첫 asset 복사 비용이 크다. 후속 language-pack delivery가 필요할 수 있다.
- exact lookup만 지원하며 prefix/fuzzy search는 없다.
- 공식 ID가 단독으로 전역 유일하지 않아 표제어와 합친 source ID를 쓴다. 공식 표제어가 바뀌면 새 source reference로 취급하는 보수적 동작을 한다.
- release마다 전체 변환, metadata 갱신, attribution/license 재검토가 필요하다.

## Rejected alternatives

- Open API: reverse offline lookup, local-first, no-key 목표를 만족하지 못한다.
- raw JSON runtime parse/in-memory index: 1GB급 source와 다국어 역색인을 기기 heap에 두기 어렵다.
- user Room에 dictionary rows import: user-owned lifecycle, migration, backup과 provider dataset lifecycle을 결합한다.
- generated translation 또는 language-wide fuzzy key: 공식 human-edited source 범위를 넘어선다.
