# ADR-0011: Kaikki를 언어별 English fallback pack으로 제공

## Status

Accepted — 2026-08-26

## Context

기존 PanLex는 여러 유럽·아시아 언어와 한국어의 직접 lexical relation을 제공하지만 English gloss와 POS/pronunciation/forms가 없다. Kaikki English Wiktionary extraction은 richer English fallback을 제공하지만 raw gzip 하나가 2.6GiB이고 언어별 설치 크기 편차가 매우 크다. 한 provider가 여러 independently installable language pack을 가져야 하며 user vocabulary Room과 provider dataset lifecycle은 계속 분리되어야 한다.

## Decision

- stable provider ID는 `kaikki`, access는 `LOCAL_DATASET`으로 둔다.
- current official raw JSONL release와 SHA-256을 고정하고 build-time streaming converter가 reviewed language만 compact read-only SQLite로 만든다.
- 첫 batch는 measured coverage/크기를 기준으로 `de, hi, pl, nl, pt, tr, cs, sv, uk, vi, th, id → en`이다.
- pack은 `kaikki.<language>-en` per-language 단위이며 manifest는 exact pair 하나만 선언한다. generic resolver를 `providerId + DictionaryLanguagePair` 조회로 확장하되 기존 one-pack provider API는 유지한다.
- runtime exact key는 NFC, trim, whitespace collapse, locale-independent lowercase만 사용한다. homograph와 sense/source order를 보존하고 fuzzy/morphology를 만들지 않는다.
- POS, pronunciation, gender, 대표 forms/전체 count와 example은 common result model로 표현한다. Room/backup schema는 확장하지 않는다.
- explicit row tap만 English gloss/POS와 provenance를 generic mapper로 가져온다. refresh는 user-authored data를 갱신하지 않는다.
- Wiktionary text의 dual license 중 CC BY-SA 4.0 재사용 경로를 선택한다. `type=example`이고 외부 `ref`가 없는 usage example은 sense당 최대 두 개를 source order로 보존한다. quotation/attributed text, audio/media, category/graph/full etymology는 제외한다.
- release/base APK에는 pack을 포함하지 않는다. debug는 `debugDictionaryPackLanguages`에 명시한 일부 pack만 opt-in bundle할 수 있다.

## Consequences

- 언어 pack 하나가 없거나 손상돼도 같은 provider의 다른 언어 pack과 수동 편집은 계속 동작한다.
- 새 언어는 candidate coverage/license/size 검토, shared config와 runtime mirror 갱신, converter/pack/QA test를 거쳐 추가한다.
- 전체 selected DB는 약 1.20GiB이므로 모든 pack의 default debug bundling은 금지한다.
- pronunciation/forms는 transient이다. usage example은 기존 sense/example aggregate와 generic sense provenance를 재사용하므로 Room/backup migration 없이 영구 저장과 export가 가능하다.
- mutable official URL의 새 파일을 기존 release로 간주하지 않으며 checksum mismatch는 명시적인 update 작업을 요구한다.
