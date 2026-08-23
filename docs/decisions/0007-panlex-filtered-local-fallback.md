# ADR-0007: PanLex filtered local Korean fallback

- 상태: Accepted
- 날짜: 2026-08-23
- 관련 결정: [ADR-0003](0003-dictionary-provider-boundary.md), [ADR-0005](0005-dictionary-import-provenance.md), [ADR-0006](0006-korean-basic-dictionary-local-reverse-index.md)

## Context

한국어기초사전은 11개 외국어를 지원하지만 German, Hindi, Polish, Latin은 포함하지 않는다. PanLex는 많은 language variety의 expression을 source별 meaning/denotation 관계로 제공하며 local-first fallback 후보가 될 수 있다.

검토 시점의 공식 API와 snapshot host는 DNS에서 사용할 수 없었고 현재 snapshot page도 artifact manifest를 제공하지 않았다. 확인 가능한 공식 2019-09-01 CSV snapshot은 1.27GB ZIP/5.43GB 비압축이라 APK에 그대로 포함하거나 runtime에서 parse하기 어렵다. PanLex graph에서 여러 언어를 연결해 번역을 추론하면 실제 source가 attested하지 않은 결과가 생성될 수 있다.

## Decision

- stable provider ID `panlex`, access `LOCAL_DATASET`을 사용한다.
- 현재 APK 범위는 실제 coverage를 확인한 `deu-000`, `hin-000`, `pol-000`, `lat-000`과 `kor-000`의 양방향 exact translation뿐이다.
- BCP 47 → PanLex variety mapping은 converter의 reviewed allowlist에 명시한다. 동일 macrolanguage의 다른 variety를 자동 병합하지 않는다.
- build-time Python converter가 같은 source-owned meaning에 직접 co-denotation된 distance-1 relation만 선택한다. pivot 또는 graph inference는 금지한다.
- 같은 expression pair의 여러 attestation은 PanLex `tr1q` source-group quality 개념으로 합치고 representative PanLex meaning/source ID를 보존한다.
- 최종 53,211,136-byte SQLite index는 user Room과 분리하고 release별 app private directory에서 read-only로 조회한다.
- CC0 grant 아래 import mode는 `COPY_EXPORTABLE_FIELDS`이지만 `Use`가 선택된 번역과 provenance만 user vocabulary sense로 복사한다. dataset 전체를 Room/backup에 저장하지 않는다.
- 현재 한국어기초사전이 지원하는 11개 언어는 측정만 하고 중복 provider/APK 비용을 피하기 위해 PanLex descriptor와 asset에서 제외한다.

## Consequences

장점:

- 네 신규 언어에서 network/API key 없이 한국어 양방향 exact fallback을 제공한다.
- provider-specific IDs, ranking, SQLite schema는 PanLex package에 머물고 기존 generic inline suggestion/editor/backup 경계를 재사용한다.
- user-authored/provider-imported data는 snapshot refresh와 분리되어 자동 overwrite되지 않는다.
- clean clone에서 asset이 없어도 build와 manual vocabulary 기능이 유지된다.

비용과 제한:

- 선택 snapshot은 2019년 자료이며 현재 official distribution outage 때문에 최신성이 낮다.
- exact relation만 제공하며 definition, POS, example, inflection, morphology, fuzzy/prefix search는 없다.
- 53MB raw SQLite가 APK 크기와 첫 copy 시간을 늘린다. 공개 배포 전 language-pack 전달을 검토해야 한다.
- expression pair가 여러 meaning/source에서 attested되어도 현재 UI result는 representative provenance 한 개와 aggregate score만 가진다.
- official distribution이 복구되면 source/license/checksum/variety mapping/coverage를 다시 review해야 한다.

## Rejected alternatives

- 현재 PanLex API: host가 사용할 수 없고 offline/local-first를 만족하지 못한다.
- full CSV/JSON/SQL snapshot bundling: 크기와 runtime 비용이 과도하다.
- third-party transformed dataset: source, release, 변환 규칙과 license chain을 독립적으로 검증하기 어렵다.
- English pivot 또는 multi-hop graph translation: 같은 source가 attested한 직접 lexical relation이 아니다.
- 기존 11개 언어까지 한 번에 포함: 현재 provider와 중복되고 초기 APK 비용만 늘린다.
- user Room에 PanLex row import: dictionary release lifecycle을 user-owned editable data/migration/backup과 결합한다.
